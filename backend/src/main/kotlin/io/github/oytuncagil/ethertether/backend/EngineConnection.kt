// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.backend

import android.os.IBinder
import io.github.oytuncagil.ethertether.engine.service.EngineCodec
import io.github.oytuncagil.ethertether.engine.service.IEtherTetherService
import io.github.oytuncagil.ethertether.model.KnownUids
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Starts and stops the engine's user service. The real one wraps Shizuku.bindUserService. */
interface UserServiceBinder {
    /** Asks for the service; [onConnected] and [onDisconnected] may be called on any thread. */
    fun bind(onConnected: (IBinder) -> Unit, onDisconnected: () -> Unit)

    /** Releases the service and lets it exit. */
    fun unbind()
}

/** Why the engine could not be reached. */
enum class UnavailableReason {
    /** The user service did not connect in time (reported on some vendor ROMs). */
    BIND_TIMEOUT,

    /** Binding failed at once, for example because Shizuku stopped. */
    BIND_FAILED,

    /** The service runs as a uid other than shell. */
    WRONG_UID,

    /** The service speaks another protocol version, even after a restart. */
    PROTOCOL_MISMATCH,

    /** The service or Shizuku died during a call. */
    DIED,

    /** The service took a request but gave no answer within the time limit. */
    NO_ANSWER,
}

/** The engine cannot be reached; [reason] says why and [detail] holds technical text. */
class EngineUnavailable(val reason: UnavailableReason, val detail: String = "") :
    Exception("Engine unavailable: $reason $detail")

/**
 * Keeps one connection to the engine's user service. It binds on first use, checks the uid and
 * the protocol version, and forgets the service when it dies, so the next call binds again.
 *
 * @param asInterface turns the binder into the service interface; replaced in tests
 */
class EngineConnection(
    private val binder: UserServiceBinder,
    private val bindTimeoutMs: Long = 15_000,
    private val asInterface: (IBinder) -> IEtherTetherService = IEtherTetherService.Stub::asInterface,
) {
    private val mutex = Mutex()

    @Volatile
    private var current: IEtherTetherService? = null

    @Volatile
    private var deathLink: IBinder.DeathRecipient? = null

    /** Called with every newly connected and checked service, for example to register a listener. */
    @Volatile
    var onConnected: ((IEtherTetherService) -> Unit)? = null

    /** True while a checked service is connected. */
    val isConnected: Boolean get() = current != null

    /** The connected service, binding it first if needed. Throws [EngineUnavailable]. */
    suspend fun service(): IEtherTetherService = mutex.withLock {
        current?.takeIf { it.asBinder().isBinderAlive }?.let { return it }
        current = null
        val service = connect()
        checkIdentity(service)
        watchDeath(service)
        current = service
        runCatching { onConnected?.invoke(service) }
        service
    }

    /** Forgets the service, for example after Shizuku's binder died. The next call binds again. */
    fun reset() {
        forget()
    }

    /** Unbinds and lets the service process exit. */
    fun close() {
        forget()
        runCatching { binder.unbind() }
    }

    /** Drops the service and its death link, so nothing keeps the old binder alive. */
    private fun forget() {
        val old = current
        val link = deathLink
        current = null
        deathLink = null
        if (old != null && link != null) runCatching { old.asBinder().unlinkToDeath(link, 0) }
    }

    private suspend fun connect(): IEtherTetherService {
        val connected = CompletableDeferred<IBinder>()
        try {
            binder.bind(onConnected = { connected.complete(it) }, onDisconnected = { current = null })
        } catch (e: RuntimeException) {
            throw EngineUnavailable(UnavailableReason.BIND_FAILED, e.message.orEmpty())
        }
        val binderObject = withTimeoutOrNull(bindTimeoutMs) { connected.await() }
            ?: throw EngineUnavailable(UnavailableReason.BIND_TIMEOUT, "no connection after $bindTimeoutMs ms")
        return asInterface(binderObject)
    }

    private fun checkIdentity(service: IEtherTetherService) {
        try {
            val uid = service.uid()
            if (uid != KnownUids.SHELL) throw EngineUnavailable(UnavailableReason.WRONG_UID, "uid $uid")
            val protocol = service.protocolVersion()
            if (protocol != EngineCodec.PROTOCOL_VERSION) {
                // Shizuku replaces a service whose version differs on the next bind; a mismatch here is a bug.
                runCatching { binder.unbind() }
                throw EngineUnavailable(UnavailableReason.PROTOCOL_MISMATCH, "service $protocol, app ${EngineCodec.PROTOCOL_VERSION}")
            }
        } catch (e: android.os.RemoteException) {
            throw EngineUnavailable(UnavailableReason.DIED, e.message.orEmpty())
        }
    }

    private fun watchDeath(service: IEtherTetherService) {
        val death = IBinder.DeathRecipient { if (current === service) current = null }
        deathLink = death
        try {
            service.asBinder().linkToDeath(death, 0)
        } catch (e: android.os.RemoteException) {
            throw EngineUnavailable(UnavailableReason.DIED, e.message.orEmpty())
        }
    }
}
