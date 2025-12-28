package com.example.myfocusblocker

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Clase de datos
data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    var isSelected: Boolean = false
)

class AppAdapter(
    private var apps: List<AppInfo>,
    private val onAppChecked: (String, Boolean) -> Unit
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgIcon: ImageView = view.findViewById(R.id.imgIcon)
        val tvName: TextView = view.findViewById(R.id.tvAppName)
        val cbBlock: CheckBox = view.findViewById(R.id.cbBlock)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]
        holder.tvName.text = app.name
        holder.imgIcon.setImageDrawable(app.icon)

        // Limpiamos el listener previo para evitar bugs al hacer scroll
        holder.cbBlock.setOnCheckedChangeListener(null)
        holder.cbBlock.isChecked = app.isSelected

        holder.cbBlock.setOnCheckedChangeListener { _, isChecked ->
            app.isSelected = isChecked
            onAppChecked(app.packageName, isChecked)
        }
    }

    override fun getItemCount() = apps.size

    fun updateList(newApps: List<AppInfo>) {
        this.apps = newApps
        notifyDataSetChanged()
    }
}