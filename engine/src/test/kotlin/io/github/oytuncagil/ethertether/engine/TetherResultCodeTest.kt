// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TetherResultCodeTest {
    @Test
    fun codesAreContiguousFromZeroToEighteen() {
        assertEquals((0..18).toList(), TetherResultCode.entries.map { it.code })
    }

    @Test
    fun knownValuesMatchTheAndroidSource() {
        assertEquals(TetherResultCode.NO_ERROR, TetherResultCode.fromCode(0))
        assertEquals(TetherResultCode.UNAVAIL_IFACE, TetherResultCode.fromCode(4))
        assertEquals(TetherResultCode.DHCPSERVER_ERROR, TetherResultCode.fromCode(12))
        assertEquals(TetherResultCode.NO_CHANGE_TETHERING_PERMISSION, TetherResultCode.fromCode(14))
        assertEquals(TetherResultCode.DUPLICATE_REQUEST, TetherResultCode.fromCode(18))
    }

    @Test
    fun internalAndUnknownCodesAreNotGuessed() {
        listOf(-1, 19, 20, 999).forEach { assertNull(TetherResultCode.fromCode(it)) }
    }

    @Test
    fun onlyTheAndroid16CodesNeedApi36() {
        val newer = TetherResultCode.entries.filter { it.sinceSdk > 30 }
        assertEquals(listOf(TetherResultCode.UNKNOWN_REQUEST, TetherResultCode.DUPLICATE_REQUEST), newer)
        assertTrue(newer.all { it.sinceSdk == 36 })
    }

    @Test
    fun staticPrefixRangeIsTheDhcpServerRange() {
        assertEquals(16, TetheringConstants.MIN_STATIC_PREFIX)
        assertEquals(30, TetheringConstants.MAX_STATIC_PREFIX)
        assertEquals(5, TetheringConstants.TETHERING_ETHERNET)
        assertEquals(2, TetheringConstants.CONNECTIVITY_SCOPE_LOCAL)
    }
}
