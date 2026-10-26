// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.engine.reflect

import java.lang.reflect.Modifier

/** Readable dump of an object's public instance fields, for raw results on the debug screen. */
object FieldDump {
    /** "TetheringRequestParcel{tetheringType=5, localIPv4Address=...}" style text. */
    fun of(target: Any): String {
        val fields = target.javaClass.fields.filter { !Modifier.isStatic(it.modifiers) }
        if (fields.isEmpty()) return target.toString()
        return fields.joinToString(prefix = "${target.javaClass.simpleName}{", postfix = "}") { field ->
            "${field.name}=${render(field.get(target))}"
        }
    }

    private fun render(value: Any?): String = when (value) {
        is Array<*> -> value.joinToString(prefix = "[", postfix = "]")
        is IntArray -> value.joinToString(prefix = "[", postfix = "]")
        else -> value.toString()
    }
}
