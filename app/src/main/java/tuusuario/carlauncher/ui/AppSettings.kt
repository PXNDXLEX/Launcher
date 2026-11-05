package com.tuusuario.carlauncher.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.compose.runtime.mutableStateOf

object AppSettings {
    // Variables de estado reactivas para toda la UI
    val speedoStyle = mutableStateOf("PREMIUM")
    val speedoColor = mutableStateOf(Color.parseColor("#1A6BFF"))
    val vehicleType = mutableStateOf("STYLUS") // Modelo 3D por defecto acorde al Kia Rio Stylus
    val vehicle3DScale = mutableStateOf(0.012f) // Escala del modelo 3D
    val uiColor = mutableStateOf(Color.parseColor("#1A6BFF"))
    val mapIconColor = mutableStateOf(Color.parseColor("#FFD600"))
    val isDarkMode = mutableStateOf(false) // Estilo claro moderno por defecto (como referencia)
    val autoThemeByTime = mutableStateOf(false) // Cambio automático día/noche opcional
    val isMapDarkMode = mutableStateOf(false)
    val mapStyle = mutableStateOf("LIGHT") // LIGHT, DARK, NEON, SATELLITE
    val batterySaverMode = mutableStateOf(false)
    val dashcamCameraIndex = mutableStateOf(0)
    // Asistente de voz Sara / Arlet
    val arletVoiceWakeEnabled = mutableStateOf(true)
    // Modo simulación GPS — no se persiste (solo para testing)
    val isGpsSimulationMode = mutableStateOf(false)
    // Modo dividido CarPlay en Navegación (Mapa + Música reducida al costado)
    val navSplitMode = mutableStateOf(true)

    // Personalización de la tarjeta principal del vehículo (Home Bento)
    val carBrandName = mutableStateOf("KIA")
    val carModelName = mutableStateOf("RIO STYLUS")
    val carYearOrPlate = mutableStateOf("AD513IM")
    val customHomeCarPhotoPath = mutableStateOf("") // Vacío = usa Kia Rio amarillo integrado
    val homeCarScale = mutableStateOf(1.14f) // Escala visual del auto en la tarjeta de Inicio

    // Custom Speedometer settings
    val customSpeedoShape = mutableStateOf("ARC")
    val customSpeedoNeedle = mutableStateOf("PLASMA")
    val customSpeedoThickness = mutableStateOf(0.08f)
    val customSpeedoBgUri = mutableStateOf("")
    val customSpeedoBgPath = mutableStateOf("")
    val customSpeedoBgOpacity = mutableStateOf(0.5f)

    // Custom Vehicle Icon (2D fallback en mapa)
    val customVehicleIconPath = mutableStateOf("")

    // ── Ajuste fino de luces del auto (debug/tuning) ─────────────────────────
    val glowCarHalfW   = mutableStateOf(22f)
    val glowHeadY      = mutableStateOf(240f)
    val glowHeadReach  = mutableStateOf(40f)
    val glowHeadSpread = mutableStateOf(50f)
    val glowTailY      = mutableStateOf(272f)
    val glowTailRadius = mutableStateOf(14f)
    val glowIconSize   = mutableStateOf(1.5f)

    private var prefs: SharedPreferences? = null
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        val p = context.getSharedPreferences("CarLauncherPrefs", Context.MODE_PRIVATE)
        prefs = p

        speedoStyle.value = p.getString("speedoStyle", "PREMIUM") ?: "PREMIUM"
        speedoColor.value = p.getInt("speedoColor", Color.parseColor("#1A6BFF"))
        vehicleType.value = p.getString("vehicleType", "STYLUS") ?: "STYLUS"

        loadVehicleSpecificSettings(vehicleType.value)

        uiColor.value = p.getInt("uiColor", Color.parseColor("#1A6BFF"))
        mapIconColor.value = p.getInt("mapIconColor", Color.parseColor("#FFD600"))
        autoThemeByTime.value = p.getBoolean("autoThemeByTime", false)
        navSplitMode.value = p.getBoolean("navSplitMode", true)

        carBrandName.value = p.getString("carBrandName", "KIA") ?: "KIA"
        carModelName.value = p.getString("carModelName", "RIO STYLUS") ?: "RIO STYLUS"
        carYearOrPlate.value = p.getString("carYearOrPlate", "AD513IM") ?: "AD513IM"
        customHomeCarPhotoPath.value = p.getString("customHomeCarPhotoPath", "") ?: ""
        homeCarScale.value = p.getFloat("homeCarScale", 1.14f)

        if (autoThemeByTime.value) {
            val calendar = java.util.Calendar.getInstance()
            val hourOfDay = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            val isNightTime = hourOfDay >= 19 || hourOfDay < 5
            isDarkMode.value = isNightTime
            isMapDarkMode.value = isNightTime
            val savedStyle = p.getString("mapStyle", "LIGHT") ?: "LIGHT"
            if (savedStyle == "LIGHT" || savedStyle == "DARK") {
                mapStyle.value = if (isNightTime) "DARK" else "LIGHT"
            } else {
                mapStyle.value = savedStyle
            }
        } else {
            // Respetar siempre la preferencia elegida por el usuario (por defecto modo claro moderno)
            isDarkMode.value = p.getBoolean("remakeDarkMode", false)
            isMapDarkMode.value = p.getBoolean("isMapDarkMode", false)
            mapStyle.value = p.getString("mapStyle", if (isDarkMode.value) "DARK" else "LIGHT") ?: "LIGHT"
        }

