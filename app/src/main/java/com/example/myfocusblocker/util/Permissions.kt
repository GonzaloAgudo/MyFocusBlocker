package com.example.myfocusblocker.util

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import androidx.core.app.NotificationManagerCompat
import com.example.myfocusblocker.service.AdminReceiver
import com.example.myfocusblocker.service.FocusAccessibilityService

/** Comprobaciones de permisos e intents para abrir cada pantalla de ajustes. */
object Permissions {

    fun accessibilityEnabled(context: Context): Boolean {
        val expected = "${context.packageName}/${FocusAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) return true
        }
        return false
    }

    fun usageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun ignoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun deviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(AdminReceiver.component(context))
    }

    /** Acceso a las notificaciones (para poder retenerlas durante los bloqueos). */
    fun notificationAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    // ---- Intents ----

    fun accessibilityIntent() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun notificationAccessIntent() = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun usageAccessIntent() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun overlayIntent(context: Context) =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

    fun batteryIntent(context: Context) =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    fun deviceAdminIntent(context: Context) =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, AdminReceiver.component(context))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Actívalo para que no puedas desinstalar la app y saltarte tus bloqueos.",
            )
        }

    fun removeDeviceAdmin(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        runCatching { dpm.removeActiveAdmin(AdminReceiver.component(context)) }
    }
}
