<!--
SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later
-->

# Sources

Sources behind [RESEARCH.md](RESEARCH.md) and [DESIGN.md](DESIGN.md), grouped by topic. All were accessed on 2026-10-07 unless a different date is given. Android source paths refer to android.googlesource.com.

## Android tethering

Paths below are in `platform/packages/modules/Connectivity` on the branches `android12-release` to `android17-release` (and tag `android-17.0.0_r1`, Mainline tags `aml_tet_341810050` to `aml_tet_351415080`) unless another repository is named.

| Source | Supports |
|---|---|
| https://android.googlesource.com/platform/packages/modules/Connectivity/+refs | Branch list including android17-release, tag android-17.0.0_r1, aml_tet tags |
| `Tethering/common/TetheringLib/src/android/net/ITetheringConnector.aidl` | Connector signatures per version; stopTetheringRequest on 16 and later |
| platform/frameworks/base, android11-release, `packages/Tethering/common/TetheringLib/src/android/net/ITetheringConnector.aidl` and sibling AIDL and Java files | Android 11 signatures, parcels, TetheringService, IpServer |
| `Tethering/common/TetheringLib/src/android/net/TetheringRequestParcel.aidl`, `ITetheringEventCallback.aidl`, `TetherStatesParcel.aidl`, `TetheringCallbackStartedParcel.aidl`, `TetheringInterface.java`, `IIntResultListener.aidl` | Parcel layouts and callback methods per version |
| `Tethering/common/TetheringLib/src/android/net/TetheringManager.java` | Builder setters, constants, error codes, scope and static pair validation |
| `Tethering/src/com/android/networkstack/tethering/TetheringService.java` | Permission logic per version and module tag |
| `Tethering/src/com/android/networkstack/tethering/Tethering.java` | Ethernet start and stop, scope mapping, unplug handling |
| `Tethering/src/com/android/networkstack/tethering/RequestTracker.java` | Duplicate request handling on 16 and later |
| `Tethering/src/android/net/ip/IpServer.java` | Static address use, DHCP parameters, local-only versus tethered, IPv6 |
| `Tethering/src/com/android/networkstack/tethering/IPv6TetheringCoordinator.java` | ULA prefix for local-only, upstream /64 for global |
| `Tethering/src/com/android/networkstack/tethering/EntitlementManager.java` | Ethernet is subject to entitlement |
| `Tethering/res/values/config.xml`, `Tethering/Android.bp` (android17-release, android16-qpr2-release) | USB regex, legacy DHCP default, module min_sdk 31 |
| `service-t/src/com/android/server/ethernet/EthernetTracker.java`, `EthernetServiceImpl.java` | Tethered interface selection, server-mode link handling, permission |
| `service/ServiceConnectivityResources/res/values/config.xml` (android13, 14, 15, 17) | Ethernet interface regex per version |
| platform/frameworks/base, android11-release and android12-release, `core/res/res/values/config.xml` | `eth\d` default regex on 11 and 12 |
| platform/frameworks/opt/net/ethernet, android11-release, `java/com/android/server/ethernet/EthernetTracker.java` | Android 11 and 12 tracker |
| platform/frameworks/base, android11-release to android17-release, `packages/Shell/AndroidManifest.xml` | Shell permissions on every version |
| platform/packages/modules/NetworkStack, android17-release, `src/android/net/dhcp/DhcpServingParams.java`, `DhcpLeaseRepository.java` | Prefix 16 to 30, single client lease |
| platform/frameworks/base, android17-release, `core/java/android/os/Build.java` | API 37 constant |

## Shizuku, Sui and root

