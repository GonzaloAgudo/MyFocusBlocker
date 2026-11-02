package com.example.myfocusblocker.data

import android.content.Context
import com.example.myfocusblocker.engine.BlockEngine
import com.example.myfocusblocker.service.ContextHolder
import com.example.myfocusblocker.util.GuardianCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Resultado de intentar aplicar un cambio. */
sealed interface ChangeResult {
    /** Aplicado ya. */
    data object Applied : ChangeResult
    /** Encolado por el retardo; se aplicará en [appliesAt]. */
    data class Queued(val appliesAt: Long) : ChangeResult
    /** Rechazado por el modo estricto (hay un bloqueo activo). */
    data object DeniedStrict : ChangeResult
}

/**
 * Fuente única de verdad. Persiste en un fichero JSON y expone el estado como [StateFlow].
 * Encapsula la lógica de modo estricto y retardo de cambios.
 */
object AppRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private lateinit var file: File
    private val _state = MutableStateFlow(Root())
    val state: StateFlow<Root> get() = _state.asStateFlow()

    fun init(context: Context) {
        if (::file.isInitialized) return
        file = File(context.applicationContext.filesDir, "state.json")
        if (file.exists()) {
            runCatching { json.decodeFromString<Root>(file.readText()) }
                .onSuccess { loaded ->
                    val migrated = migrate(loaded)
                    if (migrated != loaded) save(migrated) else _state.value = loaded
                }
        }
        applyDuePending()
    }

    /**
     * El antiguo `tamperProtection` mezclaba dos cosas: impedir la desinstalación y vigilar
     * Ajustes. Se migra a `blockUninstall` (que es lo que el usuario quería) dejando el
     * guardia de Ajustes apagado, porque era demasiado intrusivo.
     */
    private fun migrate(root: Root): Root {
        val p = root.live.protection
        if (!p.tamperProtection) return root
        val migrated = p.copy(
            blockUninstall = true,
            guardSettings = false,
            tamperProtection = false,
        )
        return root.copy(live = root.live.copy(protection = migrated))
    }

    val live: AppState get() = _state.value.live

    @Synchronized
    private fun save(root: Root) {
        _state.value = root
        runCatching { file.writeText(json.encodeToString(root)) }
    }

    // ---- Maduración del retardo ----

    /** Aplica el cambio pendiente si ya venció. Devuelve true si aplicó algo. */
    @Synchronized
    fun applyDuePending(): Boolean {
        val root = _state.value
        val desired = root.desired ?: return false
        if (System.currentTimeMillis() >= root.pendingAppliesAt) {
            save(root.copy(live = desired, desired = null, pendingAppliesAt = 0L))
            return true
        }
        return false
    }

    /** Cancela el cambio pendiente (siempre permitido: solo endurece). */
    @Synchronized
    fun cancelPending() {
        val root = _state.value
        if (root.desired != null) save(root.copy(desired = null, pendingAppliesAt = 0L))
    }

    // ---- ¿Estamos en un bloqueo ahora? (modo estricto) ----

    fun isBlockingNow(): Boolean {
        val ctx = ContextHolder.snapshot()
        return BlockEngine.isBlockingPeriodNow(ctx, _state.value.live)
    }

    /** El usuario puede editar/debilitar cosas ahora mismo. */
    fun canWeakenNow(): Boolean {
        val p = _state.value.live.protection
        return !(p.strictMode && isBlockingNow())
    }

    // ---- Núcleo de cambios ----

    @Synchronized
    private fun applyChange(weakening: Boolean, transform: (AppState) -> AppState): ChangeResult {
        val root = _state.value
        val live = root.live
        val p = live.protection

        if (weakening) {
            if (p.strictMode && isBlockingNow()) return ChangeResult.DeniedStrict

            val delayActive = p.delayEnabled && p.delayMinutes > 0
            if (delayActive) {
                val base = root.desired ?: live
                val newDesired = transform(base)
                val at = System.currentTimeMillis() + p.delayMinutes * 60_000L
                save(root.copy(desired = newDesired, pendingAppliesAt = at))
                return ChangeResult.Queued(at)
            }
        }
        // Endurecer, o debilitar sin retardo: aplicar ya (a live y, si existe, a desired).
        val newLive = transform(live)
        val newDesired = root.desired?.let { transform(it) }
        save(root.copy(live = newLive, desired = newDesired))
        return ChangeResult.Applied
    }

    // ---- API de alto nivel usada por la UI ----

    fun setOnboarded() = save(_state.value.copy(onboarded = true))

    fun addManagedApps(pkgs: Set<String>): ChangeResult =
        applyChange(weakening = false) { it.copy(managedApps = it.managedApps + pkgs) }

    fun removeManagedApp(pkg: String): ChangeResult =
        applyChange(weakening = true) { it.copy(managedApps = it.managedApps - pkg) }

    /** Marcar apps como productivas no afecta al bloqueo: se aplica siempre al momento. */
    fun setProductive(pkg: String, productive: Boolean): ChangeResult =
        applyChange(weakening = false) {
            it.copy(
                productiveApps = if (productive) it.productiveApps + pkg
                else it.productiveApps - pkg,
            )
        }

    fun setDeferNotifications(on: Boolean): ChangeResult =
        applyChange(weakening = !on) { it.copy(deferNotifications = on) }

    fun addRule(rule: Rule): ChangeResult =
        applyChange(weakening = rule.action == RuleAction.ALLOW) { it.copy(rules = it.rules + rule) }

    fun updateRule(rule: Rule): ChangeResult =
        // Conservador: editar puede debilitar, así que va por el retardo.
        applyChange(weakening = true) { s ->
            s.copy(rules = s.rules.map { if (it.id == rule.id) rule else it })
        }

    fun setRuleEnabled(id: String, enabled: Boolean): ChangeResult {
        val rule = live.rules.firstOrNull { it.id == id } ?: return ChangeResult.Applied
        val weakening = (rule.action == RuleAction.BLOCK && !enabled) ||
            (rule.action == RuleAction.ALLOW && enabled)
        return applyChange(weakening) { s ->
            s.copy(rules = s.rules.map { if (it.id == id) it.copy(enabled = enabled) else it })
        }
    }

    fun deleteRule(id: String): ChangeResult {
        val rule = live.rules.firstOrNull { it.id == id } ?: return ChangeResult.Applied
        val weakening = rule.action == RuleAction.BLOCK
        return applyChange(weakening) { s -> s.copy(rules = s.rules.filter { it.id != id }) }
    }

    // ---- Código de emergencia ----

    /**
     * Guarda el hash de un código nuevo.
     *
     * Solo se permite con el modo estricto APAGADO: si se pudiera regenerar con el modo
     * estricto activo, bastaría con crear un código nuevo y leerlo en pantalla para saltarse
     * la protección, y todo esto no serviría de nada.
     */
    @Synchronized
    fun setGuardianCode(codeHash: String): Boolean {
        val root = _state.value
        if (root.live.protection.strictMode) return false
        val protection = root.live.protection.copy(guardianCodeHash = codeHash)
        save(root.copy(live = root.live.copy(protection = protection)))
        return true
    }

    /**
     * Canjea el código de emergencia: apaga el modo estricto y el retardo, y consume el código
     * (para volver a protegerte habrá que generar otro y dárselo a alguien de nuevo).
     * Salta a propósito el modo estricto y el retardo: es justo la vía de escape.
     */
    @Synchronized
    fun redeemGuardianCode(input: String): Boolean {
        val root = _state.value
        val expected = root.live.protection.guardianCodeHash ?: return false
        if (!GuardianCode.matches(input, expected)) return false
        val protection = root.live.protection.copy(
            strictMode = false,
            delayEnabled = false,
            guardianCodeHash = null,
        )
        // Descartamos también el cambio pendiente: su instantánea podría reactivar el modo
        // estricto al vencer y volver a encerrarte.
        save(
            root.copy(
                live = root.live.copy(protection = protection),
                desired = null,
                pendingAppliesAt = 0L,
            ),
        )
        return true
    }

    fun updateProtection(new: ProtectionSettings): ChangeResult {
        val old = live.protection
        val weakening = (!new.strictMode && old.strictMode) ||
            (!new.delayEnabled && old.delayEnabled) ||
            (new.delayMinutes < old.delayMinutes) ||
            (!new.blockUninstall && old.blockUninstall) ||
            (!new.guardSettings && old.guardSettings)
        return applyChange(weakening) { it.copy(protection = new) }
    }
}
