package com.example.myfocusblocker.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.myfocusblocker.R
import com.example.myfocusblocker.data.AppRepository
import com.example.myfocusblocker.data.InstalledApps
import com.example.myfocusblocker.util.WifiUtils
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Servicio en primer plano que mantiene vivo el bloqueador y actualiza el contexto
 * (SSID de WiFi, ubicación, uso de hoy) cada pocos segundos, reevaluando la app en primer plano.
 */
class MonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var fused: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null

    private val tick = object : Runnable {
        override fun run() {
            refreshContext()
            AppRepository.applyDuePending()
            FocusAccessibilityService.instance?.reevaluateForeground()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AppRepository.init(applicationContext)
        startAsForeground()
        startLocationUpdates()
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        locationCallback?.let { fused?.removeLocationUpdates(it) }
        super.onDestroy()
    }

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Bloqueo activo", NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) }
            nm.createNotificationChannel(channel)
        }
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MyFocusBlocker activo")
            .setContentText("Vigilando tus bloqueos")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        var type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
        if (hasLocationPermission() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        ServiceCompat.startForeground(this, NOTIF_ID, notif, type)
    }

    private fun refreshContext() {
        // SSID de la WiFi conectada.
        ContextHolder.ssid = runCatching { WifiUtils.currentSsid(this) }.getOrNull()
        // Uso de hoy (para límites diarios).
        ContextHolder.usageTodayMinutes = runCatching {
            InstalledApps.usageTodayMinutes(this)
        }.getOrDefault(emptyMap())
    }

    private fun startLocationUpdates() {
        if (!hasLocationPermission()) return
        fused = LocationServices.getFusedLocationProviderClient(this)
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 60_000L)
            .setMinUpdateIntervalMillis(30_000L)
            .build()
        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    ContextHolder.lat = it.latitude
                    ContextHolder.lon = it.longitude
                }
            }
        }
        locationCallback = cb
        runCatching {
            fused?.requestLocationUpdates(request, cb, Looper.getMainLooper())
            fused?.lastLocation?.addOnSuccessListener {
                if (it != null) {
                    ContextHolder.lat = it.latitude
                    ContextHolder.lon = it.longitude
                }
            }
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val CHANNEL_ID = "focus_monitor"
        private const val NOTIF_ID = 1001
        private const val TICK_MS = 15_000L

        fun start(context: Context) {
            val intent = Intent(context, MonitorService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
