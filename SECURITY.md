<!--
SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later
-->

# Security policy

EtherTether talks to Android's tethering service with the shell identity it gets through Shizuku, so security reports matter a lot to this project.

## Reporting a vulnerability

Please do not open a public issue for a security problem.

Use GitHub's private vulnerability reporting instead: open the **Security** tab of this repository and choose **Report a vulnerability**. Only the maintainer can read the report.

Please include:

- what an attacker can do, and what they need first (an installed app, physical access, a network position, root);
- the EtherTether version, Android version and phone model;
- the steps to reproduce, or a proof of concept;
- whether the problem is already public.

You will get an answer within 14 days. Fixed problems are credited in the release notes unless you ask not to be named.

## Scope

In scope: the app, including the engine that runs in Shizuku's process, the release workflow and the signing of published APKs.

Out of scope: problems in Android itself or in Shizuku (please report those upstream), and attacks that need a device that is already fully compromised.

## Supported versions

Only the newest release receives security fixes.

## Verifying downloads

The SHA-256 fingerprint of the signing certificate is in README.md and in every release's notes. An APK signed with any other certificate is not an official EtherTether build.
