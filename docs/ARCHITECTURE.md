<!--
SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later
-->

# Architecture

How the code is organised and how a tethering request travels from a tap to Android's tethering service. What the app does and why is in [DESIGN.md](DESIGN.md); the platform research behind it is in [RESEARCH.md](RESEARCH.md).

## Modules

```
app  ──►  backend  ──►  engine  ──►  model
  └──────────────────────►─┘
build-logic (convention plugins, build only)
```

| Module | Type | Role | Android code |
|---|---|---|---|
| `model` | Pure Kotlin (JVM) | Shared types: `Ipv4Address`, `Ipv4Subnet`, `BackendKind`, `BackendState`, the tether request (`TetherInput`, `TetherRequest`) and its validation (`TetherRequestValidator`, prefix 16 to 30); profiles (`Profile`, `Presets`, `ProfileList`, `ProfileLimits`) and their versioned JSON format for storage, import and export (`ProfileJson`). | None, so all of it is tested on the plain JVM. |
| `engine` | Android library | The engine logic (`TetheringEngine`, `StartOperation`, `RawSteps`, `StatusPublisher` which pushes every status change to the app) over the `TetheringPort` interface; the reflection layer (`engine.reflect`) that implements the port with the device's own hidden classes; the kernel interface reader; the Shizuku user service (`EngineUserService`) and its AIDL interface and Bundle codec (`engine.service`). | The logic uses no Android types and is tested with fakes; Android code is limited to the reflection layer and the service. |
| `backend` | Android library | Shizuku and Sui detection; the Shizuku readiness check, the permission request, binding the engine's user service (`EngineConnection`, `ShizukuUserServiceBinder`) and the client the app calls (`EngineClient`). | Behind `ShizukuApi` and `UserServiceBinder`, faked in tests. |
| `app` | Android application | Compose UI (home, onboarding, guided Shizuku setup, profiles and editor, log, settings, about, open-source libraries, engine debug screen); a plain message with the next step for every result; the tether controller, foreground service, notification, tile, widget and boot receiver (`tether`); settings, profiles and the log (`data`); the app-wide wiring in `EtherTetherApplication` (no DI framework). | Yes. |
| `build-logic` | Gradle included build | Convention plugins `ethertether.jvm.library`, `ethertether.android.library`, `ethertether.android.application`; the `verify<Variant>Permissions` and `verify<Variant>KeepRules` tasks. | Build only. |

There is no root helper and no root module (see DESIGN.md, "Left out on purpose").

## Build rules

- SDK levels: compileSdk 37, targetSdk 36, minSdk 30. Java and Kotlin bytecode target 17, built with JDK 21.
- Kotlin warnings are errors. Android lint runs on every module (also `model`, through the `com.android.lint` plugin) with warnings as errors; each exception in `lint.xml` has a reason.
- `verifyDebugPermissions` and `verifyReleasePermissions` read the merged manifest and fail on INTERNET, AD_ID, location, contacts or phone-state permissions.
- Release builds use R8 shrinking and optimization with `-dontobfuscate`, for readable logs and reproducible builds. `engine/consumer-rules.pro` keeps `EngineUserService` and its AIDL stub, because Shizuku starts the service by class name. `verifyReleaseKeepRules` reads R8's `seeds.txt` and fails the build if they were not kept (the same goes for the constructors Room and WorkManager create by name).
- The list of open-source libraries is generated at build time by the AboutLibraries plugin, offline, in strict mode: a shipped library with a license other than Apache-2.0, MIT or BSD fails the build.
- The dependency-info signing block is disabled (`dependenciesInfo`), as F-Droid and IzzyOnDroid scanners require.
- Debug builds use the application id suffix `.debug`, so a debug and a release build can be installed side by side.
- Release builds are unsigned except in the release workflow, which provides the release key through environment variables (`RELEASE_KEYSTORE_FILE` and the passwords); signatures v2 and v3. The version comes from `appVersion`, the published certificate from `ethertether.releaseCertSha256` in `gradle.properties`. Details in [RELEASING.md](RELEASING.md).

## How a tethering request runs

```
App process (EtherTether's uid)              Shizuku user service process (uid 2000, shell)
─────────────────────────────────            ──────────────────────────────────────────────
EngineClient ──EngineCodec Bundles──►  IEtherTetherService (EtherTether's own AIDL)
   ▲   EngineConnection                       EngineUserService
   │   (bind, uid check, protocol,              └► TetheringEngine (validates again)
   │    death handling)                              └► ReflectiveTetheringPort
   │                                                     └► ITetheringConnector (system "tethering" service)
Shizuku.bindUserService (documented API)                     callerPkg "com.android.shell"
```

