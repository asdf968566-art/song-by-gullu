package com.dxdepartment.screenmonitor.device

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.dxdepartment.screenmonitor.settings.AppSettings
import java.util.UUID

/** A stable id per install (kept until the app is uninstalled) and a human name for the phone. */
object DeviceIdentity {
    private const val FILE = "dx_identity"

    fun id(ctx: Context): String {
        val p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        p.getString("device_id", null)?.let { return it }
        val hex = UUID.randomUUID().toString().replace("-", "").uppercase()
        val id = "DX-${hex.substring(0, 4)}-${hex.substring(4, 8)}-${hex.substring(8, 12)}"
        p.edit().putString("device_id", id).apply()
        return id
    }

    /** The label set in the app (e.g. "Pixel 01"), else the name set in Android, else the model. */
    fun name(ctx: Context): String {
        AppSettings.get(ctx).deviceLabel.takeIf { it.isNotBlank() }?.let { return it }
        return systemName(ctx)
    }

    fun systemName(ctx: Context): String =
        runCatching { Settings.Global.getString(ctx.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

    val model: String get() = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
}
