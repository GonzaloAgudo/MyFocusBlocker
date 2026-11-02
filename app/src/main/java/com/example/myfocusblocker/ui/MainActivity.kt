package com.example.myfocusblocker.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.myfocusblocker.data.AppInfo
import com.example.myfocusblocker.data.AppRepository
import com.example.myfocusblocker.data.InstalledApps
import com.example.myfocusblocker.data.Rule
import com.example.myfocusblocker.service.MonitorService
import com.example.myfocusblocker.util.Permissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Dashboard : Screen
    data object Permissions : Screen
    data object Protection : Screen
    data object ManagedApps : Screen
    data object Stats : Screen
    data object ProductiveApps : Screen
    data class EditRule(val rule: Rule?) : Screen
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppRepository.init(applicationContext)
        MonitorService.start(applicationContext)
        setContent {
            MyFocusTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    var screen by remember { mutableStateOf<Screen>(Screen.Dashboard) }

    // El gesto de atrás navega entre pantallas; solo sale de la app desde el inicio.
    BackHandler(enabled = screen != Screen.Dashboard) {
        screen = if (screen == Screen.ProductiveApps) Screen.Stats else Screen.Dashboard
    }

    // Permisos runtime en el arranque.
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }
    LaunchedEffect(Unit) {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        launcher.launch(perms.toTypedArray())
    }

    // Lista de apps instaladas (carga en segundo plano).
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { InstalledApps.launchable(context) }
    }

    // Estado de permisos, recalculado al volver a primer plano.
    var permStatus by remember { mutableStateOf(PermissionStatus.read(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                AppRepository.applyDuePending()
                // Si el ajuste quedó apagado (p. ej. al vencer un cambio con retardo), retiramos
                // de verdad el administrador: si no, la app seguiría sin poder desinstalarse.
                if (!AppRepository.live.protection.blockUninstall &&
                    Permissions.deviceAdminActive(context)
                ) {
                    Permissions.removeDeviceAdmin(context)
                }
                permStatus = PermissionStatus.read(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val root by AppRepository.state.collectAsState()

    when (val s = screen) {
        Screen.Dashboard -> DashboardScreen(
            root = root,
            apps = apps,
            permStatus = permStatus,
            onOpenPermissions = { screen = Screen.Permissions },
            onOpenProtection = { screen = Screen.Protection },
            onOpenManagedApps = { screen = Screen.ManagedApps },
            onOpenStats = { screen = Screen.Stats },
            onAddRule = { screen = Screen.EditRule(null) },
            onEditRule = { screen = Screen.EditRule(it) },
        )
        Screen.Stats -> StatsScreen(
            root = root,
            onBack = { screen = Screen.Dashboard },
            onOpenProductiveApps = { screen = Screen.ProductiveApps },
        )
        Screen.ProductiveApps -> ProductiveAppsScreen(
            root = root,
            apps = apps,
            onBack = { screen = Screen.Stats },
        )
        Screen.Permissions -> PermissionsScreen(
            permStatus = permStatus,
            onBack = { screen = Screen.Dashboard },
        )
        Screen.Protection -> ProtectionScreen(
            root = root,
            onBack = { screen = Screen.Dashboard },
        )
        Screen.ManagedApps -> ManagedAppsScreen(
            root = root,
            apps = apps,
            onBack = { screen = Screen.Dashboard },
        )
        is Screen.EditRule -> EditRuleScreen(
            existing = s.rule,
            apps = apps,
            onBack = { screen = Screen.Dashboard },
        )
    }
}
