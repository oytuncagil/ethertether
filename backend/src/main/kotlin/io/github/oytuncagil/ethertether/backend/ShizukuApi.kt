// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.backend

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import rikka.sui.Sui

/** The part of the Shizuku API EtherTether uses. Faked in unit tests. */
interface ShizukuApi {
    /** True when a Shizuku-API binder (Shizuku or Sui) is alive. */
    fun pingBinder(): Boolean

    /** True when the binder belongs to Sui rather than the Shizuku app. */
    fun isSui(): Boolean

    /** True when the server predates Shizuku 11, which this app does not support. */
    fun isPreV11(): Boolean

    /** The server's API version, or -1 when unknown. */
    fun version(): Int

    /** True when the user granted EtherTether access in Shizuku or Sui. */
    fun isPermissionGranted(): Boolean

    /** True when the user denied access and chose not to be asked again. */
    fun isPermissionDeniedForGood(): Boolean

    /** Shows Shizuku's permission dialog; [onResult] receives true when access was granted. */
    fun requestPermission(onResult: (Boolean) -> Unit)

    /** The uid the server runs as: 2000 for shell, 0 for root. */
    fun uid(): Int
}

/** [ShizukuApi] backed by the real Shizuku library. */
object RikkaShizukuApi : ShizukuApi {
    private const val REQUEST_CODE = 0x45_54

    override fun pingBinder(): Boolean = Shizuku.pingBinder()

    override fun isSui(): Boolean = Sui.isSui()

    override fun isPreV11(): Boolean = Shizuku.isPreV11()

    override fun version(): Int = Shizuku.getVersion()

    override fun isPermissionGranted(): Boolean =
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    override fun isPermissionDeniedForGood(): Boolean = Shizuku.shouldShowRequestPermissionRationale()

    override fun requestPermission(onResult: (Boolean) -> Unit) {
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode != REQUEST_CODE) return
                Shizuku.removeRequestPermissionResultListener(this)
                onResult(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        Shizuku.requestPermission(REQUEST_CODE)
    }

    override fun uid(): Int = Shizuku.getUid()
}
