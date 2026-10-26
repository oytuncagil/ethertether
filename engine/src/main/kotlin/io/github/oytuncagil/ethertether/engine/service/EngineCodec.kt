// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.engine.service

import android.os.Bundle
import io.github.oytuncagil.ethertether.engine.EngineNote
import io.github.oytuncagil.ethertether.engine.EngineReport
import io.github.oytuncagil.ethertether.engine.EngineStatus
import io.github.oytuncagil.ethertether.engine.EngineStep
import io.github.oytuncagil.ethertether.engine.FailureReason
import io.github.oytuncagil.ethertether.engine.InterfaceInfo
import io.github.oytuncagil.ethertether.engine.Outcome
import io.github.oytuncagil.ethertether.engine.ServingScope
import io.github.oytuncagil.ethertether.model.SharingScope
import io.github.oytuncagil.ethertether.model.TetherInput
import io.github.oytuncagil.ethertether.model.TetherMode

/**
 * Writes and reads the Bundles that cross [IEtherTetherService]. A request travels as the raw
 * [TetherInput], so the service validates it again on its side. Unknown enum names read back
 * as failures instead of crashing, in case app and service ever differ.
 */
object EngineCodec {
    /** Raised whenever a Bundle layout changes; the app replaces a service with another version. */
    const val PROTOCOL_VERSION = 2

    /** The request Bundle for [input]. */
    fun encodeInput(input: TetherInput): Bundle = Bundle().apply {
        putString(K_MODE, input.mode.name)
        putString(K_PHONE, input.phoneAddress)
        putString(K_CLIENT, input.clientAddress)
        putString(K_PREFIX, input.prefixLength)
        putString(K_SCOPE, input.scope.name)
        putBoolean(K_FALLBACK, input.allowScopeFallback)
    }

    /** The [TetherInput] in [bundle]; null when the Bundle is not a request. */
    fun decodeInput(bundle: Bundle): TetherInput? {
        val mode = enumOrNull<TetherMode>(bundle.getString(K_MODE)) ?: return null
        val scope = enumOrNull<SharingScope>(bundle.getString(K_SCOPE)) ?: return null
        return TetherInput(
            mode = mode,
            phoneAddress = bundle.getString(K_PHONE).orEmpty(),
            clientAddress = bundle.getString(K_CLIENT).orEmpty(),
            prefixLength = bundle.getString(K_PREFIX).orEmpty(),
            scope = scope,
            allowScopeFallback = bundle.getBoolean(K_FALLBACK),
        )
    }

    /** The Bundle for [report]. */
    fun encodeReport(report: EngineReport): Bundle = Bundle().apply {
        putBundle(K_OUTCOME, encodeOutcome(report.outcome))
        putStringArray(K_NOTES, report.notes.map { it.name }.toTypedArray())
        putAll(encodeSteps(report.steps))
    }

    /** The [EngineReport] in [bundle]. */
    fun decodeReport(bundle: Bundle): EngineReport = EngineReport(
        outcome = bundle.getBundle(K_OUTCOME)?.let(::decodeOutcome) ?: malformed(K_OUTCOME),
        notes = bundle.getStringArray(K_NOTES).orEmpty().mapNotNull { enumOrNull<EngineNote>(it) }.toSet(),
        steps = decodeSteps(bundle),
    )

    /** The Bundle for raw [steps]. */
    fun encodeSteps(steps: List<EngineStep>): Bundle = Bundle().apply {
        putStringArray(K_STEP_LABELS, steps.map { it.label }.toTypedArray())
        putStringArray(K_STEP_DETAILS, steps.map { it.detail }.toTypedArray())
    }

    /** The raw steps in [bundle]. */
    fun decodeSteps(bundle: Bundle): List<EngineStep> {
        val labels = bundle.getStringArray(K_STEP_LABELS).orEmpty()
        val details = bundle.getStringArray(K_STEP_DETAILS).orEmpty()
        return labels.mapIndexed { index, label -> EngineStep(label, details.getOrElse(index) { "" }) }
    }

    /** The Bundle for [status]. */
    fun encodeStatus(status: EngineStatus): Bundle = Bundle().apply {
        putInt(K_SDK, status.sdkInt)
        putBoolean(K_EVENTS, status.eventsAvailable)
        status.serving?.let { (iface, scope) -> putString(K_SERVING_IFACE, iface); putString(K_SCOPE, scope.name) }
        status.error?.let { (iface, code) -> putString(K_ERROR_IFACE, iface); putInt(K_CODE, code) }
        putString(K_AVAILABLE, status.available)
        putBundle(K_INTERFACES, encodeList(status.interfaces.map(::encodeInterface)))
        putStringArray(K_CLIENTS, status.clients.toTypedArray())
    }

    /** The [EngineStatus] in [bundle]. */
    fun decodeStatus(bundle: Bundle): EngineStatus {
        val servingIface = bundle.getString(K_SERVING_IFACE)
        val servingScope = enumOrNull<ServingScope>(bundle.getString(K_SCOPE))
        val errorIface = bundle.getString(K_ERROR_IFACE)
        return EngineStatus(
            sdkInt = bundle.getInt(K_SDK),
            eventsAvailable = bundle.getBoolean(K_EVENTS),
            serving = if (servingIface != null && servingScope != null) servingIface to servingScope else null,
            error = errorIface?.let { it to bundle.getInt(K_CODE) },
            available = bundle.getString(K_AVAILABLE),
            interfaces = decodeList(bundle.getBundle(K_INTERFACES)).map(::decodeInterface),
            clients = bundle.getStringArray(K_CLIENTS).orEmpty().toList(),
        )
    }

