package com.example.myfocusblocker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.myfocusblocker.data.AppRepository

/** Rearranca el servicio de vigilancia tras reiniciar el teléfono. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            AppRepository.init(context.applicationContext)
            MonitorService.start(context.applicationContext)
        }
    }
}
