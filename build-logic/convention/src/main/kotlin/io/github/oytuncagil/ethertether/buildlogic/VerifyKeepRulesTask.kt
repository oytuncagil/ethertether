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
 * Fails a minified build when R8 did not keep a class or member that is only reached by name at
 * run time (for example the engine's user service, which Shizuku creates by class name). It reads
 * seeds.txt, R8's own list of what the keep rules matched, written next to the mapping file. A
 * removed entry would build fine and only fail on a phone, so this check proves the rules instead.
 */
@CacheableTask
abstract class VerifyKeepRulesTask : DefaultTask() {
    /** The R8 mapping file of one minified variant; seeds.txt sits in the same folder. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mappingFile: RegularFileProperty

    /** Lines that must appear in seeds.txt, in R8's format ("pkg.Class" or "pkg.Class: member"). */
    @get:Input
    abstract val required: SetProperty<String>

    /** Written when the check passes. */
    @get:OutputFile
    abstract val report: RegularFileProperty

    /** Compares seeds.txt with [required]. */
    @TaskAction
    fun verify() {
        val seeds = mappingFile.get().asFile.resolveSibling("seeds.txt")
        if (!seeds.isFile) throw GradleException("R8 wrote no seeds.txt next to ${mappingFile.get().asFile}")
        val kept = seeds.readLines().map(String::trim).toSet()
        val missing = required.get().filterNot(kept::contains)
        if (missing.isNotEmpty()) {
            throw GradleException("R8 did not keep entries that are reached by name at run time: $missing")
        }
        report.get().asFile.writeText(required.get().sorted().joinToString("\n", postfix = "\n"))
    }
}
