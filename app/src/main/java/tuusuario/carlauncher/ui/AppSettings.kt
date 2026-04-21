package com.tuusuario.carlauncher.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.compose.runtime.mutableStateOf

object AppSettings {
    // Variables de estado que la UI observa
    val speedoStyle = mutableStateOf("PREMIUM")
    val speedoColor = mutableStateOf(Color.parseColor("#007AFF"))
    val vehicleType = mutableStateOf("SEDAN") // El Kia Rio es un Sedan de corazón
    val uiColor = mutableStateOf(Color.parseColor("#007AFF"))
    val isDarkMode = mutableStateOf(true)

    private var prefs: SharedPreferences? = null
    private var isInitialized = false

    // Función para leer lo guardado al abrir la app
    fun init(context: Context) {
        if (isInitialized) return
        prefs = context.getSharedPreferences("CarLauncherPrefs", Context.MODE_PRIVATE)
        
        speedoStyle.value = prefs?.getString("speedoStyle", "PREMIUM") ?: "PREMIUM"
        speedoColor.value = prefs?.getInt("speedoColor", Color.parseColor("#007AFF")) ?: Color.parseColor("#007AFF")
        vehicleType.value = prefs?.getString("vehicleType", "SEDAN") ?: "SEDAN"
        uiColor.value = prefs?.getInt("uiColor", Color.parseColor("#007AFF")) ?: Color.parseColor("#007AFF")
        isDarkMode.value = prefs?.getBoolean("isDarkMode", true) ?: true
        
        isInitialized = true
    }

    // Funciones elegantes para cambiar y GUARDAR al mismo tiempo
    fun setSpeedoStyle(style: String) {
        speedoStyle.value = style
        prefs?.edit()?.putString("speedoStyle", style)?.apply()
    }

    fun setSpeedoColor(color: Int) {
        speedoColor.value = color
        prefs?.edit()?.putInt("speedoColor", color)?.apply()
    }

    fun setVehicleType(type: String) {
        vehicleType.value = type
        prefs?.edit()?.putString("vehicleType", type)?.apply()
    }

    fun setUiColor(color: Int) {
        uiColor.value = color
        prefs?.edit()?.putInt("uiColor", color)?.apply()
    }

    fun setDarkMode(isDark: Boolean) {
        isDarkMode.value = isDark
        prefs?.edit()?.putBoolean("isDarkMode", isDark)?.apply()
    }
}