| Source | Supports |
|---|---|
| https://repo1.maven.org/maven2/dev/rikka/shizuku/api/maven-metadata.xml | Shizuku API 13.1.5 is the latest (also provider, aidl, shared) |
| https://repo1.maven.org/maven2/dev/rikka/shizuku/api/13.1.5/api-13.1.5.pom | Shizuku API license: MIT |
| https://github.com/RikkaApps/Shizuku-API (README, LICENSE) | Usage guide, permission flow, uid 2000 for ADB and 0 for root, user services |
| https://raw.githubusercontent.com/RikkaApps/Shizuku-API/master/api/src/main/java/rikka/shizuku/Shizuku.java | Verified signatures: getUid, getVersion, listeners, requestPermission, user services |
| https://raw.githubusercontent.com/RikkaApps/Shizuku-API/master/api/src/main/java/rikka/shizuku/ShizukuBinderWrapper.java | How the wrapper forwards a transaction |
| https://raw.githubusercontent.com/RikkaApps/Shizuku-API/master/api/src/main/java/rikka/shizuku/SystemServiceHelper.java | getSystemService; deprecated direct transact helpers |
| https://raw.githubusercontent.com/RikkaApps/Shizuku-API/master/api/src/main/java/rikka/sui/Sui.java | Sui.init and Sui.isSui |
| https://raw.githubusercontent.com/RikkaApps/Shizuku-API/master/server-shared/src/main/java/rikka/shizuku/server/Service.java | Server clears the calling identity before forwarding; getUid is the server's own uid |
| https://raw.githubusercontent.com/RikkaApps/Shizuku/master/manager/src/main/jni/starter.cpp | How the Shizuku server is started with app_process; no uid change |
| https://raw.githubusercontent.com/RikkaApps/Shizuku/master/LICENSE and README.md | Shizuku app license and name, icon and package carve-outs |
| https://shizuku.rikka.app/guide/setup/ | Starting Shizuku with wireless debugging; restart after reboot |
| https://github.com/RikkaApps/Shizuku-API/blob/master/README.md (UserService section, changelog) | User services: AIDL destroy method with code 16777114, no-argument or Context constructor (v13), runs as the server uid, "no restrictions on non-SDK APIs in the user service process", not killed automatically when unbound |
| https://raw.githubusercontent.com/RikkaApps/Shizuku-API/master/api/src/main/java/rikka/shizuku/Shizuku.java (UserServiceArgs, bindUserService, unbindUserService, peekUserService, getVersion) | UserServiceArgs(ComponentName) with daemon, tag, version, debuggable, processNameSuffix; user services need server version 10; connection callbacks on the main thread |
| https://shizuku.rikka.app/download/ | Official Shizuku download page, linked from the guided setup |
| https://github.com/RikkaApps/Sui (README, LICENSE, releases) | Sui internals, GPL-3.0, v13.5.1 |
| https://github.com/topjohnwu/libsu (README, LICENSE, RootService.java) | libsu 6.0.0, Apache-2.0, RootService runs as uid 0 |
| https://raw.githubusercontent.com/topjohnwu/Magisk/master/docs/tools.md | su options: target uid, -Z context, --drop-cap; magisk domain |
| platform/packages/modules/Connectivity, main, `Tethering/src/com/android/networkstack/tethering/TetheringService.java` | Caller checks: checkPackageNameMatchesUid, hasTetherChangePermission, onlyAllowPrivileged |
| platform/packages/modules/Connectivity, main, `Tethering/common/TetheringLib/src/android/net/ITetheringConnector.aidl` | Connector methods; no shell command handler |
| platform/frameworks/base, main and android-11.0.0_r1, `services/core/java/com/android/server/appop/AppOpsService.java` | Root bypass of the package check; resolveUid; com.android.shell special case |
| platform/frameworks/base, main, `core/java/android/app/ActivityManager.java` | Root and system uid hold every permission |
| platform/frameworks/base, main, `packages/Shell/AndroidManifest.xml` | Shell holds TETHER_PRIVILEGED, NETWORK_SETTINGS, WRITE_SETTINGS |
| platform/frameworks/base, main, `cmds/svc/src/com/android/commands/svc/` | svc has no tethering applet |

## Root module packaging

