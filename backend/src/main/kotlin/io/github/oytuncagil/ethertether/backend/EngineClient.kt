// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.backend

import android.os.RemoteException
import io.github.oytuncagil.ethertether.engine.EngineReport
import io.github.oytuncagil.ethertether.engine.EngineStatus
import io.github.oytuncagil.ethertether.engine.EngineStep
import io.github.oytuncagil.ethertether.engine.RawStep
import android.os.Bundle
import io.github.oytuncagil.ethertether.engine.service.EngineCodec
import io.github.oytuncagil.ethertether.engine.service.IEngineListener
import io.github.oytuncagil.ethertether.engine.service.IEtherTetherService
import io.github.oytuncagil.ethertether.model.TetherInput
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext

/** What the app needs from the engine. Every call throws [EngineUnavailable] when it cannot be reached. */
interface EngineApi {
    /** Stops, then starts Ethernet tethering with [input]. */
    suspend fun start(input: TetherInput): EngineReport

    /** Stops Ethernet tethering. */
    suspend fun stop(): EngineReport

    /** The current status. */
    suspend fun status(): EngineStatus

    /** Runs one raw step; [input] is needed by the build and start steps. */
    suspend fun runStep(step: RawStep, input: TetherInput?): List<EngineStep>

    /** Every status the engine pushes once [watch] was called, also after reconnects. */
    val statusUpdates: SharedFlow<EngineStatus>

    /** Starts the status pushes; they continue across reconnects. Throws [EngineUnavailable]. */
    suspend fun watch()
}

/**
 * The app's entry to the engine: encodes requests, calls the user service off the main thread
 * and decodes the results. Every call throws [EngineUnavailable] when the engine cannot be
 * reached; a service that died during a call is forgotten so the next call binds again.
 */
class EngineClient(
    private val connection: EngineConnection,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val answerTimeoutMs: Long = ANSWER_TIMEOUT_MS,
) : EngineApi {
    /** Binder calls block their thread, so each runs here, apart from its caller (see [call]). */
    private val calls = CoroutineScope(SupervisorJob() + dispatcher)

    private val updates = MutableSharedFlow<EngineStatus>(replay = 1, extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    @Volatile
    private var watching = false

    private val listener = object : IEngineListener.Stub() {
        override fun onStatus(status: Bundle?) {
            status?.let { updates.tryEmit(EngineCodec.decodeStatus(it)) }
        }
    }

    init {
        connection.onConnected = { service -> if (watching) service.setListener(listener) }
    }

    override val statusUpdates: SharedFlow<EngineStatus> = updates.asSharedFlow()

    override suspend fun watch() {
        watching = true
        call { it.setListener(listener) }
    }

    /** Stops, then starts Ethernet tethering with [input]; the service validates it again. */
    override suspend fun start(input: TetherInput): EngineReport = call { EngineCodec.decodeReport(it.start(EngineCodec.encodeInput(input))) }

    /** Stops Ethernet tethering. */
    override suspend fun stop(): EngineReport = call { EngineCodec.decodeReport(it.stop()) }

    /** The current status. */
    override suspend fun status(): EngineStatus = call { EngineCodec.decodeStatus(it.status()) }

    /** Runs one raw step for the debug screen; [input] is needed by the build and start steps. */
    override suspend fun runStep(step: RawStep, input: TetherInput?): List<EngineStep> =
        call { EngineCodec.decodeSteps(it.runStep(step.name, input?.let(EngineCodec::encodeInput))) }

    /**
     * Runs [block] on the service. A binder call cannot be cancelled, so it runs in its own job and
     * the caller waits at most [answerTimeoutMs]: a call that never answers (a stopped or frozen
     * process on the other side) becomes [UnavailableReason.NO_ANSWER] instead of a request that
     * hangs for ever. The connection is then forgotten, so the next call binds again.
     */
    private suspend fun <T> call(block: (IEtherTetherService) -> T): T {
        val service = withContext(dispatcher) { connection.service() }
        val answer = calls.async {
            try {
                block(service)
            } catch (e: RemoteException) {
                connection.reset()
                throw EngineUnavailable(UnavailableReason.DIED, e.message.orEmpty())
            }
        }
        return withTimeoutOrNull(answerTimeoutMs) { answer.await() } ?: run {
            connection.reset()
            throw EngineUnavailable(UnavailableReason.NO_ANSWER, "no answer after $answerTimeoutMs ms")
        }
    }

    /** Limits. */
    companion object {
        /**
         * The longest a request may take: a stop that settles (4 s), up to four start attempts with
         * their result codes (5 s each) and the confirmation (10 s), with room to spare.
         */
        const val ANSWER_TIMEOUT_MS = 45_000L
    }
}
