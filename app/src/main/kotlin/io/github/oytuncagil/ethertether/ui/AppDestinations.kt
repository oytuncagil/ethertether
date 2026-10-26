// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import io.github.oytuncagil.ethertether.EtherTetherApplication
import io.github.oytuncagil.ethertether.model.Ipv4Address
import io.github.oytuncagil.ethertether.model.Ipv4Subnet
import io.github.oytuncagil.ethertether.model.ProfileLimits
import io.github.oytuncagil.ethertether.model.BackendStatus
import io.github.oytuncagil.ethertether.tether.Notifications
import io.github.oytuncagil.ethertether.ui.common.displayName
import io.github.oytuncagil.ethertether.ui.common.resolve
import io.github.oytuncagil.ethertether.ui.profiles.ProfileEditorScreen
import io.github.oytuncagil.ethertether.ui.profiles.ProfileEditorViewModel
import io.github.oytuncagil.ethertether.ui.profiles.ProfilesActions
import io.github.oytuncagil.ethertether.ui.profiles.ProfilesScreen
import io.github.oytuncagil.ethertether.ui.profiles.ProfilesViewModel
import io.github.oytuncagil.ethertether.ui.settings.AppLocales
import io.github.oytuncagil.ethertether.ui.settings.SettingsActions
import io.github.oytuncagil.ethertether.ui.settings.SettingsExtras
import io.github.oytuncagil.ethertether.ui.settings.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun ProfilesDestination(app: EtherTetherApplication, nav: NavHostController) {
    val viewModel: ProfilesViewModel = viewModel { ProfilesViewModel(app.profiles, app.log) }
    val list by viewModel.profiles.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it.resolve(context)) } }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { viewModel.import(withContext(Dispatchers.IO) { readLimited(context, uri) }) }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val text = viewModel.exportText { it.displayName(context) }
            scope.launch { viewModel.exported(withContext(Dispatchers.IO) { write(context, uri, text) }) }
        }
    }
    ProfilesScreen(
        list,
        ProfilesActions(
            onBack = { nav.popBackStack() },
            onSelect = viewModel::select,
            onEdit = { nav.navigate(EditorRoute(it)) },
            onNew = { nav.navigate(EditorRoute(null)) },
            onDuplicate = { id, name -> viewModel.duplicate(id, name) },
            onDelete = viewModel::delete,
            onImport = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            onExport = { exporter.launch("ethertether-profiles.json") },
        ),
        snackbar,
    )
}

@Composable
internal fun EditorDestination(app: EtherTetherApplication, id: String?, nav: NavHostController) {
    val context = LocalContext.current
    val viewModel: ProfileEditorViewModel = viewModel(key = "editor-$id") {
        ProfileEditorViewModel(app.profiles, id, phoneNetworks = { phoneNetworks(context) })
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfileEditorScreen(
        state,
        onName = viewModel::setName,
        onInput = viewModel::updateInput,
        onSave = { viewModel.save { nav.popBackStack() } },
        onBack = { nav.popBackStack() },
    )
}

@Composable
internal fun SettingsDestination(app: EtherTetherApplication, platform: Platform, nav: NavHostController) {
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val tether by app.controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var backends by remember { mutableStateOf(emptyList<BackendStatus>()) }
    var notificationsAllowed by remember { mutableStateOf(Notifications.canNotify(context)) }
    var unusedAppRestricted by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = Notifications.canNotify(context)
        unusedAppRestricted = !context.packageManager.isAutoRevokeWhitelisted
        scope.launch { backends = withContext(Dispatchers.IO) { app.detectors.detectAll() } }
        onPauseOrDispose {}
    }
    SettingsScreen(
        settings,
        SettingsExtras(tether.readiness, backends, notificationsAllowed, AppLocales.current(context), unusedAppRestricted),
        SettingsActions(
            onBack = { nav.popBackStack() },
            onChange = { transform ->
                val before = settings
                scope.launch { app.settings.update(transform) }
                if (!before.keepArmed && transform(before).keepArmed) platform.requestNotificationsIfNeeded()
            },
            onLanguage = { AppLocales.set(context, it) },
            onOpenSetup = { nav.navigate(SetupRoute) },
            onGrant = platform::requestShizukuPermission,
            onAllowNotifications = platform::allowNotifications,
            onOpenAppSettings = platform::openAppSettings,
            onOpenUnusedAppSettings = platform::openUnusedAppSettings,
            onOpenDebug = { nav.navigate(DebugRoute) },
            onShowIntro = { nav.navigate(OnboardingRoute) },
        ),
    )
}

/** Reads an import file, at most the size limit plus one character, so huge files are refused. */
private fun readLimited(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
        val buffer = CharArray(ProfileLimits.MAX_IMPORT_CHARS + 1)
        var total = 0
        while (total < buffer.size) {
            val read = reader.read(buffer, total, buffer.size - total)
            if (read < 0) break
            total += read
        }
        String(buffer, 0, total)
    }
}.getOrNull()

private fun write(context: Context, uri: Uri, text: String): Boolean = runCatching {
    context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(text) } != null
}.getOrDefault(false)

/** The IPv4 subnets of the phone's current default network, for the overlap warning. */
private fun phoneNetworks(context: Context): List<Ipv4Subnet> = runCatching {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    val properties = manager.getLinkProperties(manager.activeNetwork) ?: return@runCatching emptyList()
    properties.linkAddresses.mapNotNull { link ->
        val address = link.address as? Inet4Address ?: return@mapNotNull null
        Ipv4Address.parse(address.hostAddress.orEmpty())?.let { Ipv4Subnet.of(it, link.prefixLength) }
    }
}.getOrDefault(emptyList())

/** A file name with the time, for exports. */
fun exportFileName(prefix: String, extension: String): String =
    "$prefix-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())}.$extension"