| Source | Supports |
|---|---|
| https://topjohnwu.github.io/Magisk/guides.html (docs/guides.md) | Module layout, module.prop, update JSON, action.sh, installer rules, boot stages, BusyBox standalone mode |
| https://raw.githubusercontent.com/topjohnwu/Magisk/master/docs/changes.md | v31.0 (2026-09-04), v30.7 (2026-02-23) su keeps capabilities without --drop-cap, v28.0 action.sh |
| https://raw.githubusercontent.com/topjohnwu/Magisk/master/docs/details.md | /data/adb is root 0700 and device-encrypted; magisk domain permissive |
| https://github.com/topjohnwu/Magisk/blob/master/native/src/core/bootstages.rs | No module script at boot complete; bootloop protection |
| https://github.com/topjohnwu/Magisk/blob/master/native/src/core/module.rs | Updates replace the module directory; uninstall.sh handling |
| https://github.com/topjohnwu/Magisk/releases/tag/v24.0 | Online repo removed, updateJson introduced |
| https://kernelsu.org/guide/module.html, difference-with-magisk.html, metamodule.html, module-webui.html, module-config.html | KernelSU module format, scripts, environment, metamodule only for /system changes |
| https://github.com/tiann/KernelSU (kernel/selinux, userspace/ksud/src/su.rs, init_event.rs) | Permissive ksu domain; su accepts a target user; scripts started without waiting |
| https://apatch.dev/apm-guide.html | APatch module scripts and environment |
| https://github.com/bmax121/APatch (releases, apd/src) | APatch 11224; all-allow context; su target user |
| https://github.com/topjohnwu/ndk-box-kitchen/blob/master/busybox.config | BusyBox applets: setuidgid, setpriv without uid switch, ip, flock, timeout |
| https://android.googlesource.com/kernel/common/+/d49297739550%5E%21/ | Binder takes the caller euid from the credentials saved at binder_open |
| platform/frameworks/base, main, `cmds/app_process/app_main.cpp` | app_process opens binder before main |
| platform/art, main, `runtime/runtime.cc` | Hidden API enforcement off unless zygote or a flag enables it |
| platform/packages/modules/Connectivity, main, `Tethering/src/com/android/networkstack/tethering/Tethering.java` | Ethernet request, pending request, unplug stops tethering |
| platform/packages/modules/Connectivity, main, `service-t/src/com/android/server/ethernet/EthernetTracker.java` | Server mode and tethered interface request |
| platform/packages/modules/Connectivity, main, `service/ServiceConnectivityResources/res/values/config.xml` | Default Ethernet interface regex per version |
| https://developer.android.com/training/articles/direct-boot | Boot completes before unlock; device-encrypted storage |
| https://lkml.iu.edu/hypermail/linux/kernel/2405.2/00872.html | inotify does not work on sysfs |
| https://github.com/Magisk-Modules-Alt-Repo/submission | Alt-Repo submission rules |
| https://mmrl.dev/guide | MMRL repositories and repo.json |
| https://github.com/Fox2Code/FoxMagiskModuleManager | Archived |
| https://api.github.com/orgs/KernelSU-Modules-Repo/repos | KernelSU module repo organization (web pages returned 404) |

## Distribution and listings

| Source | Supports |
|---|---|
| https://github.com/ImranR98/Obtainium (and releases) | Obtainium v1.6.17 (2026-09-13) |
| https://wiki.obtainium.imranr.dev/deep_links/, /sources/, /app_tracking/ | Add links, redirect page, version detection, APK filters |
| https://raw.githubusercontent.com/ImranR98/Obtainium/main/lib/app_sources/github.dart | tag_name, drafts and pre-releases, asset types |
| https://izzyondroid.org/docs/general/AppInclusionPolicy/ | Inclusion requirements, 30 MB, signing, AI policy |
| https://izzyondroid.org/docs/general/Fastlane/ | Fastlane paths and length limits |
| https://izzyondroid.org/docs/reproducibleBuilds/EstablishApp/ | Reproducible builds requirements, Windows caveat |
| https://codeberg.org/IzzyOnDroid/repodata (issue templates) | Request process, required AI field, Magisk module template |
| https://android.izzysoft.de/articles/named/iod-scan-apkchecks?lang=en (2024-03-25) | APK checks, dependency-info block |
| https://apt.izzysoft.de/magisk/ | IzzyOnDroid Magisk repository (beta) |
| https://github.com/timschneeb/awesome-shizuku and CONTRIBUTING.md | Sections, entry format, Network category |
| https://developer.android.com/developer-verification and /guides/faq, /guides/limited-distribution, /guides/full-distribution, /guides/open-source-app-registration | Developer verification timeline, scope, account types, registration |
| https://android-developers.googleblog.com/2026/06/android-developer-verification.html | 2026-09-30 enforcement in four countries, participating stores |
| https://f-droid.org/2026/02/24/open-letter-opposing-developer-verification.html | F-Droid advises against registering |
| https://support.google.com/googleplay/android-developer/answer/9888379 | Device and Network Abuse policy |
| https://support.google.com/googleplay/android-developer/answer/11926878 | Target API 36 since 2026-08-31 |
| https://support.google.com/googleplay/android-developer/answer/14151465 | 12 testers for 14 days |
| https://support.google.com/googleplay/android-developer/answer/9842756 | Play App Signing with an uploaded key |
| https://f-droid.org/docs/Inclusion_Policy/, /docs/Anti-Features/, /docs/Reproducible_Builds/ | F-Droid rules |
| https://github.blog/changelog/2025-10-28-immutable-releases-are-now-generally-available/ | Immutable GitHub releases |

## Licensing and dependencies

