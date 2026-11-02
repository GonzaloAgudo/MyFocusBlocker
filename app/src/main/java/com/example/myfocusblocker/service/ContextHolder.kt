package com.example.myfocusblocker.service

import com.example.myfocusblocker.engine.EnvContext
import java.util.Calendar

/**
 * Estado del entorno compartido entre servicios. [MonitorService] lo mantiene al día
 * (SSID, ubicación, uso de hoy) y tanto el servicio de accesibilidad como el repositorio
 * lo leen para evaluar reglas.
 */
object ContextHolder {
    @Volatile var ssid: String? = null
    @Volatile var lat: Double? = null
    @Volatile var lon: Double? = null
    @Volatile var usageTodayMinutes: Map<String, Int> = emptyMap()

    /** Construye el [EnvContext] "ahora" con la hora del sistema y los datos cacheados. */
    fun snapshot(): EnvContext {
        val cal = Calendar.getInstance()
        val minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        // Calendar: 1=domingo … 7=sábado. Lo convertimos a ISO 1=lunes … 7=domingo.
        val iso = ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
        return EnvContext(
            nowMinuteOfDay = minute,
            isoDay = iso,
            ssid = ssid,
            lat = lat,
            lon = lon,
            usageTodayMinutes = usageTodayMinutes,
        )
    }
}
