<!--
SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later
-->

# Design

What EtherTether is meant to do, what it deliberately leaves out, and why things are built the way they are. Module layout and the hidden APIs are in [ARCHITECTURE.md](ARCHITECTURE.md); the platform background with sources is in [RESEARCH.md](RESEARCH.md).

## Scope

EtherTether starts and stops Android's own Ethernet tethering for a USB or USB-C Ethernet adapter, either with a fixed IPv4 address you choose or with Android's normal DHCP sharing. It uses the shell identity you grant through Shizuku for exactly two things: the tethering request and reading the tethering state. It does not touch routes, DNS, Wi-Fi, mobile data or firewall rules, and it collects nothing.

Not part of the project: VPN or proxy features, routing changes, control of Wi-Fi hotspot or USB (RNDIS/NCM) tethering, getting around carrier entitlement checks, and any network access by the app itself.

Without a privileged identity there is no way to do this. Android lets no ordinary app set an interface address or start tethering with one (it gets error 14). The permissions involved are signature-level, so they cannot be granted to an app, not even over ADB. A process running as the shell user, which is what Shizuku provides, is the smallest identity that works. LSPatch and similar tools don't help: they patch single apps and cannot reach the system service.

## Features

- Start and stop Ethernet tethering with a fixed phone address, one client address for the single DHCP lease and a prefix length, or with normal DHCP sharing.
- Local only (the default) or sharing the phone's internet connection.
- Profiles: three ready-made ones ("Car ENET link-local 169.254.0.42/16", "Laptop over USB-C Ethernet with internet", "Isolated lab network") and your own, checked before anything is sent, with import and export as a small JSON file.
- Live status: Shizuku, the adapter (name, link, speed when readable), the tethering state, the real addresses on the interface and the connected device.
- Keep armed: after the adapter is removed, the request is sent again, so the next adapter is served by itself. Start after restart arms the selected profile once Shizuku runs after a reboot.
- Quick Settings tile, home-screen widget and a notification with Stop.
- Illustrated introduction and a guided Shizuku setup (wireless debugging on the phone alone).
- Log screen with export through the share sheet; nothing is uploaded.
- Settings, About (version, license, libraries, source link, whether the copy carries the official signature), English, Turkish and German.
- A watchdog that tells you when tethering was stopped outside the app or the adapter was removed.
- A debug screen behind a developer switch that runs the engine step by step and shows every raw result.

### Left out on purpose

