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
    val vehicle3DScale = mutableStateOf(0.15f) // Escala del modelo 3D
    val uiColor = mutableStateOf(Color.parseColor("#007AFF"))
    val mapIconColor = mutableStateOf(Color.parseColor("#007AFF")) // Color independiente para el icono en el mapa
    val isDarkMode = mutableStateOf(true)
    val isMapDarkMode = mutableStateOf(true)
    val mapStyle = mutableStateOf("DARK") // LIGHT, DARK, NEON, SATELLITE
    val batterySaverMode = mutableStateOf(false)
    val dashcamCameraIndex = mutableStateOf(0)
    // Modo simulación GPS — no se persiste (solo para testing)
    val isGpsSimulationMode = mutableStateOf(false)
    
    // Custom Speedometer settings
    val customSpeedoShape = mutableStateOf("ARC")
    val customSpeedoNeedle = mutableStateOf("PLASMA")
    val customSpeedoThickness = mutableStateOf(0.08f)
    val customSpeedoBgUri = mutableStateOf("")    // URI de imagen de fondo (legacy)
    val customSpeedoBgPath = mutableStateOf("")    // Ruta del archivo recortado
    val customSpeedoBgOpacity = mutableStateOf(0.5f) // opacidad del fondo
    
    // Custom Vehicle Icon
    val customVehicleIconPath = mutableStateOf("") // Ruta del ícono personalizado del vehículo

    // ── Ajuste fino de luces del auto (debug/tuning) ─────────────────────────
    // Todos los valores son coordenadas en el bitmap 512×512 del glow
    val glowCarHalfW   = mutableStateOf(22f)   // semiancho visual del modelo (px en bitmap)
    val glowHeadY      = mutableStateOf(240f)  // Y de origen de los faros delanteros
    val glowHeadReach  = mutableStateOf(40f)   // Y hasta donde llega el haz de luz
    val glowHeadSpread = mutableStateOf(50f)   // cuánto se abre el cono lateralmente
    val glowTailY      = mutableStateOf(272f)  // Y de las luces traseras
    val glowTailRadius = mutableStateOf(14f)   // radio del glow trasero
    val glowIconSize   = mutableStateOf(1.5f)  // escala del símbolo en el mapa

    private var prefs: SharedPreferences? = null
    private var isInitialized = false

    // Función para leer lo guardado al abrir la app
    fun init(context: Context) {
        if (isInitialized) return
        prefs = context.getSharedPreferences("CarLauncherPrefs", Context.MODE_PRIVATE)
        
        speedoStyle.value = prefs?.getString("speedoStyle", "PREMIUM") ?: "PREMIUM"
        speedoColor.value = prefs?.getInt("speedoColor", Color.parseColor("#007AFF")) ?: Color.parseColor("#007AFF")
        vehicleType.value = prefs?.getString("vehicleType", "SEDAN") ?: "SEDAN"
        vehicle3DScale.value = prefs?.getFloat("vehicle3DScale", 0.15f) ?: 0.15f
        glowCarHalfW.value   = prefs?.getFloat("glowCarHalfW",   22f)  ?: 22f
        glowHeadY.value      = prefs?.getFloat("glowHeadY",      240f) ?: 240f
        glowHeadReach.value  = prefs?.getFloat("glowHeadReach",  40f)  ?: 40f
        glowHeadSpread.value = prefs?.getFloat("glowHeadSpread", 50f)  ?: 50f
        glowTailY.value      = prefs?.getFloat("glowTailY",      272f) ?: 272f
        glowTailRadius.value = prefs?.getFloat("glowTailRadius", 14f)  ?: 14f
        glowIconSize.value   = prefs?.getFloat("glowIconSize",   1.5f) ?: 1.5f
        uiColor.value = prefs?.getInt("uiColor", Color.parseColor("#007AFF")) ?: Color.parseColor("#007AFF")
        mapIconColor.value = prefs?.getInt("mapIconColor", Color.parseColor("#007AFF")) ?: Color.parseColor("#007AFF")
        isDarkMode.value = prefs?.getBoolean("isDarkMode", true) ?: true
        isMapDarkMode.value = prefs?.getBoolean("isMapDarkMode", true) ?: true
        mapStyle.value = prefs?.getString("mapStyle", "DARK") ?: "DARK"
        batterySaverMode.value = prefs?.getBoolean("batterySaverMode", false) ?: false
        dashcamCameraIndex.value = prefs?.getInt("dashcamCameraIndex", 0) ?: 0
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

    fun setVehicle3DScale(scale: Float) {
        vehicle3DScale.value = scale
        prefs?.edit()?.putFloat("vehicle3DScale", scale)?.apply()
    }

    fun setGlowCarHalfW(v: Float)   { glowCarHalfW.value   = v; prefs?.edit()?.putFloat("glowCarHalfW",   v)?.apply() }
    fun setGlowHeadY(v: Float)      { glowHeadY.value      = v; prefs?.edit()?.putFloat("glowHeadY",      v)?.apply() }
    fun setGlowHeadReach(v: Float)  { glowHeadReach.value  = v; prefs?.edit()?.putFloat("glowHeadReach",  v)?.apply() }
    fun setGlowHeadSpread(v: Float) { glowHeadSpread.value = v; prefs?.edit()?.putFloat("glowHeadSpread", v)?.apply() }
    fun setGlowTailY(v: Float)      { glowTailY.value      = v; prefs?.edit()?.putFloat("glowTailY",      v)?.apply() }
    fun setGlowTailRadius(v: Float) { glowTailRadius.value = v; prefs?.edit()?.putFloat("glowTailRadius", v)?.apply() }
    fun setGlowIconSize(v: Float)   { glowIconSize.value   = v; prefs?.edit()?.putFloat("glowIconSize",   v)?.apply() }

    fun setUiColor(color: Int) {
        uiColor.value = color
        prefs?.edit()?.putInt("uiColor", color)?.apply()
    }

    fun setMapIconColor(color: Int) {
        mapIconColor.value = color
        prefs?.edit()?.putInt("mapIconColor", color)?.apply()
    }

    fun setDarkMode(isDark: Boolean) {
        isDarkMode.value = isDark
        prefs?.edit()?.putBoolean("isDarkMode", isDark)?.apply()
    }

    fun setMapDarkMode(isDark: Boolean) {
        isMapDarkMode.value = isDark
        prefs?.edit()?.putBoolean("isMapDarkMode", isDark)?.apply()
    }

    fun setMapStyle(style: String) {
        mapStyle.value = style
        prefs?.edit()?.putString("mapStyle", style)?.apply()
    }

    fun setBatterySaverMode(enabled: Boolean) {
        batterySaverMode.value = enabled
        prefs?.edit()?.putBoolean("batterySaverMode", enabled)?.apply()
    }

    fun setDashcamCameraIndex(index: Int) {
        dashcamCameraIndex.value = index
        prefs?.edit()?.putInt("dashcamCameraIndex", index)?.apply()
    }

    fun setGpsSimulationMode(enabled: Boolean) {
        isGpsSimulationMode.value = enabled
        // No se persiste — siempre inicia desactivado
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