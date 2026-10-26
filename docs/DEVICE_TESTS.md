<!--
SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later
-->

# Device tests

Manual tests on a real phone, run before a release and after changes to the engine or the automation. Everything is done on the phone itself, no computer needed. The unit and UI tests in the repository cover the logic; these cover what only a real phone, a real adapter and a real device on the other end can show.

You need: a phone with Shizuku (wireless debugging), a USB-C Ethernet adapter, and for some tests a device with an Ethernet port (a laptop, or a car's diagnostic port with an ENET cable). For each test, note pass or fail, the phone, the Android version and the adapter. When something looks wrong, export the log (**Log**, **Export**) and keep it with the result; debug builds also log StrictMode findings with the tag "strictmode".

The results so far are at the end.

## Install and Shizuku

### I1 Install and the introduction

1. Install the APK and open EtherTether. Allow installing from that source if Android asks.
2. Expected: the introduction, "Page 1 of 4", with **Skip** and **Next**. It shows only once; Settings has **Show the introduction again**.
3. **Next**: page 2 "Shizuku gives the access" with the current Shizuku state, **Show me the steps** (if Shizuku is not ready) and **Check again**.
4. Page 3 "Test your adapter": plug in the adapter and tap **Check adapter**. Expected: "Adapter: usb0, no cable" (or eth0).
5. Page 4, **Start using EtherTether**. Expected: the home screen. Close and reopen the app: the introduction does not come back.

### I2 Home without Shizuku

1. Stop Shizuku (or leave it uninstalled) and open EtherTether.
2. Expected: the status card "Off", **Start** greyed out, and a card "Shizuku is installed but not running." (or "not installed") with **Set up Shizuku**.
3. Start Shizuku and come back. Expected: within a second or two the card goes away and **Start** works.

### I3 Guided Shizuku setup and access

1. Stop Shizuku, open EtherTether, tap **Set up Shizuku**.
2. Expected, from top to bottom: "Current state" with **Check again**; "What Shizuku is"; "1. Install Shizuku" with **Open Shizuku**; "2. Start Shizuku with wireless debugging" with steps 1 to 6 and **Open Developer options**; "3. Allow EtherTether"; "After every restart"; **Back**.
3. **Open Developer options** opens the system page; **Open Shizuku** opens Shizuku. Start Shizuku there (Wi-Fi on, wireless debugging, pairing code), come back, tap **Check again**.
4. Expected: "EtherTether has no access to Shizuku yet." with **Grant access**. Tap it and choose **Allow all the time** in Shizuku's dialog. Expected: "Shizuku is ready."
5. On a phone without Shizuku, the section shows **Open the official Shizuku page**, which opens shizuku.rikka.app/download.

### I4 Shizuku stopped while the app is open

1. With EtherTether open (split screen, or switch apps), stop Shizuku.
2. Return to EtherTether. Expected: it no longer says ready, and it does not crash.

## Engine (debug screen)

Turn on Settings, Developer, **Developer switch** and open **the debug screen**. After each test, **Copy all** keeps the raw lines, which matter more than a screenshot.

### E1 Diagnose

1. Expected on opening: "Engine debug", a readiness line "Shizuku is ready...", the request (Fixed address, 169.254.0.42, 169.254.0.1, 16, Local only, fallback off) and "No results yet."
2. Tap **Diagnose** (the first time takes a few seconds while Shizuku starts the engine).
3. Expected: the API level and phone maker, a connector class name, `startTethering(TetheringRequestParcel, String, String, IIntResultListener)`, `stopTethering(int, String, String, IIntResultListener)`, a register line, four "Builder.... found" lines, and transaction codes with three numbers, none "not found".

### E2 Read without an adapter

1. Unplug the adapter. Tap **Read states**, **Read interfaces**, **Status**.
2. Expected: "registered" (or "already registered"), no eth or usb interface in the serving lists, "no Ethernet interface", and Status "Off. No Ethernet adapter found."

### E3 Start with a fixed address, local only

1. Plug in the adapter (no cable needed), wait five seconds, tap **Start** under "Full operations".
2. Expected: "Ethernet tethering is on, local only, on usb0" (or eth0) and "Addresses: 169.254.0.42/16". The raw lines show `stopTethering (before start): 0 NO_ERROR`, one or two `startTethering (scope=2, exempt=...)` lines and a confirm line. A note that the carrier check could not be skipped is normal on some versions.
3. **Status**: "On, local only, interface usb0", with "up" and the address on the interface line.

### E4 Stop

1. Tap **Stop**. Expected: "Ethernet tethering is off.", `stopTethering: 0 NO_ERROR` and `wait for stop: Ethernet not serving`.

### E5 A real device on the other end

1. Start as in E3 and connect the other device, set to get its address automatically.
2. Expected on the other device: 169.254.0.1, netmask 255.255.0.0; it can ping 169.254.0.42.
3. **Status**: "cable connected" on the interface line (if the phone reports the cable state at all).
4. Pull only the cable out of the adapter, wait five seconds, **Status**. Expected: still "On, local only".

### E6 Removing the adapter

1. While on, tap **Status** once, then pull the adapter out of the phone, wait five seconds, **Status**.
2. Expected: "The adapter was removed, which ends Ethernet tethering. Plug it in again and tap Start."
3. Plug it in, **Status** (off, adapter found), **Start** (on again).

### E7 Share the internet connection

1. **Stop**, choose **Share internet**, **Start**. Expected: "on, sharing the internet connection" and `scope=1` in the raw lines. **Stop**.

### E8 Normal DHCP

1. Choose **Normal (DHCP)** and **Local only**, **Start**. Expected: on, with an address Android picked itself (a random private subnet). **Stop**.

### E9 Start without an adapter

1. **Fixed address**, unplug the adapter, **Start**. Expected at once: "Android accepted the request, but no Ethernet adapter serves yet." with the next step.
2. Plug the adapter in, wait five seconds, **Status**. Expected: on, local only. **Stop**.

### E10 Invalid input is never sent

1. Set the prefix length to 8. Expected under the field: "Android's DHCP server accepts only /16 to /30. Use /16 for the largest network." and **Use /16**.
2. **Start**. Expected: "The request is not valid; fix the fields first." and no raw lines.
3. **Use /16** clears the error.

### E11 Single steps

1. With the adapter in: **Build request** shows one "parcel" line with the request fields (type 5, both addresses, exempt true, scope 2).
2. **Start only, no exemption** shows a raw result code line.
3. **Status**, then **Stop only**: `stopTethering: 0 NO_ERROR`.

### E12 Shizuku stopped and started again

1. With the debug screen open, stop Shizuku (or restart the phone and open the debug screen without starting Shizuku).
2. Expected: the readiness line says Shizuku is not running. **Start** gives a clear message that the engine could not be reached, with the next step; no crash.
3. Start Shizuku, **Diagnose**, **Start**. Expected: works again without restarting EtherTether.

## Home and automation

### H1 Start and stop from home

1. Adapter in, no cable, profile "Car ENET link-local 169.254.0.42/16" selected.
2. **Start**. Expected: "Starting" briefly, then "On" with the profile, "Interface usb0, local only", "Phone address: 169.254.0.42/16" and an adapter line. The profile list says "Stop tethering to change the profile."
3. Expected: a notification "Ethernet tethering is on" with **Stop**. On Android 13 and later the first Start asks for notification permission.
4. **Stop** on home: "Off", and the notification goes away.

### H2 Start without an adapter, then plug it in

1. Unplug the adapter, **Start**. Expected: "Waiting for the adapter", the message "Plug in the adapter. Android starts serving it as soon as it appears.", and the notification says "Waiting for the adapter".
2. Plug it in. Expected within a few seconds: "On" with the address. **Stop**.

### H3 Stop from the notification

1. Start as in H1 and leave the app. In the notification, tap **Stop**. Expected: the notification goes away.
2. Open EtherTether: "Off", with no "stopped outside" message.

### H4 Watchdog: stopped outside EtherTether

1. Start as in H1. In the phone's settings (Connection and sharing, or Hotspot and tethering), turn Ethernet tethering off.
2. Expected: an alert "Ethernet tethering stopped"; in the app the same message with "Tap Start to turn it on again." and **Dismiss**.

### H5 Watchdog: adapter removed, keep armed off

1. Settings: **Keep armed** off. Start as in H1 and pull the adapter out.
2. Expected: an alert "The adapter was removed"; in the app the same message with **Dismiss**, state "Off".

### H6 Keep armed

1. Settings: **Keep armed** on. Start as in H1, then pull the adapter out. Expected: no alert; app and notification say "Waiting for the adapter" (briefly "Arming again").
2. Plug it back in. Expected: "On" again within a few seconds without touching the phone; the other device gets 169.254.0.1 as in E5.
3. Repeat with the screen off and the app swiped away from recent apps. Expected: the same.
4. **Stop** in the notification, then pull and plug the adapter: nothing starts.

### H7 Start after restart

1. Settings: **Start after restart** on. (Shizuku 13.6 and later can start by itself on a trusted Wi-Fi on Android 13 and later; otherwise start it by hand.)
2. Restart the phone, unlock, start Shizuku within five minutes if it did not start by itself.
3. Expected: the notification "Waiting for the adapter" (or "On" with the adapter in); the log has "boot: waiting for Shizuku" and a start line.
4. Repeat without starting Shizuku. Expected after five minutes: no notification, and the log says "Shizuku did not become ready; not starting".

### H8 Quick Settings tile

1. Add the tile "Ethernet tethering" to Quick Settings and tap it. Expected: the tile lights up, its subtitle shows the profile, and the notification appears. If the app opens instead, Android refused the start from the tile (the log says "foreground service not allowed from the tile"). Every tap is logged as "tile: tap" and "service: command: toggle".
2. Tap again: off.
3. Stop Shizuku and tap the tile: subtitle "Shizuku needed", and the app opens.
4. If a start ever shows "Starting" for long: **Stop** works while starting, and the start ends with a message after 45 seconds at most.

### H9 Shizuku stopped while tethering runs

1. Start as in H1, then stop Shizuku and come back to EtherTether.
2. Expected: state "Unknown", the message "Shizuku stopped, so EtherTether cannot see or stop Ethernet tethering. It may still be on." with **Set up Shizuku** and **Open tethering settings**.
3. **Open tethering settings** opens Android's own tethering page (not a chooser, not another app).
4. Start Shizuku and come back: the real state instead of "Unknown"; **Stop** works if it is on.

### H10 Home-screen widget

1. Add the widget "Ethernet tethering". Expected: "Tethering off", the profile name and **Start**.
2. **Start**: "Tethering on" and **Stop**, plus the notification. **Stop**: off.
3. Tapping the widget outside the button opens the app.

## Profiles, log, settings

### U1 Profiles

1. Home, **Manage profiles**. Expected: the three ready-made profiles; the selected one says "Used for Start, the tile and the widget".
2. **New profile**: name "Bench", Fixed address, phone 10.9.0.1, prefix 8. Expected: an error under the prefix with **Use /16**; tap it. Leave the client empty: **Use 10.9.0.2** appears; tap it. **Save**: "Bench" is listed and selected.
3. On "Bench": **Duplicate** ("Bench (copy)"), **Edit** (rename, save), **Delete** (asks first).
4. **More options**, **Export profiles**, save the file; then **Import profiles** with the same file. Expected: "4 profiles added. 0 skipped."
5. Import something else (a photo). Expected: "This is not an EtherTether profile file."
6. Edit "Isolated lab network": below the empty name it says which ready-made name it keeps.

### U2 Log

1. **Log**: entries with time, level and tag, and "The log stays on this phone".
2. **Warnings and errors** and a search for "tether": the list and the count follow.
3. **Copy** works; **Export** opens the share sheet with a text file.
4. **Clear** asks first, then shows "No entries."

### U3 Appearance and language

1. Settings, Appearance: **Light**, **Dark**, **Follow the phone**; **Colours from the wallpaper** off and on. Everything stays readable.
2. Language **Türkçe**: the whole app in Turkish, including the notification and the tile after the next start. Then **Deutsch**, then **Phone language**.
3. On Android 13 and later, the phone's Settings, Apps, EtherTether, Language shows the same choice.
4. Check that no Turkish or German text is cut off.

### U4 Notifications and phone settings

1. Turn notifications off for EtherTether in the phone's settings. EtherTether's Settings show "Notifications are off for EtherTether..." with **Allow notifications**, which leads there.
2. **Alert when tethering stops** off, repeat H4: the message in the app, no alert.
3. **Open app settings** opens the phone's page for EtherTether (autostart and battery settings on phones that have them).
4. With **Keep armed** on and Android's "Manage app if unused" (or "Remove permissions if app isn't used") on: a hint with **Open the setting**. With it off, no hint.

### U5 About

1. **More options**, **About**: version and build type, the license text, the source code link, the signature verdict ("not signed with the official EtherTether release key" for a debug build) and the privacy note.
2. **Open-source libraries**: a list with licenses (Apache 2.0, MIT, BSD). **Open in the browser** opens the GitHub page.

### U6 Dark theme

1. In dark theme, check home, the setup screen, the debug screen, Settings and About: all text readable, colours follow the wallpaper on Android 12 and later.

### U7 TalkBack

1. With TalkBack on, move through home: the status card is read as one sentence ("Ethernet tethering: On. Profile ..."), Start says which profile it starts, every icon button has a name (Profiles, Log, Settings, More options), messages are read with their next step, and profile buttons name their profile ("Edit Bench").
2. On the setup screen: headings are announced as headings and every button is named.
3. Start or stop: TalkBack announces the new state.

### U8 No internet permission

1. Phone settings, Apps, EtherTether, permissions (including "Other permissions"). Expected: no internet or network access; only notifications and Shizuku's API permission.

## Robustness

### R1 Unplug, replug and the cable

1. **Keep armed** off. Start the car profile with the other device connected.
2. Pull only the cable: still "On"; plug it back and the other device works again.
3. Pull the adapter: "The adapter was removed". Plug it in again: the message now offers **Start**; tap it: "On".
4. **Keep armed** on: pull and plug the adapter three times quickly. Expected: it ends "On" by itself, without a "could not start again" alert.
5. With tethering off, plug and unplug the adapter: the status follows within a few seconds.

### R2 App ended while armed

1. **Keep armed** on, start without the adapter ("Waiting for the adapter").
2. Swipe EtherTether away, then **Force stop** it in the phone's app settings.
3. Open it. Expected within ten seconds: "Waiting for the adapter" again with the notification; plugging in the adapter gives "On".
4. **Stop**, force stop again, open: "Off", nothing starts.

### R3 Rotation and changes while open

1. With auto-rotate on, turn the phone on home, in Settings and in the profile editor with a half-typed name. Expected: same screen, typed text kept, nothing starts or stops, readable sideways.
2. While "On", switch between dark and light mode: still "On".

### R4 Two adapters

1. Plug in two adapters (or a hub with Ethernet and an adapter) and start. Expected: "On" with one interface and a line naming the other adapter as not used.
2. Pull the adapter in use: "The adapter was removed" (Android does not move to the other one). With keep armed on, note whether the other one starts serving.

### R5 Hotspot at the same time

1. Turn on the Wi-Fi hotspot and start the car profile. Expected: both work. Stop the hotspot: EtherTether stays "On".

### R6 Airplane mode, battery saver, Data Saver

1. While "On" (local only), turn on airplane mode: still "On", the cable device still reaches the phone. If Wi-Fi goes off with it, Shizuku (wireless debugging) stops too; EtherTether then shows the unknown state from H9.
2. Battery saver on: still "On"; with keep armed, pulling and plugging the adapter brings it back.
3. Data Saver on: Android stops tethering and EtherTether reports "Ethernet tethering stopped". Note what **Start** does with Data Saver on. Turn it off.

### R7 Security

1. Start, lock the phone, tap **Stop** in the notification on the lock screen. Expected: it asks to unlock first, then stops.
2. Start and stop from home, then stop and restart Shizuku: everything still works (the engine checks who calls it).
3. In H9, **Open tethering settings** opens Android's own page.

### R8 Battery use

1. Charge to about 90 %, unplug the charger, start without the adapter (keep armed on, "Waiting for the adapter"), screen off for one hour. Note EtherTether's (and Shizuku's) battery use.
2. Repeat for one hour while "On" with a device connected.
3. Expected: EtherTether near the bottom of the list, well under 1 % per hour of its own.

