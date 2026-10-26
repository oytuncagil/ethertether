<!--
SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later
-->

# Contributing to EtherTether

Thank you for helping. Bug reports, phone and adapter test results, translations and code are all welcome.

## Licensing of contributions

EtherTether is offered under `Apache-2.0 OR GPL-3.0-or-later`, at the user's choice. Every contribution is accepted under exactly these two licenses, with no extra terms. That is the only way the project can keep offering both.

- Do not paste code from sources under GPL-2.0-only, EPL, CC BY-SA, proprietary or unknown licenses. Answers from Q&A sites are usually CC BY-SA and cannot be used.
- Code under Apache-2.0, MIT, BSD or ISC may be included only with its original notice, a matching SPDX header and an entry in `REUSE.toml` when needed.
- Every new library shipped in the APK must be compatible with both Apache-2.0 and GPL-3.0-or-later. Say which license it has in your pull request.

## Developer Certificate of Origin (DCO)

Every commit must be signed off. By signing off you certify the [Developer Certificate of Origin 1.1](https://developercertificate.org/): in short, that you wrote the change or have the right to submit it under the project's licenses.

Sign off with:

```bash
git commit -s
```

This adds a line like `Signed-off-by: Your Name <you@example.com>`, which must match the commit author. You may use your GitHub noreply address. A CI check rejects pull requests with unsigned commits. To fix a branch, run `git rebase --signoff main` and force-push the branch.

## Every file has a license header

New files start with the two SPDX lines in the comment style of the file type, for example in Kotlin:

```kotlin
// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later
```

Add your own `SPDX-FileCopyrightText` line when you make a substantial change. Files that cannot hold a comment are listed in `REUSE.toml`. CI runs `reuse lint`.

## Building

Requirements: JDK 21 and the Android SDK with platform `android-37.0` and build tools 37.0.0. The Gradle wrapper downloads Gradle 9.8.1 and checks its checksum.

```bash
./gradlew assembleDebug
```

The APK is in `app/build/outputs/apk/debug/`. On Windows use `gradlew.bat`.

## Checks before you push

CI runs the same commands, and a pull request is merged only when all of them pass:

```bash
python3 scripts/check_repo.py
./gradlew testDebugUnitTest :model:test lint assembleDebug assembleRelease verifyDebugPermissions verifyReleasePermissions verifyReleaseKeepRules
```

- Lint runs with warnings as errors, and so does the Kotlin compiler. Exceptions live in `lint.xml`, each with a reason.
- The build fails if the app requests the INTERNET permission or other forbidden permissions (privacy is a design rule).
- The release build fails if R8 removed a class that is only reached by name at run time, such as the engine's user service (`verifyReleaseKeepRules`).
- Never weaken a test to make it pass. Change a test only when the specification changed, and say why in the pull request.
- Screens are covered by Compose UI tests, accessibility checks (Google's Accessibility Test Framework; warnings fail) and screenshot tests. The test run compares every screenshot with its reference image in `app/src/test/screenshots`. After an intended visual change, run `./gradlew recordRoborazziDebug` and include the new images, so reviewers see the change.

## Versions and releases

The version is set in one place, `appVersion` in `app/build.gradle.kts`, as a semantic version `X.Y.Z`. The `versionCode` follows from it (`X * 10000 + Y * 100 + Z`), so it grows with every release. Releases are made only by the maintainer from a tag `vX.Y.Z`, built and signed in GitHub Actions; see [docs/RELEASING.md](docs/RELEASING.md). Contributors do not change the version.

## Code style

- Kotlin official style (`kotlin.code.style=official`), 4 spaces, LF line endings, UTF-8. `.editorconfig` sets this up for most editors.
- Small files and small functions. KDoc on every public API.
- All user-visible text lives in string resources, never in code.
- Every visual element has a TalkBack label or is marked decorative.
- No em or en dashes in any file; use a comma, colon or plain hyphen (CI checks this).
- Hidden Android APIs are used only in the `engine` module, through reflection that resolves methods by parameter type, and each one is listed in `docs/ARCHITECTURE.md` with its source.

## Translations

English is the source language (`app/src/main/res/values/strings*.xml`); Turkish (`values-tr`) and German (`values-de`) are complete. A test checks that every language has every string, the same placeholders and no em or en dash, and lint reports missing translations. Until a translation platform is set up, translations come as pull requests: keep the button names the texts refer to identical to the translated buttons, and use `<plurals>` for counts.

## Pull requests

- One topic per pull request, with a short description of what and why.
- Link the issue it fixes.
- Include tests for new logic.
- For behaviour that only a real phone can show, describe the phone, Android version, adapter and Shizuku version you tested with.

## Reporting bugs

Use the bug report template. Please attach an exported log from the app (Log screen, Export) and remove anything private from it first.
