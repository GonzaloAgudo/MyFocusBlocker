package com.example.myfocusblocker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import com.example.myfocusblocker.data.InstalledApps

/** Convierte el icono de una app a ImageBitmap para Compose (cacheado por paquete). */
@Composable
fun rememberAppIcon(pkg: String): ImageBitmap? {
    val context = LocalContext.current
    return remember(pkg) {
        InstalledApps.icon(context, pkg)?.let {
            runCatching { it.toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
    }
}

fun minutesToHHmm(minute: Int): String {
    val h = (minute / 60) % 24
    val m = minute % 60
    return "%02d:%02d".format(h, m)
}

fun minutesToDuration(min: Int): String {
    val h = min / 60
    val m = min % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}min"
        h > 0 -> "${h}h"
        else -> "${m}min"
    }
}

val ISO_DAY_LABELS = listOf("L", "M", "X", "J", "V", "S", "D") // 1..7

fun daysLabel(days: Set<Int>): String {
    if (days.isEmpty()) return "Todos los días"
    return (1..7).filter { it in days }.joinToString(" ") { ISO_DAY_LABELS[it - 1] }
}
