// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.profiles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.oytuncagil.ethertether.model.ProfileList
import io.github.oytuncagil.ethertether.model.TetherInput
import io.github.oytuncagil.ethertether.model.TetherMode
import io.github.oytuncagil.ethertether.model.TetherRequestValidator
import io.github.oytuncagil.ethertether.ui.theme.EtherTetherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class ProfilesScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val clicks = mutableListOf<String>()

    private fun showList(list: ProfileList = ProfileList.initial, dark: Boolean = false) {
        val actions = ProfilesActions(
            onBack = { clicks += "back" },
            onSelect = { clicks += "select $it" },
            onEdit = { clicks += "edit $it" },
            onNew = { clicks += "new" },
            onDuplicate = { id, name -> clicks += "duplicate $id $name" },
            onDelete = { clicks += "delete $it" },
            onImport = { clicks += "import" },
            onExport = { clicks += "export" },
        )
        compose.setContent { EtherTetherTheme(darkTheme = dark, dynamicColor = false) { ProfilesScreen(list, actions) } }
    }

    @Test
    fun theThreeReadyMadeProfilesAreListedAndTheSelectedOneIsMarked() {
        showList()
        compose.onNodeWithText("Car ENET link-local 169.254.0.42/16").assertExists()
        compose.onNodeWithText("Laptop over USB-C Ethernet with internet").assertExists()
        compose.onNodeWithText("Isolated lab network").performScrollTo().assertExists()
        compose.onNodeWithText("Used for Start, the tile and the widget").assertExists()
        compose.onNodeWithContentDescription("Use Car ENET link-local 169.254.0.42/16").assertDoesNotExist()
    }

    @Test
    fun eachCardHasLabelledActions() {
        showList()
        compose.onNodeWithContentDescription("Use Isolated lab network").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Edit Isolated lab network").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Duplicate Isolated lab network").performScrollTo().performClick()
        assertEquals(listOf("select preset-lab", "edit preset-lab", "duplicate preset-lab Isolated lab network (copy)"), clicks)
    }

    @Test
    fun deleteAsksFirst() {
        showList()
        compose.onNodeWithContentDescription("Delete Isolated lab network").performScrollTo().performClick()
        compose.onNodeWithText("Delete this profile?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(emptyList<String>(), clicks)
        compose.onNodeWithContentDescription("Delete Isolated lab network").performScrollTo().performClick()
        compose.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(listOf("delete preset-lab"), clicks)
    }

    @Test
    fun newImportAndExport() {
        showList()
        compose.onNode(hasText("New profile"), useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Import profiles").performClick()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Export profiles").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("new", "import", "export", "back"), clicks)
    }

    @Test
    fun anEmptyListSaysSo() {
        showList(ProfileList(emptyList(), null))
        compose.onNodeWithText("No profiles yet. Create one under Manage profiles.").assertExists()
    }

    @Test
    @Config(qualifiers = "night")
    fun rendersInDarkTheme() {
        showList(dark = true)
        compose.onNodeWithText("Isolated lab network").performScrollTo().assertExists()
    }

    private var saves = 0
    private var name = ""
    private var input = TetherInput(TetherMode.STATIC, "", "", "16")

    private fun showEditor(existingId: String? = null) {
        compose.setContent {
            EtherTetherTheme(dynamicColor = false) {
                var shownName by remember { mutableStateOf(name) }
                var shownInput by remember { mutableStateOf(input) }
                val existing = existingId?.let(ProfileList.initial::find)
                val state = EditorState(existing, shownName, shownInput, TetherRequestValidator.validate(shownInput))
                ProfileEditorScreen(
                    state,
                    onName = { shownName = it; name = it },
                    onInput = { shownInput = it(shownInput); input = shownInput },
                    onSave = { saves++ },
                    onBack = { clicks += "back" },
                )
            }
        }
    }

    @Test
    fun anEmptyNewProfileCannotBeSaved() {
        showEditor()
        compose.onNodeWithText("New profile").assertExists()
        compose.onNodeWithText("Save").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun typingGoesToTheCallbacks() {
        showEditor()
        compose.onNodeWithText("Name").performTextInput("Bench")
        compose.onNodeWithText("Phone address").performTextInput("10.9.0.1")
        assertEquals("Bench", name)
        assertEquals("10.9.0.1", input.phoneAddress)
    }

    @Test
    fun aValidPresetCanBeSavedAndShowsItsNameAsPlaceholder() {
        input = ProfileList.initial.find("preset-lab")!!.input
        showEditor("preset-lab")
        compose.onNodeWithText("Edit profile").assertExists()
        compose.onNodeWithText("Leave empty to keep the ready-made name: Isolated lab network.", substring = true).assertExists()
        compose.onNodeWithText("Save").performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, saves)
    }

    @Test
    fun aTooShortPrefixOffersSixteen() {
        name = "Bench"
        input = TetherInput(TetherMode.STATIC, "10.9.0.1", "10.9.0.2", "8")
        showEditor()
        compose.onNodeWithText("Use /16").performScrollTo().performClick()
        assertEquals("16", input.prefixLength)
    }

    @Test
    fun aMissingClientAddressIsSuggested() {
        name = "Bench"
        input = TetherInput(TetherMode.STATIC, "10.9.0.1", "", "24")
        showEditor()
        compose.onNodeWithText("Use 10.9.0.2").performScrollTo().performClick()
        assertEquals("10.9.0.2", input.clientAddress)
    }

    @Test
    fun normalModeHidesTheAddressFields() {
        input = TetherInput(TetherMode.DHCP, "", "", "")
        showEditor()
        compose.onNodeWithText("Phone address").assertDoesNotExist()
        compose.onNodeWithText("Android picks a private subnet itself", substring = true).assertExists()
    }
}
