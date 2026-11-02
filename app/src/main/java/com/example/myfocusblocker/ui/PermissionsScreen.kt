package com.example.myfocusblocker.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.myfocusblocker.data.AppRepository
import com.example.myfocusblocker.util.Permissions
import com.example.myfocusblocker.util.WifiUtils

/** Estado de todos los permisos que la app necesita. */
data class PermissionStatus(
    val accessibility: Boolean,
    val usage: Boolean,
    val overlay: Boolean,
    val battery: Boolean,
    val location: Boolean,
    val backgroundLocation: Boolean,
    /** El servicio de ubicación del sistema (no el permiso): sin él no hay SSID ni geovallas. */
    val locationServicesOn: Boolean,
    val notifications: Boolean,
    /** Acceso a las notificaciones, para retenerlas durante los bloqueos. */
    val notificationAccess: Boolean,
    val deviceAdmin: Boolean,
) {
    /** Permisos imprescindibles para que el bloqueo básico funcione. */
    val coreReady: Boolean get() = accessibility && overlay

    companion object {
        fun read(context: Context): PermissionStatus {
            fun granted(p: String) =
                ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
            val bgLoc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else true
            val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                granted(Manifest.permission.POST_NOTIFICATIONS)
            } else true
            return PermissionStatus(
                accessibility = Permissions.accessibilityEnabled(context),
                usage = Permissions.usageAccess(context),
                overlay = Permissions.canDrawOverlays(context),
                battery = Permissions.ignoringBatteryOptimizations(context),
                location = granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
                    granted(Manifest.permission.ACCESS_COARSE_LOCATION),
                backgroundLocation = bgLoc,
                locationServicesOn = WifiUtils.locationServicesEnabled(context),
                notifications = notif,
                notificationAccess = Permissions.notificationAccess(context),
                deviceAdmin = Permissions.deviceAdminActive(context),
            )
        }
    }
}

@Composable
fun PermissionsScreen(permStatus: PermissionStatus, onBack: () -> Unit) {
    val context = LocalContext.current
    fun open(intent: Intent) = runCatching { context.startActivity(intent) }
    fun appDetails() = open(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        ),
    )

    ScreenScaffold(title = "Permisos", onBack = onBack) { padding ->
        ColumnScroll(padding) {
            Text(
                "Concede estos permisos para que el bloqueo funcione y no se pueda saltar. " +
                    "En Xiaomi/MIUI activa también el arranque automático desde los ajustes del sistema.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )

            PermRow(
                "Accesibilidad", "Imprescindible. Permite detectar y bloquear las apps.",
                permStatus.accessibility,
            ) { open(Permissions.accessibilityIntent()) }

            PermRow(
                "Mostrar sobre otras apps", "Imprescindible. Dibuja la pantalla de bloqueo.",
                permStatus.overlay,
            ) { open(Permissions.overlayIntent(context)) }

            PermRow(
                "Acceso al uso", "Necesario para los límites de tiempo diario.",
                permStatus.usage,
            ) { open(Permissions.usageAccessIntent()) }

            PermRow(
                "Sin optimización de batería", "Evita que el sistema mate el servicio.",
                permStatus.battery,
            ) { open(Permissions.batteryIntent(context)) }

            PermRow(
                "Ubicación (siempre)", "Necesario para reglas por WiFi y por ubicación.",
                permStatus.location && permStatus.backgroundLocation,
            ) { appDetails() }

            PermRow(
                "Ubicación del teléfono encendida",
                "Android exige el GPS activado para leer el nombre de la WiFi y usar geovallas.",
                permStatus.locationServicesOn,
            ) { open(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }

            PermRow(
                "Notificaciones", "Para el aviso permanente del servicio.",
                permStatus.notifications,
            ) { appDetails() }

            PermRow(
                "Acceso a las notificaciones",
                "Para retener las notificaciones de las apps bloqueadas hasta que acabe el bloqueo.",
                permStatus.notificationAccess,
            ) { open(Permissions.notificationAccessIntent()) }

            PermRow(
                "Administrador de dispositivos", "Impide desinstalar la app.",
                permStatus.deviceAdmin,
            ) {
                // Dejamos el ajuste y el estado real del sistema siempre en sintonía.
                AppRepository.updateProtection(
                    AppRepository.live.protection.copy(blockUninstall = true),
                )
                open(Permissions.deviceAdminIntent(context))
            }
        }
    }
}

@Composable
private fun PermRow(title: String, desc: String, granted: Boolean, onAction: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (granted) Color(0xFF3DDC84) else MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.width(8.dp))
            if (!granted) {
                Button(onClick = onAction) { Text("Activar") }
            }
        }
    }
}
