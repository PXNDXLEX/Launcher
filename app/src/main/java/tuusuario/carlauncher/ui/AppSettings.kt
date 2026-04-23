package com.tuusuario.carlauncher.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.compose.runtime.mutableStateOf

object AppSettings {
    // Variables de estado que la UI observa
    val speedoStyle = mutableStateOf("PREMIUM")
    val speedoColor = mutableStateOf(Color.parseColor("#007AFF"))
    val vehicleType = mutableStateOf("SEDAN") // Universal vehicle type
    val uiColor = mutableStateOf(Color.parseColor("#007AFF"))
    val isDarkMode = mutableStateOf(true)
    val isMapDarkMode = mutableStateOf(true)
    
    // Custom Speedometer settings
    val customSpeedoShape = mutableStateOf("ARC")
    val customSpeedoNeedle = mutableStateOf("PLASMA")
    val customSpeedoThickness = mutableStateOf(0.08f)
    val customSpeedoBgUri = mutableStateOf("")    // URI de imagen de fondo (legacy)
    val customSpeedoBgPath = mutableStateOf("")    // Ruta del archivo recortado
    val customSpeedoBgOpacity = mutableStateOf(0.5f) // opacidad del fondo
    
    // Custom Vehicle Icon
    val customVehicleIconPath = mutableStateOf("") // Ruta del ícono personalizado del vehículo

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
        isMapDarkMode.value = prefs?.getBoolean("isMapDarkMode", true) ?: true
        customSpeedoShape.value = prefs?.getString("customSpeedoShape", "ARC") ?: "ARC"
        customSpeedoNeedle.value = prefs?.getString("customSpeedoNeedle", "PLASMA") ?: "PLASMA"
        customSpeedoThickness.value = prefs?.getFloat("customSpeedoThickness", 0.08f) ?: 0.08f
        customSpeedoBgUri.value = prefs?.getString("customSpeedoBgUri", "") ?: ""
        customSpeedoBgPath.value = prefs?.getString("customSpeedoBgPath", "") ?: ""
        customSpeedoBgOpacity.value = prefs?.getFloat("customSpeedoBgOpacity", 0.5f) ?: 0.5f
        customVehicleIconPath.value = prefs?.getString("customVehicleIconPath", "") ?: ""
        
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

    fun setMapDarkMode(isDark: Boolean) {
        isMapDarkMode.value = isDark
        prefs?.edit()?.putBoolean("isMapDarkMode", isDark)?.apply()
    }

    fun setCustomSpeedoShape(shape: String) {
        customSpeedoShape.value = shape
        prefs?.edit()?.putString("customSpeedoShape", shape)?.apply()
    }

    fun setCustomSpeedoNeedle(needle: String) {
        customSpeedoNeedle.value = needle
        prefs?.edit()?.putString("customSpeedoNeedle", needle)?.apply()
    }

    fun setCustomSpeedoThickness(thickness: Float) {
        customSpeedoThickness.value = thickness
        prefs?.edit()?.putFloat("customSpeedoThickness", thickness)?.apply()
    }

    fun setCustomSpeedoBgUri(uri: String) {
        customSpeedoBgUri.value = uri
        prefs?.edit()?.putString("customSpeedoBgUri", uri)?.apply()
    }

    fun setCustomSpeedoBgOpacity(opacity: Float) {
        customSpeedoBgOpacity.value = opacity
        prefs?.edit()?.putFloat("customSpeedoBgOpacity", opacity)?.apply()
    }

    fun setCustomSpeedoBgPath(path: String) {
        customSpeedoBgPath.value = path
        prefs?.edit()?.putString("customSpeedoBgPath", path)?.apply()
    }

    fun setCustomVehicleIconPath(path: String) {
        customVehicleIconPath.value = path
        prefs?.edit()?.putString("customVehicleIconPath", path)?.apply()
    }
}