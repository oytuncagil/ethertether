<!--
SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later
-->

# Triage guide

How new issues and pull requests are handled. The labels are defined in `.github/labels.json` and created by the Labels workflow.

## New issue

1. **Security?** A report about a vulnerability belongs in a private advisory (SECURITY.md). If one is posted publicly, thank the reporter, hide the details if needed and move it to a private advisory.
2. **Private data?** Logs and screenshots can show serial numbers, MAC addresses, network names or a car's VIN. Edit them out (or ask the reporter to) before anything else.
3. **Kind**: the templates set `bug` or `enhancement`. Change it when needed: `question`, `compatibility` (a phone, Android version or adapter that behaves differently), `translation`, `documentation`.
4. **Enough to act on?** A bug needs the phone, Android version, adapter, EtherTether version, the steps and an exported log (Log, Export). If something is missing, ask once and add `needs info`. Close after 30 days without an answer, with a note that it can be reopened.
5. **Decide**:
   - Reproduced or clear from the log: `confirmed`.
   - Android or the phone maker does not allow it: `platform limit`, with the source (Android code or document) in a comment and in docs/COMPATIBILITY.md if it is about a device.
   - Against the scope in docs/DESIGN.md (routing, VPN, DNS, Wi-Fi or USB tethering control, carrier bypass, network access by the app): `out of scope`, with one friendly sentence why, then close.
   - Already reported: `duplicate`, link the original, close.
6. Small and well described: add `good first issue` or `help wanted`.

## Reading a log

The exported log has one line per event with time, level and source. Useful sources: `tether` (start, stop, watchdog, re-arm, with Android's result), `adapter` (USB plug events and status reads), `service` and `boot` (the notification service and start after restart), `shizuku` (access and binder state), `tile`, `profiles`, and `strictmode` (debug builds only). The raw results of every engine step are on the debug screen (Settings, developer switch). The cases the app handles are in docs/ROBUSTNESS.md.

## Compatibility reports

A report that a phone or adapter works or fails is valuable even without a bug. Add it to docs/COMPATIBILITY.md as "verified by a public report" with a link to the issue, never as "supported".

## Pull requests

1. CI must be green: tests, lint, permission and keep-rule checks, REUSE and the DCO check (every commit `Signed-off-by`). Without a sign-off the change cannot be accepted, because the dual license needs it (CONTRIBUTING.md).
2. No code under other licenses, no new INTERNET permission, no trackers or proprietary libraries.
3. User-visible text in string resources; for translations, button names must match the translated buttons.
4. Changes to `.github/` or `scripts/` get a careful read: CI runs the pull request's own copy of the workflows and checks, so such a change could switch a check off.
5. Merge with squash; the title ends with the pull request number.

## Dependabot

Dependabot's update pull requests (weekly and security) sign off as `dependabot[bot] <support@github.com>`, which is not their author address. The sign-off check therefore exempts commits with Dependabot's author address on pull requests Dependabot opened, and nothing else (`scripts/check_dco.sh`). Only people with write access can push to Dependabot's branches. Before merging one with squash:

1. CI is green.
2. The library's release notes show nothing that changes the app's behaviour, and its license is still compatible with both Apache-2.0 and GPL-3.0-or-later.
3. Updates of libraries shipped in the APK get a line under `[Unreleased]` in CHANGELOG.md; build and test tools do not.

Several updates can also be taken over together in one pull request, with the changes made in your own signed-off commits. Cherry-picked Dependabot commits keep Dependabot as their author and fail the check. Dependabot closes its own pull requests once main has the new versions.
