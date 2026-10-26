// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

plugins {
    id("ethertether.android.application")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.roborazzi)
}

// The one place for the version (CONTRIBUTING.md, "Versions"): a release tag vX.Y.Z must match it.
val appVersion = "1.0.0"

/** versionCode = major * 10000 + minor * 100 + patch, so it grows with every release. */
fun versionCodeOf(version: String): Int {
    val parts = version.split(".").map(String::toInt)
    require(parts.size == 3 && parts[1] < 100 && parts[2] < 100) { "version must be X.Y.Z with Y and Z below 100: $version" }
    return parts[0] * 10_000 + parts[1] * 100 + parts[2]
}

// The release key exists only in the release workflow (GitHub secrets, docs/RELEASING.md). Every
// other build of the release type stays unsigned; nothing here falls back to another key.
val releaseKeystore: String? = providers.environmentVariable("RELEASE_KEYSTORE_FILE").orNull

android {
    namespace = "io.github.oytuncagil.ethertether"

    defaultConfig {
        // Never change this: Android treats another application ID as another app.
        applicationId = "io.github.oytuncagil.ethertether"
        versionCode = versionCodeOf(appVersion)
        versionName = appVersion
        // The published release certificate, for the About screen (empty before the first release).
        val releaseCert = providers.gradleProperty("ethertether.releaseCertSha256").getOrElse("")
        buildConfigField("String", "RELEASE_CERT_SHA256", "\"$releaseCert\"")
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("RELEASE_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").get()
                // minSdk 30: the v1 (JAR) scheme is not needed; v2 and v3 (key rotation ready).
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
    }

    // Store images (StoreGraphicsTest) are made only with scripts/store_graphics.sh.
    testOptions.unitTests.all {
        it.systemProperty("ethertether.storeGraphics", providers.gradleProperty("storeGraphics").getOrElse("false"))
        it.systemProperty("ethertether.fastlaneDir", rootProject.file("fastlane/metadata/android").absolutePath)
    }

    // The in-app language choice needs every language in the base APK, also in a future bundle.
    bundle {
        language {
            enableSplit = false
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(project(":engine"))
    implementation(project(":backend"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    // Newer than the one Glance brings: its keep rules work with R8 full mode (gradle/libs.versions.toml).
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries.compose.m3)

    // Test only, never in the APK: accessibility checks and screenshot tests (Apache-2.0).
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.roborazzi.accessibility.check)
}

// The in-app list of open-source libraries is generated at build time, without network access.
// Strict mode fails the build when a shipped library has a license not compatible with both
// Apache-2.0 and GPL-3.0-or-later.
aboutLibraries {
    offlineMode = true
    license {
        strictMode = com.mikepenz.aboutlibraries.plugin.StrictMode.FAIL
        allowedLicenses.addAll("Apache-2.0", "MIT", "BSD-3-Clause", "BSD-2-Clause")
    }
}

// Screenshot tests (ScreenshotTest): the reference images live with the tests and every test run
// compares against them (roborazzi.test.verify in gradle.properties). After an intended change,
// run ./gradlew recordRoborazziDebug and review the new images in the pull request.
roborazzi {
    outputDir.set(file("src/test/screenshots"))
}
