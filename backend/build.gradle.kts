// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

// Privileged backend: Shizuku detection and the Shizuku connection that carries the engine.
plugins {
    id("ethertether.android.library")
}

android {
    namespace = "io.github.oytuncagil.ethertether.backend"
}

dependencies {
    api(project(":model"))
    api(project(":engine"))
    implementation(libs.shizuku.api)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}
