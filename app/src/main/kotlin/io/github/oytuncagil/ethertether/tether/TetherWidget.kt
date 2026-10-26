// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.tether

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.oytuncagil.ethertether.EtherTetherApplication
import io.github.oytuncagil.ethertether.MainActivity
import io.github.oytuncagil.ethertether.R
import io.github.oytuncagil.ethertether.backend.EngineReadiness

/** What the widget shows, taken from the controller state. */
data class WidgetContent(val title: Int, val profile: String, val action: Int, val canToggle: Boolean)

/** The widget content for [state]; [profileName] is the profile it would start or runs. */
fun widgetContent(state: TetherState, profileName: String): WidgetContent {
    val ready = state.readiness == null || state.readiness == EngineReadiness.Ready
    val title = when {
        !ready -> R.string.widget_needs_shizuku
        state.serving -> R.string.widget_on
        state.waitingForAdapter -> R.string.widget_waiting
        else -> R.string.widget_off
    }
    val action = if (state.active) R.string.action_stop else R.string.action_start
    return WidgetContent(title, profileName, action, ready)
}

/**
 * Home-screen widget: state, profile and one Start or Stop button.
 *
 * The widget's receiver is exported (the system must reach it), so any app can send it Glance's
 * broadcasts. Its actions therefore stay PendingIntents to the app's own components; a lambda
 * onClick or actionRunCallback would let other apps start or stop tethering.
 */
class TetherWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as EtherTetherApplication
        provideContent {
            val state by app.controller.state.collectAsState()
            val profiles by app.profiles.profiles.collectAsState()
            val name = app.profileName(state.profileId ?: profiles.selected?.id).orEmpty()
            GlanceTheme { WidgetBody(context, widgetContent(state, name)) }
        }
    }
}

@Composable
private fun WidgetBody(context: Context, content: WidgetContent) {
    val title = context.getString(content.title)
    val action = context.getString(content.action)
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(title, style = TextStyle(fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface))
        Text(content.profile, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
        Spacer(GlanceModifier.height(8.dp))
        val onClick = if (content.canToggle) {
            actionStartService(TetherService.toggleIntent(context), isForegroundService = true)
        } else {
            actionStartActivity<MainActivity>()
        }
        Button(
            text = action,
            onClick = onClick,
            modifier = GlanceModifier.semantics { contentDescription = context.getString(R.string.widget_button_description, action, content.profile) },
        )
    }
}

/** The widget's broadcast receiver. */
class TetherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TetherWidget()
}
