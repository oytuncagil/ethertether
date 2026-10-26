// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.buildlogic

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.register

/** Permissions EtherTether must never request. */
private val FORBIDDEN_PERMISSIONS = setOf(
    "android.permission.INTERNET",
    "com.google.android.gms.permission.AD_ID",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.READ_CONTACTS",
    "android.permission.READ_PHONE_STATE",
)

/**
 * Classes and members reached only by name at run time, which R8 must keep (see the engine's
 * consumer-rules.pro). Shizuku creates the user service by class name with its no-argument
 * constructor, and the app and the user service talk through the AIDL stub.
 *
 * Libraries create some classes by name too. Room creates WorkManager's database and WorkManager
 * its input merger and Glance's workers with these constructors; the first release build crashed
 * at start when an old Room keep rule let R8 drop the database constructor (2026-10-09).
 */
private val REQUIRED_KEEPS = setOf(
    "io.github.oytuncagil.ethertether.engine.service.EngineUserService",
    "io.github.oytuncagil.ethertether.engine.service.EngineUserService: EngineUserService()",
    "io.github.oytuncagil.ethertether.engine.service.EngineUserService: EngineUserService(android.content.Context)",
    "io.github.oytuncagil.ethertether.engine.service.EngineUserService: boolean onTransact(int,android.os.Parcel,android.os.Parcel,int)",
    "io.github.oytuncagil.ethertether.engine.service.IEtherTetherService\$Stub",
    "io.github.oytuncagil.ethertether.engine.service.IEtherTetherService\$Stub: boolean onTransact(int,android.os.Parcel,android.os.Parcel,int)",
    "androidx.work.impl.WorkDatabase_Impl: WorkDatabase_Impl()",
    "androidx.work.OverwritingInputMerger: OverwritingInputMerger()",
    "androidx.glance.session.SessionWorker: SessionWorker(android.content.Context,androidx.work.WorkerParameters)",
)

/** The application module: SDK levels, Compose, lint policy and Robolectric Compose UI tests. */
class AndroidApplicationPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        extensions.configure<ApplicationExtension> {
            configureAndroidCommon(this)
            defaultConfig {
                minSdk = ProjectConfig.MIN_SDK
                targetSdk = ProjectConfig.TARGET_SDK
            }
            buildFeatures {
                compose = true
                buildConfig = true
            }
            // The encrypted dependency block is unreadable for F-Droid and IzzyOnDroid scanners.
            dependenciesInfo {
                includeInApk = false
                includeInBundle = false
            }
        }
        configureKotlinJvmTarget()
        addComposeDependencies()
        registerPermissionCheck()
        registerKeepRulesCheck()
    }

    /** One verify<Variant>KeepRules task per minified variant, wired into check. */
    private fun Project.registerKeepRulesCheck() {
        extensions.configure<ApplicationAndroidComponentsExtension> {
            onVariants(selector().all()) { variant ->
                if (!variant.isMinifyEnabled) return@onVariants
                val name = "verify${variant.name.replaceFirstChar { it.uppercase() }}KeepRules"
                val task = tasks.register<VerifyKeepRulesTask>(name) {
                    group = "verification"
                    description = "Fails if R8 removed a class of the ${variant.name} build that is reached by name."
                    mappingFile.set(variant.artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE))
                    required.set(REQUIRED_KEEPS)
                    report.set(layout.buildDirectory.file("reports/keep-rules/${variant.name}.txt"))
                }
                tasks.named("check") { dependsOn(task) }
            }
        }
    }

    /** One verify<Variant>Permissions task per variant, wired into check. */
    private fun Project.registerPermissionCheck() {
        extensions.configure<ApplicationAndroidComponentsExtension> {
            onVariants(selector().all()) { variant ->
                val name = "verify${variant.name.replaceFirstChar { it.uppercase() }}Permissions"
                val task = tasks.register<VerifyPermissionsTask>(name) {
                    group = "verification"
                    description = "Fails if the ${variant.name} merged manifest asks for a forbidden permission."
                    mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
                    forbidden.set(FORBIDDEN_PERMISSIONS)
                    report.set(layout.buildDirectory.file("reports/permissions/${variant.name}.txt"))
                }
                tasks.named("check") { dependsOn(task) }
            }
        }
    }

    private fun Project.addComposeDependencies() {
        dependencies {
            val bom = platform(libs.lib("compose-bom"))
            add("implementation", bom)
            add("implementation", libs.lib("compose-ui"))
            add("implementation", libs.lib("compose-ui-graphics"))
            add("implementation", libs.lib("compose-ui-tooling-preview"))
            add("implementation", libs.lib("compose-material3"))
            add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
            add("debugImplementation", libs.lib("compose-ui-tooling"))
            add("debugImplementation", libs.lib("compose-ui-test-manifest"))

            add("testImplementation", platform(libs.lib("compose-bom")))
            add("testImplementation", libs.lib("compose-ui-test-junit4"))
            add("testImplementation", libs.lib("robolectric"))
            add("testImplementation", libs.lib("androidx-test-core"))
            add("testImplementation", libs.lib("androidx-test-ext-junit"))
            add("testImplementation", libs.lib("junit"))
            add("testImplementation", libs.lib("kotlinx-coroutines-test"))
        }
    }
}
