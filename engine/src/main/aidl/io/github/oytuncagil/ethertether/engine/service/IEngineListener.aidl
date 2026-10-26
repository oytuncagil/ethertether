// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.engine.service;

import android.os.Bundle;

/** Receives every change of the Ethernet tethering status from the engine. */
oneway interface IEngineListener {
    /** A status Bundle written by EngineCodec.encodeStatus. */
    void onStatus(in Bundle status) = 1;
}