- **Sui and plain root.** Only Shizuku is supported, started with wireless debugging or ADB. On a rooted phone, start Shizuku the normal way. The Sui detector stays in the backend module because Sui serves the Shizuku API.
- **Client mode** (a fixed address on the phone's own wired connection, to get internet from a router). `EthernetManager.updateConfiguration` needs `MANAGE_ETHERNET_NETWORKS` or `NETWORK_STACK`, and the shell user has neither (it has `NETWORK_SETTINGS` and `TETHER_PRIVILEGED`), so Shizuku cannot do it. Android's own client mode with DHCP works when EtherTether is stopped and keep armed is off; the README explains this.
- **A root module** (Magisk, KernelSU, APatch) for boot and hot-plug triggers, and a built-in wireless-debugging pairing. The research for a module is in RESEARCH.md and still applies if this is picked up later.

## Decisions

### Identity and licensing

- Name EtherTether; application ID and package root `io.github.oytuncagil.ethertether`, fixed for good. Debug builds add `.debug`, so both can be installed side by side.
- Dual license `Apache-2.0 OR GPL-3.0-or-later`. Every file has an SPDX header and the repository follows REUSE 3.3 (`LICENSES/`, `REUSE.toml`, `reuse lint` in CI). Every dependency must be compatible with both licenses. Contributions need a DCO sign-off so the dual license stays valid. In store metadata the license field is `Apache-2.0` (only one identifier is allowed there); the descriptions state the dual license.
- The icon is under the project license. The name and logo identify the official app; forks use their own name, icon and application ID (README).
- Commits use the GitHub noreply address.

### Privacy

- No INTERNET permission, no analytics, trackers, ads, crash reporting or accounts; a build check fails if any library adds INTERNET. Nothing is backed up. The log leaves the phone only when you export it.

### Platform

- JDK 21, Gradle 9.8, AGP 9.4 with built-in Kotlin 2.4, Jetpack Compose with Material 3, compileSdk 37, targetSdk 36, minSdk 30 (static IPv4 tethering requests exist since Android 11). Patch updates are taken when CI and the keep-rule check pass.
- Modules: `model` (pure Kotlin), `engine`, `backend`, `app`, plus `build-logic` convention plugins. No DI framework.
- Icons are own vector drawables, not `material-icons-extended` (unmaintained). Material Symbols may be imported as vectors (Apache-2.0) with a `REUSE.toml` entry.
- Dependencies that only exist on JitPack are avoided where a small own implementation is enough.
- Libraries in the APK: Navigation Compose, DataStore, Lifecycle Service, Glance for the widget (it brings WorkManager and with it the WAKE_LOCK permission), kotlinx-serialization, AboutLibraries (list generated offline at build time, strict mode allowing only Apache-2.0, MIT and BSD). WorkManager is pinned above the 2.7.1 that Glance brings: its old Room keep rule let R8 full mode drop `WorkDatabase_Impl()`, and the first signed build crashed at start. `verifyReleaseKeepRules` now also requires the constructors Room and WorkManager call by name.

### Engine

- The engine runs inside a Shizuku user service (`Shizuku.bindUserService`), which runs as the shell user. The request is built and the tethering service is called there; the app talks to it only through EtherTether's own AIDL interface. User services run with the server's uid and without hidden API restrictions, so no bypass library is needed.
- Rules taken from the Android source: the device's own `TetheringRequest.Builder` and `getParcel()`, never a hand-built parcel; connector methods resolved by parameter types, never by hard-coded transaction codes or argument counts; `setExemptFromEntitlementCheck(true)` first, with one retry without it on error 14; result 0 only means "accepted", success is confirmed through the tethering event callback; always stop before a new start; `stopTethering`, never `stopTetheringRequest`; no local-only scope on Android 11.
- Event callbacks use the transaction codes from the device's own AIDL stub fields (`TRANSACTION_*`), read by reflection. When they are missing, the engine works without events and says the result is unconfirmed.
- Prefix length for a fixed address is 16 to 30, the range Android's DHCP server accepts (error 12 otherwise). A smaller value gets an explanation and the suggestion /16.
- Timeouts: 5 s per result code, 4 s for a stop to settle, 10 s to confirm a start (skipped when no Ethernet interface exists; no confirmation means "waiting for the adapter"), 15 s to bind the user service, 45 s for any engine call, after which it is reported as "no answer" instead of hanging. The scope fallback is never tried after errors 3, 12, 14, 16 or 18.
- Removing the adapter ends Ethernet tethering for good; pulling only the cable does not. The app reports it and offers Start when the adapter is back.

### Automation

- One `TetherController` starts and stops for the app, the notification, the tile, the widget and boot. It has a lock and a generation number, so the last request wins and nothing stays busy; Stop works while a start is in progress.
- While tethering is on or armed, a foreground service of type `connectedDevice` runs (with `CHANGE_NETWORK_STATE`, which Android 14 requires for it) and shows the notification with Stop. Stop from the notification needs the phone unlocked.
- Keep armed sends the request again after the adapter is removed (Android keeps a request pending until an adapter appears and drops it on removal), with pauses of 2, 4 and 8 s on failure, then a "could not start again" alert.
- Start after restart starts the service from `BOOT_COMPLETED` (allowed for `connectedDevice` on Android 15) and waits up to five minutes for Shizuku, driven by Shizuku's own events.
- When Android ends the app while a profile is armed, the profile is restored on the next start (waiting up to 10 s for Shizuku); a deleted profile is forgotten.
- The tile opens the app when Android refuses a foreground start from it (tiles are not a documented exemption).
- The status is read again after USB attach and detach broadcasts (at 1.5 s and 4 s, then nothing) and when the app comes to the front. Nothing polls while idle and every timer is bounded.
- Watchdog: tethering that ends without EtherTether stopping it is reported in the app and, when notifications are allowed, as an alert: "stopped outside EtherTether" when the adapter is still there, "adapter removed" when it is gone (unless keep armed handles it).
- After Shizuku stops while tethering runs, the state is shown as unknown, with a way to turn it off through Android's tethering settings. Settings warn when Android may remove the app's permissions after months without use.

### Storage

- Settings and profiles in DataStore (private, never backed up; the developer switch of early builds is migrated). Profiles use the same versioned JSON format as import and export. The log is one small text file of at most 1000 entries in the app's private files.

### Security

- The engine user service accepts calls only from EtherTether and Shizuku (it checks the caller's app ID when Shizuku 13 or later passes a Context; both constructors are kept). Profile names drop bidirectional control characters. Shared log files are removed when the app starts. Android's settings pages are opened only from system apps. Details in [SECURITY_REVIEW.md](SECURITY_REVIEW.md).

### User interface and languages

- Own design, phone first, light and dark themes, dynamic color, TalkBack labels everywhere, a large status and one obvious Start/Stop button.
- English, Turkish and German. Per-app language on Android 13 and later (also in the phone's settings), the app's own context on 11 and 12. Community translation can move to Hosted Weblate (Libre plan) once the repository is public.
- The About screen says only whether the installed copy carries the official signature; the fingerprint itself is in the README and the release notes, where verifier apps and people can compare it.

### Quality

- Compose UI tests under Robolectric for every screen; Google's Accessibility Test Framework through Roborazzi on every screen in light and dark, where warnings fail the test (Compose's own checks do not run under Robolectric); screenshot tests with Roborazzi; `verifyReleaseKeepRules` proves from R8's output that the classes created by name are kept (CI has no emulator).
- Compatibility claims need evidence: each cell of the matrix is "verified on a real device", "verified by a public report" (linked) or "unknown". Reading the Android source explains behaviour but never counts as a device test.

### Releases

- One release key, kept offline by the maintainer, used for GitHub Releases, Obtainium and IzzyOnDroid. How Google Play App Signing relates to it is decided before any Play release ([PLAY_STORE.md](PLAY_STORE.md)).
- R8 shrinking with `-dontobfuscate`, no dependency-info block, one universal APK per release. Release APKs are built only by GitHub Actions on Linux from a tag `vX.Y.Z` equal to `versionName`, signed with v2 and v3, and published as a draft first. Without the key secrets only a pre-release can be built, with a throwaway key and a warning in its notes.
- `versionCode = X * 10000 + Y * 100 + Z` from the one `appVersion` in `app/build.gradle.kts`; suffix tags such as `-rc.1` are pre-releases. The first public version is 1.0.0.
- Builds are reproducible: the release APK of a commit is byte for byte the same across clean builds and checkout folders. `scripts/compare_apks.py` compares an official APK with your own build.
- Store metadata in Fastlane format (en-US, tr-TR, de-DE). Screenshots, feature graphic and icon are rendered from the real screens with the ready-made profiles as demo data (`scripts/store_graphics.sh`), at 1233 x 2466.
- Distribution: GitHub Releases and Obtainium first; IzzyOnDroid and the awesome-shizuku list are prepared ([LISTINGS.md](LISTINGS.md)); Google Play later. Nothing may block F-Droid later.

## Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Hidden API changes in a Tethering module update | Start fails on some phones | Methods resolved by parameter type, one clear error per missing method, version table in ARCHITECTURE.md, the debug screen, COMPATIBILITY.md (checked against the Android 17 source) |
| Phone makers restrict Ethernet tethering or rename interfaces | Adapter not usable | Clear error with the next step; compatibility matrix with evidence only |
| Unplugging the adapter stops tethering | Unexpected stop | Watchdog message and alert; keep armed sends the request again |
| Entitlement exemption refused on Android 11 to 15 with older Tethering modules | Carrier prompt or failure on mobile data | Retry without the exemption; local only by default; errors 11 and 13 explained |
| Data Saver or a tethering restriction disables Ethernet tethering | Start fails | Detected and explained with the next step |
| Vendor framework changes (for example a Samsung crash inside callback registration) | Crash or missing status | Every hidden call is guarded; the engine carries on without events |
| No Shizuku on the phone | The app cannot work until it runs | Guided setup; the app says that Shizuku must be started again after every reboot |
| Google developer verification from 2027 | Harder installs and updates outside Play | Registration steps in [DEVELOPER_VERIFICATION.md](DEVELOPER_VERIFICATION.md) |
| Release key lost | No more updates for existing users | Offline backups and the password in a password manager ([RELEASING.md](RELEASING.md)) |
| Phone makers (for example HyperOS) stop the service or Shizuku in the background | Keep armed or start after restart stop working | Settings point to autostart and battery settings |
| Android refuses a foreground start from the tile | Tap does nothing | Caught; the tile opens the app instead |
| While armed, every Ethernet adapter is used for tethering | The phone cannot use a wired network itself | Said in Settings next to keep armed, and in the README |

## Later

- Community translations on Hosted Weblate.
- A Google Play release ([PLAY_STORE.md](PLAY_STORE.md)).
- IzzyOnDroid listing ([LISTINGS.md](LISTINGS.md)).
- A root module with boot and hot-plug triggers, if there is demand.
