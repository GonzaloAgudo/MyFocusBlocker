package com.example.myfocusblocker.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.Android
import com.example.myfocusblocker.data.AppInfo
import com.example.myfocusblocker.data.AppRepository
import com.example.myfocusblocker.data.ChangeResult
import com.example.myfocusblocker.data.Root

/** Lista con buscador y checkboxes. Estado de selección controlado por el llamador. */
@Composable
fun AppPickerContent(
    apps: List<AppInfo>,
    selected: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, ignoreCase = true) }
    }
    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text("Buscar app…") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = contentPadding,
        ) {
            items(filtered, key = { it.pkg }) { app ->
                AppRow(
                    app = app,
                    checked = app.pkg in selected,
                    onCheckedChange = { onToggle(app.pkg, it) },
                )
            }
        }
    }
}

@Composable
private fun AppRow(app: AppInfo, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = rememberAppIcon(app.pkg)
        Box(Modifier.size(40.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Image(icon, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            } else {
                Icon(Icons.Default.Android, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(app.label, Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun ManagedAppsScreen(root: Root, apps: List<AppInfo>, onBack: () -> Unit) {
    val context = LocalContext.current
    val selected = root.live.managedApps

    ScreenScaffold(title = "Apps a bloquear", onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Elige qué apps quieres gestionar. Las reglas decidirán cuándo bloquearlas.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            AppPickerContent(
                apps = apps,
                selected = selected,
                onToggle = { pkg, checked ->
                    val result = if (checked) {
                        AppRepository.addManagedApps(setOf(pkg))
                    } else {
                        AppRepository.removeManagedApp(pkg)
                    }
                    context.toastFor(result)
                },
                contentPadding = PaddingValues(bottom = 24.dp),
            )
        }
    }
}

@Composable
fun ProductiveAppsScreen(root: Root, apps: List<AppInfo>, onBack: () -> Unit) {
    val context = LocalContext.current

    ScreenScaffold(title = "Apps productivas", onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Marca las apps que consideras productivas (estudio, trabajo…). Solo cambia cómo " +
                    "se reparten tus estadísticas; no afecta a los bloqueos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            AppPickerContent(
                apps = apps,
                selected = root.live.productiveApps,
                onToggle = { pkg, checked ->
                    context.toastFor(AppRepository.setProductive(pkg, checked))
                },
                contentPadding = PaddingValues(bottom = 24.dp),
            )
        }
    }
}

/** Muestra un aviso según el resultado del cambio (encolado / bloqueado por modo estricto). */
fun android.content.Context.toastFor(result: ChangeResult) {
    val msg = when (result) {
        is ChangeResult.Applied -> null
        is ChangeResult.DeniedStrict ->
            "Modo estricto: no puedes debilitar un bloqueo mientras esté activo."
        is ChangeResult.Queued -> {
            val mins = ((result.appliesAt - System.currentTimeMillis()) / 60_000L).coerceAtLeast(1)
            "Cambio programado: se aplicará en ~$mins min (retardo de cambios)."
        }
    }
    if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
