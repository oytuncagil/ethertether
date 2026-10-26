#!/usr/bin/env sh
# SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
# SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later
#
# Draws the store images in fastlane/metadata/android (phone screenshots of the real screens with
# demo data, feature graphic, icon) for every store language. Review the images, then commit them.
# Usage: scripts/store_graphics.sh
set -eu

./gradlew :app:testDebugUnitTest --tests '*StoreGraphics*' \
  -PstoreGraphics=true -Proborazzi.test.verify=false -Proborazzi.test.record=true
