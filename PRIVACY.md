<!--
SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later
-->

# Privacy policy

Last updated: 2026-10-08

EtherTether collects no data. This is enforced by how the app is built, not only promised.

## What the app does not do

- It has no INTERNET permission, so it cannot send anything over the network. The build fails if any library tries to add that permission.
- It contains no analytics, no crash reporting service, no advertising and no trackers.
- It has no accounts and no sign-in.
- It does not back up its data to any cloud service.

## What stays on your phone

- Your settings and tethering profiles (addresses and options you enter).
- A log of what the app did, so you can understand problems. The log can contain network interface names and IP addresses of the tethered link. It keeps at most the last 1000 entries, and **Clear** on the log screen deletes them.

This data never leaves the phone unless you choose to share it: the log leaves only when you tap **Export** and pick where to send it. Please check an exported log before you post it publicly.

## Notifications and automation

EtherTether shows a notification while tethering runs or waits for the adapter, and, if you allow it, an alert when tethering stops outside the app. "Start after restart" and the widget only start EtherTether on your phone; nothing is sent anywhere.

## Privileged access

EtherTether asks Android to start or stop Ethernet tethering through Shizuku, which you set up and grant yourself. It uses that access only for the tethering request and for reading the state of the tethering link. It does not change routes, DNS, Wi-Fi, mobile data or firewall rules.

## Contact

Questions about privacy can be asked in a GitHub issue of this repository.
