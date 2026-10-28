<!--
SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later
-->

# Research

Background for the design, collected in October 2026 from public sources only: the Android source, official documentation and the projects involved. Each finding names its source; full URLs with access dates are in [SOURCES.md](SOURCES.md). "Unverified" marks what could not be confirmed from a primary source. What was later checked on real phones is in [COMPATIBILITY.md](COMPATIBILITY.md) and [DEVICE_TESTS.md](DEVICE_TESTS.md).

Contents:

- Android tethering
- Shizuku, Sui and root
- Root module packaging
- Distribution and listings
- Other tools
- Licensing and dependencies
- Auto-start and background limits
- Translation platform

## Android tethering

Source base: `platform/packages/modules/Connectivity` (Android 12 and later) and `platform/frameworks/base/packages/Tethering` (Android 11), branches `android11-release` to `android17-release`, Mainline tags `aml_tet_*`, and `platform/packages/modules/NetworkStack` for the DHCP server. Android 17 (API 37) source is public (`android17-release`, tag `android-17.0.0_r1`).

### Summary

- The method works on Android 11 to 17. Shell (uid 2000, `com.android.shell`) holds `TETHER_PRIVILEGED`, `NETWORK_SETTINGS`, `WRITE_SETTINGS` and `ACCESS_NETWORK_STATE` on every version, but never `NETWORK_STACK`. A static IPv4 pair or the local-only scope needs no extra permission in the service.
- Entitlement exemption is the catch: `setExemptFromEntitlementCheck(true)` needs `NETWORK_STACK` on Android 11 to 15 with module trains up to at least `aml_tet_351314140`, so shell gets error 14 there. From module `aml_tet_351415080` and on 16 and 17, `NETWORK_SETTINGS` is enough. Ethernet is subject to carrier entitlement even in local scope. Engine rule: try exempt = true, retry once with false on error 14, and log which path worked.
- Connector signatures change: Android 12 added `String callingAttributionTag`; Android 16 inserted `stopTetheringRequest` at index 6, shifting the transaction codes of every later method. Never hard-code codes or argument counts; resolve through the device's own `ITetheringConnector$Stub`.
- Request parcel layout changed four times in Mainline; Android 16 put `int requestType` first. Never write the parcel by hand: build it with the device's `TetheringManager.TetheringRequest.Builder` and `getParcel()`.
- Local-only scope (`CONNECTIVITY_SCOPE_LOCAL = 2`) exists from Android 12 and is honoured for Ethernet on 12 to 17: no NAT or forwarding to the upstream, IPv6 gets a private ULA /64. Android 11 ignores scope: Ethernet is always shared (global).
- Static IPv4: the DHCP server offers only the client address (one lease, 3600 s), with router and DNS set to the phone address. The prefix length must be 16 to 30, otherwise the DHCP server refuses to start (error 12). The Builder rejects a pair that is not in the same prefix with the same length. A static subnet that overlaps the upstream network is not detected by the system.
- Interface choice is the system's: the first interface matching the Ethernet regex becomes the only tetherable Ethernet port (`eth\d` up to 14, `(usb|eth)\d+` on 15 and later; OEMs may override). A second adapter is ignored; `setInterfaceName` is rejected for Ethernet.
- Unplug: pulling the cable keeps tethering up (server mode ignores link state). Removing the adapter stops Ethernet tethering completely, and it does not restart on re-plug.
- Restart: on Android 16 and later a second start while one is active returns error 18 (duplicate request); on 12 to 15 a different request silently restarts. Always stop first.
- Result 0 from `startTethering` only means "accepted"; the outcome (serving interface, or an error per interface) arrives through the tethering event callback.
- New Tethering module trains for Android 17 have `min_sdk_version 31`, so Android 11 devices keep an older module.

### Builder setters

| Setter | 11 | 12 to 15 | 16, 17 |
|---|---|---|---|
| `setStaticIpv4Addresses(LinkAddress local, LinkAddress client)` | yes | yes | yes (`@SystemApi`) |
| `setExemptFromEntitlementCheck(boolean)` | yes | yes | yes |
| `setShouldShowEntitlementUi(boolean)` | yes | yes | yes |
| `setConnectivityScope(int)` | no | yes | yes |
| `setInterfaceName(String)` | no | no | yes, but throws for Ethernet |

