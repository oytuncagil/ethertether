// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every translation has exactly the English strings (except translatable="false"), the same
 * format placeholders in every string and plural item, and no em or en dash. Lint checks missing
 * translations too; this also catches a placeholder dropped or doubled by a translator.
 */
class TranslationsTest {
    private val res = File("src/main/res")
    private val languages = listOf("tr", "de")

    private data class Entry(val name: String, val texts: List<String>)

    private fun read(folder: String): Map<String, Entry> {
        val files = File(res, folder).listFiles { file -> file.name.startsWith("strings") && file.extension == "xml" }.orEmpty()
        return files.flatMap { file ->
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val root = document.documentElement
            val strings = root.getElementsByTagName("string").elements()
                .filter { it.getAttribute("translatable") != "false" }
                .map { Entry(it.getAttribute("name"), listOf(it.textContent)) }
            val plurals = root.getElementsByTagName("plurals").elements()
                .map { plural -> Entry(plural.getAttribute("name"), plural.getElementsByTagName("item").elements().map { it.textContent }) }
            strings + plurals
        }.associateBy { it.name }
    }

    private fun org.w3c.dom.NodeList.elements(): List<Element> = (0 until length).map { item(it) as Element }

    private fun placeholders(text: String): List<String> = PLACEHOLDER.findAll(text).map { it.value }.sorted().toList()

    @Test
    fun everyLanguageHasEveryString() {
        val english = read("values")
        assertTrue(english.size > 300)
        languages.forEach { language ->
            val translated = read("values-$language")
            assertEquals("strings of $language", english.keys.sorted(), translated.keys.sorted())
        }
    }

    @Test
    fun placeholdersMatchTheEnglishText() {
        val english = read("values")
        languages.forEach { language ->
            read("values-$language").values.forEach { entry ->
                val expected = english.getValue(entry.name).texts.map(::placeholders).toSet()
                entry.texts.forEach { text ->
                    assertTrue("$language ${entry.name}: \"$text\" has ${placeholders(text)}, English has $expected", placeholders(text) in expected)
                }
            }
        }
    }

    @Test
    fun noDashesThatTheProjectAvoids() {
        (listOf("values") + languages.map { "values-$it" }).forEach { folder ->
            read(folder).values.forEach { entry ->
                entry.texts.forEach { text -> assertTrue("$folder ${entry.name}", DASHES.none { it in text }) }
            }
        }
    }

    private companion object {
        val PLACEHOLDER = Regex("""%\d+\$[sd]""")

        /** Em dash and en dash, written as code points so this file contains neither. */
        val DASHES = listOf(0x2014.toChar(), 0x2013.toChar())
    }
}
