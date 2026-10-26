// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.tether

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import io.github.oytuncagil.ethertether.data.EventLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Reads the status again after a USB device is plugged in or out. While tethering is off,
 * Android sends no tethering event for an adapter, so without this the home screen kept saying
 * "No Ethernet adapter found" or kept an adapter that was gone (phone test 2026-10-09).
 * The kernel names the interface a moment after the USB event, so it reads at each of
 * [delaysMs] after the latest event, then stops: nothing runs between USB events.
 */
class AdapterChanges(
    private val scope: CoroutineScope,
    private val log: EventLog,
    private val delaysMs: List<Long> = listOf(1_500, 4_000),
    private val refresh: suspend () -> Unit,
) {
    private var job: Job? = null

    /** Called for every USB attach or detach. */
    @Synchronized
    fun onUsbChange(attached: Boolean) {
        log.info(TAG, if (attached) "usb device attached" else "usb device detached")
        job?.cancel()
        job = scope.launch {
            var waited = 0L
            for (at in delaysMs) {
                delay(at - waited)
                waited = at
                refresh()
            }
        }
    }

    /** Listens for USB attach and detach broadcasts, which Android also sends to running apps. */
    fun register(context: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> onUsbChange(attached = true)
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> onUsbChange(attached = false)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        // Sent by the system, which reaches a receiver that other apps cannot.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private companion object {
        const val TAG = "adapter"
    }
}