1. The app checks readiness (`ShizukuReadiness`): Shizuku running, not Sui, server API 11 or newer, access granted, uid 2000. Shizuku as root, Sui and plain root are not supported.
2. `EngineConnection` binds `EngineUserService` through `Shizuku.bindUserService` (non-daemon, so it ends with the app; the version changes with every installed APK so Shizuku never keeps old code). It waits up to 15 s, then checks that the service runs as uid 2000 and speaks the same `EngineCodec.PROTOCOL_VERSION`. A dead service, or a dead Shizuku, is forgotten and bound again on the next call.
   With Shizuku v13 or later, Shizuku creates the service with the app's Context, and the service then answers only callers with EtherTether's app id or its own uid (`CallerCheck`); older servers use the no-argument constructor. Both constructors are kept by the engine's consumer rules and proven kept by `verifyReleaseKeepRules`.
3. Requests cross the boundary as the raw `TetherInput`; the service validates them again with `TetherRequestValidator`, so only values Android accepts reach the engine.
4. In the service, `TetheringEngine` follows the rules taken from the Android source:
   - register the tethering event callback once (failure is tolerated and reported);
   - `stopTethering(ETHERNET)` and wait until Ethernet leaves the serving lists;
   - `startTethering` with `setExemptFromEntitlementCheck(true)`, and once more with `false` on error 14;
   - local-only uses `CONNECTIVITY_SCOPE_LOCAL` (Android 12 and later); if the phone refuses it and the user allowed the fallback, one more start with `CONNECTIVITY_SCOPE_GLOBAL` (never after errors 3, 12, 14, 16, 18, which another scope cannot fix); on Android 11 local-only is refused unless the fallback is allowed;
   - result 0 only means accepted: success is confirmed when Ethernet appears in `localOnlyList` or `tetheredList`, failure when it appears in `erroredIfaceList` with a new error; no event within 10 s means "waiting for the adapter";
   - without the event callback, a static start is confirmed only by seeing the phone address on an Ethernet interface (kernel view), and the result is marked unconfirmed.
5. Every step is recorded (`EngineStep`), every special path becomes a note (`EngineNote`), and the outcome (`Outcome`) is turned into a message with the next step in `app/ui/result`.

Removing the adapter ends Ethernet tethering for good. The status reader shows it as "no adapter"; after a status that showed serving, the watchdog says the adapter was removed, or, with keep armed, sends the request again so the next adapter is served (see "App layer").

### Why a user service and not ShizukuBinderWrapper

The simpler approach wraps the tethering binder with `ShizukuBinderWrapper` in the app process. EtherTether runs the work in a Shizuku user service instead, as Shizuku's documentation describes: the service runs as the Shizuku server's uid, so the system sees uid 2000 and `com.android.shell` on every call, including callback registration, and "there are no restrictions on non-SDK APIs in the user service process", so no hidden API bypass library is needed (hiddenapibypass is not a dependency). The cost is a bind step, which some vendor ROMs are reported to delay; the bind timeout reports this plainly.

## Hidden API uses

Every hidden API the engine touches, with its source and the versions it was checked against. "Source" means verified in the Android source; "Device" means verified on a real phone. All are reached only from the user service process, by reflection, through the names in `engine/reflect/HiddenNames.kt`.

