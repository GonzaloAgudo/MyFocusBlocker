package com.example.myfocusblocker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.NotificationsPaused
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myfocusblocker.data.AppInfo
import com.example.myfocusblocker.data.AppRepository
import com.example.myfocusblocker.data.Rule
import com.example.myfocusblocker.data.RuleAction
import com.example.myfocusblocker.data.RuleType
import com.example.myfocusblocker.data.Root
import com.example.myfocusblocker.util.Permissions
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(
    root: Root,
    apps: List<AppInfo>,
    permStatus: PermissionStatus,
    onOpenPermissions: () -> Unit,
    onOpenProtection: () -> Unit,
    onOpenManagedApps: () -> Unit,
    onOpenStats: () -> Unit,
    onAddRule: () -> Unit,
    onEditRule: (Rule) -> Unit,
) {
    val context = LocalContext.current
    val state = root.live

    // Refresco periódico del estado "bloqueando ahora" y del contador de cambios pendientes.
    val blockingNow by produceState(initialValue = false, root) {
        while (true) {
            value = AppRepository.isBlockingNow()
            delay(5_000)
        }
    }
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(1_000) }
    }

    ScreenScaffold(
        title = "MyFocusBlocker",
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddRule,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Nueva regla") },
            )
        },
    ) { padding ->
        ColumnScroll(padding) {
            StatusCard(blockingNow)

            if (!permStatus.coreReady) {
                val missing = buildList {
                    if (!permStatus.accessibility) add("Accesibilidad")
                    if (!permStatus.overlay) add("Mostrar sobre otras apps")
                }.joinToString(" y ")
                WarnCard(
                    "Faltan permisos",
                    "El bloqueo no funcionará hasta conceder: $missing.\n\nOjo: Android desactiva " +
                        "la Accesibilidad cada vez que se actualiza la app, así que hay que " +
                        "volver a activarla.",
                    "Revisar permisos",
                    onOpenPermissions,
                )
            }

            if (root.desired != null) {
                PendingCard(remainingMs = (root.pendingAppliesAt - now).coerceAtLeast(0))
            }

            // Apps gestionadas
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Apps, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Apps a bloquear", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (state.managedApps.isEmpty()) "Ninguna seleccionada"
                            else "${state.managedApps.size} apps seleccionadas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                    FilledTonalButton(onClick = onOpenManagedApps) { Text("Gestionar") }
                }
            }

            // Estadísticas
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BarChart, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Estadísticas", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Uso diario, semanal y si vas mejorando",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                    FilledTonalButton(onClick = onOpenStats) { Text("Ver") }
                }
            }

            // Notificaciones retenidas durante el bloqueo
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.NotificationsPaused, null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Silenciar notificaciones", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (!permStatus.notificationAccess && state.deferNotifications)
                                "Falta el permiso «Acceso a las notificaciones»"
                            else
                                "Retiene las de las apps bloqueadas y las entrega al acabar",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (!permStatus.notificationAccess && state.deferNotifications)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                    Switch(
                        checked = state.deferNotifications,
                        onCheckedChange = { on ->
                            context.toastFor(AppRepository.setDeferNotifications(on))
                            if (on && !permStatus.notificationAccess) {
                                runCatching {
                                    context.startActivity(Permissions.notificationAccessIntent())
                                }
                            }
                        },
                    )
                }
            }

            // Protección
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Protección", fontWeight = FontWeight.SemiBold)
                        Text(
                            protectionSummary(state.protection),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                    FilledTonalButton(onClick = onOpenProtection) { Text("Ajustes") }
                }
            }

            Text(
                "Reglas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )

            if (state.rules.isEmpty()) {
                SectionCard {
                    Text(
                        "Aún no tienes reglas. Crea una con el botón «Nueva regla» para definir " +
                            "cuándo bloquear tus apps (horario, límite de tiempo, WiFi o ubicación).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    )
                }
            } else {
                // Mostradas por prioridad descendente.
                state.rules.sortedByDescending { it.priority }.forEach { rule ->
                    RuleCard(
                        rule = rule,
                        onToggle = { context.toastFor(AppRepository.setRuleEnabled(rule.id, it)) },
                        onEdit = { onEditRule(rule) },
                        onDelete = { context.toastFor(AppRepository.deleteRule(rule.id)) },
                    )
                }
            }

            Spacer(Modifier.size(72.dp))
        }
    }
}

