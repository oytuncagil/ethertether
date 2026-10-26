// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SystemInterfaceReaderTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun sysfsValuesAreReadAndTrimmed() {
        val eth0 = File(folder.root, "eth0").apply { mkdirs() }
        File(eth0, "carrier").writeText("1\n")
        File(eth0, "speed").writeText("1000\n")
        val reader = SystemInterfaceReader(folder.root)
        assertEquals("1", reader.readSysfs("eth0", "carrier"))
        assertEquals("1000", reader.readSysfs("eth0", "speed"))
    }

    @Test
    fun missingSysfsValuesAreNull() {
        assertNull(SystemInterfaceReader(folder.root).readSysfs("eth9", "carrier"))
    }

    @Test
    fun realInterfacesAreListedWithoutThrowing() {
        // The build machine normally has no ethN interface under these patterns; the call must still work.
        val list = SystemInterfaceReader(folder.root).ethernetInterfaces(36)
        assertTrue(list.all { EthernetNames.matches(it.name, 36) })
    }
}
