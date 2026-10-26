// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.engine.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Shizuku creates the user service by class name, with a public constructor taking a Context
 * (servers from v13) or a public no-argument one (older servers). The engine's consumer-rules.pro
 * keeps exactly this name and both constructors, and the release build's verifyReleaseKeepRules
 * task proves that R8 kept them.
 */
class UserServiceEntryTest {
    @Test
    fun theUserServiceHasTheNameAndConstructorTheKeepRuleNames() {
        val type = Class.forName("io.github.oytuncagil.ethertether.engine.service.EngineUserService", false, javaClass.classLoader)
        assertEquals(EngineUserService::class.java, type)
        assertTrue(Modifier.isPublic(type.modifiers))
        assertTrue(Modifier.isPublic(type.getConstructor().modifiers))
        assertTrue(Modifier.isPublic(type.getConstructor(android.content.Context::class.java).modifiers))
        assertTrue(IEtherTetherService.Stub::class.java.isAssignableFrom(type))
    }
}
