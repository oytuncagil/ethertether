<!--
SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later
-->

# Security review

Review of `app`, `backend`, `engine` and `model` on 2026-10-09, including the merged release manifest (every component the libraries add). There is no root helper or root module, so nothing like that was reviewed. Sources read: Shizuku server (commit b844bc4) and Shizuku-API (commit a27f6e4) from github.com/RikkaApps, AOSP frameworks/base (main and android11-release) and the Navigation and Glance library sources.

**Result: no high or medium findings.** Four low findings and two notes, all fixed before the first release.

## Findings and fixes

| # | Severity | Finding | Fix |
|---|---|---|---|
| L1 | Low | The Stop action of the ongoing notification worked on a locked phone, so anyone holding it could stop tethering and disarm keep armed (the tile already needed an unlock) | The action needs authentication (`setAuthenticationRequired`, enforced on Android 12 and later) |
| L2 | Low (defense in depth) | The engine user service did not check who calls it. Today only EtherTether and Shizuku can hold its binder (see below), but a leaked binder would let its holder start or stop tethering as shell | With Shizuku v13 or later the service gets the app's Context and rejects every caller whose app id is not EtherTether's, except its own uid (Shizuku calls `destroy()` from it). The no-argument constructor stays for older servers. `CallerCheckTest`, `UserServiceEntryTest`, keep rule and `verifyReleaseKeepRules` updated |
| L3 | Low | Profile names kept Unicode bidirectional controls; a shared profile file could reorder the text around the name in the notification | `ProfileLimits.cleanName` drops U+061C, U+200E, U+200F, U+202A to U+202E and U+2066 to U+2069, and keeps the zero width joiner for emoji (`ProfileTest`) |
| L4 | Low | A shared log file stayed in the app's cache until the next export | The export folder is emptied when the app starts |
| I1 | Note | The tethering settings and unused-app pages were opened with implicit intents, so another installed app declaring those actions could pose as them | They are resolved to system apps only (`MATCH_SYSTEM_ONLY`, with the matching `<queries>`), falling back to the general settings |
| I2 | Note | The widget receiver must be exported, so any app can send it Glance broadcasts. Harmless today because the widget's buttons are PendingIntents | A comment in `TetherWidget.kt` keeps it that way: no lambda or callback actions |

## What was checked and found sound

- **Engine binder (Shizuku user service).** Shizuku accepts `bindUserService` only from a client the user allowed, and only for a package owned by the caller's uid; the service class is loaded from that package's own APK, and the binder goes back only to the connections registered for that package and tag. No other app can start, reach or attach to EtherTether's engine.
- **Exported components.** The launcher activity reads no intent data; Navigation trusts deep-link extras only from the app itself and the graph has no deep links, so no other app can open the debug screen or the editor. The tile needs `BIND_QUICK_SETTINGS_TILE` and asks for an unlock. The Shizuku provider needs `INTERACT_ACROSS_USERS_FULL` (not available to normal apps). The boot receiver is not exported and still receives the system's broadcast. The file provider shares only `cache/exports/`, read-only, to the target the user picks. Library components are not exported or need system-only permissions (`BIND_JOB_SERVICE`, `BIND_REMOTEVIEWS`, `DUMP`).
- **PendingIntents.** All immutable and explicit, including the widget's.
- **Input across the binder.** The service validates every request again with the same validator; unknown step names do nothing; reflection uses fixed class and method names only, never text from a request.
- **Profile import.** At most 256 K characters and 100 profiles; malformed files are rejected with a message; deep nesting cannot overflow the stack; imported ids are replaced and every entry is validated.
- **Storage and backups.** Settings, profiles and the log are private; backup and device transfer are excluded completely.
- **Builds.** Debug builds are a separate package; only they make the user service debuggable and turn on StrictMode. No logcat output, no WebView, no dynamic code loading, no INTERNET permission (a build check fails otherwise).
- **Developer screen in release builds.** Its raw steps stay within Ethernet tethering and go through the same validation, so they give no more power than Start and Stop.

## Check on the phone

L2 changes how Shizuku creates the engine. Device test R7 confirms that Start, Stop and stopping Shizuku still work with this build.