| API | Used for | Source | Checked versions |
|---|---|---|---|
| `android.os.ServiceManager.getService(String)` | the `tethering` service binder | frameworks/base `core/java/android/os/ServiceManager.java` | Source 11 to 17. Device: Android 16 |
| `android.net.ITetheringConnector$Stub.asInterface(IBinder)` | the device's own proxy, so transaction codes come from the device | Connectivity `ITetheringConnector.aidl` | Source 11 to 17. Device: Android 16 |
| `ITetheringConnector.startTethering` | start | same | Source: `(TetheringRequestParcel, String callerPkg, IIntResultListener)` on 11; `(TetheringRequestParcel, String, String callingAttributionTag, IIntResultListener)` on 12 to 17. Resolved by parameter types (`ConnectorMethods`). Device: Android 16 |
| `ITetheringConnector.stopTethering` | stop | same | Source: `(int type, String, IIntResultListener)` on 11; with the attribution tag on 12 to 17. `stopTetheringRequest` (16 and later) is never used and never mistaken for it (first parameter type). Device: Android 16 |
| `ITetheringConnector.registerTetheringEventCallback` and `unregisterTetheringEventCallback` | live state | same | Source 11 to 17: `(ITetheringEventCallback, String callerPkg)`. Device: Android 16 |
| `android.net.TetheringManager$TetheringRequest$Builder` (`<init>(int)`, `setStaticIpv4Addresses(LinkAddress, LinkAddress)`, `setExemptFromEntitlementCheck(boolean)`, `setShouldShowEntitlementUi(boolean)`, `setConnectivityScope(int)`, `build()`) and `TetheringRequest.getParcel()` | building the request with the device's own parcel layout | Connectivity `TetheringLib/src/android/net/TetheringManager.java` | Source 11 to 17; `setConnectivityScope` from 12 (its presence decides scope support at run time). Device: Android 16 |
| `android.net.LinkAddress(InetAddress, int)` | the static addresses | frameworks/base `core/java/android/net/LinkAddress.java` | Device: Android 16 |
| `android.net.IIntResultListener` and `$Stub.TRANSACTION_onResult` | result code callback, a plain `Binder` behind a `Proxy` of the hidden interface | Connectivity `IIntResultListener.aidl` | Source 11 to 17 (one method, unchanged). The code is read from the stub field. Device: Android 16 |
| `android.net.ITetheringEventCallback` and `$Stub.TRANSACTION_onCallbackStarted`, `TRANSACTION_onTetherStatesChanged` | live state callback, same technique | Connectivity `ITetheringEventCallback.aidl` (android17-release: eight oneway methods) | Source 11 to 17. Codes are read from the device's stub fields, never hard-coded; a missing field disables events and the kernel fallback is used. Device: Android 16 |
| `android.net.TetheringCallbackStartedParcel.CREATOR` and its `states` and `tetheredClients` fields | initial state and connected devices | Connectivity `TetheringCallbackStartedParcel.aidl` | Source 11 to 17. A missing `tetheredClients` field only means no client addresses. Device: Android 16 |
| `ITetheringEventCallback$Stub.TRANSACTION_onTetherClientsChanged` and `android.net.TetheredClient.CREATOR` (`getTetheringType()`, `getAddresses()`, `AddressInfo.getAddress()` as a `LinkAddress`) | the IPv4 address of the device on the cable, shown on the home screen | Connectivity `ITetheringEventCallback.aidl`, `TetheredClient.java` | Source 11 to 17. Optional: when the code or the class is missing, no client is shown. MAC addresses are never read. Device: not checked yet |
| `android.net.TetherStatesParcel.CREATOR` and its fields `availableList`, `tetheredList`, `localOnlyList`, `erroredIfaceList`, `lastErrorList` | state lists | Connectivity and frameworks/base (11) `TetherStatesParcel.aidl` | Source: `String[]` on 11, `TetheringInterface[]` (`getType`, `getInterface`) on 12 to 17; both handled by type (`StatesParser`). Device: Android 16 |

Not hidden but used: `java.net.NetworkInterface` and `/sys/class/net/<name>/carrier` and `speed` for the kernel view (`SystemInterfaceReader`).

## Debug screen

Behind the developer switch in Settings. It runs the full start, stop and status, and seven single steps (`RawStep`): diagnose (API level, resolved connector signatures, Builder setters, transaction codes), read states, read interfaces, build request (shows the parcel without sending it), stop only, start only, start only without the exemption. Every result shows the message and the raw steps, and "Copy all" puts everything on the clipboard for test reports. Every start and stop of the app is also in the log, which can be exported as a file.

## Backend detection

`BackendDetectors` runs read-only detectors in a fixed order. The app gives it only the Shizuku detector; the Sui detector stays in the backend module, tested, because Sui serves the Shizuku API. No detector asks the user anything or starts a process.

| Detector | How it decides | States |
|---|---|---|
| `ShizukuDetector` | Shizuku binder alive and not Sui; Shizuku manager package installed (visible through a `<queries>` entry); permission granted; `Shizuku.getUid()` | NotFound, NotRunning, PermissionRequired, Ready(uid), Failed (server older than 11) |
| `SuiDetector` | `Sui.isSui()` after `Sui.init(packageName)` in `Application.onCreate` | NotFound, NotRunning, PermissionRequired, Ready(0) |

Settings show the Shizuku row with its state; only Shizuku running as shell can carry the engine.

## Data and privacy

- No INTERNET permission, no backup of any data (`data_extraction_rules.xml`, `backup_rules.xml`).
- Settings and profiles live in DataStore in the app's private storage; the developer switch of early builds is migrated from SharedPreferences. Profiles are stored in the same versioned JSON format that import and export use; every imported profile is validated and files over 256 KiB are refused.
- The log (`EventLog`) is one text file in the app's private files, at most 1000 entries, never uploaded. It leaves the phone only through Export (share sheet, through a `FileProvider` that shares only the export folder) or Copy.

