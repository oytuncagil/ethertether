<!--
SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later
-->

# Google Play (prepared, not submitted)

Everything needed for a later Google Play release. Checked on 2026-10-23; sources in [SOURCES.md](SOURCES.md). Nothing has been sent to Google yet.

## Before the first upload

| Item | Status |
|---|---|
| Developer account | Personal Play Console account, USD 25, government ID (the same account can register the app for developer verification, docs/DEVELOPER_VERIFICATION.md) |
| Testing rule for new personal accounts | A closed test with at least 12 testers for 14 days in a row before production access |
| Format | Android App Bundle (`bundleRelease`); Play builds the APKs |
| Target API | 36, which Play requires for new apps and updates since 2026-08-31. API 37 will follow in about a year; EtherTether already compiles against 37 |
| Package name | `io.github.oytuncagil.ethertether` (the same as on GitHub, so users can move between channels) |
| Privacy policy URL | https://github.com/oytuncagil/ethertether/blob/main/PRIVACY.md (works once the repository is public) |
| Play App Signing | See below |

## Play App Signing (decided before the first upload)

Play signs the APKs it delivers. Android updates an app only when the new APK has the same certificate as the installed one, so the choice decides whether users can move between GitHub/Obtainium/IzzyOnDroid and Play:

| Option | What happens | Users can switch channels |
|---|---|---|
| A. Upload our release key to Play App Signing (Play Console: "Use a different app signing key", "Export and upload a key from a Java keystore", Google's PEPK tool encrypts it) | Play signs with the same key as GitHub. Google holds a copy of the key | Yes, updates work across all channels |
| B. Let Google create a new key | Play's APKs carry a different certificate | No: a Play install and a GitHub install are different apps to Android; switching needs an uninstall |

Recommendation: A, so one app identity everywhere and IzzyOnDroid, Obtainium and Play users never get stuck. The trade-off is that Google keeps a copy of the key (it cannot be taken back). The upload key for Play can be a separate, new key. Not decided yet.

## Store listing

The texts, screenshots, feature graphic and icon are the Fastlane files in `fastlane/metadata/android/` (en-US, tr-TR, de-DE); Play's limits are the same (title 30, short description 80, full description 4000 characters; feature graphic 1024 x 500; icon 512 x 512).

- Category: Tools. Tags: connectivity, network.
- Contact e-mail: a public address of the maintainer (Play shows it).
- Ads: No. In-app purchases: No.

## Data safety form

| Question | Answer |
|---|---|
| Does the app collect or share any of the required user data types? | No |
| Is all user data encrypted in transit? | Not applicable: the app sends no data (no INTERNET permission) |
| Can users request that data be deleted? | Not applicable: nothing leaves the phone. The log and profiles are deleted with the app, and the log can be cleared in the app |

Reason: EtherTether has no INTERNET permission, no analytics, no crash reporting and no accounts (PRIVACY.md). Exporting the log is a share-sheet action the user starts; Play does not count user-initiated sharing as collection.

## Content rating (IARC questionnaire)

Category: Utility, productivity, communication or other. Every content question (violence, sexuality, language, controlled substances, gambling, user-generated content shared with others, location sharing, digital purchases) is No. Expected rating: Everyone / PEGI 3 / USK 0.

## Target audience

18 and over is not required; the app is not aimed at children. Choose "18 and over" or "13 and over" to keep the families policy out of scope.

## Permissions and declarations

| Permission | Play declaration |
|---|---|
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Foreground service declaration (App content): type `connectedDevice`; use: keeps tethering to a USB Ethernet adapter watched while it runs or is armed, started by the user, stoppable from the notification; impact if deferred: tethering would stop being watched and the adapter would not be served again after a replug. Needs a short video: start a profile, the notification appears, Stop ends it |
| `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `ACCESS_NETWORK_STATE`, `CHANGE_NETWORK_STATE`, `WAKE_LOCK`, `FOREGROUND_SERVICE` | No declaration |
| `moe.shizuku.manager.permission.API_V23` | No declaration (a normal permission of the Shizuku app) |

No sensitive permissions (no location, SMS, call log, accessibility, `QUERY_ALL_PACKAGES`, all-files access).

## Note for reviewers (App access field)

```
EtherTether starts and stops Android's own Ethernet tethering for a USB-C Ethernet adapter, with a
fixed IPv4 address or normal DHCP. Android allows this only to system apps and the shell user, so
the app needs Shizuku (https://shizuku.rikka.app), which the user starts on their own phone with
wireless debugging and grants to EtherTether. EtherTether uses that access only to ask Android's
tethering service to start or stop Ethernet tethering and to read its state. It does not change
routes, DNS, Wi-Fi, mobile data or firewall rules, and has no INTERNET permission. The tethering
request uses Android's own entitlement-exemption flag, which Android accepts from the shell user; if
it is refused, the app retries once without it and shows the carrier's refusal as an error.

To test without Shizuku: the app opens, explains how to set up Shizuku and shows "Shizuku needed".
To test fully: install Shizuku from Google Play, start it with wireless debugging (Shizuku shows the
steps), grant EtherTether, plug in a USB-C Ethernet adapter and tap Start.
Source code: https://github.com/oytuncagil/ethertether
```

## Policy risks

- Device and network abuse: the app uses a documented API (Shizuku's user service) with access the user grants; it does not hide tethering from the carrier. The local-only mode does not share the mobile connection at all.
- Carrier entitlement: the tethering request sets Android's `setExemptFromEntitlementCheck(true)`, which Android accepts from the shell user; on error 14 it retries once without it, and a carrier refusal is shown as an error, never worked around (see DESIGN.md, "Engine"). Local-only tethering shares no mobile data, so the entitlement check does not concern it. A reviewer may still ask about the flag; the honest answer is above.
- If a reviewer objects, the fallback is to keep Play out and stay on GitHub, Obtainium and IzzyOnDroid.