### R9 Car ENET cable

1. Start the car profile and connect the ENET cable to the car with the ignition on. Expected: "On", and "Connected device" shows 169.254.0.1. Check that the diagnostic software sees the car.
2. Note the adapter's brand and chip for COMPATIBILITY.md.

## Release builds

The release build has another application ID than the debug build (no `.debug`), so both can be installed at once. It needs its own Shizuku access, and while both are installed there are two tiles and two widgets.

### V1 The release build works

1. Install the release APK, go through the introduction, grant Shizuku access.
2. Start and stop the car profile, with and without the adapter, and from the tile. Expected: the same as the debug build. This catches classes R8 removed that the engine needs by name.
3. **About**: "Version X.Y.Z (code), release build".

### V2 The official signature

1. Install an APK built by the release workflow. **About**: "This application is signed with the official EtherTether release key." A test pre-release signed with a throwaway key says "not signed with...".
2. Google Play Protect may warn about an app from a new developer on first install; **More details**, **Install anyway**.

### V3 Update over the previous version

1. Install the new version over the previous one. Expected: Android offers **Update**, not a new app; profiles and settings are kept.
2. **About** shows the new version; start and stop as in H1.

### V4 Obtainium

1. Install Obtainium (github.com/ImranR98/Obtainium/releases). On the phone, open the repository page and tap **Get it on Obtainium** (or add `https://github.com/oytuncagil/ethertether` in Obtainium).
2. Expected: Obtainium lists EtherTether with the current version and recognises the installed copy.
3. At the next release: Obtainium shows the update and installs it in place.

