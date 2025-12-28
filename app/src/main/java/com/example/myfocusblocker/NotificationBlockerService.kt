package com.example.myfocusblocker

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.*

class NotificationBlockerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName

        // EVITAR BUCLES: No bloquearnos a nosotros mismos ni al sistema android
        if (packageName == this.packageName || packageName == "android" || packageName == "com.android.systemui") return

        // Si la app está en lista negra Y es hora de bloquear
        if (shouldBlock(packageName)) {

            // 1. Extraer datos
            val extras = sbn.notification.extras
            val title = extras.getString(Notification.EXTRA_TITLE) ?: "Notificación"
            val text = extras.getString(Notification.EXTRA_TEXT) ?: ""

            // 2. Guardar en nuestro almacén
            NotificationStore.add(SavedNotification(title, text, packageName, System.currentTimeMillis()))

            // 3. CANCELAR (Eliminar) la notificación del sistema
            cancelNotification(sbn.key)
        }
    }

    private fun shouldBlock(pkg: String): Boolean {
        val prefs = getSharedPreferences("BlockerPrefs", MODE_PRIVATE)
        val blockedSet = prefs.getStringSet("blocked_packages", emptySet()) ?: emptySet()

        return blockedSet.contains(pkg) && isBlockingTime(prefs)
    }

    private fun isBlockingTime(prefs: android.content.SharedPreferences): Boolean {
        val startH = prefs.getInt("start_hour", 8)
        val startM = prefs.getInt("start_min", 0)
        val endH = prefs.getInt("end_hour", 21)
        val endM = prefs.getInt("end_min", 0)

        val now = Calendar.getInstance()
        val currentH = now.get(Calendar.HOUR_OF_DAY)
        val currentM = now.get(Calendar.MINUTE)

        val nowMinutes = currentH * 60 + currentM
        val startMinutes = startH * 60 + startM
        val endMinutes = endH * 60 + endM

        return if (startMinutes <= endMinutes) {
            nowMinutes in startMinutes..endMinutes
        } else {
            nowMinutes >= startMinutes || nowMinutes <= endMinutes
        }
    }
}