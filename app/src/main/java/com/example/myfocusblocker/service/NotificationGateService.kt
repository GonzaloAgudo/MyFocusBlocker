package com.example.myfocusblocker.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.myfocusblocker.data.AppRepository
import com.example.myfocusblocker.engine.BlockEngine

/**
 * Retiene las notificaciones de las apps bloqueadas y deja que el sistema las entregue cuando
 * acabe el bloqueo.
 *
 * Usa [snoozeNotification], así que la notificación no se pierde: Android la vuelve a publicar
 * al vencer el plazo. Si para entonces el bloqueo sigue activo, se vuelve a posponer.
 */
class NotificationGateService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        AppRepository.init(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val state = AppRepository.live
        if (!state.deferNotifications) return

        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return                 // nunca las nuestras
        if (pkg !in state.managedApps) return
        if (sbn.isOngoing) return                      // reproductores, llamadas, etc.

        val ctx = ContextHolder.snapshot()
        if (!BlockEngine.evaluate(pkg, ctx, state).blocked) return

        // Si no se puede saber cuándo acaba (wifi/ubicación), se reintenta cada poco.
        val minutes = BlockEngine.minutesUntilUnblocked(pkg, ctx, state) ?: DEFAULT_RETRY_MINUTES
        val capped = minutes.coerceIn(1, MAX_SNOOZE_MINUTES)
        runCatching { snoozeNotification(sbn.key, capped * 60_000L) }
    }

    companion object {
        private const val DEFAULT_RETRY_MINUTES = 30
        /** Tope por seguridad: si algo va mal, no retenemos una notificación días enteros. */
        private const val MAX_SNOOZE_MINUTES = 12 * 60
    }
}
