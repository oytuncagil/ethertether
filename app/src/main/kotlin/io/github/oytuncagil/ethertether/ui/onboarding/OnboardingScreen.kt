// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.oytuncagil.ethertether.R
import io.github.oytuncagil.ethertether.backend.EngineReadiness
import io.github.oytuncagil.ethertether.tether.TetherState
import io.github.oytuncagil.ethertether.ui.common.resolve
import io.github.oytuncagil.ethertether.ui.home.statusLines
import io.github.oytuncagil.ethertether.ui.result.MessageView
import io.github.oytuncagil.ethertether.ui.result.message
import kotlinx.coroutines.launch

/** Callbacks of the first-run guide. */
data class OnboardingActions(
    val onOpenSetup: () -> Unit = {},
    val onGrant: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onFinish: () -> Unit = {},
)

private const val PAGES = 4

/**
 * First-run guide in five pages: what EtherTether does, Shizuku with wireless debugging, Sui and
 * root, testing the adapter, and done. Skip is always available; Settings can show it again.
 */
@Composable
fun OnboardingScreen(state: TetherState, actions: OnboardingActions) {
    val pager = rememberPagerState { PAGES }
    val scope = rememberCoroutineScope()
    Scaffold(
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = actions.onFinish, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.onboarding_skip)) }
                Text(stringResource(R.string.onboarding_page, pager.currentPage + 1, PAGES), style = MaterialTheme.typography.labelLarge)
                val last = pager.currentPage == PAGES - 1
                Button(
                    onClick = { if (last) actions.onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(if (last) R.string.onboarding_finish else R.string.onboarding_next)) }
            }
        },
    ) { padding ->
        HorizontalPager(pager, Modifier.fillMaxSize().padding(padding)) { page ->
            when (page) {
                0 -> Page(R.drawable.illus_link, R.string.onboarding_welcome_title, R.string.onboarding_welcome_text)
                1 -> Page(R.drawable.illus_shizuku, R.string.onboarding_shizuku_title, R.string.onboarding_shizuku_text) { ShizukuPart(state, actions) }
                2 -> Page(R.drawable.illus_adapter, R.string.onboarding_adapter_title, R.string.onboarding_adapter_text) { AdapterPart(state, actions) }
                else -> Page(R.drawable.illus_link, R.string.onboarding_done_title, R.string.onboarding_done_text)
            }
        }
    }
}

@Composable
private fun Page(@DrawableRes illustration: Int?, @StringRes title: Int, @StringRes text: Int, extra: @Composable () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (illustration != null) {
            // Decorative: the text below says everything the picture shows.
            Image(
                painter = painterResource(illustration),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                modifier = Modifier.size(width = 240.dp, height = 160.dp),
            )
        }
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
        extra()
    }
}

@Composable
private fun ShizukuPart(state: TetherState, actions: OnboardingActions) {
    state.readiness?.let { MessageView(it.message(), Modifier.fillMaxWidth()) }
    if (state.readiness == EngineReadiness.PermissionRequired) {
        Button(onClick = actions.onGrant, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_grant)) }
    } else if (state.readiness != EngineReadiness.Ready) {
        FilledTonalButton(onClick = actions.onOpenSetup, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.onboarding_setup_steps)) }
    }
    OutlinedButton(onClick = actions.onRefresh, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_refresh)) }
}

@Composable
private fun AdapterPart(state: TetherState, actions: OnboardingActions) {
    if (state.readiness != EngineReadiness.Ready) {
        Text(stringResource(R.string.onboarding_adapter_needs_shizuku), color = MaterialTheme.colorScheme.tertiary)
    } else {
        statusLines(state, null).forEach { Text(it.resolve(), style = MaterialTheme.typography.bodyMedium) }
    }
    OutlinedButton(onClick = actions.onRefresh, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.onboarding_check_adapter)) }
}
