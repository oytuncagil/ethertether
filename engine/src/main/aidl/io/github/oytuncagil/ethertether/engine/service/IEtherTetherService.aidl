// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.engine.service;

import android.os.Bundle;
import io.github.oytuncagil.ethertether.engine.service.IEngineListener;

/**
 * EtherTether's own interface between the app and the engine, which runs in a Shizuku user
 * service as the shell user. Requests and results are Bundles written and read by
 * EngineCodec; the service validates every request again before it uses it.
 */
interface IEtherTetherService {
    /** Called by Shizuku before it removes the service; the service process exits. */
    void destroy() = 16777114;

    /** EngineCodec.PROTOCOL_VERSION of the running service. */
    int protocolVersion() = 1;

    /** The uid the service runs as: 2000 for shell. */
    int uid() = 2;

    /** Stops, then starts Ethernet tethering with the request and confirms it. Returns a report. */
    Bundle start(in Bundle request) = 3;

    /** Stops Ethernet tethering and confirms it. Returns a report. */
    Bundle stop() = 4;

    /** The current Ethernet tethering status. */
    Bundle status() = 5;

    /** Runs one raw engine step for the debug screen. Returns its steps. */
    Bundle runStep(String step, in Bundle request) = 6;

    /** Sends every status change to the listener, starting with the current status; null stops it. */
    void setListener(IEngineListener listener) = 7;
}