| Source | Supports |
|---|---|
| https://www.apache.org/licenses/LICENSE-2.0.txt | Official Apache-2.0 text (SHA-256 cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30) |
| https://www.gnu.org/licenses/gpl-3.0.txt | Official GPLv3 text (SHA-256 3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986) |
| https://www.apache.org/licenses/GPL-compatibility.html | Apache-2.0 compatible with GPLv3, not GPLv2 |
| https://www.gnu.org/licenses/license-list.html | MIT, BSD-3-Clause compatible; EPL-1.0 incompatible |
| https://infra.apache.org/licensing-howto.html | What belongs in NOTICE |
| https://github.com/JetBrains/kotlin/blob/master/license/NOTICE.txt | Kotlin NOTICE text |
| https://github.com/Kotlin/kotlinx.coroutines/blob/master/license/NOTICE.txt | kotlinx.coroutines NOTICE text |
| https://reuse.software/spec-3.3/ and https://reuse.software/faq/ | REUSE 3.3 rules, OR expressions |
| https://github.com/fsfe/reuse-action | reuse-action v6 |
| https://developercertificate.org/ | DCO 1.1 |
| https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/managing-repository-settings/managing-the-commit-signoff-policy-for-your-repository | Web commit sign-off setting |
| https://f-droid.org/en/docs/Build_Metadata_Reference/ | One SPDX identifier per app, least restrictive |
| https://dl.google.com/android/maven2/ (maven-metadata.xml and POMs) | AndroidX and Compose versions and licenses |
| https://repo1.maven.org/maven2/ (maven-metadata.xml and POMs) | Kotlin, kotlinx, Shizuku, hiddenapibypass, Robolectric, JUnit versions and licenses |
| https://jitpack.io/com/github/topjohnwu/libsu/core/6.0.0/core-6.0.0.pom | libsu POM without license data |
| https://developer.android.com/develop/ui/compose/graphics/images/material | material-icons artifact no longer recommended |
| https://services.gradle.org/distributions/gradle-9.8.1-bin.zip.sha256 and gradle-9.8.1-wrapper.jar.sha256 | Gradle checksums pinned in the wrapper |

## Other tools

| Source | Supports |
|---|---|
| https://github.com/Mygod/VPNHotspot (README, `StaticIpSetter.kt`, `TetheringManagerCompat.kt`, `TetheringTileService.kt`) | License, root requirement, static IP on loopback, commented static request, Ethernet tile |
| https://github.com/Mygod/VPNHotspot/issues/193, /623, /618 | Random tethering subnet since Android 11 |
| https://github.com/Mygod/VPNHotspot/issues/582, /631 | Demand for automatic Ethernet tethering |
| https://github.com/Mygod/VPNHotspot/issues/547, /578, /469 | Hub and adapter problems, toggles dropping, vendor restrictions |
| https://github.com/Mygod/VPNHotspot/issues/814 | Samsung crash in registerTetheringEventCallback |
| https://github.com/Mygod/VPNHotspot/issues/789 and /pull/809 | Shizuku mode draft |
| https://github.com/newppaer/ethernet-config (and issues) | Client-mode static IP with `ip addr`, reported failure |
| https://github.com/xprateek/AOT | Legacy ndc backend, ADB over TCP, WTFPL |
| https://github.com/davnozdu/ksu-eth-tether-autostart | Root framework autostart, polling, PIN fallback, HyperOS 3 report |
| https://github.com/handicop/galaxy-ethernet-tether | Samsung accessibility automation, no license |
| https://github.com/carlelieser/shizzi (docs/android-compatibility.md, issues) | Shizuku tethering, Mainline variance, vendor issues |
| https://github.com/sigmabosshvh/android-testtap-egress (docs/writeup.md) | Shell identity Ethernet tethering reported across vendors |
| platform/packages/modules/Connectivity, main, `framework-t/src/android/net/EthernetManager.java`, `service-t/.../EthernetServiceImpl.java` | Client mode needs MANAGE_ETHERNET_NETWORKS |
| platform/packages/apps/Settings, main, `src/com/android/settings/network/tether/TetherSettings.java` | Built-in toggle, Data Saver disables it |
| platform/frameworks/base, main, `packages/SystemUI/src/com/android/systemui/qs/tiles/` | No Ethernet tethering tile in AOSP |
| https://www.androidpolice.com/2020/04/23/ethernet-tethering-arrives-in-latest-android-11-developer-preview | Ethernet tethering arrived in Android 11 |
| https://eu.community.samsung.com (Galaxy S24 and S23 threads, search snippets only) | One UI Ethernet reports (unverified, pages returned 403) |