On 11 to 15 the whole `TetheringManager` is `@hide @SystemApi`; on 16 and 17 the class is public and the privileged setters are `@SystemApi`. Constants: `TETHERING_ETHERNET = 5`; `CONNECTIVITY_SCOPE_GLOBAL = 1`, `CONNECTIVITY_SCOPE_LOCAL = 2`.

### ITetheringConnector (service `tethering`, oneway)

| Version | `startTethering` | `stopTethering` |
|---|---|---|
| 11 | `(in TetheringRequestParcel request, String callerPkg, IIntResultListener receiver)` | `(int type, String callerPkg, IIntResultListener receiver)` |
| 12 to 17 | `(in TetheringRequestParcel request, String callerPkg, String callingAttributionTag, IIntResultListener receiver)` | `(int type, String callerPkg, String callingAttributionTag, IIntResultListener receiver)` |

Also: `registerTetheringEventCallback(ITetheringEventCallback, String callerPkg)` and its unregister counterpart (same on all versions); `stopTetheringRequest` (16 and later; not used, because before 16 it never calls the listener); `setPreferTestNetworks` (13 and later). `IIntResultListener` has one method, `onResult(int)`.

`ITetheringEventCallback` delivers `onCallbackStarted` immediately on registration (initial state), then `onTetherStatesChanged(TetherStatesParcel)`, `onTetherClientsChanged`, `onUpstreamChanged` and others. `TetherStatesParcel` has `availableList`, `tetheredList`, `localOnlyList`, `erroredIfaceList` and `lastErrorList` (`String[]` on 11, `TetheringInterface[]` from 12). A local-scope Ethernet interface appears in `localOnlyList`, a global one in `tetheredList`. Registration needs `TETHER_PRIVILEGED` or `ACCESS_NETWORK_STATE`.

### Permission checks (TetheringService)

Order: package matches uid (`AppOpsManager.checkPackage`, present from module `aml_tet_351415080` and on 16 and 17), then `hasTetherChangePermission`, then "tethering supported and allowed" (`Settings.Global.TETHER_SUPPORTED`, user restriction `DISALLOW_CONFIG_TETHERING`; failure gives error 3). `onlyAllowPrivileged` is set by the entitlement exemption (and on 16 and later by an interface name).

### Result codes

| Code | Name | Meaning |
|---|---|---|
| 0 | NO_ERROR | Request accepted |
| 1 | UNKNOWN_IFACE | Interface not tracked by tethering |
| 2 | SERVICE_UNAVAIL | A needed system service is missing |
| 3 | UNSUPPORTED | Tethering not supported or not allowed (setting, user restriction) |
| 4 | UNAVAIL_IFACE | Interface not available |
| 5 | INTERNAL_ERROR | Generic internal error |
| 6 | TETHER_IFACE_ERROR | Setting up the tethered interface failed |
| 7 | UNTETHER_IFACE_ERROR | Tearing down the interface failed |
| 8 | ENABLE_FORWARDING_ERROR | Enabling forwarding or NAT failed |
| 9 | DISABLE_FORWARDING_ERROR | Disabling forwarding failed |
| 10 | IFACE_CFG_ERROR | IPv4 address configuration failed |
| 11 | PROVISIONING_FAILED | Carrier entitlement check failed |
| 12 | DHCPSERVER_ERROR | DHCP server could not start (for example prefix outside 16 to 30) |
| 13 | ENTITLEMENT_UNKNOWN | Entitlement result unknown |
| 14 | NO_CHANGE_TETHERING_PERMISSION | Permission missing or package does not match uid |
| 15 | NO_ACCESS_TETHERING_PERMISSION | Callback registration without access permission |
| 16 | UNKNOWN_TYPE | Invalid tethering type |
| 17 | UNKNOWN_REQUEST | (16 and later) No matching request to stop |
| 18 | DUPLICATE_REQUEST | (16 and later) A request of this type is already pending or serving |

Codes 19 and 20 exist on 16 and later but are internal to the module.

### DHCP, IPv6, local-only

- DHCP with static addresses: one lease to whichever client asks first. Without static addresses the module picks a random private subnet each time (the main complaint users report, see "Other tools"). If a device uses the legacy dnsmasq DHCP (off by default), the static client address is not enforced.
- Local-only: no NAT or forwarding to the upstream. Whether DNS queries are answered on a local-only downstream is unverified. IPv6: ULA /64 for local-only; for global, the upstream /64 only for the oldest downstream.
- Android 17 registers tethering downstreams as a local network agent; the effect on the phone's own apps reaching the client is unverified.

