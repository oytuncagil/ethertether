<!--
SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later
-->

# Robustness

What happens in each situation that can interrupt EtherTether, what the app does about it, and which test proves it. Unit tests run on every pull request; device tests (letter and number, such as R1) are done on a real phone and listed in [DEVICE_TESTS.md](DEVICE_TESTS.md). Android source links use the prefixes from [COMPATIBILITY.md](COMPATIBILITY.md).

## Cases

| Case | What Android does | What EtherTether does | Proof |
|---|---|---|---|
| Adapter unplugged | Ends Ethernet tethering and drops the request (`CONN16/Tethering/src/com/android/networkstack/tethering/Tethering.java#1096`) | Keep armed off: message and alert "The adapter was removed". Keep armed on: asks Android again at once (pauses of 2, 4 and 8 s if refused, then an alert), so the next adapter is served by itself | `TetherControllerTest`: `adapterRemovedWithoutKeepArmedIsReported`, `adapterRemovedWithKeepArmedArmsAgainSilently`, `rearmRetriesWithPausesThenGivesUp`; `EngineStressTest`; device test H5, H6 |
| Adapter plugged in or out while tethering is off | Sends no tethering event | Listens for Android's USB attach and detach broadcasts and reads the status 1.5 s and 4 s after the latest one (the kernel names the interface a moment later), and again whenever the app comes to the front; after the adapter was removed, home then offers **Start** | `AdapterChangesTest`, `HomePresentationTest`; device test R1 (failed on the first try, fixed) |
| Only the cable pulled | Tethering stays; the address stays | Nothing to do | Device test R1 |
| Tethering turned off in Android's settings, or by Data Saver or a device policy | Ends tethering (Data Saver: `CONN16/Tethering/src/com/android/networkstack/tethering/Tethering.java#1561`; policy: `#1656`) | Message and alert "Ethernet tethering stopped" with the next step; not armed again, since the user or the phone wanted it off | `stoppedOutsideIsReportedAndDisarms`; device test H4, R6 |
| Start refused (policy, `ro.tether.denied`, carrier) | Error 3, 11, 13 or 14 | An error in plain words with the next step; the entitlement exemption is tried first, then once without | `TetheringEngineStartTest`, `ResultPresentationTest`; device test R6 |
| Start without an adapter, then Stop at once | Keeps the request until an adapter appears | The start ends at once as "waiting for the adapter" (no 10 s confirmation wait without an Ethernet interface), and a Stop ends any confirmation wait of a start in progress | `withoutAnAdapterTheStartDoesNotWaitForAConfirmation`, `aStopEndsTheConfirmationWaitOfAStartInProgress`; device test R1 (found on the first try, fixed) |
| A start or a call to the engine hangs | | Every call ends after 45 s at most with "no answer"; Stop works while starting; the start always ends | `EngineConnectionTest` (hanging call), `aCancelledStartNeverStaysStarting`, `stopInterruptsAStartInProgress`; device test H8 |
| Shizuku stopped while tethering is off | | Start is disabled and setup is offered | `losingShizukuWhileOffChangesNothing`, `HomeScreenTest`; device test I2 |
| Shizuku stopped while tethering is on | Tethering keeps running (Android owns it) | State "Unknown" with **Set up Shizuku** and **Open tethering settings**, so it can be turned off by hand | `losingShizukuWhileServingMakesTheStateUnknown`, `stopWithShizukuGoneMakesTheStateUnknown`, screenshot and accessibility tests; device test H9 |
| Shizuku started again | | The engine is bound again and the real state is read | `EngineConnectionTest`; device test H9 |
| App ended by Android or swiped away while armed | The foreground service keeps the process while on or armed; if Android ends it anyway, tethering keeps running | On the next start of the app the saved armed profile is watched again (if still serving) or armed again; without Shizuku within 10 s, or if the profile was deleted, it is forgotten | `afterTheAppEndedAServingProfileIsWatchedAgainWithoutARestart`, `afterTheAppEndedAnArmedProfileThatNoLongerServesIsArmedAgain`, `noRestoreWithoutShizukuAndTheSavedProfileIsForgotten`, `aDeletedProfileIsNotRestored`; device test R2 |
| Rotation, language or theme change | The activity is recreated | The screen stays where it was; nothing restarts | `RotationTest` (the real app); device test R3 |
| Reboot | Tethering is off; Shizuku is not running until started again (Shizuku 13.6+ can start itself on a trusted Wi-Fi on Android 13+) | With "Start after restart" on, the boot receiver starts the service, which waits up to five minutes for Shizuku (on each Shizuku change, no polling) and then arms the profile | `afterARestartTheStartWaitsForShizukuWithoutPolling`, `afterARestartNothingStartsWhenShizukuNeverComes`, `SettingsScreenTest`; device test H7 |
| Two adapters | Only the first adapter Android sees is used (`CONN16/service-t/src/com/android/server/ethernet/EthernetTracker.java#154`) | Home names the other one as "not used" | `HomePresentationTest`; device test R4 |
| Wi-Fi hotspot or USB tethering running | Can run together with Ethernet tethering (`CONN16/Tethering/src/com/android/networkstack/tethering/Tethering.java#3111`); a fixed address is not checked against them | Nothing special; the profile editor rejects invalid and reserved addresses, and COMPATIBILITY.md says to avoid the hotspot's subnet | Device test R5: hotspot and Ethernet together worked on the POCO F7 |
| Airplane mode | No handling in the tethering module; Ethernet is not one of the airplane-mode radios (`.../SettingsProvider/res/values/defaults.xml#26`). Airplane mode turns Wi-Fi off, which ends wireless debugging and with it Shizuku | Local-only tethering keeps running (seen on the POCO F7). When Shizuku stops while tethering is on, home shows the unknown state with **Open tethering settings**; Shizuku must be started again after Wi-Fi is back | Device test R6 |
| Battery saver | No handling in the tethering module | The service is a `connectedDevice` foreground service; nothing polls, so battery saver has nothing to slow down | Device test R6, R8 |
| Many quick taps from the app, tile, widget and notification | | One controller with a lock and a generation number; the last request wins, nothing stays busy | `manyTogglesFromEverySurfaceNeverLeaveTheControllerBusy`, `EngineStressTest` (every Android version shape from 30 to 37) |

