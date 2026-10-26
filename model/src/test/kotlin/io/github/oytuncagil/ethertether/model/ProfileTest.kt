// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileTest {
    private fun custom(id: String, name: String = "Custom $id") =
        Profile(id, name, null, TetherInput(TetherMode.STATIC, "10.0.0.1", "10.0.0.2", "24"))

    @Test
    fun everyPresetIsValid() {
        assertEquals(3, Presets.all.size)
        Presets.all.forEach { assertTrue(it.preset.toString(), it.validate() is ValidationResult.Valid) }
        assertEquals(Preset.entries.toList(), Presets.all.map { it.preset })
    }

    @Test
    fun carPresetIsLinkLocalAndLocalOnly() {
        val car = Presets.all.first { it.preset == Preset.CAR_LINK_LOCAL }
        val request = (car.validate() as ValidationResult.Valid).request
        assertTrue(request.addresses!!.phone.isLinkLocal)
        assertEquals(16, request.addresses.prefixLength)
        assertEquals(SharingScope.LOCAL_ONLY, request.scope)
    }

    @Test
    fun initialListSelectsTheFirstPreset() {
        assertEquals("preset-car", ProfileList.initial.selected?.id)
    }

    @Test
    fun addSelectUpdateDuplicateDelete() {
        var list = ProfileList.initial.add(custom("a"))
        list = list.select("a")
        assertEquals("a", list.selected?.id)
        list = list.update(custom("a", "Renamed"))
        assertEquals("Renamed", list.find("a")?.name)
        list = list.duplicate("preset-car", "b", "  Car copy\n")
        val copy = list.find("b")!!
        assertEquals("Car copy", copy.name)
        assertNull("a duplicate is a custom profile", copy.preset)
        assertEquals(list.profiles.indexOfFirst { it.id == "preset-car" } + 1, list.profiles.indexOf(copy))
        list = list.delete("a")
        assertEquals("preset-car", list.selected?.id)
    }

    @Test
    fun selectingAnUnknownIdChangesNothing() {
        assertSame(ProfileList.initial, ProfileList.initial.select("missing"))
    }

    @Test
    fun deletingEverythingLeavesNoSelection() {
        var list = ProfileList.initial
        Presets.all.forEach { list = list.delete(it.id) }
        assertNull(list.selected)
        assertNull(list.selectedId)
    }

    @Test
    fun listIsCapped() {
        var list = ProfileList(emptyList(), null)
        repeat(ProfileLimits.MAX_PROFILES + 5) { list = list.add(custom("p$it")) }
        assertEquals(ProfileLimits.MAX_PROFILES, list.profiles.size)
        assertEquals(list, list.duplicate("p0", "x", "x"))
        assertEquals(ProfileLimits.MAX_PROFILES, list.addAll(listOf(custom("y"))).profiles.size)
    }

    @Test
    fun namesAreCleaned() {
        assertEquals("Lab", ProfileLimits.cleanName("  La\u0000b \t"))
        assertEquals(ProfileLimits.MAX_NAME, ProfileLimits.cleanName("x".repeat(500)).length)
        assertFalse(ProfileLimits.cleanName("a\u001Bb").contains('\u001B'))
    }

    @Test
    fun cleanNameDropsBidirectionalControlsButKeepsEmojiJoiners() {
        assertEquals("Car usb0", ProfileLimits.cleanName("Car \u202Eusb0\u202C"))
        assertEquals("ab", ProfileLimits.cleanName("\u2066a\u2069\u200Eb\u200F\u061C"))
        val family = "\uD83D\uDC69\u200D\uD83D\uDC67"
        assertEquals(family, ProfileLimits.cleanName(family))
    }
}