### Engine rules

1. Get the binder through the backend; use the device's `ITetheringConnector$Stub.asInterface`; never hard-code transaction codes or argument counts.
2. Build the request with the device's Builder: `setStaticIpv4Addresses`, `setConnectivityScope` only where it exists (12 and later), `setShouldShowEntitlementUi(false)`, `setExemptFromEntitlementCheck(true)` with a single retry as false on error 14; pass `getParcel()`.
3. Pick the `startTethering` and `stopTethering` overloads by parameter types (3 or 4 arguments, attribution tag null). `callerPkg` is always `com.android.shell` for the shell identity.
4. Treat result 0 as accepted, then confirm through the event callback (interface in `tetheredList` or `localOnlyList`, or in `erroredIfaceList` with its error), with a timeout.
5. Always `stopTethering(TETHERING_ETHERNET)` and wait for the interface to leave the lists before starting with new settings.
6. Validate locally: IPv4, same prefix and length, prefix 16 to 30, client differs from phone, neither is the network or broadcast address; warn when the subnet overlaps a current phone network.
7. Do not offer local-only on Android 11. Watch for adapter removal and offer a re-start on re-plug.
8. Wrap event callback registration in error handling (a Samsung framework crashes in it, see "Other tools").

## Shizuku, Sui and root

### Shizuku

- Latest API artifacts: `dev.rikka.shizuku:api`, `provider`, `aidl`, `shared`, all 13.1.5 on Maven Central (published 2023-09, still current). The API library is MIT licensed. The Shizuku server app is Apache-2.0 with carve-outs: its name, icons, application id and `moe.shizuku.manager.permission.*` may not be reused. Newest Shizuku app release: v13.6.0.
- `ShizukuBinderWrapper(IBinder)` forwards a transaction to the Shizuku server, which calls `Binder.clearCallingIdentity()` and re-issues it from its own process. The system service therefore sees the Shizuku server's uid: 2000 (shell) when started through ADB or wireless debugging, 0 (root) when Shizuku was started with root. `Shizuku.getUid()` returns that uid. `SystemServiceHelper.getSystemService(name)` gives the raw service binder; the older direct-transact helpers are deprecated and throw.
- Permission flow: `isPreV11()` means unsupported; `checkSelfPermission()`; `shouldShowRequestPermissionRationale()` means the user chose deny and do not ask again; `requestPermission(code)` with the result in `OnRequestPermissionResultListener`. All calls must happen between binder received and binder dead (`addBinderReceivedListenerSticky`, `addBinderDeadListener`).
- User services (`bindUserService`) run as the server's uid (2000 or 0) and die whenever Shizuku restarts.
- Wireless debugging (Android 11 and later, no computer): Developer options, Wireless debugging, pair through Shizuku's notification with the pairing code, Start. Shizuku must be started again after every reboot.

### Sui

- A Magisk (Zygisk) module that serves the same Shizuku API from a root process; uid 0. The client `rikka.sui.Sui` (`init(packageName)`, `isSui()`) ships inside the MIT `dev.rikka.shizuku:api`; there is no separate artifact. Sui itself is GPL-3.0 and is only talked to over IPC, never bundled. Latest release v13.5.1, maintained.

### Plain root and the caller check

`TetheringService.checkAndNotifyCommonError` runs, in order: `checkPackageNameMatchesUid(uid, callerPkg)` through `AppOpsManager.checkPackage`, then `hasTetherChangePermission`, then the "tethering supported and allowed" checks. A request that names an interface or is exempt from the entitlement check sets `onlyAllowPrivileged`, which needs `NETWORK_STACK`, `MAINLINE_NETWORK_STACK` or `NETWORK_SETTINGS`, then `TETHER_PRIVILEGED`.

