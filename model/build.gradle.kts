// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

// Pure Kotlin types shared by every module: addresses, backend states, tether requests and
// profiles with their JSON format. No Android dependency.
plugins {
    id("ethertether.jvm.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