## Auto-start and background limits

Accessed on 2026-10-08.

| Source | Supports |
|---|---|
| https://cs.android.com/android/platform/superproject/main/+/main:packages/modules/Connectivity/Tethering/src/com/android/networkstack/tethering/Tethering.java | `startTethering`, `setEthernetTethering`, `EthernetCallback.onAvailable` and `onUnavailable`, `stopEthernetTethering` |
| https://cs.android.com/android/platform/superproject/main/+/main:packages/modules/Connectivity/Tethering/src/com/android/networkstack/tethering/RequestTracker.java | Pending requests, fuzzy matching and `TETHER_ERROR_DUPLICATE_REQUEST` |
| https://cs.android.com/android/platform/superproject/main/+/main:packages/modules/Connectivity/service-t/src/com/android/server/ethernet/EthernetTracker.java | `requestTetheredInterface`, server mode, `maybeUpdateServerModeInterfaceState`, interface selection |
| https://cs.android.com/android/platform/superproject/main/+/main:packages/modules/Connectivity/framework-t/src/android/net/EthernetManager.java | `requestTetheredInterface` and its documentation |
| https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android11-release/packages/Tethering/src/com/android/networkstack/tethering/Tethering.java | The same request handling on Android 11 |
| https://android.googlesource.com/platform/frameworks/opt/net/ethernet/+/refs/heads/android11-release/java/com/android/server/ethernet/EthernetTracker.java | The same Ethernet server mode on Android 11 |
| https://developer.android.com/develop/background-work/services/fgs/service-types | `connectedDevice` type, its permission and prerequisites (`CHANGE_NETWORK_STATE`) |
| https://developer.android.com/about/versions/15/behavior-changes-15#fgs-boot-completed | Types blocked from `BOOT_COMPLETED` on Android 15 |
| https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start | Exemptions from background start limits (boot, widget, notification) |
| https://developer.android.com/develop/background-work/services/fgs/timeout | Time limits only for `dataSync` and `mediaProcessing` |
| https://developer.android.com/develop/ui/views/quicksettings-tiles | Tile behaviour; no foreground service exemption stated |
| https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/packages/SystemUI/src/com/android/systemui/qs/external/TileLifecycleManager.java | Bind flags of a listening tile |
| https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/services/core/java/com/android/server/am/ActiveServices.java | Foreground start check for bound processes |
| https://github.com/RikkaApps/Shizuku/releases | v13.6.0: auto start without root on Android 13 and later on a trusted WLAN |
| https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/provider/Settings.java and https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/main/AndroidManifest.xml (2026-10-09) | `Settings.ACTION_TETHER_SETTINGS` is `android.settings.TETHER_SETTINGS` (system API); the Settings app exports an activity for it |
| https://developer.android.com/reference/android/content/Intent#ACTION_AUTO_REVOKE_PERMISSIONS and https://developer.android.com/reference/android/content/pm/PackageManager#isAutoRevokeWhitelisted() (2026-10-09) | The page and the check for permissions of unused apps (Android 11) |

## Translation platform

Accessed on 2026-10-08.

| Source | Supports |
|---|---|
| https://weblate.org/en/hosting/ | Libre plan, public projects only |
| https://weblate.org/en/news/archive/weblate-even-more-open-now/ | Libre plan conditions (2020) and approval after a trial |
| https://docs.weblate.org/en/latest/formats/android.html | Android string resources, plurals, read-only strings |
| https://docs.weblate.org/en/latest/formats/appstore.html | Fastlane and F-Droid store metadata |
| https://docs.weblate.org/en/latest/admin/code-hosting.html | GitHub App, pull requests, webhooks |
| https://docs.weblate.org/en/latest/contributing/license.html | Weblate is GPL-3.0-or-later |
| https://weblate.org/en/terms/ | Only functional cookies |
| https://f-droid.org/en/docs/Translation_and_Localization/ | F-Droid translates on Hosted Weblate |
| https://crowdin.com/page/open-source-project-setup-request | Crowdin open source conditions |
| https://support.crowdin.com/github-integration/ | Crowdin pull requests |
| https://www.transifex.com/open-source/ | Transifex open source conditions |
| https://help.transifex.com/en/articles/6265125-github-installation-and-configuration | Transifex GitHub integration |
| https://docs.tolgee.io/platform/ and https://tolgee.io/tolgee-vs-crowdin | Tolgee free offer for open source |
| https://github.com/tolgee/tolgee-platform/blob/main/LICENSE | Tolgee license |
| https://poeditor.com/kb/open-source-localization and https://poeditor.com/kb/pull-requests | POEditor open source plan and pull requests |

