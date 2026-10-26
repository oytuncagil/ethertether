# SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
# SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later
#
# R8 rules for the release build.
#
# Reflection: the engine looks up hidden framework classes (android.net.ITetheringConnector,
# TetheringManager$TetheringRequest$Builder, IIntResultListener) by name at run time. Those
# classes are in the system image, never in the APK, so R8 cannot rename or remove them.
# The rules that protect our own reflective entry points live next to the code in each
# module's consumer-rules.pro (engine, backend).
#
# Shrinking and optimizing stay on, but names are not obfuscated: stack traces in exported logs
# stay readable, and F-Droid and IzzyOnDroid recommend it for reproducible builds.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
