// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Fails the build when the merged manifest (app plus every library) requests a forbidden
 * permission. Without INTERNET the app cannot send data anywhere.
 */
@CacheableTask
abstract class VerifyPermissionsTask : DefaultTask() {
    /** The merged manifest of one variant. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    /** Fully qualified permission names that must not appear. */
    @get:Input
    abstract val forbidden: SetProperty<String>

    /** Written when the check passes, so the task is up to date until the manifest changes. */
    @get:OutputFile
    abstract val report: RegularFileProperty

    /** Reads every uses-permission name and compares it with [forbidden]. */
    @TaskAction
    fun verify() {
        val text = mergedManifest.get().asFile.readText()
        val requested = PERMISSION.findAll(text).map { it.groupValues[1] }.toSortedSet()
        val bad = requested.intersect(forbidden.get())
        if (bad.isNotEmpty()) {
            throw GradleException("Forbidden permissions in the merged manifest: $bad")
        }
        report.get().asFile.writeText(requested.joinToString("\n", postfix = "\n"))
    }

    private companion object {
        val PERMISSION = Regex("""<uses-permission(?:-sdk-23)?\s[^>]*android:name="([^"]+)"""")
    }
}
