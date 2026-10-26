<!--
SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later
-->

# Compatibility

Which phones, Android versions, adapters and hubs EtherTether works with, and how we know. Every cell has one of three labels:

- **Device**: verified on a real phone by the project ([DEVICE_TESTS.md](DEVICE_TESTS.md)).
- **Report**: verified only by a public report, blog post or third-party guide (linked). Not tested by the project.
- **Unknown**: no usable evidence yet.

Where the Android or kernel source says something, it is given in the column "Android source". Source reading explains behaviour but is not a device test, so a cell is never marked **Device** because of it. All links were opened on 2026-10-09. Gitiles line anchors (`#123`) work in a browser.

Short link prefixes:

- `CONN12`, `CONN16`, `CONN17`: `https://android.googlesource.com/platform/packages/modules/Connectivity/+/refs/heads/android12-release`, `android16-release`, `android17-release`
- `FWB11`: `https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android11-release`
- `KCOMMON`: `https://android.googlesource.com/kernel/common/+/refs/heads`

Results from other phones are welcome: open an issue with the phone, Android version, adapter and an exported log.

## Android versions

EtherTether needs Android 11 (API 30) or later and Shizuku. Ethernet tethering lives in the Tethering Mainline module (com.android.tethering, [AOSP](https://source.android.com/docs/core/ota/modular-system/tethering)), which Google updates outside platform releases, so two phones on the same Android version can behave differently.

| Android | Status | What works | Android source |
|---|---|---|---|
| 11 (API 30) | Unknown | Expected: fixed address and DHCP, always sharing (no local-only scope; the app says so and only falls back when the profile allows it) | Ethernet tethering type and `setStaticIpv4Addresses` exist: `FWB11/packages/Tethering/common/TetheringLib/src/android/net/TetheringManager.java#172`, `#590`. No `setConnectivityScope` in that file. Default interface name pattern is `eth\d` (one digit): `FWB11/core/res/res/values/config.xml#431`. Before Android 13, a phone without Wi-Fi, USB and Bluetooth tethering patterns also reports Ethernet tethering as unsupported: `CONN16/Tethering/src/com/android/networkstack/tethering/Tethering.java#2831` |
| 12 (API 31), 12L (32) | Unknown | Expected: everything, including local only | Local scope arrives: `CONN12/Tethering/common/TetheringLib/src/android/net/TetheringManager.java#660` |
| 13 (33), 14 (34) | Unknown | Expected: everything | Default pattern `eth\d+`: `CONN16/service-t/src/com/android/server/ethernet/EthernetTracker.java#278` |
| 15 (35) | Unknown | Expected: everything | Default pattern `(usb\|eth)\d+`: same file `#275` |
| 16 (36) | **Device** (POCO F7, HyperOS 3) | Fixed address local only and shared, DHCP, waiting without an adapter, stop, notification, watchdog, keep armed, profiles, Wi-Fi hotspot at the same time, airplane mode (local only keeps running). The phone did not let the cable state and speed be read | A second start while one is pending or serving is refused with error 18 even with a different address or scope (`CONN16/Tethering/src/com/android/networkstack/tethering/Tethering.java#714`); EtherTether always stops first, so this does not reach the user |
| 17 (37) | Unknown | Expected: everything | The Ethernet start path, the static address handling and the error 18 rule are unchanged from 16: `CONN17/Tethering/src/com/android/networkstack/tethering/Tethering.java#715`. The Tethering module now needs Android 12, so Android 11 phones get no newer module: `CONN17/Tethering/Android.bp#25` |

The shell user, which Shizuku runs as, holds the two permissions the engine needs (TETHER_PRIVILEGED and NETWORK_SETTINGS) on every version from 11 to the newest: `FWB11/packages/Shell/AndroidManifest.xml#257`, [main](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/Shell/AndroidManifest.xml#444).

## Phone makers

| Maker | Status | Evidence and quirks |
|---|---|---|
| Xiaomi / POCO (HyperOS) | **Device** | POCO F7, Android 16, HyperOS 3.0.301.0, Shizuku over wireless debugging: see the results in [DEVICE_TESTS.md](DEVICE_TESTS.md). A guide shows the Ethernet toggle on MIUI 14 and HyperOS 1 and 2: [techbone.de](https://www.techbone.de/xiaomi/ethernet-tethering-aktivieren-oder-deaktivieren). HyperOS limits on background apps: Settings in EtherTether point to Autostart and the battery setting |
| Google Pixel | Report | Pixel 3a on an Android 11 preview: [Android Police](https://www.androidpolice.com/2020/04/23/ethernet-tethering-arrives-in-latest-android-11-developer-preview). Pixel 2 XL through a USB-C hub: [ISPreview forum](https://www.ispreview.co.uk/talk/threads/android-11-ethernet-tethering-working-perfect-recommend-cheaper-5g-phone.37571/post-263269). A rooted Pixel 7 is available as a later test candidate |
| Samsung (One UI) | Report | Works through a hub on a Galaxy S24+: [gstylemag](https://gstylemag.com/2024/05/20/galaxy-s24-to-the-rescue-ethernet-tethering-made-easy/). The menu stays grey until an adapter is detected: [techbone.de](https://www.techbone.de/samsung/ethernet-tethering-aktivieren-oder-deaktivieren). Open user report that Ethernet is greyed out on an S24 after One UI 8: [Samsung community](https://eu.community.samsung.com/t5/galaxy-s24-series/ethernet-adapter-and-dex-station-ethernet-not-working-on-s24/td-p/13529903). A TP-Link UE300C was not recognised on an S24 Ultra: [Samsung community](https://eu.community.samsung.com/t5/galaxy-s24-series/tplink-usbc-ethernet-adapter-not-recognised-by-samsung-galaxy/m-p/10410883). Auto Blocker may block adapters: [Samsung community](https://us.community.samsung.com/t5/Tablets/Galaxy-Tab-A8-2022-lan-to-usb-issue/td-p/3353343/page/2). Whether One UI refuses the request from the shell user: Unknown |
| OnePlus (OxygenOS) | Report | Guide for OxygenOS 14: [techbone.de](https://www.techbone.de/oneplus/smartphone/tethering-internet-ueber-ethernet-teilen). A personal note says Developer options, Default USB configuration had to be changed: [aalto.fi](https://users.aalto.fi/~ltuuri/apu/koti-pc/ethernet-tether-oneplus.txt) |
| Fairphone (/e/OS) | Report | FP4 works with an RTL8153 adapter; an RTL8156BG adapter was not recognised. On an FP5 the switch turns on but the computer gets no address: [/e/ community](https://community.e.foundation/t/ethernet-tethering-not-working-no-ip-address-assigned/73709) |
| Oppo / realme (ColorOS) | Unknown | |
| Motorola | Unknown | Only reports of adapters used as a client |
| Sony | Unknown | Sony lists USB Ethernet as a client feature only |
| Nothing | Unknown | Only USB tethering is documented |
| Huawei, Honor | Unknown | Honor documents USB and Bluetooth tethering only |

Any maker can turn tethering off with `ro.tether.denied`, the TETHER_SUPPORTED setting or a failed carrier entitlement; then Android answers error 3, which EtherTether explains: `CONN16/Tethering/src/com/android/networkstack/tethering/Tethering.java#2848`, `CONN16/Tethering/src/com/android/networkstack/tethering/TetheringService.java#320`.

## Adapter chipsets

The adapter needs a driver in the phone's kernel. Phones that launched with Android 12 or later on kernel 5.10 or newer use Google's Generic Kernel Image ([GKI policy](https://source.android.com/docs/core/architecture/kernel/generic-kernel-image)); older phones use their maker's kernel, which may lack drivers.

| Chipset | Status | Evidence | Kernel source |
|---|---|---|---|
| Realtek RTL8153 (1 Gbit/s, most common) | Report (FP4 above) | The project's test adapter worked as eth0, but its chip is not identified yet (device test R9) | Built in or a GKI module in every branch: `KCOMMON/android11-5.4/arch/arm64/configs/gki_defconfig#286`, `KCOMMON/android12-5.10/...#334`, `KCOMMON/android13-5.15/...#354`, `KCOMMON/android14-6.1/...#366` (module from 6.1 on, listed in `KCOMMON/android14-6.1/modules.bzl#27`) |
| Realtek RTL8152 (100 Mbit/s) | Unknown | | Same driver (r8152) as above |
| ASIX AX88179 / AX88179A (1 Gbit/s) | Unknown | | On by default under USB networking: `KCOMMON/android-mainline/drivers/net/usb/Kconfig#198` |
| ASIX AX88772 (100 Mbit/s) | Unknown | | On by default: same file `#166` |
| CDC ECM / NCM class adapters | Unknown | | On by default: same file `#216`, `#258` |
| Realtek RTL8156 (2.5 Gbit/s) | Report (not recognised on FP4) | | In r8152 only from kernel 5.15: `KCOMMON/android13-5.15/drivers/net/usb/r8152.c#9835`; absent in 5.4 and 5.10 |
| Realtek RTL8157 (5 Gbit/s) | Unknown | | Only in android-mainline so far: `KCOMMON/android-mainline/drivers/net/usb/r8152.c#10404` |

Whether a phone maker actually loads the GKI modules (kernel 6.1 and later): Unknown per phone. The adapter test on the last page of EtherTether's introduction, and the home status card, show whether Android sees the adapter.

## USB-C hubs and power

| Case | Status | Evidence |
|---|---|---|
| Hub with Ethernet, phone charging at the same time | Report | Pixel 2 XL ([ISPreview forum](https://www.ispreview.co.uk/talk/threads/android-11-ethernet-tethering-working-perfect-recommend-cheaper-5g-phone.37571/post-263269)), Galaxy S24+ ([gstylemag](https://gstylemag.com/2024/05/20/galaxy-s24-to-the-rescue-ethernet-tethering-made-easy/)) |
| Phone cannot power the adapter | Report | An externally powered hub may be needed: [GrapheneOS forum](https://discuss.grapheneos.org/d/96) |
| A hub dropping the link when charging starts or stops (USB power renegotiation) | Unknown | For EtherTether this looks like an unplug: keep armed starts again |
| Hub chipset differences | Unknown | |

## Several adapters and other tethering

| Case | Status | Android source | What EtherTether does |
|---|---|---|---|
| Two Ethernet adapters at once | Unknown on a device | Only the first adapter Android sees is used for tethering; if it is removed, the second is not taken over until it is plugged in again: `CONN16/service-t/src/com/android/server/ethernet/EthernetTracker.java#154`, `#622`, `#748` | Home names the other adapter as "not used" |
| Wi-Fi hotspot or USB tethering running at the same time | **Device** (Wi-Fi hotspot, POCO F7) | One server per interface; the duplicate rule is per tethering type, so they can run together: `CONN16/Tethering/src/com/android/networkstack/tethering/Tethering.java#3111` | Nothing special needed. With a fixed address, choose a subnet the hotspot and the upstream do not use: Android does not check a fixed address against them (`CONN16/staticlibs/device/com/android/net/module/util/PrivateAddressCoordinator.java#207`). The car profile uses link-local 169.254.0.0/16, which Android never picks for a hotspot; the lab profile uses 192.168.50.0/24, which Android's random choice for a hotspot or a Wi-Fi network at home can also use, so change it if they clash |

Robustness cases such as unplugging, airplane mode, Data Saver and battery saver are in [ROBUSTNESS.md](ROBUSTNESS.md).
