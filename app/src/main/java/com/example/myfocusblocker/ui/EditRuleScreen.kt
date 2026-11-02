package com.example.myfocusblocker.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.myfocusblocker.data.AppInfo
import com.example.myfocusblocker.data.AppRepository
import com.example.myfocusblocker.data.Rule
import com.example.myfocusblocker.data.RuleAction
import com.example.myfocusblocker.data.RuleType
import com.example.myfocusblocker.service.ContextHolder
import com.example.myfocusblocker.util.WifiUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditRuleScreen(existing: Rule?, apps: List<AppInfo>, onBack: () -> Unit) {
    val context = LocalContext.current
    val base = existing ?: Rule()

    var name by remember { mutableStateOf(base.name) }
    var type by remember { mutableStateOf(base.type) }
    var action by remember { mutableStateOf(base.action) }
    var priority by remember { mutableStateOf(base.priority) }
    var allApps by remember { mutableStateOf(base.allApps) }
    var ruleApps by remember { mutableStateOf(base.apps) }
    var startMinute by remember { mutableStateOf(base.startMinute) }
    var endMinute by remember { mutableStateOf(base.endMinute) }
    var days by remember { mutableStateOf(base.days) }
    var dailyLimit by remember { mutableStateOf(base.dailyLimitMinutes) }
    var ssids by remember { mutableStateOf(base.ssids) }
    var lat by remember { mutableStateOf(base.lat) }
    var lon by remember { mutableStateOf(base.lon) }
    var radius by remember { mutableStateOf(base.radiusMeters) }

    var showAppDialog by remember { mutableStateOf(false) }
    var newSsid by remember { mutableStateOf("") }

    ScreenScaffold(
        title = if (existing == null) "Nueva regla" else "Editar regla",
        onBack = onBack,
    ) { padding ->
        ColumnScroll(padding) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre (opcional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Tipo de condición
            SectionCard("¿Cuándo se activa?") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuleType.entries.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(typeLabel(t)) },
                        )
                    }
                }
            }

            // Acción
            SectionCard("¿Qué hace?") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = action == RuleAction.BLOCK,
                        onClick = { action = RuleAction.BLOCK },
                        label = { Text("Bloquear") },
                    )
                    FilterChip(
                        selected = action == RuleAction.ALLOW,
                        onClick = { action = RuleAction.ALLOW },
                        label = { Text("Permitir") },
                    )
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    if (action == RuleAction.ALLOW)
                        "Una regla PERMITIR con más prioridad anula a los bloqueos cuando su condición se cumple."
                    else "Bloquea las apps mientras la condición se cumpla.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }

            // Prioridad
            SectionCard("Prioridad") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Mayor número gana cuando varias reglas coinciden.",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    IconButton(onClick = { priority = (priority - 1).coerceAtLeast(0) }) {
                        Icon(Icons.Default.Remove, "Menos")
                    }
                    Text("$priority", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { priority = (priority + 1).coerceAtMost(99) }) {
                        Icon(Icons.Default.Add, "Más")
                    }
                }
            }

            // Apps afectadas
            SectionCard("Apps afectadas") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = allApps,
                        onClick = { allApps = true },
                        label = { Text("Todas las gestionadas") },
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = !allApps,
                        onClick = { allApps = false },
                        label = { Text("Elegir apps") },
                    )
                }
                if (!allApps) {
                    Spacer(Modifier.size(8.dp))
                    OutlinedButton(onClick = { showAppDialog = true }) {
                        Text("Seleccionar (${ruleApps.size})")
                    }
                }
            }

            // Detalle según el tipo
            when (type) {
                RuleType.SCHEDULE -> SectionCard("Horario") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TimeField("Inicio", startMinute, Modifier.weight(1f)) {
                            pickTime(context, startMinute) { startMinute = it }
                        }
                        TimeField("Fin", endMinute, Modifier.weight(1f)) {
                            pickTime(context, endMinute) { endMinute = it }
                        }
                    }
                    Spacer(Modifier.size(12.dp))
                    Text("Días (vacío = todos)", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.size(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..7).forEach { d ->
                            FilterChip(
                                selected = d in days,
                                onClick = {
                                    days = if (d in days) days - d else days + d
                                },
                                label = { Text(ISO_DAY_LABELS[d - 1]) },
                            )
                        }
                    }
                }

                RuleType.DAILY_LIMIT -> SectionCard("Límite diario") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Máximo por app al día", Modifier.weight(1f))
                        IconButton(onClick = { dailyLimit = (dailyLimit - 15).coerceAtLeast(5) }) {
                            Icon(Icons.Default.Remove, "Menos")
                        }
                        Text(minutesToDuration(dailyLimit), fontWeight = FontWeight.Bold)
                        IconButton(onClick = { dailyLimit = (dailyLimit + 15).coerceAtMost(1440) }) {
                            Icon(Icons.Default.Add, "Más")
                        }
                    }
                }

                RuleType.WIFI -> SectionCard("Redes WiFi") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newSsid,
                            onValueChange = { newSsid = it },
                            label = { Text("Nombre de la red (SSID)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = {
                            if (newSsid.isNotBlank()) { ssids = ssids + newSsid.trim(); newSsid = "" }
                        }) { Icon(Icons.Default.Add, "Añadir") }
                    }
                    Spacer(Modifier.size(8.dp))
                    OutlinedButton(onClick = {
                        val cur = WifiUtils.currentSsid(context)
                        if (cur != null) {
                            ssids = ssids + cur
                            ContextHolder.ssid = cur
                        } else {
                            val problem = WifiUtils.diagnose(context)
                            Toast.makeText(context, WifiUtils.message(problem), Toast.LENGTH_LONG).show()
                            if (problem == WifiUtils.SsidProblem.LOCATION_OFF) {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                }
                            }
                        }
                    }) { Text("Añadir WiFi actual") }
                    if (!WifiUtils.locationServicesEnabled(context)) {
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "La Ubicación del teléfono está apagada. Android la exige para leer el " +
                                "nombre de la WiFi, así que las reglas por WiFi no funcionarán sin ella.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.size(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ssids.forEach { s ->
                            InputChip(
                                selected = false,
                                onClick = { ssids = ssids - s },
                                label = { Text(s) },
                                trailingIcon = { Icon(Icons.Default.Close, "Quitar", Modifier.size(16.dp)) },
                            )
                        }
                    }
                }

                RuleType.LOCATION -> SectionCard("Ubicación") {
                    Text(
                        if (lat == 0.0 && lon == 0.0) "Sin ubicación fijada"
                        else "Punto: %.5f, %.5f".format(lat, lon),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.size(8.dp))
                    OutlinedButton(onClick = {
                        val la = ContextHolder.lat; val lo = ContextHolder.lon
                        if (la != null && lo != null) {
                            lat = la; lon = lo
                            Toast.makeText(context, "Ubicación fijada", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Aún no hay ubicación (activa el GPS y espera)", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Usar ubicación actual") }
                    Spacer(Modifier.size(12.dp))
                    Text("Radio: ${radius.toInt()} m", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = radius.toFloat(),
                        onValueChange = { radius = it.toDouble() },
                        valueRange = 50f..1000f,
                    )
                }
            }

            Button(
                onClick = {
                    val err = validate(type, allApps, ruleApps, ssids, lat, lon)
                    if (err != null) {
                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    val rule = base.copy(
                        name = name.trim(),
                        type = type,
                        action = action,
                        priority = priority,
                        allApps = allApps,
                        apps = ruleApps,
                        startMinute = startMinute,
                        endMinute = endMinute,
                        days = days,
                        dailyLimitMinutes = dailyLimit,
                        ssids = ssids,
                        lat = lat,
                        lon = lon,
                        radiusMeters = radius,
                    )
                    val result = if (existing == null) AppRepository.addRule(rule)
                    else AppRepository.updateRule(rule)
                    context.toastFor(result)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (existing == null) "Crear regla" else "Guardar cambios") }

            Spacer(Modifier.size(48.dp))
        }
    }

    if (showAppDialog) {
        AppSelectionDialog(
            apps = apps,
            initial = ruleApps,
            onDismiss = { showAppDialog = false },
            onConfirm = { ruleApps = it; showAppDialog = false },
        )
    }
}

