package com.example.myfocusblocker.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

/**
 * Lectura del SSID de la WiFi conectada.
 *
 * Android 10+ solo devuelve el SSID si la app tiene permiso de ubicación Y el servicio de
 * ubicación del sistema está encendido; si no, devuelve "<unknown ssid>".
 */
object WifiUtils {

    /** Motivo por el que no se puede leer el SSID (para avisar al usuario con precisión). */
    enum class SsidProblem { NONE, LOCATION_OFF, NO_PERMISSION, NOT_ON_WIFI }

    fun currentSsid(context: Context): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        val network = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(network) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null

        // API moderna (API 29+): el WifiInfo viaja en las capacidades de la red.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val info = caps.transportInfo as? WifiInfo
            sanitize(info?.ssid)?.let { return it }
        }
        // Respaldo con la API antigua.
        @Suppress("DEPRECATION")
        val legacy = runCatching {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wm?.connectionInfo?.ssid
        }.getOrNull()
        return sanitize(legacy)
    }

    /** Diagnostica por qué no hay SSID, para dar un mensaje útil. */
    fun diagnose(context: Context): SsidProblem {
        if (!hasLocationPermission(context)) return SsidProblem.NO_PERMISSION
        if (!locationServicesEnabled(context)) return SsidProblem.LOCATION_OFF
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) {
            return SsidProblem.NOT_ON_WIFI
        }
        return SsidProblem.NONE
    }

    fun message(problem: SsidProblem): String = when (problem) {
        SsidProblem.LOCATION_OFF ->
            "Activa la Ubicación del teléfono: Android la exige para leer el nombre de la WiFi."
        SsidProblem.NO_PERMISSION -> "Falta el permiso de ubicación de la app."
        SsidProblem.NOT_ON_WIFI -> "No estás conectado a ninguna WiFi."
        SsidProblem.NONE -> "No se pudo leer el nombre de la red."
    }

    fun locationServicesEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return runCatching { LocationManagerCompat.isLocationEnabled(lm) }.getOrDefault(false)
    }

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun sanitize(raw: String?): String? {
        val s = raw?.removeSurrounding("\"")?.trim() ?: return null
        return if (s.isBlank() || s == "<unknown ssid>" || s == "0x") null else s
    }
}
