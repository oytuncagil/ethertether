// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.engine.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallerCheckTest {
    private val shell = 2000
    private val app = 10_234

    @Test
    fun theAppAndShizukuMayCall() {
        assertTrue(CallerCheck.allowed(app, shell, app))
        assertTrue(CallerCheck.allowed(shell, shell, app))
        // The app in a work profile or second user (user 10).
        assertTrue(CallerCheck.allowed(1_010_234, shell, app))
    }

    @Test
    fun anotherAppMayNotCall() {
        assertFalse(CallerCheck.allowed(10_235, shell, app))
        assertFalse(CallerCheck.allowed(1_010_235, shell, app))
        assertFalse(CallerCheck.allowed(0, shell, app))
    }

    @Test
    fun anOldServerWithoutAContextAllowsEveryCallerAsBefore() {
        assertTrue(CallerCheck.allowed(10_235, shell, null))
    }
}