- The shell package (`frameworks/base/packages/Shell/AndroidManifest.xml`) holds `TETHER_PRIVILEGED`, `NETWORK_SETTINGS`, `WRITE_SETTINGS` and others; `checkPackage(2000, "com.android.shell")` passes. This is the combination EtherTether uses, and it was first confirmed with a small test app on a POCO F7.
- From source, uid 0 should also pass: `AppOpsService.verifyAndGetBypass` skips the package check for root ("for backwards compatibility"), and root holds every permission. Early experiments with plain root failed anyway. Likely causes: the call carried the app's own uid, SELinux blocked binder from the su domain, or a non-existent package string failed the newer check. Only a device test can settle it (uid 0 with `callerPkg="android"` or `"root"` versus uid 2000 with `"com.android.shell"`).
- There is no shell command that starts Ethernet tethering with a static address: `ITetheringConnector` has no shell command handler, and `svc` has no tethering applet.

Legitimate ways to get a correct identity under root: an `app_process` helper started as uid 2000 (`su 2000`, BusyBox `setuidgid shell`); libsu `RootService` (Apache-2.0, 6.0.0, JitPack only, runs as uid 0); a Shizuku-style helper that hands its binder back to the app. See "Root module packaging" for why the uid must be set before the helper starts.

If a root backend is ever added, it should use a one-shot helper started as uid 2000 with `callerPkg = "com.android.shell"`, the identity that is known to work, and the same helper a root module would use. A direct uid 0 call only if a device test shows it works.

## Root module packaging

Not built (see DESIGN.md); kept for a later decision.

Verdict: feasible and safe on Magisk (v30.7, v31.0 pre-release), KernelSU (v3.3.0) and APatch (11224), with one module and one code path. No `system/` overlay, `sepolicy.rule`, `system.prop` or `post-fs-data.sh` is needed, and KernelSU needs no metamodule.

Three hard constraints:

1. Become uid 2000 before `app_process` starts. Since the 2021 kernel change "binder: use euid from cred instead of using task", binder records the caller's uid at `binder_open()`, and `app_process` opens binder before `main()`. `Os.setuid(2000)` inside the helper is too late. Use BusyBox `setuidgid shell` (in Magisk's BusyBox, which KernelSU also ships; APatch's build unverified) or `su 2000 -c`, adding `--drop-cap` on Magisk 30.7 and later, which otherwise keeps root capabilities. BusyBox `setpriv` cannot change the uid.
2. uid 2000 cannot read `/data/adb` (root, 0700). At boot the root script copies the helper to a root-owned, read-only runtime folder (for example on tmpfs), and passes profile values as validated arguments. Using the installed app's `base.apk` from the module has a package-squatting risk after uninstall.
3. Unplugging the adapter stops Ethernet tethering (`EthernetCallback.onUnavailable` calls `stopEthernetTethering`). A start with no adapter leaves a pending request that covers the first plug; each re-plug needs a new start, so hot-plug needs a watcher.

Other findings:

- Magisk has no `boot-completed.sh`; `service.sh` plus a bounded wait for `sys.boot_completed` works on all three. That property is set before the user unlocks, so the module can only read device-encrypted storage: the profile must live in `/data/adb/ethertether` or in the app's device-protected storage. `post-fs-data` blocks boot (Magisk up to 40 s, others 10 s) and runs before system services, so it is unusable.
- Hot-plug: `ip -o monitor link` blocks on netlink and uses no CPU when idle (recommended). inotify on `/sys/class/net` does not work; polling every 5 s or more is the fallback.
- Module updates replace the whole module directory, so configuration must not live inside it.
- Root scripts run in permissive or all-allow SELinux domains on all three managers.
- Ethernet interface regex default: `(eth|usb)\d+` on Android 15 and later, `eth\d+` on 13 and 14; OEMs may override. The watcher must use the same pattern.
- Repositories: the Magisk app's built-in repo was removed in v24 (updates through `updateJson`). Magisk-Modules-Alt-Repo is active (GitHub issue, source required for executables, module id must equal the repository name). MMRL is active and supports all three managers. The IzzyOnDroid Magisk repository (beta) takes requests through its Codeberg template. Fox's manager is archived. KernelSU's module repository could not be verified (pages returned 404). No official APatch repository exists.

## Distribution and listings

### Obtainium

Needs only a GitHub release with a signed `.apk`. It reads the version from `tag_name`, skips drafts always and pre-releases by default, strips a leading `v`, and ignores `.zip` assets unless enabled, so a module zip in the same release would be harmless. One universal APK per release; tag `vX.Y.Z` with `versionName` exactly `X.Y.Z`. Add link: `obtainium://add/https://github.com/oytuncagil/ethertether`; web redirect: `https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/...` (the redirect site may use analytics, unverified).

