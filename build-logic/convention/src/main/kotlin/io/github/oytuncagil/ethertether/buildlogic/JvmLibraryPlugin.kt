// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.buildlogic

import com.android.build.api.dsl.Lint
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Pure Kotlin module without Android: lint, JUnit 4 and coroutines-test for tests. */
class JvmLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        // Android lint also checks pure Kotlin modules, with the same policy.
        pluginManager.apply("com.android.lint")
        extensions.configure<Lint> { configureLint(this) }
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = ProjectConfig.JAVA_VERSION
            targetCompatibility = ProjectConfig.JAVA_VERSION
        }
        configureKotlinJvmTarget()
        dependencies {
            add("testImplementation", libs.lib("junit"))
            add("testImplementation", libs.lib("kotlinx-coroutines-test"))
        }
    }
}