### V5 Store texts and images

1. Read `fastlane/metadata/android/<locale>/full_description.txt` and `short_description.txt` in every language and look at the screenshots. Everything matches the current app and reads naturally.

## Results

Test phone: POCO F7, Android 16, HyperOS 3.0, not rooted, Shizuku 13 over wireless debugging, USB-C Ethernet adapter (interface eth0). A Pixel 7 rooted with Magisk is available for root-related checks.

### Development builds, 2026-10-09

| Test | Result | Notes |
|---|---|---|
| E1 Diagnose | Pass | Every connector method, Builder setter and callback code found (onResult=1, onCallbackStarted=1, onTetherStatesChanged=5) |
| E2 Read states and interfaces | Pass | This phone does not report the cable state or speed (`hasCarrier=null`, `speedMbps=null`) |
| E3, E4, E7, E8, E9, E11 | Pass | Fixed address local only and shared, normal DHCP (10.23.60.195/24), waiting without an adapter, single steps |
| E12 Shizuku stopped | Failed, fixed | Stop failed silently and the old state stayed; now the unknown state of H9 |
| H1, H2, H3, H5 | Pass | All three profiles, waiting, the notification, the adapter removed alert |
| H8 Quick Settings tile | Failed twice, fixed | First stuck at "Starting" while Android served, then stayed dark while on; both fixed |
| U1, U2, U7, U8 | Pass | |
| R1 Unplug and replug | Failed, fixed | With tethering off, plugging or unplugging did not update the status, so the "adapter removed" message never offered Start; a start without an adapter followed by Stop took about ten seconds; with keep armed, "Starting" showed for ten seconds after an unplug. Plugging back with keep armed served at once |
| R3 Rotation | Pass | |
| R5 Hotspot at the same time | Pass | |
| R6 Airplane mode | Pass | Local-only tethering kept running. Airplane mode turned Wi-Fi off, which stopped Shizuku; documented in ROBUSTNESS.md |
| R7 Security, steps 1 and 2 | Pass | |

### Release builds, 2026-10-09 to 2026-10-23

| Test | Result | Notes |
|---|---|---|
| V1 First signed build | Failed, fixed | Closed at once: `Failed to create an instance of androidx.work.impl.WorkDatabase` (old Room keep rule under R8 full mode); fixed by the WorkManager pin |
| V1, V2 after the fix | Pass | Starts and works; About shows the official verdict |
| V2 Play Protect | Pass, with a warning | Play Protect warned about a new developer; installed with "Install anyway" |
| V5 Store texts | Pass | Turkish and German texts reviewed and reworded |
| V3 1.0.0-rc.1 over the test build | Pass | Installed as an update; profiles kept |

### Not run yet

I1 to I4, E5, E6, E10, H4, H6, H7, H9, H10, U3 to U6, R2, R4, R6 (battery saver, Data Saver), R7 step 3, R8, R9 and V4. To retest after their fixes: E12, H8 and R1.