### Permissions of the release build

| Permission | Why |
|---|---|
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` | The service that keeps tethering watched and armed |
| `CHANGE_NETWORK_STATE` | Android 14 prerequisite for a `connectedDevice` service; EtherTether changes no network setting with it |
| `ACCESS_NETWORK_STATE` | Reads the phone's own networks to warn about overlapping profile subnets |
| `POST_NOTIFICATIONS` | The notification with Stop and the watchdog alerts |
| `RECEIVE_BOOT_COMPLETED` | Start after restart, only when the user turns it on |
| `WAKE_LOCK` | Added by WorkManager, which Glance uses to update the widget |
| `moe.shizuku.manager.permission.API_V23` | Shizuku's own permission, from the Shizuku library |
| `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Added by AndroidX core for its own receivers |

`verifyReleasePermissions` fails on INTERNET, AD_ID, location, contacts or phone-state permissions.

## App layer

```
MainActivity (Compose, navigation) ─┐
Notification Stop, tile, widget ────┼──►  TetherController  ──►  EngineClient (backend) ──► user service
BootReceiver ───────────────────────┘        │  state: TetherState (StateFlow)
                                             ├─► TetherService (foreground, connectedDevice): keeps the process,
                                             │     the Shizuku connection and the notification alive while active
                                             └─► watchdog: pushed status changes from the engine (StatusPublisher)
```

- `TetherController` is the only place that starts and stops. Operations run one at a time (a mutex). It keeps `armed` while a request is active, serving or waiting for the adapter, and runs the watchdog on every status the engine pushes (`IEngineListener`).
- Nothing can leave the app "starting": `EngineClient` runs every binder call in its own job and gives up after 45 s (`NO_ANSWER`), the controller ends every start and stop in a `finally`, Stop interrupts a start in progress, and service commands run in the app's scope rather than the service's.
- After Shizuku stops while tethering is on or armed, the state becomes `statusUnknown`: Android may still serve, but nothing can be read or changed until Shizuku runs again. Home says so and opens Android's tethering page (`android.settings.TETHER_SETTINGS`, exported by the Settings app) as a way out.
- Watchdog: serving ended without a stop by EtherTether. With the adapter still present it reports "stopped outside EtherTether" and disarms. With the adapter gone it either reports "adapter removed" or, with keep armed, starts the same profile again (2, 4 and 8 s pauses on failure, then "could not start again"). A user start or stop cancels a running re-arm (a generation counter).
- `TetherService` starts in the foreground when a start begins and stops itself as soon as nothing is serving, armed, re-arming or busy. It carries the actions of the notification, the tile, the widget and the boot receiver. If Android refuses the foreground start, tethering still works while the app is open, and the log says so.
- The adapter: while tethering is off Android sends no tethering event for it, so `AdapterChanges` reads the status 1.5 s and 4 s after each USB attach or detach broadcast, and the activity reads it when it comes to the front.
- The tile (active tile; while the panel shows it, it follows the controller state, not polled) and the Glance widget show the same state and toggle through the service. Without Shizuku they open the app.
- Start after restart: `BootReceiver` starts the service, which asks the controller to wait up to five minutes for Shizuku (checked on each Shizuku change) and then start the selected profile.
- Restore: the controller saves the armed profile id in the settings. When the app process starts again (Android ended it while armed), it waits up to 10 s for Shizuku, then watches the profile again if Android still serves it, or arms it again; otherwise it forgets it.
- Security details: [SECURITY_REVIEW.md](SECURITY_REVIEW.md); situations and their handling: [ROBUSTNESS.md](ROBUSTNESS.md).
- Screens get their state as plain data classes and their callbacks as action objects, so every screen is tested without the app.
- Language: `AppLocales` uses the per-app language on Android 13 and later (`locales_config.xml`) and wraps the activity's context on 11 and 12.

## Threading

Detection and readiness run on `Dispatchers.IO` from the view model; engine calls run on `Dispatchers.IO` in `EngineClient` and block in the user service (`runBlocking` on a binder thread), one operation at a time. Callbacks from the tethering service arrive on other binder threads and only update a `StateFlow`. Shizuku listeners may fire on any thread; they only trigger a refresh or forget the engine connection. The event log reads and writes its file on its own thread. Debug builds turn on StrictMode and write each kind of violation once to the event log.
