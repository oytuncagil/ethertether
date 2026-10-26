// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.buildlogic

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.Lint
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/** SDK levels and bytecode target shared by every module. */
object ProjectConfig {
    const val COMPILE_SDK = 37
    const val TARGET_SDK = 36

    /** Static IPv4 tethering requests exist since Android 11 (API 30). */
    const val MIN_SDK = 30
    val JAVA_VERSION = JavaVersion.VERSION_17
    val JVM_TARGET = JvmTarget.JVM_17
}

/** The version catalog of the main build. */
val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** Looks up a library alias in the catalog, failing loudly when it is missing. */
fun VersionCatalog.lib(alias: String): Provider<MinimalExternalModuleDependency> = findLibrary(alias).get()

internal fun Project.configureKotlinJvmTarget() {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(ProjectConfig.JVM_TARGET)
        compilerOptions.allWarningsAsErrors.set(true)
    }
}

/** Lint policy for every Android module: warnings are errors; exceptions live in lint.xml. */
internal fun Project.configureLint(lint: Lint) {
    lint.abortOnError = true
    lint.warningsAsErrors = true
    lint.checkDependencies = true
    lint.lintConfig = rootProject.file("lint.xml")
}

internal fun Project.configureAndroidCommon(android: CommonExtension) {
    android.compileSdk = ProjectConfig.COMPILE_SDK
    android.compileOptions.sourceCompatibility = ProjectConfig.JAVA_VERSION
    android.compileOptions.targetCompatibility = ProjectConfig.JAVA_VERSION
    android.testOptions.unitTests.isIncludeAndroidResources = true
    configureLint(android.lint)
}