@Composable
private fun StatusCard(blockingNow: Boolean) {
    val bg = if (blockingNow) MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
    else Color(0xFF3DDC84).copy(alpha = 0.15f)
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(bg),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Lock, null, tint = if (blockingNow) MaterialTheme.colorScheme.error else Color(0xFF3DDC84)) }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    if (blockingNow) "Bloqueo activo ahora" else "Sin bloqueos activos",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (blockingNow) "Alguna de tus apps está bloqueada en este momento."
                    else "Ninguna regla está bloqueando ahora mismo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun WarnCard(title: String, body: String, action: String, onAction: () -> Unit) {
    SectionCard {
        Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.size(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.size(8.dp))
        Button(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun PendingCard(remainingMs: Long) {
    val mins = (remainingMs / 60_000L)
    val secs = (remainingMs / 1000L) % 60
    SectionCard {
        Text("Cambio pendiente", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.size(4.dp))
        Text(
            "Un cambio que reduce tus bloqueos se aplicará en ${mins}m ${secs}s.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.size(8.dp))
        TextButton(onClick = { AppRepository.cancelPending() }) { Text("Cancelar cambio") }
    }
}

@Composable
private fun RuleCard(rule: Rule, onToggle: (Boolean) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ActionBadge(rule.action)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        defaultRuleTitle(rule),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Spacer(Modifier.size(4.dp))
                Text(
                    ruleSummary(context, rule),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Text(
                    "Prioridad ${rule.priority}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
            Switch(checked = rule.enabled, onCheckedChange = onToggle)
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Editar") }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Default.Delete, "Eliminar", tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar regla") },
            text = { Text("¿Seguro que quieres eliminar esta regla?") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun ActionBadge(action: RuleAction) {
    val (label, color) = if (action == RuleAction.BLOCK) {
        "BLOQUEAR" to MaterialTheme.colorScheme.error
    } else {
        "PERMITIR" to Color(0xFF3DDC84)
    }
    Box(
        Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

private fun protectionSummary(p: com.example.myfocusblocker.data.ProtectionSettings): String {
    val parts = buildList {
        if (p.strictMode) add("Modo estricto")
        if (p.delayEnabled) add("Retardo ${p.delayMinutes}min")
        if (p.blockUninstall) add("Sin desinstalar")
        if (p.guardSettings) add("Vigila Ajustes")
    }
    return if (parts.isEmpty()) "Desactivada" else parts.joinToString(" · ")
}

fun defaultRuleTitle(rule: Rule): String {
    if (rule.name.isNotBlank()) return rule.name
    return when (rule.type) {
        RuleType.SCHEDULE -> "Horario ${minutesToHHmm(rule.startMinute)}–${minutesToHHmm(rule.endMinute)}"
        RuleType.DAILY_LIMIT -> "Límite ${minutesToDuration(rule.dailyLimitMinutes)}/día"
        RuleType.WIFI -> "WiFi"
        RuleType.LOCATION -> "Ubicación"
    }
}

fun ruleSummary(context: android.content.Context, rule: Rule): String {
    val scope = if (rule.allApps) "Todas las apps gestionadas"
    else "${rule.apps.size} app(s)"
    val cond = when (rule.type) {
        RuleType.SCHEDULE ->
            "${minutesToHHmm(rule.startMinute)}–${minutesToHHmm(rule.endMinute)} · ${daysLabel(rule.days)}"
        RuleType.DAILY_LIMIT -> "Tras ${minutesToDuration(rule.dailyLimitMinutes)} de uso al día"
        RuleType.WIFI ->
            if (rule.ssids.isEmpty()) "Sin redes" else "Redes: ${rule.ssids.joinToString(", ")}"
        RuleType.LOCATION -> "Radio ${rule.radiusMeters.toInt()} m"
    }
    return "$cond\n$scope"
}
