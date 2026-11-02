package com.example.myfocusblocker.data

import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

/** Uso de un día concreto, en minutos por paquete. */
data class DayUsage(
    val dayStartMillis: Long,
    val perApp: Map<String, Int>,
) {
    val total: Int get() = perApp.values.sum()

    fun totalOf(pkgs: Set<String>): Int =
        perApp.entries.filter { it.key in pkgs }.sumOf { it.value }
}

object UsageHistory {

    /**
     * Uso de los últimos [days] días (el último elemento es hoy).
     * Requiere el permiso de acceso al uso; si falta, devuelve la lista vacía.
     */
    fun lastDays(context: Context, days: Int): List<DayUsage> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()
        val now = System.currentTimeMillis()

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -(days - 1))
        }

        val result = ArrayList<DayUsage>(days)
        repeat(days) {
            val start = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
            val end = minOf(cal.timeInMillis, now)
            if (end > start) {
                val stats = runCatching { usm.queryAndAggregateUsageStats(start, end) }
                    .getOrNull().orEmpty()
                val perApp = stats.mapNotNull { (pkg, s) ->
                    val minutes = (s.totalTimeInForeground / 60_000L).toInt()
                    if (minutes > 0) pkg to minutes else null
                }.toMap()
                result.add(DayUsage(start, perApp))
            } else {
                result.add(DayUsage(start, emptyMap()))
            }
        }
        return result
    }
}
