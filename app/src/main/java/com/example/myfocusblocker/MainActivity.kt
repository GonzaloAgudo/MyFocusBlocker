package com.example.myfocusblocker

import android.app.AppOpsManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: AppAdapter
    private val fullAppList = mutableListOf<AppInfo>()

    private var startHour = 8
    private var startMin = 0
    private var endHour = 21
    private var endMin = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadPreferences()
        setupTimePickers()
        setupRecyclerView()
        setupSearch()

        val btnSave = findViewById<Button>(R.id.btnSaveAndStart)
        btnSave.setOnClickListener {
            if (hasAllPermissions()) {
                savePreferences()
                stopService(Intent(this, BlockerService::class.java))
                startService(Intent(this, BlockerService::class.java))
                Toast.makeText(this, "Guardado. Servicio activo.", Toast.LENGTH_SHORT).show()
            } else {
                requestNextPermission()
            }
        }
    }

    // ... setupSearch, setupTimePickers, updateTimeButtons ...
    // (Mantén esas funciones igual que antes, no cambian)

    private fun setupSearch() {
        val etSearch = findViewById<EditText>(R.id.etSearch)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterAndSort(s.toString())
            }
        })
    }

    private fun setupTimePickers() {
        val btnStart = findViewById<Button>(R.id.btnStartTime)
        val btnEnd = findViewById<Button>(R.id.btnEndTime)
        updateTimeButtons(btnStart, btnEnd)
        btnStart.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                startHour = h; startMin = m
                updateTimeButtons(btnStart, btnEnd)
            }, startHour, startMin, true).show()
        }
        btnEnd.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                endHour = h; endMin = m
                updateTimeButtons(btnStart, btnEnd)
            }, endHour, endMin, true).show()
        }
    }

    private fun updateTimeButtons(btnStart: Button, btnEnd: Button) {
        btnStart.text = String.format("Inicio: %02d:%02d", startHour, startMin)
        btnEnd.text = String.format("Fin: %02d:%02d", endHour, endMin)
    }

    private fun setupRecyclerView() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        val resolvedInfos = pm.queryIntentActivities(intent, 0)
        val prefs = getSharedPreferences("BlockerPrefs", MODE_PRIVATE)
        val blockedSet = prefs.getStringSet("blocked_packages", emptySet()) ?: emptySet()

        fullAppList.clear()

        for (info in resolvedInfos) {
            val packageName = info.activityInfo.packageName
            if (packageName == this.packageName) continue

            val name = info.loadLabel(pm).toString()
            val icon = info.loadIcon(pm)
            val isBlocked = blockedSet.contains(packageName)

            fullAppList.add(AppInfo(name, packageName, icon, isBlocked))
        }

        adapter = AppAdapter(mutableListOf()) { pkg, isChecked ->
            fullAppList.find { it.packageName == pkg }?.isSelected = isChecked
            val currentSearch = findViewById<EditText>(R.id.etSearch).text.toString()
            filterAndSort(currentSearch)
        }

        val rv = findViewById<RecyclerView>(R.id.rvApps)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        filterAndSort("")
    }

    private fun filterAndSort(query: String) {
        val filtered = fullAppList.filter { app ->
            app.name.contains(query, ignoreCase = true)
        }.sortedWith(
            compareByDescending<AppInfo> { it.isSelected }
                .thenBy { it.name.lowercase() }
        )
        adapter.updateList(filtered)
    }

    private fun savePreferences() {
        val prefs = getSharedPreferences("BlockerPrefs", MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putInt("start_hour", startHour)
        editor.putInt("start_min", startMin)
        editor.putInt("end_hour", endHour)
        editor.putInt("end_min", endMin)
        val blockedPackages = fullAppList.filter { it.isSelected }.map { it.packageName }.toSet()
        editor.putStringSet("blocked_packages", blockedPackages)
        editor.apply()
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences("BlockerPrefs", MODE_PRIVATE)
        startHour = prefs.getInt("start_hour", 8)
        startMin = prefs.getInt("start_min", 0)
        endHour = prefs.getInt("end_hour", 21)
        endMin = prefs.getInt("end_min", 0)
    }

    // --- LÓGICA DE PERMISOS ACTUALIZADA ---
    private fun hasAllPermissions(): Boolean {
        val overlay = Settings.canDrawOverlays(this)
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        val usage = (mode == AppOpsManager.MODE_ALLOWED)

        // Check notificación
        val notificationListenerString = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val notificationAccess = notificationListenerString != null && notificationListenerString.contains(packageName)

        return overlay && usage && notificationAccess
    }

    private fun requestNextPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            Toast.makeText(this, "1. Permite 'Mostrar sobre otras apps'", Toast.LENGTH_LONG).show()
            return
        }

        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        if (mode != AppOpsManager.MODE_ALLOWED) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            Toast.makeText(this, "2. Permite 'Acceso a uso'", Toast.LENGTH_LONG).show()
            return
        }

        // Si faltan notificaciones
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "3. Activa el interruptor para leer notificaciones", Toast.LENGTH_LONG).show()
    }
}