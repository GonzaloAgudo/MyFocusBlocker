package com.example.myfocusblocker.data

import kotlinx.serialization.Serializable
import java.util.UUID

/** Tipo de condición que activa la regla. */
enum class RuleType { SCHEDULE, DAILY_LIMIT, WIFI, LOCATION }

/** Qué hace la regla cuando su condición está activa. */
enum class RuleAction { BLOCK, ALLOW }

/**
 * Una regla del motor de bloqueo.
 *
 * En cada instante se evalúan todas las reglas cuya condición se cumple "ahora" y que
 * afectan a la app en primer plano; gana la de mayor [priority]. Si gana un BLOCK -> se
 * bloquea; si gana un ALLOW -> se permite; si ninguna aplica -> se permite (por defecto).
 */
@Serializable
data class Rule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val type: RuleType = RuleType.SCHEDULE,
    val action: RuleAction = RuleAction.BLOCK,
    /** Mayor número = mayor prioridad. Desempate: gana BLOCK. */
    val priority: Int = 1,
    val enabled: Boolean = true,

    /** Si true, la regla afecta a todas las apps gestionadas. Si false, solo a [apps]. */
    val allApps: Boolean = true,
    /** Paquetes concretos a los que afecta (cuando allApps = false). */
    val apps: Set<String> = emptySet(),

    // --- SCHEDULE ---
    /** Minutos desde medianoche. Si start > end, el rango cruza la medianoche. */
    val startMinute: Int = 8 * 60,
    val endMinute: Int = 20 * 60,
    /** Días ISO (1 = lunes … 7 = domingo). Vacío = todos los días. */
    val days: Set<Int> = emptySet(),

    // --- DAILY_LIMIT ---
    /** Minutos de uso permitidos al día antes de que la condición se active. */
    val dailyLimitMinutes: Int = 60,

    // --- WIFI ---
    val ssids: Set<String> = emptySet(),

    // --- LOCATION ---
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val radiusMeters: Double = 150.0,
)

/** Ajustes de protección anti-autoengaño. */
@Serializable
data class ProtectionSettings(
    /** Mientras haya un bloqueo activo no se puede editar/debilitar nada. */
    val strictMode: Boolean = false,
    /** Los cambios que debilitan un bloqueo tardan [delayMinutes] en aplicarse. */
    val delayEnabled: Boolean = false,
    val delayMinutes: Int = 120,
    /**
     * Administrador de dispositivos activo: Android impide desinstalar la app.
     * No interfiere con la navegación por Ajustes.
     */
    val blockUninstall: Boolean = false,
    /**
     * Capa extra y agresiva: expulsa de las pantallas de Ajustes que permitirían
     * desactivar la app (su ficha, accesibilidad, administrador). Desactivada por defecto.
     */
    val guardSettings: Boolean = false,
    /**
     * Hash SHA-256 del código de emergencia que le das a otra persona. Nunca se guarda el
     * código en claro. null = no hay código configurado.
     */
    val guardianCodeHash: String? = null,
    /** Campo antiguo: se migra a [blockUninstall] al cargar. No usar. */
    val tamperProtection: Boolean = false,
)

/** Estado que se aplica/enforcea. */
@Serializable
data class AppState(
    val managedApps: Set<String> = emptySet(),
    /** Apps marcadas como productivas: solo afecta a las estadísticas, no al bloqueo. */
    val productiveApps: Set<String> = emptySet(),
    val rules: List<Rule> = emptyList(),
    val protection: ProtectionSettings = ProtectionSettings(),
    /**
     * Retener las notificaciones de las apps bloqueadas y entregarlas cuando acabe el bloqueo.
     * Requiere permiso de acceso a las notificaciones.
     */
    val deferNotifications: Boolean = false,
)

/**
 * Raíz persistida.
 *
 * [live] es lo que se aplica ahora mismo. [desired] es el estado futuro pendiente por el
 * retardo de cambios; cuando llega [pendingAppliesAt] se copia sobre [live].
 */
@Serializable
data class Root(
    val live: AppState = AppState(),
    val desired: AppState? = null,
    val pendingAppliesAt: Long = 0L,
    /** true si el onboarding ya se completó una vez. */
    val onboarded: Boolean = false,
)