### IzzyOnDroid

- The tracker moved to Codeberg: requests go to `codeberg.org/IzzyOnDroid/repodata` with the "App Inclusion Request" template (needs a Codeberg account).
- Requirements: OSI or FSF license, no proprietary components, Fastlane metadata in the repo, release-key signed APK attached to tagged GitHub releases, not debuggable, about 30 MB limit, no dependency-info signing block (already disabled), never replace a published release.
- Fastlane limits: `title.txt` 30 characters, `short_description.txt` 80, `full_description.txt` 4000, `changelogs/<versionCode>.txt` 500 bytes plain ASCII.
- Reproducible builds program (rbtlog builders): build from a clean tag on Linux, pinned dependencies, no timestamps, `-dontobfuscate`, vcsInfo kept. Windows-built APKs are unlikely to get reproducible-build status.
- Generative AI policy. The policy says they are "strongly opposed to apps which are fully or in part created by generative AI tools" and that such requests "will most likely be rejected"; the request form has a required field on AI-tool assistance, and lack of transparency can lead to rejection. The IzzyOnDroid request is on hold for now.
- No rule targets Shizuku, Sui, root or hidden APIs. No anti-feature is expected.

### awesome-shizuku

`github.com/timschneeb/awesome-shizuku` (branch `master`, active). Add a line to the Network section of README.md through a pull request, alphabetical, format `* [Name](link) - description `License``. The `Root` tag only if Shizuku must run as root (it must not for EtherTether).

### Google developer verification (status 2026-10-07, checked again 2026-10-23)

- Enforcement started 2026-09-30, but only for installs from participating stores (Google Play and several OEM stores) in Brazil, Indonesia, Singapore and Thailand. Google's FAQ (2026-07-15): sideloaded apps and other stores are not affected yet. Global rollout in 2027, no exact date published.
- After enforcement, unregistered apps install and update only with the "advanced flow" (Developer options, confirm, restart, wait 24 hours, then 7 days or indefinitely) or ADB. Updates through Obtainium or IzzyOnDroid would fail with the advanced flow off.
- Options: full registration (USD 25 one-time, government ID, proof of address); free "limited distribution" (up to 20 authorized devices, unsuitable for public apps); or not registering (F-Droid advises against registering). To be decided before 2027.
- 2026-10-23: unchanged for direct downloads. Google's guide for open-source apps: register the package name and add the release certificate's SHA-256 fingerprint; no file in the APK is needed unless the app already had 50 or more installs before it was registered (then `assets/adi-registration.properties` with a snippet from the console, in a published release). Several keys per package are allowed. Steps: [DEVELOPER_VERIFICATION.md](DEVELOPER_VERIFICATION.md).

### Google Play (later)

No policy bans apps that use user-granted Shizuku or root; the risks are the Device and Network Abuse policy and skipping carrier entitlement checks. Target API: 36 required since 2026-08-31; 37 expected by about 2027-08-31 (unverified). New personal accounts need a 12-tester, 14-day closed test. Play requires App Bundles; to keep one certificate across channels, upload our own key to Play App Signing before the first release ([PLAY_STORE.md](PLAY_STORE.md)).

### F-Droid (keep the door open)

Only FOSS dependencies from Maven Central, Google Maven (JitPack also allowed); no committed binaries besides the Gradle wrapper (which F-Droid replaces with its verified Gradle); no Google Play Services or Firebase; Fastlane metadata; tagged releases; no dependency-info block; reproducible builds recommended so F-Droid can ship our signed APK.

## Other tools

No existing tool starts Android's own Ethernet tethering with a user-chosen fixed IPv4 address without root, with a clean stop, a Quick Settings tile and profiles. Eleven tools were reviewed:

