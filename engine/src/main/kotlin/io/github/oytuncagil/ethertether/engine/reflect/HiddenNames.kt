// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.engine.reflect

/**
 * Names of the hidden framework classes the engine reaches by reflection. Every one is listed
 * in docs/ARCHITECTURE.md with its source. Tests pass fake names to resolve fake classes.
 */
data class HiddenNames(
    val connectorStub: String = "android.net.ITetheringConnector\$Stub",
    val requestParcel: String = "android.net.TetheringRequestParcel",
    val intResultListener: String = "android.net.IIntResultListener",
    val intResultListenerStub: String = "android.net.IIntResultListener\$Stub",
    val eventCallback: String = "android.net.ITetheringEventCallback",
    val eventCallbackStub: String = "android.net.ITetheringEventCallback\$Stub",
    val requestBuilder: String = "android.net.TetheringManager\$TetheringRequest\$Builder",
    val statesParcel: String = "android.net.TetherStatesParcel",
    val callbackStartedParcel: String = "android.net.TetheringCallbackStartedParcel",
    val linkAddress: String = "android.net.LinkAddress",
    val tetheredClient: String = "android.net.TetheredClient",
    val serviceManager: String = "android.os.ServiceManager",
)
