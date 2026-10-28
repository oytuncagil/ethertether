<!--
SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later
-->

# Listings

Where EtherTether is or will be listed, what each place requires, and the texts used.

| Place | Status |
|---|---|
| GitHub Releases | 1.0.0 published on 2026-10-28 |
| Obtainium | Works with the releases (device test V4, 2026-10-28) |
| awesome-shizuku | Pull request [timschneeb/awesome-shizuku#183](https://github.com/timschneeb/awesome-shizuku/pull/183) opened on 2026-10-28, waiting for the maintainer |
| IzzyOnDroid | On hold |
| Google Play | Later |

## Obtainium

Obtainium installs and updates straight from GitHub Releases.

- Add link: `obtainium://add/https://github.com/oytuncagil/ethertether`, shown with Obtainium's badge in the README.
- What it needs and what the release workflow produces:

| Obtainium reads | Release workflow |
|---|---|
| The latest release that is not a draft; pre-releases only when the user allows them | Drafts until published by hand; suffix tags are marked pre-release |
| The version from the tag name, without a leading `v` | Tag `vX.Y.Z`, the same as `versionName` |
| A `.apk` asset | One universal `EtherTether-X.Y.Z.apk`, plus `SHA256SUMS` (ignored) |
| The same signing key across updates | Every release signed with the one release key, checked in the workflow |

- Checked against the test draft releases (asset names, tag, pre-release flag), then on a phone with the published 1.0.0 (device test V4).

## IzzyOnDroid (on hold)

Inclusion rules (see RESEARCH.md) and how EtherTether meets them:

| Rule | Status |
|---|---|
| OSI or FSF approved license | Yes: Apache-2.0 OR GPL-3.0-or-later |
| No proprietary components, trackers or ads | Yes: every library is Apache-2.0, MIT or BSD (AboutLibraries strict mode fails the build otherwise); no Google Play Services or Firebase |
| APK attached to a tagged GitHub release, signed with the release key | Yes: release workflow ([RELEASING.md](RELEASING.md)) |
| Not debuggable | Yes: release build type |
| Size below about 30 MB | Yes: about 4.3 MB |
| No dependency-info signing block | Yes: disabled in the build (`dependenciesInfo`) |
| Fastlane metadata in the repository | Yes: `fastlane/metadata/android/` in English, Turkish and German, with screenshots, feature graphic and icon |
| Never replace a published release | Rule in [RELEASING.md](RELEASING.md) |
| Reproducible build (optional, for the green badge) | Yes: builds are byte for byte reproducible from a clean clone ([RELEASING.md](RELEASING.md#verifying-a-release)) |
| Anti-features | None expected. Permissions are listed below |

Permissions of the release APK, with the reason (also in ARCHITECTURE.md):

| Permission | Why |
|---|---|
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` | The notification service that keeps tethering watched while it is on or armed |
| `CHANGE_NETWORK_STATE` | Android 14 requires it for a `connectedDevice` foreground service; EtherTether does not change network settings with it |
| `ACCESS_NETWORK_STATE` | Reads the phone's own networks to warn about overlapping subnets in profiles; no network access (there is no INTERNET permission) |
| `POST_NOTIFICATIONS` | The ongoing notification with Stop and the alerts |
| `RECEIVE_BOOT_COMPLETED` | "Start after restart", only when the user turns it on |
| `WAKE_LOCK` | Added by WorkManager, which the widget library uses; EtherTether's own code holds no wake lock |
| `moe.shizuku.manager.permission.API_V23` | Asking Shizuku for access |

### Inclusion request draft (for codeberg.org/IzzyOnDroid/repodata, "App Inclusion Request")

The request is made from the maintainer's Codeberg account. The form has further required questions, among them one about the use of generative AI tools, which the maintainer answers personally when submitting.

```
App name: EtherTether
Package name: io.github.oytuncagil.ethertether
Source code: https://github.com/oytuncagil/ethertether
License: Apache-2.0 OR GPL-3.0-or-later
Releases: https://github.com/oytuncagil/ethertether/releases (signed APK on every tagged release)
Signing certificate SHA-256: 75:37:BE:B7:0C:8E:EF:0E:39:88:1F:A9:BC:4A:1C:37:DE:FC:97:86:29:AC:DE:4E:A3:85:B8:24:31:1D:05:7B
Metadata: Fastlane, fastlane/metadata/android (en-US, tr-TR, de-DE)
Category: Connectivity

Description:
EtherTether starts and stops Android's own Ethernet tethering for any USB or USB-C
Ethernet adapter, with a fixed IPv4 address of the user's choice or with Android's normal
DHCP sharing. It needs Shizuku (shell identity) and makes only the tethering request; it
changes no routes, DNS, Wi-Fi, mobile data or firewall rules.

Anti-features: none. No INTERNET permission, no trackers, no ads, no proprietary libraries.
Reproducible builds: yes, documented in docs/RELEASING.md.
```

## awesome-shizuku

The list at https://github.com/timschneeb/awesome-shizuku (rules checked again on 2026-10-28): entries go into README.md through a pull request, in alphabetical order in their category, in the format of their CONTRIBUTING.md: `* [Name](link) - Short description, under 250 characters `License``, with the SPDX license expression and tags only if they apply (`Root` only when Shizuku must run as root, which it need not for EtherTether).

Submitted as pull request #183 on 2026-10-28, an entry in the Network section between "EasySpot" and "FindMyDevice":

```
* [EtherTether](https://github.com/oytuncagil/ethertether) - Ethernet tethering with a fixed IPv4 address of your choice (for example for link-local devices) or normal DHCP, for any USB-C Ethernet adapter; profiles, Quick Settings tile, widget and automatic start on plug-in `Apache-2.0 OR GPL-3.0-or-later`
```

Pull request title and text:

```
Add EtherTether

EtherTether starts Android's own Ethernet tethering with a fixed IPv4 address or normal
DHCP for any USB or USB-C Ethernet adapter. It uses Shizuku in shell mode (no root needed)
for the tethering request only. Open source (Apache-2.0 OR GPL-3.0-or-later), English
README, signed APKs on GitHub Releases.
```

## Root module repositories

Not applicable: there is no root module. The research for one is in RESEARCH.md.

## Google Play

Later. Everything is prepared in [PLAY_STORE.md](PLAY_STORE.md), including the Play App Signing choice that has to be made before the first upload.