| Tool | License | Privilege | Framework tethering | Fixed IPv4 on the tethered Ethernet | Tile | Auto-start |
|---|---|---|---|---|---|---|
| Android built-in Ethernet tethering (11+) | Apache-2.0 (AOSP) | none | yes | no (random subnet each time) | no | no |
| VPN Hotspot | Apache-2.0 | root (Shizuku mode only as a draft) | yes | no (its static IP goes on the loopback interface; the static request is commented out) | yes | no |
| Ethernet Config | no license file | Shizuku | no (client mode with `ip addr`, reported not to work) | client mode only | no | no |
| Always-On Tethering (root module) | WTFPL | root | no (legacy `ndc tether`, iptables; exposes ADB over TCP) | yes | no | yes |
| Ethernet Tether Autostart (root module) | MIT | root | yes (uid 0) | no | no | yes (5 s polling; a fallback stores the screen PIN in plain text) |
| Galaxy Ethernet Tether | no license | Accessibility, Samsung only | taps the Settings switch | no | no | yes |
| Shizzi | no license detected | Shizuku | yes (Wi-Fi hotspot) | no | yes | no |
| android-testtap-egress | MIT | Shizuku | yes (Ethernet on a test interface, proof of concept) | no | no | no |
| EasyTether, Every Proxy | proprietary | none | no (ADB tunnel, proxy) | no | no | no |
| Root shell recipes (`ndc`, `ip`, iptables) | forum snippets | root | no | yes, but fights the framework | no | do it yourself |

### What EtherTether does better

1. Rootless fixed-IP Ethernet tethering through the framework (shell identity through Shizuku).
2. Address input that prevents framework errors: prefix 16 to 30, same subnet, a proposed client address, a clear note that DHCP hands out exactly one address, presets.
3. Local-only option, avoiding shared mobile data and entitlement prompts.
4. Re-start on re-plug ("keep armed"), without unlocking the screen or tapping Settings.
5. Clean stop through `stopTethering`, verified by the event callback; never `ip`, iptables or `ndc` residue.
6. Real state: tile, widget and notification follow the framework callback, not an internal flag.
7. Diagnostics: backend and uid, Tethering module version, supported types, result code in words, interface name and state, address, Data Saver and tethering-allowed state.
8. Nothing stores a PIN and nothing exposes ADB over the network.
9. Vendor resilience: guard callback registration, probe capabilities at run time instead of trusting the API level, fall back to "open Settings" links rather than automated taps.
10. Clear license and privacy.

### Technical lessons

- Settings disables Ethernet tethering while Data Saver is on; warn the user.
- With `tether_supported = 0` or the `DISALLOW_CONFIG_TETHERING` restriction, the supported types are empty; show this explicitly.
- With two adapters or a dock with built-in Ethernet, the wrong one may be chosen; show which interface is used.
- Vendors: a Samsung framework writes a setting inside `registerTetheringEventCallback` and crashes some callers; One UI 8 reports a greyed-out Ethernet section on some phones; HyperOS 3 on Android 16 works through the framework; field reports show the shell identity working on Google, Samsung, Vivo, Xiaomi, Huawei, Lenovo, Infinix and Tecno phones (not controlled tests); Shizuku user service bind timeouts were reported on Infinix XOS.
- Client-mode static IP (`EthernetManager.updateConfiguration`) is a different feature: it needs `MANAGE_ETHERNET_NETWORKS`, which shell does not hold.

### Licenses of these tools

Apache-2.0 (VPN Hotspot, AOSP) and MIT (Ethernet Tether Autostart, android-testtap-egress) are compatible with our licenses, with attribution if code were ever reused. WTFPL is not OSI-approved: avoid. Projects without a license and proprietary apps: never reuse. We read code only to learn facts and copy none.

## Licensing and dependencies

- `Apache-2.0 OR GPL-3.0-or-later` works: every planned runtime dependency is Apache-2.0, MIT or BSD-3-Clause, all compatible with both options. The only incompatible license in the plan is JUnit 4 (EPL-1.0), which is test-only and never shipped.
- Setup adopted: root `LICENSE` (explains the choice), `LICENSE-APACHE` and `LICENSE-GPL` (verbatim official texts; SHA-256 `cfc7749b...3d30` and `3972dc97...6986`), REUSE 3.3 with `LICENSES/`, `REUSE.toml` and `reuse lint` in CI, `NOTICE` carrying the Kotlin and kotlinx.coroutines NOTICE attributions. Per-file headers: `SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk` and one `SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later` line (never two separate identifier lines).
- DCO 1.1 sign-off on every commit, checked by our own CI job (`scripts/check_dco.sh`); "require sign-off on web commits" is enabled in the repository settings. The DCO keeps the dual license valid because each contributor certifies the contribution under "the open source license indicated in the file", which is the `OR` expression.
- GitHub shows dual-licensed repositories as "Other" with both licenses listed (cosmetic). F-Droid and IzzyOnDroid store a single SPDX identifier per app; request `Apache-2.0` (the least restrictive license under which the whole APK can be used) and mention the dual license in the description.
- `material-icons-extended` is frozen at 1.7.8 and no longer recommended: use our own vectors or Material Symbols vector drawables (Apache-2.0, annotated in `REUSE.toml` if imported).
- libsu is only on JitPack and its POM has no license data; if used, it needs a manual license entry and dependency verification checksums.
- In-app license screen: AboutLibraries 15.3.0 with offline mode and strict mode allowing only Apache-2.0, MIT and BSD-3-Clause, plus a manual NOTICE entry. AGP strips `META-INF/NOTICE` files from the APK, so the in-app screen is what carries the notices to users.

