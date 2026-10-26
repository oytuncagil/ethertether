// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.oytuncagil.ethertether.BuildConfig
import io.github.oytuncagil.ethertether.EtherTetherApplication
import io.github.oytuncagil.ethertether.R
import io.github.oytuncagil.ethertether.ui.about.AboutScreen
import io.github.oytuncagil.ethertether.ui.about.LicensesScreen
import io.github.oytuncagil.ethertether.ui.about.SOURCE_URL
import io.github.oytuncagil.ethertether.ui.about.aboutInfo
import io.github.oytuncagil.ethertether.ui.debug.DebugActions
import io.github.oytuncagil.ethertether.ui.debug.DebugScreen
import io.github.oytuncagil.ethertether.ui.debug.DebugViewModel
import io.github.oytuncagil.ethertether.ui.debug.entriesAsText
import io.github.oytuncagil.ethertether.ui.home.HomeActions
import io.github.oytuncagil.ethertether.ui.home.HomeScreen
import io.github.oytuncagil.ethertether.ui.home.HomeViewModel
import io.github.oytuncagil.ethertether.ui.log.LogActions
import io.github.oytuncagil.ethertether.ui.log.LogScreen
import io.github.oytuncagil.ethertether.ui.onboarding.OnboardingActions
import io.github.oytuncagil.ethertether.ui.onboarding.OnboardingScreen
import io.github.oytuncagil.ethertether.ui.setup.SetupActions
import io.github.oytuncagil.ethertether.ui.setup.SetupUiState
import io.github.oytuncagil.ethertether.ui.setup.ShizukuSetupScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** Navigation routes. */
@Serializable data object OnboardingRoute

/** Home. */
@Serializable data object HomeRoute

/** Profile list. */
@Serializable data object ProfilesRoute

/** Profile editor; [id] is null for a new profile. */
@Serializable data class EditorRoute(val id: String? = null)

/** Log. */
@Serializable data object LogRoute

/** Settings. */
@Serializable data object SettingsRoute

/** About. */
@Serializable data object AboutRoute

/** Open-source libraries. */
@Serializable data object LicensesRoute

/** Guided Shizuku setup. */
@Serializable data object SetupRoute

/** Engine debug screen. */
@Serializable data object DebugRoute

/** All screens of the app; [showOnboarding] starts with the first-run guide. */
@Composable
fun AppNavigation(app: EtherTetherApplication, platform: Platform, showOnboarding: Boolean) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = if (showOnboarding) OnboardingRoute else HomeRoute) {
        composable<OnboardingRoute> { Onboarding(app, platform, nav) }
        composable<HomeRoute> { Home(app, platform, nav) }
        composable<ProfilesRoute> { ProfilesDestination(app, nav) }
        composable<EditorRoute> { entry -> EditorDestination(app, entry.toRoute<EditorRoute>().id, nav) }
        composable<LogRoute> { LogDestination(app, platform, nav) }
        composable<SettingsRoute> { SettingsDestination(app, platform, nav) }
        composable<AboutRoute> {
            val context = LocalContext.current
            AboutScreen(aboutInfo(context, BuildConfig.BUILD_TYPE, BuildConfig.RELEASE_CERT_SHA256), onBack = { nav.popBackStack() }, onLibraries = { nav.navigate(LicensesRoute) }) {
                platform.openUrl(SOURCE_URL)
            }
        }
        composable<LicensesRoute> { LicensesScreen(onBack = { nav.popBackStack() }) }
        composable<SetupRoute> { Setup(app, platform, nav) }
        composable<DebugRoute> { Debug(app, platform, nav) }
    }
}

@Composable
private fun Onboarding(app: EtherTetherApplication, platform: Platform, nav: NavHostController) {
    val state by app.controller.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { app.controller.refreshStatus() }
    OnboardingScreen(
        state,
        OnboardingActions(
            onOpenSetup = { nav.navigate(SetupRoute) },
            onGrant = platform::requestShizukuPermission,
            onRefresh = { scope.launch { app.controller.refreshStatus() } },
            onFinish = {
                scope.launch { app.settings.update { it.copy(onboardingDone = true) } }
                nav.navigate(HomeRoute) { popUpTo(OnboardingRoute) { inclusive = true } }
            },
        ),
    )
}

@Composable
private fun Home(app: EtherTetherApplication, platform: Platform, nav: NavHostController) {
    val viewModel: HomeViewModel = viewModel { HomeViewModel(app.controller, app.profiles) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    HomeScreen(
        state,
        HomeActions(
            onStart = {
                platform.requestNotificationsIfNeeded()
                viewModel.start()
            },
            onStop = viewModel::stop,
            onSelect = viewModel::select,
            onGrant = platform::requestShizukuPermission,
            onDismiss = viewModel::dismissEvent,
            onOpenSetup = { nav.navigate(SetupRoute) },
            onOpenProfiles = { nav.navigate(ProfilesRoute) },
            onOpenLog = { nav.navigate(LogRoute) },
            onOpenSettings = { nav.navigate(SettingsRoute) },
            onOpenAbout = { nav.navigate(AboutRoute) },
            onOpenTetherSettings = platform::openTetherSettings,
        ),
    )
}

@Composable
private fun LogDestination(app: EtherTetherApplication, platform: Platform, nav: NavHostController) {
    val entries by app.log.entries.collectAsStateWithLifecycle()
    val label = stringResource(R.string.log_title)
    LogScreen(
        entries,
        LogActions(
            onBack = { nav.popBackStack() },
            onCopy = { platform.copy(label, app.log.asText(it)) },
            onExport = platform::shareLog,
            onClear = app.log::clear,
        ),
    )
}

@Composable
private fun Setup(app: EtherTetherApplication, platform: Platform, nav: NavHostController) {
    val state by app.controller.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { app.controller.refreshReadiness() }
    ShizukuSetupScreen(
        SetupUiState(state.readiness, app.isShizukuInstalled()),
        SetupActions(
            onBack = { nav.popBackStack() },
            onRefresh = { scope.launch { app.controller.refreshStatus() } },
            onGrant = platform::requestShizukuPermission,
            onOpenDownload = { platform.openUrl(SHIZUKU_DOWNLOAD_URL) },
            onOpenShizuku = platform::openShizuku,
            onOpenDeveloperOptions = platform::openDeveloperOptions,
        ),
    )
}

@Composable
private fun Debug(app: EtherTetherApplication, platform: Platform, nav: NavHostController) {
    val viewModel: DebugViewModel = viewModel { DebugViewModel(app.engine) }
    val debug by viewModel.state.collectAsStateWithLifecycle()
    val tether by app.controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val label = stringResource(R.string.debug_clip_label)
    DebugScreen(
        debug,
        tether.readiness,
        DebugActions(
            onBack = { nav.popBackStack() },
            onInput = viewModel::updateInput,
            onStart = viewModel::start,
            onStop = viewModel::stop,
            onStatus = viewModel::status,
            onStep = viewModel::step,
            onCopy = { platform.copy(label, entriesAsText(context, debug.entries)) },
            onClear = viewModel::clear,
        ),
    )
}

/** Shizuku's official download page. */
const val SHIZUKU_DOWNLOAD_URL = "https://shizuku.rikka.app/download/"
