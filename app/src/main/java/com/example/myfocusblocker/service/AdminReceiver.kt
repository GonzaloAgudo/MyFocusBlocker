package com.example.myfocusblocker.service

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context

/**
 * Al estar activo como administrador de dispositivos, Android impide desinstalar la app
 * hasta desactivarlo. La desactivación pasa por una pantalla del sistema que el servicio
 * de accesibilidad puede vigilar.
 */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: android.content.Intent): CharSequence {
        return "Si desactivas el administrador podrás desinstalar la app y saltarte tus bloqueos."
    }

    companion object {
        fun component(context: Context) = ComponentName(context, AdminReceiver::class.java)
    }
}
