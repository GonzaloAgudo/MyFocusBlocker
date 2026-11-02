package com.example.myfocusblocker.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.example.myfocusblocker.R

/**
 * Superposición a pantalla completa que tapa la app bloqueada. Se dibuja con
 * TYPE_APPLICATION_OVERLAY (requiere permiso "mostrar sobre otras apps"), lo que evita las
 * restricciones de lanzar Activities desde segundo plano.
 */
object BlockOverlay {

    private var view: View? = null

    fun isShowing(): Boolean = view != null

    fun show(context: Context, appLabel: String, reason: String, onExit: () -> Unit) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        if (view == null) {
            val v = LayoutInflater.from(context).inflate(R.layout.window_blocker, null)
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.OPAQUE,
            )
            params.gravity = Gravity.CENTER
            v.findViewById<Button>(R.id.blockExit).setOnClickListener { onExit() }
            runCatching { wm.addView(v, params) }
            view = v
        }
        view?.let { v ->
            v.findViewById<TextView>(R.id.blockApp).text = appLabel
            v.findViewById<TextView>(R.id.blockReason).text = reason
        }
    }

    fun hide(context: Context) {
        val v = view ?: return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        runCatching { wm.removeView(v) }
        view = null
    }
}
