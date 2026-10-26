// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.engine

/**
 * Values of the Android tethering module that the engine passes over binder. Verified against
 * the Android 11 to 17 sources listed in docs/SOURCES.md; identical on all of them.
 */
object TetheringConstants {
    /** TetheringManager.TETHERING_ETHERNET. */
    const val TETHERING_ETHERNET = 5

    /** TetheringManager.CONNECTIVITY_SCOPE_GLOBAL: the phone's connection is shared (Android 12+). */
    const val CONNECTIVITY_SCOPE_GLOBAL = 1

    /** TetheringManager.CONNECTIVITY_SCOPE_LOCAL: no internet is shared (Android 12+). */
    const val CONNECTIVITY_SCOPE_LOCAL = 2

    /** First Android API level whose Builder has setConnectivityScope (Android 12). */
    const val MIN_SDK_CONNECTIVITY_SCOPE = 31

    /** Smallest prefix length Android's DHCP server accepts for a static address. */
    const val MIN_STATIC_PREFIX = 16

    /** Largest prefix length Android's DHCP server accepts for a static address. */
    const val MAX_STATIC_PREFIX = 30

    /** Name of the tethering system service. */
    const val SERVICE_NAME = "tethering"

    /** Package the shell uid (2000) owns; the service checks the caller package against the uid. */
    const val SHELL_PACKAGE = "com.android.shell"
}
