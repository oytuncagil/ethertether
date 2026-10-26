// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.settings

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.oytuncagil.ethertether.R
import io.github.oytuncagil.ethertether.backend.EngineReadiness
import io.github.oytuncagil.ethertether.data.Settings
import io.github.oytuncagil.ethertether.data.ThemeChoice
import io.github.oytuncagil.ethertether.model.BackendStatus
import io.github.oytuncagil.ethertether.ui.common.ScreenColumn
import io.github.oytuncagil.ethertether.ui.common.ScreenScaffold
import io.github.oytuncagil.ethertether.ui.common.SectionHeading
import io.github.oytuncagil.ethertether.ui.home.BackendRow
import io.github.oytuncagil.ethertether.ui.home.present
import io.github.oytuncagil.ethertether.ui.result.MessageView
import io.github.oytuncagil.ethertether.ui.result.message

/**
 * What the settings screen shows besides the [Settings] themselves.
 *
 * @property unusedAppRestricted true when Android may remove the app's permissions after months without use
 */
data class SettingsExtras(
    val readiness: EngineReadiness?,
    val backends: List<BackendStatus>,
    val notificationsAllowed: Boolean,
    val language: String,
    val unusedAppRestricted: Boolean = false,
)

/** Callbacks of the settings screen. */
data class SettingsActions(
    val onBack: () -> Unit = {},
    val onChange: ((Settings) -> Settings) -> Unit = {},
    val onLanguage: (String) -> Unit = {},
    val onOpenSetup: () -> Unit = {},
    val onGrant: () -> Unit = {},
    val onAllowNotifications: () -> Unit = {},
    val onOpenAppSettings: () -> Unit = {},
    val onOpenUnusedAppSettings: () -> Unit = {},
    val onOpenDebug: () -> Unit = {},
    val onShowIntro: () -> Unit = {},
)

/** Settings: privileged access, automation, notifications, appearance, language, developer. */
@Composable
fun SettingsScreen(settings: Settings, extras: SettingsExtras, actions: SettingsActions) {
    ScreenScaffold(title = stringResource(R.string.settings_title), onBack = actions.onBack) { padding ->
        ScreenColumn(padding) {
            AccessSection(extras, actions)
            AutomationSection(settings, extras, actions)
            NotificationSection(settings, extras, actions)
            AppearanceSection(settings, actions)
            LanguageSection(extras.language, actions.onLanguage)
            DeveloperSection(settings, actions)
        }
    }
}

@Composable
private fun AccessSection(extras: SettingsExtras, actions: SettingsActions) {
    SectionHeading(stringResource(R.string.settings_access))
    Text(stringResource(R.string.settings_backend_only_shizuku), style = MaterialTheme.typography.bodyMedium)
    extras.readiness?.let { MessageView(it.message()) }
    if (extras.readiness != EngineReadiness.Ready) {
        FilledTonalButton(onClick = actions.onOpenSetup, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_open_setup)) }
    }
    extras.backends.forEach { BackendRow(it.present(), actions.onGrant) }
}

@Composable
private fun AutomationSection(settings: Settings, extras: SettingsExtras, actions: SettingsActions) {
    SectionHeading(stringResource(R.string.settings_automation))
    SwitchRow(R.string.settings_keep_armed, R.string.settings_keep_armed_summary, settings.keepArmed) { on -> actions.onChange { it.copy(keepArmed = on) } }
    SwitchRow(R.string.settings_boot, R.string.settings_boot_summary, settings.startAtBoot) { on -> actions.onChange { it.copy(startAtBoot = on) } }
    Text(stringResource(R.string.settings_automation_limits), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(stringResource(R.string.settings_vendor_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedButton(onClick = actions.onOpenAppSettings, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.settings_open_app_settings)) }
    if ((settings.keepArmed || settings.startAtBoot) && extras.unusedAppRestricted) {
        Text(stringResource(R.string.settings_unused_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
        FilledTonalButton(onClick = actions.onOpenUnusedAppSettings, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.settings_unused_open)) }
    }
}

@Composable
private fun NotificationSection(settings: Settings, extras: SettingsExtras, actions: SettingsActions) {
    SectionHeading(stringResource(R.string.settings_notifications))
    SwitchRow(R.string.settings_alerts, R.string.settings_alerts_summary, settings.alertOnExternalStop) { on -> actions.onChange { it.copy(alertOnExternalStop = on) } }
    if (!extras.notificationsAllowed) {
        Text(stringResource(R.string.settings_notifications_off), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
        FilledTonalButton(onClick = actions.onAllowNotifications, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.settings_allow_notifications)) }
    }
}

@Composable
private fun AppearanceSection(settings: Settings, actions: SettingsActions) {
    SectionHeading(stringResource(R.string.settings_appearance))
    val themes = listOf(ThemeChoice.SYSTEM to R.string.theme_system, ThemeChoice.LIGHT to R.string.theme_light, ThemeChoice.DARK to R.string.theme_dark)
    RadioGroup(themes.map { (choice, label) -> Triple(choice.name, stringResource(label), settings.theme == choice) }) { name ->
        actions.onChange { it.copy(theme = ThemeChoice.valueOf(name)) }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        SwitchRow(R.string.settings_dynamic_color, R.string.settings_dynamic_color_summary, settings.dynamicColor) { on -> actions.onChange { it.copy(dynamicColor = on) } }
    }
}

@Composable
private fun LanguageSection(current: String, onLanguage: (String) -> Unit) {
    SectionHeading(stringResource(R.string.settings_language))
    val names = mapOf("" to stringResource(R.string.language_system), "en" to "English", "de" to "Deutsch", "tr" to "Türkçe")
    RadioGroup(AppLocales.supported.map { Triple(it, names.getValue(it), it == current) }, onLanguage)
}

@Composable
private fun DeveloperSection(settings: Settings, actions: SettingsActions) {
    SectionHeading(stringResource(R.string.developer_title))
    SwitchRow(R.string.developer_switch, R.string.developer_switch_summary, settings.developerMode) { on -> actions.onChange { it.copy(developerMode = on) } }
    if (settings.developerMode) {
        OutlinedButton(onClick = actions.onOpenDebug, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_open_debug)) }
    }
    OutlinedButton(onClick = actions.onShowIntro, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.settings_show_intro)) }
}

/** A row with a title, a summary and a switch; the whole row toggles. */
@Composable
fun SwitchRow(@StringRes title: Int, @StringRes summary: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun RadioGroup(options: List<Triple<String, String, Boolean>>, onSelect: (String) -> Unit) {
    Column(Modifier.selectableGroup()) {
        options.forEach { (key, label, selected) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(selected = selected, role = Role.RadioButton) { onSelect(key) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RadioButton(selected = selected, onClick = null)
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