### Dependency table (runtime, shipped in the APK)

| Artifact | Version | License | Used |
|---|---|---|---|
| Kotlin stdlib | 2.4.21 | Apache-2.0 (with BSD-3-Clause and BSL-1.0 derived parts) | yes |
| kotlinx-coroutines-core, -android | 1.11.0 | Apache-2.0 | yes |
| AndroidX core-ktx | 1.19.1 | Apache-2.0 | yes |
| activity-compose | 1.13.0 | Apache-2.0 | yes |
| lifecycle-runtime-compose, -viewmodel-compose | 2.11.0 | Apache-2.0 | yes |
| Compose BOM / ui / material3 | 2026.09.00 / 1.12.1 / 1.4.0 | Apache-2.0 | yes |
| dev.rikka.shizuku api, provider | 13.1.5 | MIT | yes |
| hiddenapibypass | 6.1 | Apache-2.0 | no: the engine runs in a Shizuku user service, which has no hidden API restrictions |
| kotlinx-serialization-json | 1.12.0 | Apache-2.0 | yes (profile storage, import and export, navigation routes) |
| navigation-compose | 2.10.2 | Apache-2.0 | yes |
| datastore-preferences | 1.3.0 | Apache-2.0 (bundled protobuf BSD-3-Clause) | yes |
| glance-appwidget, glance-material3 | 1.2.0 | Apache-2.0 (brings WorkManager, Apache-2.0, which adds the WAKE_LOCK permission) | yes |
| lifecycle-service | 2.11.0 | Apache-2.0 | yes |
| work-runtime | 2.12.0 | Apache-2.0 | yes (pinned above the version Glance brings; see DESIGN.md) |
| aboutlibraries-core, -compose-m3 | 15.3.0 | Apache-2.0 | yes |
| libsu core, service | 6.0.0 (JitPack) | Apache-2.0 | no (no root backend) |

Build and test only (not shipped): AGP 9.4.1, Kotlin Gradle plugins 2.4.21, Gradle 9.8.2 wrapper (Apache-2.0), JUnit 4.13.2 (EPL-1.0), Robolectric 4.17 (MIT), kotlinx-coroutines-test 1.11.0, Compose ui-test 1.12.1, androidx.test core 1.7.0 and ext-junit 1.3.0, Roborazzi 1.77.0 (screenshot and accessibility tests) with Google's Accessibility Test Framework 4.1.1, and the AboutLibraries Gradle plugin 15.3.0 (all Apache-2.0).

## Auto-start and background limits

From the Android source (AOSP `main`, cross-checked with `android11-release`) and the Android developer documentation.

### An Ethernet tethering request waits for the adapter

- `Tethering.startTethering()` records a pending request and, for `TETHERING_ETHERNET`, calls `EthernetManager.requestTetheredInterface()` with an `EthernetCallback`. It returns success at once, with or without an adapter.
- `EthernetTracker.requestTetheredInterface()` registers the callback and switches Ethernet to server mode. Without an interface nothing more happens; the callback stays registered.
- When an adapter appears (`onNewLink`, `maybeTrackInterface`, `addInterface`), `maybeUpdateServerModeInterfaceState(iface, true)` calls `onAvailable(iface)`, and `Tethering.EthernetCallback.onAvailable()` starts IP serving with the pending request, including its static addresses and scope.
- Only one interface serves: the first one tracked. An interface with configured network capabilities is never chosen.

### Removing the adapter ends the request