## Leaks and leftovers

Checked in code and by tests; debug builds also run StrictMode (thread and VM policies: leaked closables and registrations, activity leaks, unsafe intents), and every kind of violation is written once to the app's log with the tag "strictmode", so an exported log from a phone test shows them. The first phone test showed disk reads on the main thread from the tile and widget update (moved off the main thread) and from inside `NotificationManager.notify` and the garbage collector (platform code, no app frame); nothing else.

- **Tethering event callback**: registered once per engine, unregistered on close. `EngineStressTest` counts registrations: at most one at a time, none after close.
- **Engine binder**: the death link is unlinked whenever the connection forgets a service (reset or close), and the user service runs with `daemon(false)`, so it ends when the app's connection is gone.
- **Status listener**: one at a time; a new one replaces the old one (`StatusPublisher`).
- **Foreground service**: stops itself as soon as tethering is neither on nor armed (after a 10 s grace period for a start in progress). Commands run in the app's scope, so an early service end never leaves the controller busy.
- **Tile**: while the panel shows it, it follows the state and stops following when the panel closes; never polled.
- **USB receiver**: one receiver for the life of the process; after each USB event two status reads, then nothing.
- **Shizuku listeners**: registered once for the life of the process.
- **Event log**: reads and writes its file on its own background thread; at most 1000 entries.
- **Shared log files**: removed when the app starts.

## Performance and battery

- **Nothing polls while idle.** Changes arrive as events: Android's tethering callback, Shizuku's binder listeners, the boot broadcast and the user's taps. With tethering off and nothing armed, EtherTether has no service, no timer and no wake lock. If the engine was used, its process waits idle on its binder and ends with the app's process (`daemon(false)`).
- **Every timer is bounded**: engine calls 45 s, result codes 5 s, stop settling 4 s, start confirmation 10 s (skipped when no Ethernet interface exists), binding 15 s, waiting for Shizuku after the app was ended 10 s (500 ms checks), after a reboot five minutes (event driven), status reads 1.5 s and 4 s after a USB event, re-arming pauses 2, 4 and 8 s, the service's grace period 10 s.
- **While on or armed**: the foreground service and the engine process sleep until an event arrives. The app holds no wake lock (the WAKE_LOCK permission comes from WorkManager through the widget library and is not used by EtherTether's own code).
- **Measurement**: device test R8 (battery use over one hour armed without an adapter, and one hour serving).
