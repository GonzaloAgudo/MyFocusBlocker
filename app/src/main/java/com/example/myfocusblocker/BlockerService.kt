package com.example.myfocusblocker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import java.util.*

class BlockerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var blockView: View? = null
    private var isBlockViewShowing = false

    private var lastEscapeTime: Long = 0

    // Variable para detectar el cambio de estado (De bloqueado a libre)
    private var wasBlockingPreviously = false

    private val checkRunnable = object : Runnable {
        override fun run() {
            val currentlyBlocking = isBlockingTime()

            checkCurrentApp()
            checkForEndOfShift(currentlyBlocking) // <--- NUEVA LÓGICA

            // Ajuste de tiempos (1s día, 5min noche)
            if (currentlyBlocking) {
                handler.postDelayed(this, 1000)
            } else {
                handler.postDelayed(this, 300000)
            }
        }
    }

    // --- NUEVA FUNCIÓN: Detectar fin de bloqueo y mostrar resumen ---
    private fun checkForEndOfShift(isBlockingNow: Boolean) {
        // Si antes estaba bloqueando (True) y ahora ya no (False) -> SE ACABÓ EL TIEMPO
        if (wasBlockingPreviously && !isBlockingNow) {
            sendSummaryNotification()
        }
        wasBlockingPreviously = isBlockingNow
    }

    private fun sendSummaryNotification() {
        if (NotificationStore.savedList.isEmpty()) return

        val summaryText = NotificationStore.getSummaryText()
        val count = NotificationStore.savedList.size

        val channelId = "BlockerSummary"
        val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Resumen Diario", NotificationManager.IMPORTANCE_DEFAULT)
            mgr.createNotificationChannel(channel)
        }

        // Al pulsar la notif vamos a la app
        val intent = Intent(this, MainActivity::class.java)
        val pIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle("🎉 ¡Jornada terminada!")
            .setContentText("Se bloquearon $count notificaciones.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Resumen: $summaryText"))
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentIntent(pIntent)
            .setAutoCancel(true)
            .build()

        mgr.notify(200, notif)

        // Limpiamos el almacén
        NotificationStore.clear()
    }
    // -------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundServiceNotification()

        // Inicializamos el estado actual
        wasBlockingPreviously = isBlockingTime()
        handler.post(checkRunnable)
    }

    private fun startForegroundServiceNotification() {
        val channelId = "BlockerChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Servicio Bloqueo", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Modo Enfoque Activo")
            .setContentText("Protegiendo tu tiempo...")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .build()

        startForeground(1, notification)
    }

    private fun checkCurrentApp() {
        if (System.currentTimeMillis() - lastEscapeTime < 2000) return

        if (!isBlockingTime()) {
            removeBlockOverlay()
            return
        }

        val prefs = getSharedPreferences("BlockerPrefs", MODE_PRIVATE)
        val blockedSet = prefs.getStringSet("blocked_packages", emptySet()) ?: emptySet()
        val topPackage = getTopPackageName()

        if (isLauncher(topPackage)) {
            removeBlockOverlay()
            return
        }

        if (blockedSet.contains(topPackage)) {
            showBlockOverlay()
        } else if (topPackage == "com.android.systemui") {
            // Sticky logic
        } else {
            removeBlockOverlay()
        }
    }

    private fun isLauncher(packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == packageName
    }

    private fun isBlockingTime(): Boolean {
        val prefs = getSharedPreferences("BlockerPrefs", MODE_PRIVATE)
        val startH = prefs.getInt("start_hour", 8)
        val startM = prefs.getInt("start_min", 0)
        val endH = prefs.getInt("end_hour", 21)
        val endM = prefs.getInt("end_min", 0)

        val now = Calendar.getInstance()
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val startMinutes = startH * 60 + startM
        val endMinutes = endH * 60 + endM

        return if (startMinutes <= endMinutes) {
            nowMinutes in startMinutes..endMinutes
        } else {
            nowMinutes >= startMinutes || nowMinutes <= endMinutes
        }
    }

    private fun getTopPackageName(): String {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 60, time)

        if (stats != null && stats.isNotEmpty()) {
            val sortedStats = stats.sortedByDescending { it.lastTimeUsed }
            return sortedStats[0].packageName
        }
        return ""
    }

    private fun showBlockOverlay() {
        if (isBlockViewShowing) return

        blockView = LayoutInflater.from(this).inflate(R.layout.window_blocker, null)
        val btnEscape = blockView?.findViewById<View>(R.id.btnEscape)

        btnEscape?.setOnClickListener {
            lastEscapeTime = System.currentTimeMillis()
            removeBlockOverlay()
            val homeIntent = Intent(Intent.ACTION_MAIN)
            homeIntent.addCategory(Intent.CATEGORY_HOME)
            homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(homeIntent)
        }

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        params.gravity = Gravity.CENTER

        try {
            windowManager?.addView(blockView, params)
            isBlockViewShowing = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeBlockOverlay() {
        if (isBlockViewShowing && blockView != null) {
            try {
                windowManager?.removeView(blockView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isBlockViewShowing = false
            blockView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeBlockOverlay()
        handler.removeCallbacks(checkRunnable)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}