- `onDelLink` leads to `stopTrackingInterface()`, `maybeUpdateServerModeInterfaceState(iface, false)` and `onUnavailable()`. `Tethering.EthernetCallback.onUnavailable()` calls `stopEthernetTethering()`, which stops the IP server, releases the request and clears the callback; Ethernet goes back to client mode.
- The next adapter is therefore used as a normal network by the phone until the request is made again. Android 11 has the same logic (`frameworks/base/packages/Tethering` and `frameworks/opt/net/ethernet`).
- On Android 16 and later (fuzzy request matching in `RequestTracker`), a second equal request while one is pending returns `TETHER_ERROR_DUPLICATE_REQUEST`. The engine always stops before it starts, so a new request never meets a pending one.

Consequence for EtherTether ("Keep armed"): a foreground service keeps the request pending; when the watchdog sees serving end and no adapter left, it sends the request again (pauses of 2, 4 and 8 seconds on failure, then it gives up and says so). No USB polling, no USB permission, no INTERNET permission.

### Foreground service rules

| Question | Answer | Source |
|---|---|---|
| Type for a service that keeps a tethering request alive | `connectedDevice`, with `FOREGROUND_SERVICE_CONNECTED_DEVICE` | fgs/service-types |
| Prerequisite on Android 14 and later | one of a short list of permissions; `CHANGE_NETWORK_STATE` (normal, install time) is on it | fgs/service-types |
| Start from `BOOT_COMPLETED` on Android 15 and later | allowed; only `dataSync`, `camera`, `mediaPlayback`, `phoneCall`, `mediaProjection` and `microphone` are blocked | behavior-changes-15 |
| Time limit on Android 15 and later | none; the 6 hours in 24 limit applies only to `dataSync` and `mediaProcessing` | fgs/timeout |
| Start from a home-screen widget tap | documented exemption ("interact with a ... widget") | fgs/restrictions-bg-start |
| Start from a Quick Settings tile tap | not in the documented exemptions. In AOSP it works: SystemUI binds a listening tile with `BIND_FOREGROUND_SERVICE_WHILE_AWAKE`, which gives the process the state `ActiveServices` accepts. EtherTether catches a refusal and opens the app instead. | quicksettings-tiles, TileLifecycleManager, ActiveServices |

### Shizuku after a restart

Shizuku must be started again after every reboot. Shizuku 13.6.0 added "auto start without root on Android 13+ when connected to a trusted WLAN" (Shizuku releases). "Start after restart" in EtherTether therefore waits up to five minutes for Shizuku and does nothing without it.

## Translation platform

| | Hosted Weblate (Libre) | Crowdin (open source) | Transifex (open source) | Tolgee Cloud | POEditor (open source) |
|---|---|---|---|---|---|
| Free for EtherTether | yes, after approval | not yet: needs three months of work and an active community | only without any funding or donations model | on request by email | after review |
| Conditions | public repository, OSI or FSF libre license | OSI license, no related commercial product; translations go to Crowdin's shared memory | OSI license | none published | OSI license |
| Android plurals | yes | yes | yes | yes | yes (no `string-array`) |
| Pull requests to GitHub | yes (GitHub App) | yes | yes | no native feature (CLI in CI) | Premium plan only |
| Platform itself | GPL-3.0-or-later | proprietary | proprietary | Apache-2.0 core, separate license for `ee/` | proprietary |

Choice: Hosted Weblate, Libre plan. It is libre software like EtherTether, F-Droid translates its own client on it, its conditions fit (no age, community or revenue rule), it handles Android plurals and Fastlane store texts, and translations come back as pull requests to review. Its terms say only functional cookies are used. Unverified: the current text of hosted.weblate.org/hosting/ (behind an anti-bot page; the newest readable statement of the conditions is from 2020). Read it before applying.

Setup, once the repository is public:

1. Sign in at hosted.weblate.org and start a Libre project at hosted.weblate.org/hosting/.
2. Install the Hosted Weblate GitHub App on the repository.
3. Add a component with the format "Android String Resource", file mask `app/src/main/res/values-*/strings*.xml` (one component per strings file), base file in `values/`, push mode "pull request". Optionally a second component for `fastlane/metadata/android/*`.
4. Add the GitHub webhook so new source strings appear automatically.
5. Request approval of the Libre plan within the trial.
6. Add the translation badge and a "Translate" link to README and CONTRIBUTING.
7. Review and merge Weblate's pull requests; CI and lint check every one.

