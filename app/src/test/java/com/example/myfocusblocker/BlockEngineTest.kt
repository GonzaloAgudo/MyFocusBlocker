package com.example.myfocusblocker

import com.example.myfocusblocker.data.AppState
import com.example.myfocusblocker.data.Rule
import com.example.myfocusblocker.data.RuleAction
import com.example.myfocusblocker.data.RuleType
import com.example.myfocusblocker.engine.BlockEngine
import com.example.myfocusblocker.engine.EnvContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockEngineTest {

    private val app = "com.test.app"

    private fun ctx(
        minute: Int = 10 * 60,
        day: Int = 3,
        ssid: String? = null,
        lat: Double? = null,
        lon: Double? = null,
        usage: Map<String, Int> = emptyMap(),
    ) = EnvContext(minute, day, ssid, lat, lon, usage)

    private fun state(vararg rules: Rule, managed: Set<String> = setOf(app)) =
        AppState(managedApps = managed, rules = rules.toList())

    @Test fun `horario bloquea dentro del rango`() {
        val s = state(Rule(type = RuleType.SCHEDULE, action = RuleAction.BLOCK, startMinute = 480, endMinute = 1200))
        assertTrue(BlockEngine.evaluate(app, ctx(minute = 600), s).blocked)   // 10:00
        assertFalse(BlockEngine.evaluate(app, ctx(minute = 1260), s).blocked) // 21:00
    }

    @Test fun `permitir por wifi con mas prioridad anula el bloqueo por horario`() {
        val block = Rule(type = RuleType.SCHEDULE, action = RuleAction.BLOCK, priority = 1, startMinute = 480, endMinute = 1200)
        val allow = Rule(type = RuleType.WIFI, action = RuleAction.ALLOW, priority = 2, ssids = setOf("novia"))
        val s = state(block, allow)
        // En la wifi de la novia -> permitido (gana el ALLOW de prioridad 2)
        assertFalse(BlockEngine.evaluate(app, ctx(minute = 600, ssid = "novia"), s).blocked)
        // En otra wifi -> bloqueado por el horario
        assertTrue(BlockEngine.evaluate(app, ctx(minute = 600, ssid = "casa"), s).blocked)
        // Sin wifi -> bloqueado
        assertTrue(BlockEngine.evaluate(app, ctx(minute = 600, ssid = null), s).blocked)
    }

    @Test fun `empate de prioridad gana BLOCK`() {
        val block = Rule(type = RuleType.SCHEDULE, action = RuleAction.BLOCK, priority = 5, startMinute = 0, endMinute = 1440)
        val allow = Rule(type = RuleType.WIFI, action = RuleAction.ALLOW, priority = 5, ssids = setOf("x"))
        val s = state(block, allow)
        assertTrue(BlockEngine.evaluate(app, ctx(ssid = "x"), s).blocked)
    }

    @Test fun `horario nocturno cruza medianoche`() {
        val s = state(Rule(type = RuleType.SCHEDULE, action = RuleAction.BLOCK, startMinute = 1320, endMinute = 360)) // 22:00-06:00
        assertTrue(BlockEngine.evaluate(app, ctx(minute = 1380), s).blocked) // 23:00
        assertTrue(BlockEngine.evaluate(app, ctx(minute = 300), s).blocked)  // 05:00
        assertFalse(BlockEngine.evaluate(app, ctx(minute = 720), s).blocked) // 12:00
    }

    @Test fun `limite diario bloquea al superar el uso`() {
        val s = state(Rule(type = RuleType.DAILY_LIMIT, action = RuleAction.BLOCK, dailyLimitMinutes = 60))
        assertTrue(BlockEngine.evaluate(app, ctx(usage = mapOf(app to 70)), s).blocked)
        assertFalse(BlockEngine.evaluate(app, ctx(usage = mapOf(app to 30)), s).blocked)
    }

    @Test fun `app no gestionada nunca se bloquea`() {
        val s = state(Rule(type = RuleType.SCHEDULE, action = RuleAction.BLOCK, startMinute = 0, endMinute = 1440))
        assertFalse(BlockEngine.evaluate("com.otra.app", ctx(), s).blocked)
    }

    @Test fun `regla dirigida a apps concretas no afecta a otras`() {
        val s = state(
            Rule(type = RuleType.SCHEDULE, action = RuleAction.BLOCK, allApps = false, apps = setOf("com.otra"), startMinute = 0, endMinute = 1440),
            managed = setOf(app, "com.otra"),
        )
        assertFalse(BlockEngine.evaluate(app, ctx(), s).blocked)
        assertTrue(BlockEngine.evaluate("com.otra", ctx(), s).blocked)
    }

    @Test fun `dias de la semana limitan el horario`() {
        val s = state(Rule(type = RuleType.SCHEDULE, action = RuleAction.BLOCK, startMinute = 0, endMinute = 1440, days = setOf(1, 2, 3, 4, 5)))
        assertTrue(BlockEngine.evaluate(app, ctx(day = 3), s).blocked)  // miércoles
        assertFalse(BlockEngine.evaluate(app, ctx(day = 7), s).blocked) // domingo
    }

    @Test fun `calcula cuanto falta para desbloquear`() {
        // Horario 08:00-20:00, ahora las 10:00 -> faltan 10 h
        val horario = state(Rule(type = RuleType.SCHEDULE, action = RuleAction.BLOCK, startMinute = 480, endMinute = 1200))
        assertEquals(600, BlockEngine.minutesUntilUnblocked(app, ctx(minute = 600), horario))

        // Horario nocturno 22:00-06:00, ahora las 23:00 -> faltan 7 h
        val noche = state(Rule(type = RuleType.SCHEDULE, action = RuleAction.BLOCK, startMinute = 1320, endMinute = 360))
        assertEquals(420, BlockEngine.minutesUntilUnblocked(app, ctx(minute = 1380), noche))

        // Límite diario: hasta medianoche
        val limite = state(Rule(type = RuleType.DAILY_LIMIT, action = RuleAction.BLOCK, dailyLimitMinutes = 60))
        assertEquals(
            1440 - 600,
            BlockEngine.minutesUntilUnblocked(app, ctx(minute = 600, usage = mapOf(app to 70)), limite),
        )

        // Si no está bloqueada, 0
        assertEquals(0, BlockEngine.minutesUntilUnblocked(app, ctx(minute = 1260), horario))

        // Con WiFi no se puede saber: null
        val wifi = state(Rule(type = RuleType.WIFI, action = RuleAction.BLOCK, ssids = setOf("casa")))
        assertNull(BlockEngine.minutesUntilUnblocked(app, ctx(ssid = "casa"), wifi))
    }

    @Test fun `ubicacion bloquea dentro del radio`() {
        val s = state(Rule(type = RuleType.LOCATION, action = RuleAction.BLOCK, lat = 40.4168, lon = -3.7038, radiusMeters = 200.0))
        // Mismo punto -> dentro
        assertTrue(BlockEngine.evaluate(app, ctx(lat = 40.4168, lon = -3.7038), s).blocked)
        // ~1.5 km al norte -> fuera
        assertFalse(BlockEngine.evaluate(app, ctx(lat = 40.4300, lon = -3.7038), s).blocked)
    }
}
