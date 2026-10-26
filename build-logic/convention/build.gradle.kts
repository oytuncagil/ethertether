// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

plugins {
    `kotlin-dsl`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("jvmLibrary") {
            id = "ethertether.jvm.library"
            implementationClass = "io.github.oytuncagil.ethertether.buildlogic.JvmLibraryPlugin"
        }
        register("androidLibrary") {
            id = "ethertether.android.library"
            implementationClass = "io.github.oytuncagil.ethertether.buildlogic.AndroidLibraryPlugin"
        }
        register("androidApplication") {
            id = "ethertether.android.application"
            implementationClass = "io.github.oytuncagil.ethertether.buildlogic.AndroidApplicationPlugin"
        }
    }
}
