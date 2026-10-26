// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.engine.service

import android.content.Context
import android.os.Binder
import android.os.Bundle
import android.os.Parcel
import android.os.Process
import io.github.oytuncagil.ethertether.engine.EngineReport
import io.github.oytuncagil.ethertether.engine.EngineStep
import io.github.oytuncagil.ethertether.engine.FailureReason
import io.github.oytuncagil.ethertether.engine.Outcome
import io.github.oytuncagil.ethertether.engine.RawStep
import io.github.oytuncagil.ethertether.engine.RawSteps
import io.github.oytuncagil.ethertether.engine.StatusPublisher
import io.github.oytuncagil.ethertether.engine.SystemInterfaceReader
import io.github.oytuncagil.ethertether.engine.TetheringEngine
import io.github.oytuncagil.ethertether.engine.reflect.ReflectiveTetheringPort
import io.github.oytuncagil.ethertether.model.TetherRequest
import io.github.oytuncagil.ethertether.model.TetherRequestValidator
import io.github.oytuncagil.ethertether.model.ValidationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/**
 * The engine host. Shizuku starts this class in its own process as the shell user,
 * through Shizuku.bindUserService; the app reaches it only through [IEtherTetherService].
 * Shizuku creates it by class name: servers from v13 use the constructor with a Context, older
 * ones the no-argument constructor, so both stay.
 *
 * Only EtherTether and Shizuku can hold this binder (Shizuku hands it only to the package that
 * owns the service). As a second line, every call is checked against the app's uid when Shizuku
 * passed a Context, see [CallerCheck].
 */
class EngineUserService private constructor(private val appUid: Int?) : IEtherTetherService.Stub() {
    /** Shizuku v11 and v12 servers. */
    constructor() : this(null)

    /** Shizuku v13 and later, with the Context of the EtherTether package. */
    constructor(context: Context) : this(context.applicationInfo.uid)

    private val engine = TetheringEngine(ReflectiveTetheringPort(), SystemInterfaceReader())
    private val rawSteps = RawSteps(engine)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val publisher = StatusPublisher(engine, scope)

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        val caller = Binder.getCallingUid()
        if (!CallerCheck.allowed(caller, Process.myUid(), appUid)) throw SecurityException("caller $caller is not EtherTether")
        return super.onTransact(code, data, reply, flags)
    }

    override fun destroy() {
        scope.cancel()
        engine.close()
        exitProcess(0)
    }

    override fun protocolVersion(): Int = EngineCodec.PROTOCOL_VERSION

    override fun uid(): Int = Process.myUid()

    override fun start(request: Bundle?): Bundle {
        val validated = validate(request)
            ?: return EngineCodec.encodeReport(EngineReport(Outcome.Failed(FailureReason.UNEXPECTED, detail = "invalid request")))
        return EngineCodec.encodeReport(runBlocking { engine.start(validated) })
    }

    override fun stop(): Bundle = EngineCodec.encodeReport(runBlocking { engine.stop() })

    override fun status(): Bundle = EngineCodec.encodeStatus(runBlocking { engine.status() })

    override fun runStep(step: String?, request: Bundle?): Bundle {
        val raw = RawStep.entries.firstOrNull { it.name == step }
        val steps = if (raw == null) {
            listOf(EngineStep("error", "unknown step $step"))
        } else {
            runBlocking { rawSteps.run(raw, validate(request)) }
        }
        return EngineCodec.encodeSteps(steps)
    }

    override fun setListener(listener: IEngineListener?) {
        publisher.publishTo(listener?.let { target -> { status -> target.onStatus(EngineCodec.encodeStatus(status)) } })
    }

    /** Validates the request again on this side: only values Android accepts reach the engine. */
    private fun validate(bundle: Bundle?): TetherRequest? {
        val input = bundle?.let(EngineCodec::decodeInput) ?: return null
        return (TetherRequestValidator.validate(input) as? ValidationResult.Valid)?.request
    }
}
