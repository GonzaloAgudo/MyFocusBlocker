package com.example.myfocusblocker.service

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.myfocusblocker.data.AppRepository
import com.example.myfocusblocker.data.InstalledApps
import com.example.myfocusblocker.data.RuleType
import com.example.myfocusblocker.engine.BlockEngine

/**
 * Servicio central: observa qué app pasa a primer plano y, si un bloqueo la afecta,
 * muestra la superposición. También protege contra la desinstalación.
 */
class FocusAccessibilityService : AccessibilityService() {

    private var lastPackage: String? = null
    private var blockedPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        AppRepository.init(applicationContext)
        instance = this
        MonitorService.start(applicationContext)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                AppRepository.applyDuePending()

                if (pkg == packageName) {
                    // OJO: la propia superposición de bloqueo es una ventana nuestra y también
                    // emite eventos con nuestro paquete. Si la ocultásemos aquí entraríamos en un
                    // bucle mostrar/ocultar (la pantalla parpadea y la app bloqueada queda usable).
                    // Solo ocultamos cuando es de verdad nuestra Activity la que pasa a primer plano.
                    if (isOwnActivity(event)) hideOverlay()
                    return
                }

                // Protección anti-desinstalación.
                if (guardTamper(pkg)) return

                lastPackage = pkg
                evaluateAndEnforce(pkg)
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Solo interesa para detectar la ficha de nuestra app en Ajustes/desinstalador,
                // cuyo contenido cambia sin que cambie la ventana.
                if (pkg in settingsPackages || pkg in installerPackages) guardTamper(pkg)
            }
        }
    }

    /** ¿El evento viene de nuestra Activity (y no de la ventana del overlay)? */
    private fun isOwnActivity(event: AccessibilityEvent): Boolean =
        event.className?.toString()?.contains("MainActivity") == true

    /** Reevalúa la app en primer plano (lo llama MonitorService cuando cambia el contexto). */
    fun reevaluateForeground() {
        val pkg = lastPackage ?: return
        if (pkg == packageName) return
        evaluateAndEnforce(pkg)
    }

    private fun evaluateAndEnforce(pkg: String) {
        val state = AppRepository.live
        val ctx = ContextHolder.snapshot()
        val decision = BlockEngine.evaluate(pkg, ctx, state)

        if (decision.blocked) {
            blockedPackage = pkg
            val label = InstalledApps.label(this, pkg)
            val reason = reasonFor(decision.winningRule?.type, decision.winningRule?.name)
            BlockOverlay.show(this, label, reason) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                hideOverlay()
            }
        } else if (pkg != blockedPackage) {
            hideOverlay()
        }
    }

    private fun hideOverlay() {
        if (BlockOverlay.isShowing()) BlockOverlay.hide(this)
        blockedPackage = null
    }

    private fun reasonFor(type: RuleType?, name: String?): String {
        val base = when (type) {
            RuleType.SCHEDULE -> "Bloqueo por horario"
            RuleType.DAILY_LIMIT -> "Has alcanzado el límite de tiempo diario"
            RuleType.WIFI -> "Bloqueo activo en esta red WiFi"
            RuleType.LOCATION -> "Bloqueo activo en esta ubicación"
            null -> "Bloqueado"
        }
        return if (!name.isNullOrBlank()) "$base · $name" else base
    }

    // ---- Anti-desinstalación ----

    private val installerPackages = setOf(
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.miui.packageinstaller",
    )
    private val settingsPackages = setOf(
        "com.android.settings",
        "com.miui.securitycenter",
    )

    /**
     * Expulsa SOLO de las pantallas que permitirían desactivar la app: su ficha en Ajustes,
     * el desinstalador, la accesibilidad o el administrador de dispositivos.
     *
     * Exige dos condiciones a la vez (que la pantalla mencione nuestra app Y que contenga una
     * acción peligrosa) para no estorbar mientras se navega por Ajustes: antes bastaba con que
     * apareciese el nombre de la app en cualquier lista y resultaba insufrible.
     */
    private fun guardTamper(pkg: String): Boolean {
        if (!AppRepository.live.protection.guardSettings) return false
        if (pkg !in installerPackages && pkg !in settingsPackages) return false

        val now = SystemClock.elapsedRealtime()
        if (now - lastGuardCheck < GUARD_THROTTLE_MS) return false
        lastGuardCheck = now

        val root = rootInActiveWindow ?: return false
        val texts = ArrayList<String>(64)
        collectTexts(root, texts, 0)
        root.recycle()

        val mentionsUs = texts.any { it.contains("myfocusblocker") || it.contains(appLabelLower) }
        if (!mentionsUs) return false
        val dangerous = texts.any { t -> DANGEROUS_ACTIONS.any { t.contains(it) } }
        if (!dangerous) return false

        performGlobalAction(GLOBAL_ACTION_HOME)
        BlockOverlay.show(
            this,
            "Protección activa",
            "No puedes desactivar ni desinstalar la app mientras la vigilancia de Ajustes esté encendida.",
        ) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            hideOverlay()
        }
        return true
    }

    private val appLabelLower: String by lazy {
        InstalledApps.label(this, packageName).lowercase()
    }

    /** Recoge textos visibles del árbol, con tope para no recorrer pantallas enormes. */
    private fun collectTexts(node: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int) {
        node ?: return
        if (depth > 25 || out.size > 300) return
        node.text?.toString()?.lowercase()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        node.contentDescription?.toString()?.lowercase()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTexts(child, out, depth + 1)
            child.recycle()
        }
    }

    private var lastGuardCheck = 0L

    companion object {
        @Volatile
        var instance: FocusAccessibilityService? = null

        private const val GUARD_THROTTLE_MS = 400L

        /** Acciones que solo aparecen en pantallas capaces de desactivar/desinstalar la app. */
        private val DANGEROUS_ACTIONS = listOf(
            "desinstalar", "uninstall",
            "forzar detención", "forzar parada", "force stop",
            "administrador de dispositivo", "device admin",
            "accesibilidad", "accessibility",
            "desactivar", "deactivate",
        )
    }
}
