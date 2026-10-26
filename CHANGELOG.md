<!--
SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later
-->

# Changelog

All notable changes are listed here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.0.0] - 2026-10-23

First public release.

### Added

- Start and stop Android's Ethernet tethering through Shizuku, with a fixed IPv4 address (phone address, one client address, prefix 16 to 30) or with Android's normal DHCP sharing, local only or sharing the internet connection. Every start is confirmed through Android's tethering state.
- Profiles: three ready-made ones and your own, checked before anything is sent, with suggestions for the client address and prefix, duplicate, delete, and import and export as a small JSON file.
- Home screen with the live state (profile, interface, phone address, connected device, adapter and cable), one Start/Stop button and a message with the next step whenever something is missing.
- Keep armed: after the adapter is removed, EtherTether asks Android again, so the next adapter is served by itself. Start after restart arms the selected profile once Shizuku runs after a reboot.
- A notification with Stop, a Quick Settings tile and a home-screen widget.
- Alerts when tethering is stopped outside EtherTether or the adapter is removed.
- After the app was ended by Android while armed, the profile is watched or armed again on the next start; when the adapter comes back, home offers Start; a second adapter is named as not used.
- When Shizuku stops while tethering runs, the state is shown as unknown, with a way to turn tethering off in Android's settings.
- Every request has a time limit, Stop works while a start is in progress, and a start never stays stuck.
- The status follows the adapter being plugged in or out, also while tethering is off.
- Illustrated introduction and a guided Shizuku setup (wireless debugging on the phone alone).
- Log screen with filter, search, copy, export through the share sheet and clear. The log stays on the phone.
- Settings for automation, notifications, theme, wallpaper colours and language, with the state of Shizuku and a hint when Android may remove the app's permissions after months without use.
- About screen with version, license, open-source libraries, source code link, and whether the installed copy is signed with the official release key.
- English, Turkish and German.
- Engine debug screen behind a developer switch, running the engine step by step with every raw result.
- Signed release APKs built by GitHub Actions, with checksums and reproducible builds anyone can verify.
- Store descriptions, screenshots, feature graphic and icon in English, Turkish and German.

### Security

- No INTERNET permission; the build fails if any library adds it.
- The Stop action in the notification needs the phone unlocked (Android 12 and later).
- The engine only answers EtherTether and Shizuku (Shizuku 13 and later).
- Profile names cannot contain characters that reorder the surrounding text.
- Shared log files are removed when the app starts.
- Android's settings pages are opened only from system apps.
