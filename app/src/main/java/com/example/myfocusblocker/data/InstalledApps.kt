package com.example.myfocusblocker.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import java.util.Calendar

data class AppInfo(
    val pkg: String,
    val label: String,
    val icon: Drawable?,
)

object InstalledApps {

    /** Apps con lanzador (las que el usuario puede abrir), excluyendo la nuestra. */
    fun launchable(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, 0)
        val self = context.packageName
        return resolved.asSequence()
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != self }
            .map { pkg ->
                val ai = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
                AppInfo(
                    pkg = pkg,
                    label = ai?.let { pm.getApplicationLabel(it).toString() } ?: pkg,
                    icon = runCatching { pm.getApplicationIcon(pkg) }.getOrNull(),
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun label(context: Context, pkg: String): String {
        val pm = context.packageManager
        return runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    fun icon(context: Context, pkg: String): Drawable? =
        runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull()

    /**
     * Minutos de uso (primer plano) de hoy por paquete, desde medianoche.
     * Requiere permiso de acceso al uso (PACKAGE_USAGE_STATS).
     */
    fun usageTodayMinutes(context: Context): Map<String, Int> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now)
            ?: return emptyMap()
        val result = HashMap<String, Long>()
        for (s in stats) {
            if (s.totalTimeInForeground > 0) {
                result[s.packageName] = (result[s.packageName] ?: 0L) + s.totalTimeInForeground
            }
        }
        return result.mapValues { (it.value / 60_000L).toInt() }
    }
}