@Composable
private fun TimeField(label: String, minute: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(minutesToHHmm(minute), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppSelectionDialog(
    apps: List<AppInfo>,
    initial: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var selection by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Elegir apps", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Button(onClick = { onConfirm(selection) }) { Text("Hecho (${selection.size})") }
                }
                AppPickerContent(
                    apps = apps,
                    selected = selection,
                    onToggle = { pkg, checked ->
                        selection = if (checked) selection + pkg else selection - pkg
                    },
                )
            }
        }
    }
}

private fun typeLabel(t: RuleType): String = when (t) {
    RuleType.SCHEDULE -> "Horario"
    RuleType.DAILY_LIMIT -> "Límite diario"
    RuleType.WIFI -> "WiFi"
    RuleType.LOCATION -> "Ubicación"
}

private fun pickTime(context: android.content.Context, minute: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(
        context,
        { _, h, m -> onPicked(h * 60 + m) },
        minute / 60, minute % 60, true,
    ).show()
}

private fun validate(
    type: RuleType,
    allApps: Boolean,
    ruleApps: Set<String>,
    ssids: Set<String>,
    lat: Double,
    lon: Double,
): String? {
    if (!allApps && ruleApps.isEmpty()) return "Selecciona al menos una app o elige «Todas las gestionadas»."
    if (type == RuleType.WIFI && ssids.isEmpty()) return "Añade al menos una red WiFi."
    if (type == RuleType.LOCATION && lat == 0.0 && lon == 0.0) return "Fija una ubicación."
    return null
}