        batterySaverMode.value = p.getBoolean("batterySaverMode", false)
        dashcamCameraIndex.value = p.getInt("dashcamCameraIndex", 0)
        customSpeedoShape.value = p.getString("customSpeedoShape", "ARC") ?: "ARC"
        customSpeedoNeedle.value = p.getString("customSpeedoNeedle", "PLASMA") ?: "PLASMA"
        customSpeedoThickness.value = p.getFloat("customSpeedoThickness", 0.08f)
        customSpeedoBgUri.value = p.getString("customSpeedoBgUri", "") ?: ""
        customSpeedoBgPath.value = p.getString("customSpeedoBgPath", "") ?: ""
        customSpeedoBgOpacity.value = p.getFloat("customSpeedoBgOpacity", 0.5f)
        customVehicleIconPath.value = p.getString("customVehicleIconPath", "") ?: ""
        arletVoiceWakeEnabled.value = p.getBoolean("arletVoiceWakeEnabled", true)

        isInitialized = true
        try { NavigationState.isSettingsLoaded.value = true } catch (_: Exception) {}
    }

    private fun loadVehicleSpecificSettings(type: String) {
        val prefix = "${type}_"
        val defaultScale = if (type == "STYLUS" || type == "CORSA") 0.012f else 0.15f
        vehicle3DScale.value = prefs?.getFloat(prefix + "vehicle3DScale", defaultScale) ?: defaultScale
        glowCarHalfW.value   = prefs?.getFloat(prefix + "glowCarHalfW",   22f)  ?: 22f
        glowHeadY.value      = prefs?.getFloat(prefix + "glowHeadY",      240f) ?: 240f
        glowHeadReach.value  = prefs?.getFloat(prefix + "glowHeadReach",  40f)  ?: 40f
        glowHeadSpread.value = prefs?.getFloat(prefix + "glowHeadSpread", 50f)  ?: 50f
        glowTailY.value      = prefs?.getFloat(prefix + "glowTailY",      272f) ?: 272f
        glowTailRadius.value = prefs?.getFloat(prefix + "glowTailRadius", 14f)  ?: 14f
        glowIconSize.value   = prefs?.getFloat(prefix + "glowIconSize",   1.5f) ?: 1.5f
    }

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
        loadVehicleSpecificSettings(type)
    }

    fun setVehicle3DScale(scale: Float) {
        vehicle3DScale.value = scale
        prefs?.edit()?.putFloat("${vehicleType.value}_vehicle3DScale", scale)?.apply()
    }

    fun setGlowCarHalfW(v: Float)   { glowCarHalfW.value   = v; prefs?.edit()?.putFloat("${vehicleType.value}_glowCarHalfW",   v)?.apply() }
    fun setGlowHeadY(v: Float)      { glowHeadY.value      = v; prefs?.edit()?.putFloat("${vehicleType.value}_glowHeadY",      v)?.apply() }
    fun setGlowHeadReach(v: Float)  { glowHeadReach.value  = v; prefs?.edit()?.putFloat("${vehicleType.value}_glowHeadReach",  v)?.apply() }
    fun setGlowHeadSpread(v: Float) { glowHeadSpread.value = v; prefs?.edit()?.putFloat("${vehicleType.value}_glowHeadSpread", v)?.apply() }
    fun setGlowTailY(v: Float)      { glowTailY.value      = v; prefs?.edit()?.putFloat("${vehicleType.value}_glowTailY",      v)?.apply() }
    fun setGlowTailRadius(v: Float) { glowTailRadius.value = v; prefs?.edit()?.putFloat("${vehicleType.value}_glowTailRadius", v)?.apply() }
    fun setGlowIconSize(v: Float)   { glowIconSize.value   = v; prefs?.edit()?.putFloat("${vehicleType.value}_glowIconSize",   v)?.apply() }

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
        prefs?.edit()?.putBoolean("remakeDarkMode", isDark)?.putBoolean("isDarkMode", isDark)?.apply()
    }

    fun setAutoThemeByTime(enabled: Boolean) {
        autoThemeByTime.value = enabled
        prefs?.edit()?.putBoolean("autoThemeByTime", enabled)?.apply()
    }

    fun setNavSplitMode(split: Boolean) {
        navSplitMode.value = split
        prefs?.edit()?.putBoolean("navSplitMode", split)?.apply()
    }

    fun setCarBrandName(brand: String) {
        carBrandName.value = brand
        prefs?.edit()?.putString("carBrandName", brand)?.apply()
    }

    fun setCarModelName(model: String) {
        carModelName.value = model
        prefs?.edit()?.putString("carModelName", model)?.apply()
    }

    fun setCarYearOrPlate(value: String) {
        carYearOrPlate.value = value
        prefs?.edit()?.putString("carYearOrPlate", value)?.apply()
    }

    fun setCustomHomeCarPhotoPath(path: String) {
        customHomeCarPhotoPath.value = path
        prefs?.edit()?.putString("customHomeCarPhotoPath", path)?.apply()
    }

    fun setHomeCarScale(scale: Float) {
        homeCarScale.value = scale
        prefs?.edit()?.putFloat("homeCarScale", scale)?.apply()
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

    fun setArletVoiceWakeEnabled(enabled: Boolean) {
        arletVoiceWakeEnabled.value = enabled
        prefs?.edit()?.putBoolean("arletVoiceWakeEnabled", enabled)?.apply()
    }
}
