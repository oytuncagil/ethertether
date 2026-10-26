<!--
SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later
-->

# Releasing

How official releases are signed, built and checked, and how anyone can verify them. Releases are built only by GitHub Actions on Linux, as one universal APK per version, from a tag `vX.Y.Z` equal to the version name.

## The release key

Android installs an update only when it is signed with the same key as the installed app. If the release key is lost, existing users can never get an update again: everyone would have to uninstall and install a new app under a new key, and Obtainium and the stores would see a different app. If someone else gets the key, they can make updates that every phone accepts as genuine.

So the key file and its password exist only on the maintainer's computer, in two offline backups and in the GitHub Actions secrets. They never go into the repository, a log, an issue or an e-mail.

### Creating it (done once, on 2026-10-09)

1. Generate a 32-character password (letters and digits) in a password manager and save it as "EtherTether release key". The keystore and the key use the same password.
2. In a folder outside the git checkout, create the key (RSA 4096, valid for 50 years, which is longer than Google Play's 2033 minimum):
   ```
   keytool -genkeypair -v -keystore ethertether-release.p12 -storetype PKCS12 -alias ethertether -keyalg RSA -keysize 4096 -validity 18250 -dname "CN=Oytun Cagil Koltuk, O=EtherTether"
   ```
   On Windows without a JDK on the path, Android Studio's copy works: `& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"`.
3. Read the fingerprint (the line `SHA256:`). It is public: it is in every APK and in the README.
   ```
   keytool -list -v -keystore ethertether-release.p12 -alias ethertether
   ```
4. Copy `ethertether-release.p12` to two USB sticks kept in different places, never with the computer, and attach it to the password manager entry if possible. Never upload it to a normal cloud drive or e-mail it.
5. Add four repository secrets (Settings, Secrets and variables, Actions):
   - `RELEASE_KEYSTORE_BASE64`: the file as base64. On Windows, without showing it:
     ```powershell
     [Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:USERPROFILE\EtherTether-key\ethertether-release.p12")) | Set-Clipboard
     ```
     Clear the clipboard afterwards (`Set-Clipboard -Value " "`).
   - `RELEASE_KEYSTORE_PASSWORD` and `RELEASE_KEY_PASSWORD`: the password.
   - `RELEASE_KEY_ALIAS`: `ethertether`.
6. Put the fingerprint into `gradle.properties` (`ethertether.releaseCertSha256`) and the README. From then on the release workflow refuses an APK signed with any other certificate, and the About screen of an official copy says "This application is signed with the official EtherTether release key."

Release certificate SHA-256: `75:37:BE:B7:0C:8E:EF:0E:39:88:1F:A9:BC:4A:1C:37:DE:FC:97:86:29:AC:DE:4E:A3:85:B8:24:31:1D:05:7B`.

## Versions

- `versionName` is a semantic version `X.Y.Z`, set in one place: `appVersion` in `app/build.gradle.kts`.
- `versionCode` is `X * 10000 + Y * 100 + Z` (1.0.0 is 10000, 1.2.3 is 10203). Minor and patch stay below 100, so the code always grows.
- The tag is `vX.Y.Z` and must match `appVersion`. A suffix such as `v1.1.0-rc.1` makes a pre-release of that version (Obtainium skips pre-releases by default).
- `CHANGELOG.md` gets a section `## [X.Y.Z] - YYYY-MM-DD`, and every store language a short `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` (at most 500 bytes).

## Making a release

1. On a branch: set `appVersion`, move the `[Unreleased]` notes in `CHANGELOG.md` to the new version, add the three Fastlane changelog files. Pull request, CI green, merge.
2. Start the release workflow, either
   - in the browser: Actions, **Release**, **Run workflow** on `main` with the version (for example `1.0.0`); the tag is created on that commit when the draft is published, or
   - with git:
     ```bash
     git tag -a v1.0.0 -m "EtherTether 1.0.0"
     git push origin v1.0.0
     ```
3. `.github/workflows/release.yml` then, on a fresh runner without the build cache:
   - checks that the tag matches `appVersion`;
   - runs the unit and UI tests, lint, the permission check and the keep-rule check;
   - builds the APK signed with the release key, with the v2 and v3 schemes (v1 is not needed, the app needs Android 11);
   - verifies both signatures with `apksigner` and that the certificate is the one in `gradle.properties`;
   - writes `EtherTether-X.Y.Z.apk`, `SHA256SUMS` and the notes from `CHANGELOG.md`;
   - creates a draft GitHub release with these files.
4. Check the draft and publish it. A published release is never replaced or deleted; a mistake gets a new version.

To try the process before merging, push the commit to a branch `release-test/<version>` with a pre-release version, for example `release-test/1.1.0-rc.1`; the workflow makes a draft pre-release from it.

Without the key secrets, the workflow refuses a normal version. A pre-release is then signed with a throwaway key made for that run, and its notes say so in bold. That APK is not for users.

### Checklist

Copy this into the release pull request.

- [ ] Everything since the last release is under `## [Unreleased]` in `CHANGELOG.md`, now moved to `## [X.Y.Z] - YYYY-MM-DD`.
- [ ] `appVersion` is `X.Y.Z`.
- [ ] `fastlane/metadata/android/{en-US,tr-TR,de-DE}/changelogs/<versionCode>.txt` exist, at most 500 bytes each.
- [ ] New strings are translated; the store screenshots are recorded again with `scripts/store_graphics.sh` if a screen they show changed.
- [ ] The device tests for the changed features pass ([DEVICE_TESTS.md](DEVICE_TESTS.md)), on a pre-release from `release-test/X.Y.Z-rc.1` if needed.
- [ ] The pull request is merged with CI green.
- [ ] The release workflow ran from `main`; the draft has `EtherTether-X.Y.Z.apk`, `SHA256SUMS` and notes naming the release fingerprint.
- [ ] `sha256sum -c SHA256SUMS` and `apksigner verify --print-certs` checked on the downloaded files.
- [ ] The draft is published.
- [ ] Obtainium shows the update on a phone with the previous version.

## Verifying a release

Anyone can check that an official APK is genuine and built from the published source.

1. Intact download: `sha256sum -c SHA256SUMS` in the folder with the downloaded files.
2. Signed by the project: `apksigner verify --print-certs EtherTether-X.Y.Z.apk` shows a `certificate SHA-256 digest:` equal to the fingerprint above (apksigner prints it in lower case without colons). On the phone, About says whether the installed copy carries the official key.
3. Built from the source (reproducible build), on Linux with JDK 21 and the Android SDK (platform `android-37.0`, build tools 37.0.0):
   ```bash
   git clone https://github.com/oytuncagil/ethertether.git
   cd ethertether
   git checkout vX.Y.Z
   ./gradlew assembleRelease --no-build-cache
   python3 scripts/compare_apks.py EtherTether-X.Y.Z.apk app/build/outputs/apk/release/app-release-unsigned.apk
   ```
   It prints `same contents` when every file in the official APK equals your build. The official APK is signed only with the v2 and v3 schemes, which sit outside the files, so the signature does not get in the way.

Tested on 2026-10-09: a test release built on GitHub's runner had exactly the same contents as a local build of the same commit (140 files); two clean builds were byte for byte identical; a build in another folder differed only in `META-INF/version-control-info.textproto`, because that folder was a git worktree, where the build cannot read the commit. Build from a normal `git clone`, not from a worktree or a source archive. The APK contains no paths of the build machine.

## Repository settings

These need a public repository (GitHub Free offers no branch protection or rulesets for private ones; until then CI and the squash-only setting carry the rules).

- Rules, Rulesets, new branch ruleset `main`: active, default branch, restrict deletions, block force pushes, require a pull request (0 approvals), require the status checks `repository`, `build` and `dco`, no bypass list.
- Pull requests: squash merging only; auto-merge off; head branches are not deleted automatically.
- Code security: private vulnerability reporting, Dependabot alerts and security updates, secret scanning with push protection.
- Actions: default workflow permissions "read repository contents" (the workflows ask for more only where they need it). After the first push of `.github/labels.json` to a public repository, run the **Labels** workflow once.
- Commit sign-off required for web commits.

### Checked before the first public release

On 2026-10-23 every file was checked: no keystores, key files, `local.properties`, secrets, personal data or large files, and only the GitHub noreply address in the commit.

## Artifact storage

A private repository has a small Actions storage allowance, counted over the billing month for the whole account. When it is used up, CI still passes but stores no APK. The **Clean up artifacts** workflow (Actions, run by hand) deletes stored artifacts; the allowance itself returns with the next billing cycle. Releases are not artifacts and are not affected. Public repositories have no such limit.