    private fun encodeOutcome(outcome: Outcome): Bundle = Bundle().apply {
        when (outcome) {
            is Outcome.Serving -> {
                putString(K_KIND, KIND_SERVING)
                putString(K_SERVING_IFACE, outcome.iface)
                putString(K_SCOPE, outcome.scope.name)
                putStringArray(K_IPV4, outcome.ipv4.toTypedArray())
            }
            Outcome.Waiting -> putString(K_KIND, KIND_WAITING)
            Outcome.Stopped -> putString(K_KIND, KIND_STOPPED)
            is Outcome.Failed -> {
                putString(K_KIND, KIND_FAILED)
                putString(K_REASON, outcome.reason.name)
                outcome.code?.let { putInt(K_CODE, it) }
                putString(K_DETAIL, outcome.detail)
            }
        }
    }

    private fun decodeOutcome(bundle: Bundle): Outcome = when (bundle.getString(K_KIND)) {
        KIND_SERVING -> Outcome.Serving(
            iface = bundle.getString(K_SERVING_IFACE).orEmpty(),
            scope = enumOrNull<ServingScope>(bundle.getString(K_SCOPE)) ?: malformed(K_SCOPE),
            ipv4 = bundle.getStringArray(K_IPV4).orEmpty().toList(),
        )
        KIND_WAITING -> Outcome.Waiting
        KIND_STOPPED -> Outcome.Stopped
        KIND_FAILED -> Outcome.Failed(
            reason = enumOrNull<FailureReason>(bundle.getString(K_REASON)) ?: FailureReason.UNEXPECTED,
            code = if (bundle.containsKey(K_CODE)) bundle.getInt(K_CODE) else null,
            detail = bundle.getString(K_DETAIL).orEmpty(),
        )
        else -> malformed(K_KIND)
    }

    private fun encodeInterface(info: InterfaceInfo): Bundle = Bundle().apply {
        putString(K_NAME, info.name)
        putBoolean(K_UP, info.isUp)
        info.hasCarrier?.let { putBoolean(K_CARRIER, it) }
        info.speedMbps?.let { putInt(K_SPEED, it) }
        putStringArray(K_IPV4, info.ipv4.toTypedArray())
    }

    private fun decodeInterface(bundle: Bundle): InterfaceInfo = InterfaceInfo(
        name = bundle.getString(K_NAME).orEmpty(),
        isUp = bundle.getBoolean(K_UP),
        hasCarrier = if (bundle.containsKey(K_CARRIER)) bundle.getBoolean(K_CARRIER) else null,
        speedMbps = if (bundle.containsKey(K_SPEED)) bundle.getInt(K_SPEED) else null,
        ipv4 = bundle.getStringArray(K_IPV4).orEmpty().toList(),
    )

    /** A list as one Bundle with the entries under "0", "1", ... (no Parcelable lists needed). */
    private fun encodeList(items: List<Bundle>): Bundle = Bundle().apply {
        putInt(K_COUNT, items.size)
        items.forEachIndexed { index, item -> putBundle(index.toString(), item) }
    }

    private fun decodeList(bundle: Bundle?): List<Bundle> {
        if (bundle == null) return emptyList()
        return (0 until bundle.getInt(K_COUNT)).mapNotNull { bundle.getBundle(it.toString()) }
    }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
        enumValues<T>().firstOrNull { it.name == name }

    private fun malformed(key: String): Nothing = throw IllegalArgumentException("Malformed engine Bundle: $key")

    private const val K_MODE = "mode"
    private const val K_PHONE = "phone"
    private const val K_CLIENT = "client"
    private const val K_PREFIX = "prefix"
    private const val K_SCOPE = "scope"
    private const val K_FALLBACK = "fallback"
    private const val K_OUTCOME = "outcome"
    private const val K_NOTES = "notes"
    private const val K_STEP_LABELS = "stepLabels"
    private const val K_STEP_DETAILS = "stepDetails"
    private const val K_KIND = "kind"
    private const val K_REASON = "reason"
    private const val K_CODE = "code"
    private const val K_DETAIL = "detail"
    private const val K_IPV4 = "ipv4"
    private const val K_SDK = "sdk"
    private const val K_EVENTS = "events"
    private const val K_SERVING_IFACE = "servingIface"
    private const val K_ERROR_IFACE = "errorIface"
    private const val K_AVAILABLE = "available"
    private const val K_INTERFACES = "interfaces"
    private const val K_COUNT = "count"
    private const val K_CLIENTS = "clients"
    private const val K_NAME = "name"
    private const val K_UP = "up"
    private const val K_CARRIER = "carrier"
    private const val K_SPEED = "speed"
    private const val KIND_SERVING = "serving"
    private const val KIND_WAITING = "waiting"
    private const val KIND_STOPPED = "stopped"
    private const val KIND_FAILED = "failed"
}
