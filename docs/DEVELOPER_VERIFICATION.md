<!--
SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later
-->

# Android developer verification

What Google's developer verification means for EtherTether, and how to register the app. Status checked on 2026-10-23; sources in [SOURCES.md](SOURCES.md).

## What it is and when it applies

- Since 2026-09-30, certified Android phones (Android 7 and later) in Brazil, Indonesia, Singapore and Thailand install apps from participating stores (Google Play, Samsung Galaxy Store, Xiaomi GetApps and some other phone makers' stores) only when the app is registered by a verified developer.
- EtherTether is not in those stores. Google's FAQ (2026-07-15): "If you distribute your app through other stores, or if users sideload your app directly, these new verification requirements won't apply to your app yet." GitHub Releases, Obtainium and IzzyOnDroid installs work as before for now.
- In 2027 the check extends "globally for all apps on certified Android devices" (no exact date yet). From then on, an unregistered app installs and updates only with ADB or with the "advanced flow" (Developer options, a check, a restart, a one-day wait, then unverified installs allowed for 7 days or for good). With the advanced flow off, updates of an unregistered app fail, also through Obtainium.
- Registering changes nothing in the app, the key or the release process. Users of a registered app see the normal install screen.

## Which account

| Account | Cost | Identity | Fits EtherTether? |
|---|---|---|---|
| Android Developer Console, full distribution | USD 25 once | Government ID (organizations also a D-U-N-S number) | Yes, for apps distributed only outside Google Play |
| Google Play Console | USD 25 once | Government ID | Yes, and needed for a Play release anyway; it can register apps distributed outside Play too |
| Limited distribution | Free | An e-mail address | No: at most 20 devices, for testing and personal use |

If a Google Play release is likely, one Play Console account covers both (one fee, one identity check). A limited distribution account can be upgraded to a full one later, not the other way round.

## Registering

About 30 minutes, plus Google's review of the identity.

1. Sign in at https://android.google.com/developerconsole/developers (Android Developer Console) or https://play.google.com/console/developers (Play Console) with the Google account that should own the app.
2. Create a personal account with full distribution and pay the fee.
3. Verify the identity: legal name as on the ID, address, and a photo of the ID when asked. Wait for the confirmation e-mail.
4. Click **Register package name** (in Play Console, on the developer verification tab).
5. Package name: `io.github.oytuncagil.ethertether`, **Next**.
6. **Add key** with the release certificate fingerprint from the README:
   `75:37:BE:B7:0C:8E:EF:0E:39:88:1F:A9:BC:4A:1C:37:DE:FC:97:86:29:AC:DE:4E:A3:85:B8:24:31:1D:05:7B`
   GitHub Releases, Obtainium and IzzyOnDroid all ship the APK signed with this key, so nothing more is needed.
7. Save, and update the status table below.

### Register early

When an app already has 50 or more installs before it is registered, the console asks for proof of ownership: a file `adi-registration.properties` with a snippet from the console goes into `app/src/main/assets/`, a release with it is published, and that APK is uploaded to the console. Registering before or right after the first public release, while there are fewer than 50 installs, needs only steps 4 to 7. The snippet is not secret, so if it is ever needed it goes in through a normal pull request.

### If a store signs the app with its own key

IzzyOnDroid and Obtainium ship our own signed APK, so one key covers them. If F-Droid's main repository or another store ever builds and signs EtherTether itself, that key's fingerprint is added to the same package with **Add key** (several keys per package are allowed).

## Status

| Date | Status |
|---|---|
| 2026-10-23 | Not registered. Enforcement does not cover direct downloads yet; to be done before the 2027 rollout, ideally around the first public release |
