// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

// The tethering engine: the engine logic over a TetheringPort, the reflection layer over the
// hidden tethering service, and the Shizuku user service that hosts it.
plugins {
    id("ethertether.android.library")
}

android {
    namespace = "io.github.oytuncagil.ethertether.engine"
    buildFeatures {
        aidl = true
    }
}

dependencies {
    api(project(":model"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}
