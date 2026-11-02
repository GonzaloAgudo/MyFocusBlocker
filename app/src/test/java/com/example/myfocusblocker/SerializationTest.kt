package com.example.myfocusblocker

import com.example.myfocusblocker.data.AppState
import com.example.myfocusblocker.data.ProtectionSettings
import com.example.myfocusblocker.data.Root
import com.example.myfocusblocker.data.Rule
import com.example.myfocusblocker.data.RuleAction
import com.example.myfocusblocker.data.RuleType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Bloquea el formato de persistencia (mismo Json que usa AppRepository). */
class SerializationTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    @Test fun `ida y vuelta conserva el estado`() {
        val root = Root(
            live = AppState(
                managedApps = setOf("com.instagram.android", "com.twitter.android"),
                rules = listOf(
                    Rule(id = "r1", name = "Estudio", type = RuleType.SCHEDULE, action = RuleAction.BLOCK, priority = 1, startMinute = 480, endMinute = 1200, days = setOf(1, 2, 3, 4, 5)),
                    Rule(id = "r2", name = "Novia", type = RuleType.WIFI, action = RuleAction.ALLOW, priority = 2, ssids = setOf("WIFI_NOVIA")),
                ),
                protection = ProtectionSettings(strictMode = true, delayEnabled = true, delayMinutes = 90),
            ),
            onboarded = true,
        )
        val decoded = json.decodeFromString<Root>(json.encodeToString(root))
        assertEquals(root, decoded)
    }

    @Test fun `decodifica json parcial usando valores por defecto`() {
        val partial = """{"live":{"managedApps":["a"],"rules":[{"id":"x","type":"SCHEDULE","action":"BLOCK"}]}}"""
        val root = json.decodeFromString<Root>(partial)
        assertEquals(setOf("a"), root.live.managedApps)
        assertEquals(1, root.live.rules.size)
        assertEquals(480, root.live.rules[0].startMinute) // valor por defecto
        assertNull(root.desired)
    }
}
