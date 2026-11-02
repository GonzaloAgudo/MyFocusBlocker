package com.example.myfocusblocker.engine

import com.example.myfocusblocker.data.AppState
import com.example.myfocusblocker.data.Rule
import com.example.myfocusblocker.data.RuleAction
import com.example.myfocusblocker.data.RuleType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Fotografía del entorno "ahora" que el motor necesita para evaluar. */
data class EnvContext(
    val nowMinuteOfDay: Int,
    val isoDay: Int,                 // 1 = lunes … 7 = domingo
    val ssid: String?,               // SSID de la wifi conectada, o null
    val lat: Double?,                // ubicación actual
    val lon: Double?,
    val usageTodayMinutes: Map<String, Int> = emptyMap(), // uso hoy por paquete
)

/** Resultado de evaluar una app. */
data class Decision(
    val blocked: Boolean,
    val winningRule: Rule?,
)

object BlockEngine {

    /** ¿La regla afecta a este paquete? */
    private fun appliesToApp(rule: Rule, pkg: String, managed: Set<String>): Boolean {
        return if (rule.allApps) pkg in managed else pkg in rule.apps
    }

    /** ¿La condición de la regla se cumple ahora mismo para este paquete? */
    private fun conditionActive(rule: Rule, pkg: String, ctx: EnvContext): Boolean {
        return when (rule.type) {
            RuleType.SCHEDULE -> scheduleActive(rule, ctx)
            RuleType.WIFI -> {
                val s = ctx.ssid ?: return false
                rule.ssids.any { it.equals(s, ignoreCase = false) }
            }
            RuleType.LOCATION -> {
                val la = ctx.lat ?: return false
                val lo = ctx.lon ?: return false
                distanceMeters(la, lo, rule.lat, rule.lon) <= rule.radiusMeters
            }
            RuleType.DAILY_LIMIT -> {
                (ctx.usageTodayMinutes[pkg] ?: 0) >= rule.dailyLimitMinutes
            }
        }
    }

    private fun scheduleActive(rule: Rule, ctx: EnvContext): Boolean {
        val dayOk = rule.days.isEmpty() || ctx.isoDay in rule.days
        val minute = ctx.nowMinuteOfDay
        val inRange = if (rule.startMinute <= rule.endMinute) {
            minute in rule.startMinute until rule.endMinute
        } else {
            // Rango que cruza la medianoche (p. ej. 22:00 -> 06:00)
            minute >= rule.startMinute || minute < rule.endMinute
        }
        if (rule.startMinute <= rule.endMinute) {
            return dayOk && inRange
        }
        // En rangos nocturnos, el día se comprueba respecto al momento de inicio.
        val startDay = if (minute >= rule.startMinute) ctx.isoDay else prevDay(ctx.isoDay)
        val dayOkNight = rule.days.isEmpty() || startDay in rule.days
        return dayOkNight && inRange
    }

    private fun prevDay(iso: Int): Int = if (iso == 1) 7 else iso - 1

    /** Evalúa qué debe pasar con [pkg] en el contexto [ctx] según [state]. */
    fun evaluate(pkg: String, ctx: EnvContext, state: AppState): Decision {
        val candidates = state.rules.filter { rule ->
            rule.enabled &&
                appliesToApp(rule, pkg, state.managedApps) &&
                conditionActive(rule, pkg, ctx)
        }
        if (candidates.isEmpty()) return Decision(blocked = false, winningRule = null)

        val maxPriority = candidates.maxOf { it.priority }
        val top = candidates.filter { it.priority == maxPriority }
        // Desempate: gana BLOCK (más seguro).
        val winner = top.firstOrNull { it.action == RuleAction.BLOCK } ?: top.first()
        return Decision(blocked = winner.action == RuleAction.BLOCK, winningRule = winner)
    }

    /**
     * Minutos aproximados hasta que [pkg] deje de estar bloqueada.
     *
     * Con horarios se calcula el final del tramo y con límites diarios, hasta medianoche.
     * Con WiFi/ubicación devuelve null porque depende de dónde estés. Es una estimación:
     * quien la use debe volver a comprobarlo al vencer.
     */
    fun minutesUntilUnblocked(pkg: String, ctx: EnvContext, state: AppState): Int? {
        val decision = evaluate(pkg, ctx, state)
        if (!decision.blocked) return 0
        val rule = decision.winningRule ?: return null
        return when (rule.type) {
            RuleType.SCHEDULE -> {
                val now = ctx.nowMinuteOfDay
                val end = rule.endMinute
                val diff = if (end > now) end - now else (1440 - now) + end
                diff.coerceAtLeast(1)
            }
            RuleType.DAILY_LIMIT -> (1440 - ctx.nowMinuteOfDay).coerceAtLeast(1)
            else -> null
        }
    }

    /** ¿Hay algún bloqueo activo ahora mismo? (para el modo estricto) */
    fun isBlockingPeriodNow(ctx: EnvContext, state: AppState): Boolean {
        return state.managedApps.any { evaluate(it, ctx, state).blocked }
    }

    /** Distancia Haversine en metros. */
    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
