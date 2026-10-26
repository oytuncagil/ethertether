// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileJsonTest {
    private var ids = 0
    private val newId = { "new-${ids++}" }
    private val names = { profile: Profile -> profile.name.ifBlank { profile.preset!!.name.lowercase() } }

    private fun import(text: String) = ProfileJson.import(text, newId)

    @Test
    fun exportThenImportKeepsEveryProfile() {
        val text = ProfileJson.export(Presets.all, names)
        val read = import(text) as ImportResult.Read
        assertEquals(Presets.all.map { it.input }, read.profiles.map { it.input })
        assertEquals(listOf("car_link_local", "laptop_internet", "isolated_lab"), read.profiles.map { it.name })
        assertTrue("imports are custom profiles with new ids", read.profiles.all { it.preset == null && it.id.startsWith("new-") })
        assertTrue(read.skipped.isEmpty())
    }

    @Test
    fun exportContainsNoIds() {
        val text = ProfileJson.export(Presets.all, names)
        assertFalse(text.contains("preset-car"))
        assertFalse(text.contains("\"id\""))
        assertTrue(text.contains("\"format\": \"ethertether-profiles\""))
    }

    @Test
    fun storeThenRestoreKeepsIdsPresetsAndSelection() {
        val list = ProfileList.initial.select("preset-lab")
        assertEquals(list, ProfileJson.restore(ProfileJson.store(list)))
    }

    @Test
    fun restoreRepairsAnUnknownSelection() {
        val text = ProfileJson.store(ProfileList.initial.copy(selectedId = "gone"))
        assertEquals("preset-car", ProfileJson.restore(text)?.selectedId)
    }

    @Test
    fun invalidProfilesAreSkippedWithTheirReason() {
        val text = """
            {"format":"ethertether-profiles","version":1,"profiles":[
              {"name":"Good","mode":"STATIC","phoneAddress":"10.1.0.1","clientAddress":"10.1.0.2","prefixLength":"24","scope":"LOCAL_ONLY"},
              {"name":"","mode":"DHCP","scope":"LOCAL_ONLY"},
              {"name":"Bad mode","mode":"BRIDGE","scope":"LOCAL_ONLY"},
              {"name":"Bad prefix","mode":"STATIC","phoneAddress":"10.1.0.1","clientAddress":"10.1.0.2","prefixLength":"8","scope":"LOCAL_ONLY"},
              {"name":"Too long","mode":"STATIC","phoneAddress":"${"1".repeat(40)}","scope":"LOCAL_ONLY"}
            ]}
        """.trimIndent()
        val read = import(text) as ImportResult.Read
        assertEquals(listOf("Good"), read.profiles.map { it.name })
        assertEquals(mapOf(ImportProblem.NAME_MISSING to 1, ImportProblem.FIELD_INVALID to 2, ImportProblem.REQUEST_INVALID to 1), read.skipped)
    }

    @Test
    fun foreignAndBrokenFilesAreRefused() {
        assertEquals(ImportResult.NotAProfileFile, import("not json"))
        assertEquals(ImportResult.NotAProfileFile, import("""{"format":"other","version":1,"profiles":[]}"""))
        assertEquals(ImportResult.NotAProfileFile, import("""{"format":"ethertether-profiles"}"""))
        assertEquals(ImportResult.NotAProfileFile, import(" ".repeat(ProfileLimits.MAX_IMPORT_CHARS + 1)))
        assertNull(ProfileJson.restore("{}"))
    }

    @Test
    fun newerVersionsAreRefused() {
        assertEquals(ImportResult.NewerVersion, import("""{"format":"ethertether-profiles","version":2,"profiles":[]}"""))
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val text = """{"format":"ethertether-profiles","version":1,"extra":true,"profiles":[{"name":"Dhcp","mode":"DHCP","scope":"SHARE_INTERNET","color":"red"}]}"""
        assertEquals(1, (import(text) as ImportResult.Read).profiles.size)
    }

    @Test
    fun tooManyProfilesAreCut() {
        val entry = """{"name":"P","mode":"DHCP","scope":"LOCAL_ONLY"}"""
        val text = """{"format":"ethertether-profiles","version":1,"profiles":[${List(ProfileLimits.MAX_PROFILES + 3) { entry }.joinToString(",")}]}"""
        val read = import(text) as ImportResult.Read
        assertEquals(ProfileLimits.MAX_PROFILES, read.profiles.size)
        assertEquals(mapOf(ImportProblem.TOO_MANY to 3), read.skipped)
    }
}
