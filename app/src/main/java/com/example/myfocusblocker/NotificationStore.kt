package com.example.myfocusblocker

data class SavedNotification(
    val title: String,
    val text: String,
    val packageName: String,
    val timestamp: Long
)

object NotificationStore {
    val savedList = mutableListOf<SavedNotification>()

    fun add(n: SavedNotification) {
        savedList.add(n)
    }

    fun clear() {
        savedList.clear()
    }

    // Genera un texto bonito: "WhatsApp: 5, Instagram: 10..."
    fun getSummaryText(): String {
        if (savedList.isEmpty()) return "Sin notificaciones."

        val summary = savedList.groupingBy { it.packageName }.eachCount()
        val sb = StringBuilder()

        summary.forEach { (pkg, count) ->
            // Intentamos limpiar el nombre (de "com.whatsapp" a "whatsapp")
            val simpleName = pkg.substringAfterLast(".").replaceFirstChar { it.uppercase() }
            sb.append("$simpleName: $count  |  ")
        }
        return sb.toString()
    }
}