## Compatibility, robustness and security

Accessed 2026-10-09. Each claim with its exact source line is in [COMPATIBILITY.md](COMPATIBILITY.md), [ROBUSTNESS.md](ROBUSTNESS.md) and [SECURITY_REVIEW.md](SECURITY_REVIEW.md).

| Source | Used for |
|---|---|
| https://android.googlesource.com/platform/packages/modules/Connectivity/+/refs/heads/android12-release, android16-release, android17-release | Tethering and EthernetTracker behaviour per version: local scope, error 18, interface patterns, two adapters, Data Saver, policy, airplane mode, static address handling |
| https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android11-release and main | Android 11 Tethering, shell permissions, airplane-mode radios, broadcast delivery to non-exported receivers, provider permission checks |
| https://android.googlesource.com/kernel/common/ (android11-5.4 to android17-6.18, android-mainline) | USB Ethernet drivers in the Generic Kernel Image |
| https://source.android.com/docs/core/ota/modular-system/tethering and https://source.android.com/docs/core/architecture/kernel/generic-kernel-image | Tethering module and GKI policy |
| https://github.com/RikkaApps/Shizuku (commit b844bc4) and https://github.com/RikkaApps/Shizuku-API (commit a27f6e4) | Who can bind and reach a user service; the Context constructor from Shizuku v13 |
| Public reports linked in COMPATIBILITY.md (Android Police, ISPreview, gstylemag, Samsung and /e/ communities, techbone.de, GrapheneOS forum, aalto.fi) | Phone makers, adapters and hubs |

## Releases and listings

Accessed 2026-10-09.

| Source | Used for |
|---|---|
| https://developer.android.com/tools/apksigner | Signature schemes and `apksigner verify` (only the newest scheme the minimum SDK needs is reported unless `--min-sdk-version` is lowered) |
| https://developer.android.com/studio/publish/app-signing | Release key, keystore and the consequence of a lost key |
| https://source.android.com/docs/security/features/apksigning | v2 and v3 signature schemes live in the APK signing block, outside the zip entries |
| https://github.com/timschneeb/awesome-shizuku/blob/master/CONTRIBUTING.md | Entry format, sections and tags for awesome-shizuku |
| https://github.com/ImranR98/Obtainium (assets/graphics/badge_obtainium.png) | Obtainium badge in the README |
| https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/ | Fastlane metadata layout and image names |

## Developer verification, Google Play, client mode

Accessed 2026-10-23.

| Source | Used for |
|---|---|
| https://developer.android.com/developer-verification | Timeline: participating stores in four countries from 2026-09-30, global for all apps in 2027 |
| https://developer.android.com/developer-verification/guides/faq | Direct downloads not affected yet, USD 25 full account, limited distribution (20 devices), advanced flow, failed updates of unregistered apps, several keys per package |
| https://developer.android.com/developer-verification/guides/open-source-app-registration | Registering an open-source app: package name, certificate fingerprint, the proof file after 50 installs |
| https://developer.android.com/blog/posts/android-developer-verification-rolling-out-to-all-developers-on-play-console-and-android-developer-console | Rollout to all developers (2026-03-30) |
| https://developer.android.com/google/play/requirements/target-sdk | Play target API 36 for new apps and updates since 2026-08-31 |
| https://support.google.com/googleplay/android-developer/answer/13392821 | Play foreground service declaration with a video per type |
| https://developer.android.com/guide/components/fg-service-types | Scope of the `connectedDevice` type |
| https://services.gradle.org/distributions/gradle-9.8.1-bin.zip.sha256 and gradle-9.8.1-wrapper.jar.sha256 | Checksums of the Gradle 9.8.1 wrapper update (verified against the generated files) |
| https://android.googlesource.com/platform/packages/modules/Connectivity/+/refs/heads/main/framework-t/src/android/net/EthernetManager.java and service-t/src/com/android/server/ethernet/EthernetServiceImpl.java | `updateConfiguration`, `enableInterface`, `setConfiguration` permissions (`MANAGE_ETHERNET_NETWORKS`, `NETWORK_STACK`) |
| https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/Shell/AndroidManifest.xml | The shell user holds `NETWORK_SETTINGS` and `TETHER_PRIVILEGED`, not `MANAGE_ETHERNET_NETWORKS` or `NETWORK_STACK` |

