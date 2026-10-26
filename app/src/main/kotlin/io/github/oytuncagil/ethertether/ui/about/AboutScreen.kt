// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.about

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import io.github.oytuncagil.ethertether.R
import io.github.oytuncagil.ethertether.ui.common.ScreenColumn
import io.github.oytuncagil.ethertether.ui.common.ScreenScaffold
import io.github.oytuncagil.ethertether.ui.common.SectionHeading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * The facts the About screen shows. [officialSha256] is the release certificate published in the
 * README (gradle.properties, ethertether.releaseCertSha256), null before the first release.
 */
data class AboutInfo(
    val versionName: String,
    val versionCode: Long,
    val buildType: String,
    val signingSha256: String?,
    val officialSha256: String? = null,
)

/** Whether this copy is signed with the official release key. */
enum class SigningStatus { OFFICIAL, OTHER, NOT_PUBLISHED, UNKNOWN }

/** Compares this copy's certificate with the published one, ignoring case and colons. */
fun signingStatus(info: AboutInfo): SigningStatus {
    val own = info.signingSha256?.normalizedFingerprint() ?: return SigningStatus.UNKNOWN
    val official = info.officialSha256?.normalizedFingerprint()?.takeIf { it.isNotEmpty() } ?: return SigningStatus.NOT_PUBLISHED
    return if (own == official) SigningStatus.OFFICIAL else SigningStatus.OTHER
}

private fun String.normalizedFingerprint() = filter { it.isLetterOrDigit() }.uppercase()

/** The repository of this app. */
const val SOURCE_URL = "https://github.com/oytuncagil/ethertether"

/** About: version, license choice, libraries, source code, whether this is an official release, privacy. */
@Composable
fun AboutScreen(info: AboutInfo, onBack: () -> Unit, onLibraries: () -> Unit, onOpenSource: () -> Unit) {
    ScreenScaffold(title = stringResource(R.string.about_title), onBack = onBack) { padding ->
        ScreenColumn(padding) {
            SectionHeading(stringResource(R.string.app_name))
            Text(stringResource(R.string.about_version, info.versionName, info.versionCode, info.buildType))
            Text(stringResource(R.string.about_owner))
            SectionHeading(stringResource(R.string.about_license_title))
            Text(stringResource(R.string.about_license))
            FilledTonalButton(onClick = onLibraries, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.about_libraries)) }
            SectionHeading(stringResource(R.string.about_source_title))
            Text(SOURCE_URL, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onOpenSource, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.about_open_source)) }
            // Only the verdict; the fingerprint itself is published in the README and the release notes.
            signingStatusText(signingStatus(info))?.let {
                SectionHeading(stringResource(R.string.about_signing_title))
                Text(stringResource(it))
            }
            SectionHeading(stringResource(R.string.about_privacy_title))
            Text(stringResource(R.string.about_privacy))
        }
    }
}

/** The open-source libraries in the APK with their licenses, generated at build time. */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    ScreenScaffold(title = stringResource(R.string.about_libraries), onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val resources = LocalResources.current
            val libraries by produceLibraries {
                withContext(Dispatchers.IO) { resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() } }
            }
            LibrariesContainer(libraries, Modifier.fillMaxSize())
        }
    }
}

private fun signingStatusText(status: SigningStatus): Int? = when (status) {
    SigningStatus.OFFICIAL -> R.string.about_signing_official
    SigningStatus.OTHER -> R.string.about_signing_other
    SigningStatus.NOT_PUBLISHED, SigningStatus.UNKNOWN -> null
}

/** Reads [AboutInfo] of this app, including the SHA-256 fingerprint of its signing certificate. */
fun aboutInfo(context: Context, buildType: String, officialSha256: String?): AboutInfo {
    val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    val signer = info.signingInfo?.apkContentsSigners?.firstOrNull()
    return AboutInfo(info.versionName.orEmpty(), info.longVersionCode, buildType, signer?.toByteArray()?.let(::sha256Fingerprint), officialSha256)
}

/** "AB:CD:..." upper-case colon-separated SHA-256, the form apksigner and verifier apps show. */
fun sha256Fingerprint(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(":") { "%02X".format(it) }
