package com.example.myfocusblocker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myfocusblocker.data.DayUsage
import com.example.myfocusblocker.data.InstalledApps
import com.example.myfocusblocker.data.Root
import com.example.myfocusblocker.data.UsageHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.roundToInt

private val Productive = Color(0xFF3DDC84)
private val Distraction = Color(0xFFFF6B6B)

@Composable
fun StatsScreen(
    root: Root,
    onBack: () -> Unit,
    onOpenProductiveApps: () -> Unit,
) {
    val context = LocalContext.current
    val state = root.live
    var days by remember { mutableStateOf<List<DayUsage>?>(null) }

    LaunchedEffect(Unit) {
        days = withContext(Dispatchers.IO) { UsageHistory.lastDays(context, 14) }
    }

    ScreenScaffold(title = "Estadísticas", onBack = onBack) { padding ->
        ColumnScroll(padding) {
            val data = days
            if (data == null) {
                Row(
                    Modifier.fillMaxWidth().padding(32.dp),
                    horizontalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }
                return@ColumnScroll
            }
            if (data.all { it.perApp.isEmpty() }) {
                SectionCard {
                    Text(
                        "No hay datos de uso todavía. Concede el permiso «Acceso al uso» en " +
                            "Permisos y vuelve dentro de un rato.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                return@ColumnScroll
            }

            val productive = state.productiveApps
            // Distracción = lo que has elegido gestionar y no has marcado como productivo.
            val distracting = state.managedApps - productive

            val today = data.last()
            val last7 = data.takeLast(7)
            val prev7 = data.dropLast(7).takeLast(7)

            // --- Hoy ---
            SectionCard("Hoy") {
                StatRow("Pantalla total", minutesToDuration(today.total))
                StatRow("Distracción", minutesToDuration(today.totalOf(distracting)), Distraction)
                StatRow("Productivo", minutesToDuration(today.totalOf(productive)), Productive)
            }

            // --- Últimos 7 días ---
            SectionCard("Últimos 7 días · distracción") {
                val values = last7.map { it.totalOf(distracting) }
                BarChart(values = values, labels = last7.map { dayLabel(it.dayStartMillis) })
                Spacer(Modifier.size(10.dp))
                val avg = if (values.isEmpty()) 0 else values.sum() / values.size
                Text(
                    "Media diaria: ${minutesToDuration(avg)} · Total: ${minutesToDuration(values.sum())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }

            // --- Mejoría ---
            if (prev7.isNotEmpty()) {
                val cur = last7.sumOf { it.totalOf(distracting) }
                val old = prev7.sumOf { it.totalOf(distracting) }
                SectionCard("¿Vas mejorando?") {
                    if (old == 0) {
                        Text(
                            "Aún no hay suficientes datos de la semana anterior para comparar.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        val delta = cur - old
                        val pct = (delta * 100f / old).roundToInt()
                        val better = delta < 0
                        Text(
                            if (better) "Has bajado un ${-pct}% en distracción"
                            else if (delta == 0) "Igual que la semana pasada"
                            else "Has subido un $pct% en distracción",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (better) Productive else Distraction,
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            "Estos 7 días: ${minutesToDuration(cur)} · 7 anteriores: ${minutesToDuration(old)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            // --- Top apps ---
            SectionCard("Apps con más tiempo (7 días)") {
                val totals = HashMap<String, Int>()
                last7.forEach { day ->
                    day.perApp.forEach { (pkg, m) -> totals[pkg] = (totals[pkg] ?: 0) + m }
                }
                val top = totals.entries.sortedByDescending { it.value }.take(8)
                if (top.isEmpty()) {
                    Text("Sin datos.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    top.forEach { (pkg, minutes) ->
                        val tint = when {
                            pkg in productive -> Productive
                            pkg in distracting -> Distraction
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        }
                        StatRow(InstalledApps.label(context, pkg), minutesToDuration(minutes), tint)
                    }
                }
            }

            SectionCard {
                Text(
                    "Marca tus apps productivas para separarlas de las distracciones en estas " +
                        "estadísticas. No afecta a los bloqueos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Spacer(Modifier.size(10.dp))
                FilledTonalButton(onClick = onOpenProductiveApps) {
                    Text("Apps productivas (${state.productiveApps.size})")
                }
            }

            Spacer(Modifier.size(32.dp))
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, color: Color? = null) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            fontWeight = FontWeight.SemiBold,
            color = color ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Gráfico de barras sencillo, sin librerías externas. */
@Composable
private fun BarChart(values: List<Int>, labels: List<String>) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(140.dp),
    ) {
        if (values.isEmpty()) return@Canvas
        val slot = size.width / values.size
        val barWidth = slot * 0.55f
        values.forEachIndexed { i, v ->
            val h = (v.toFloat() / max) * (size.height - 8f)
            val left = i * slot + (slot - barWidth) / 2f
            drawRect(
                color = Distraction.copy(alpha = if (i == values.lastIndex) 1f else 0.65f),
                topLeft = Offset(left, size.height - h),
                size = Size(barWidth, h),
            )
        }
    }
    Row(Modifier.fillMaxWidth()) {
        labels.forEach { l ->
            Text(
                l,
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

private fun dayLabel(millis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    val iso = ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
    return ISO_DAY_LABELS[iso - 1]
}
