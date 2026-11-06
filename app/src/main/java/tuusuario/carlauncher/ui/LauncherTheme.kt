package com.tuusuario.carlauncher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class BentoPalette(
    val isDark: Boolean,
    val canvasBg: Color,
    val cardBg: Color,
    val subCardBg: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val navyHeroStart: Color,
    val navyHeroEnd: Color,
    val accent: Color,
    val dashcamTileBg: Color,
    val dashcamTileFg: Color,
    val routesTileBg: Color,
    val routesTileFg: Color,
    val recordsTileBg: Color,
    val recordsTileFg: Color,
    val trackInactive: Color
)

val LocalBentoPalette = staticCompositionLocalOf {
    buildBentoPalette(isDark = false, accentColor = Color(0xFF1A6BFF))
}

fun buildBentoPalette(isDark: Boolean, accentColor: Color): BentoPalette {
    return if (!isDark) {
        // Tema Claro "Automotive Ice" inspirado directamente en la interfaz de referencia
        BentoPalette(
            isDark = false,
            canvasBg = Color(0xFFEEF2F8),
            cardBg = Color(0xFFFFFFFF),
            subCardBg = Color(0xFFF6F9FE),
            cardBorder = Color(0xFFE2E8F2),
            textPrimary = Color(0xFF0F172A),
            textSecondary = Color(0xFF64748B),
            navyHeroStart = Color(0xFF0A2454),
            navyHeroEnd = Color(0xFF133B82),
            accent = accentColor,
            dashcamTileBg = Color(0xFFFFECEB),
            dashcamTileFg = Color(0xFFE53935),
            routesTileBg = Color(0xFFE6F0FF),
            routesTileFg = Color(0xFF1A6BFF),
            recordsTileBg = Color(0xFFFFF3DC),
            recordsTileFg = Color(0xFFE68A00),
            trackInactive = Color(0xFFE2E8F0)
        )
    } else {
        // Tema Oscuro "Night Cockpit" sin deslumbramiento nocturno
        BentoPalette(
            isDark = true,
            canvasBg = Color(0xFF080C14),
            cardBg = Color(0xFF121926),
            subCardBg = Color(0xFF1A2334),
            cardBorder = Color(0xFF233047),
            textPrimary = Color(0xFFF8FAFC),
            textSecondary = Color(0xFF94A3B8),
            navyHeroStart = Color(0xFF0E2C66),
            navyHeroEnd = Color(0xFF1B4BA3),
            accent = accentColor,
            dashcamTileBg = Color(0xFF33181C),
            dashcamTileFg = Color(0xFFFF6B6B),
            routesTileBg = Color(0xFF14284B),
            routesTileFg = Color(0xFF5CA0FF),
            recordsTileBg = Color(0xFF332611),
            recordsTileFg = Color(0xFFFFB74D),
            trackInactive = Color(0xFF283449)
        )
    }
}

/**
 * Interpola suavemente todos los colores del sistema Bento al cambiar entre Modo Claro y Modo Oscuro
 * (tanto manualmente como por horario automático), evitando destellos o saltos bruscos mientras conduces.
 */
@Composable
fun rememberAnimatedBentoPalette(isDark: Boolean, accentColor: Color): BentoPalette {
    val target = remember(isDark, accentColor) { buildBentoPalette(isDark, accentColor) }
    val spec = tween<Color>(durationMillis = 650, easing = FastOutSlowInEasing)

    val canvasBg by animateColorAsState(target.canvasBg, spec, label = "palCanvas")
    val cardBg by animateColorAsState(target.cardBg, spec, label = "palCard")
    val subCardBg by animateColorAsState(target.subCardBg, spec, label = "palSubCard")
    val cardBorder by animateColorAsState(target.cardBorder, spec, label = "palBorder")
    val textPrimary by animateColorAsState(target.textPrimary, spec, label = "palText1")
    val textSecondary by animateColorAsState(target.textSecondary, spec, label = "palText2")
    val navyHeroStart by animateColorAsState(target.navyHeroStart, spec, label = "palHero1")
    val navyHeroEnd by animateColorAsState(target.navyHeroEnd, spec, label = "palHero2")
    val accent by animateColorAsState(target.accent, spec, label = "palAccent")
    val dashcamTileBg by animateColorAsState(target.dashcamTileBg, spec, label = "palDashBg")
    val dashcamTileFg by animateColorAsState(target.dashcamTileFg, spec, label = "palDashFg")
    val routesTileBg by animateColorAsState(target.routesTileBg, spec, label = "palRouteBg")
    val routesTileFg by animateColorAsState(target.routesTileFg, spec, label = "palRouteFg")
    val recordsTileBg by animateColorAsState(target.recordsTileBg, spec, label = "palRecBg")
    val recordsTileFg by animateColorAsState(target.recordsTileFg, spec, label = "palRecFg")
    val trackInactive by animateColorAsState(target.trackInactive, spec, label = "palTrack")

    return BentoPalette(
        isDark = isDark,
        canvasBg = canvasBg,
        cardBg = cardBg,
        subCardBg = subCardBg,
        cardBorder = cardBorder,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        navyHeroStart = navyHeroStart,
        navyHeroEnd = navyHeroEnd,
        accent = accent,
        dashcamTileBg = dashcamTileBg,
        dashcamTileFg = dashcamTileFg,
        routesTileBg = routesTileBg,
        routesTileFg = routesTileFg,
        recordsTileBg = recordsTileBg,
        recordsTileFg = recordsTileFg,
        trackInactive = trackInactive
    )
}

fun buildMaterialColorScheme(isDark: Boolean, accentColor: Color): ColorScheme {
    return if (!isDark) {
        lightColorScheme(
            primary = accentColor,
            onPrimary = Color.White,
            primaryContainer = accentColor.copy(alpha = 0.14f),
            onPrimaryContainer = Color(0xFF0A2454),
            background = Color(0xFFEEF2F8),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFF1F5FB),
            onSurface = Color(0xFF0F172A),
            onSurfaceVariant = Color(0xFF64748B)
        )
    } else {
        darkColorScheme(
            primary = accentColor,
            onPrimary = Color.White,
            primaryContainer = accentColor.copy(alpha = 0.22f),
            onPrimaryContainer = Color(0xFFE2E8F0),
            background = Color(0xFF080C14),
            surface = Color(0xFF121926),
            surfaceVariant = Color(0xFF1C2638),
            onSurface = Color(0xFFF8FAFC),
            onSurfaceVariant = Color(0xFF94A3B8)
        )
    }
}

@Composable
fun rememberAnimatedMaterialColorScheme(isDark: Boolean, accentColor: Color): ColorScheme {
    val target = remember(isDark, accentColor) { buildMaterialColorScheme(isDark, accentColor) }
    val spec = tween<Color>(durationMillis = 650, easing = FastOutSlowInEasing)

    val primary by animateColorAsState(target.primary, spec, label = "matPrimary")
    val primaryContainer by animateColorAsState(target.primaryContainer, spec, label = "matPrimaryCont")
    val onPrimaryContainer by animateColorAsState(target.onPrimaryContainer, spec, label = "matOnPrimaryCont")
    val background by animateColorAsState(target.background, spec, label = "matBg")
    val surface by animateColorAsState(target.surface, spec, label = "matSurface")
    val surfaceVariant by animateColorAsState(target.surfaceVariant, spec, label = "matSurfaceVar")
    val onSurface by animateColorAsState(target.onSurface, spec, label = "matOnSurface")
    val onSurfaceVariant by animateColorAsState(target.onSurfaceVariant, spec, label = "matOnSurfaceVar")

    return if (!isDark) {
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            background = background,
            surface = surface,
            surfaceVariant = surfaceVariant,
            onSurface = onSurface,
            onSurfaceVariant = onSurfaceVariant
        )
    } else {
        darkColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            background = background,
            surface = surface,
            surfaceVariant = surfaceVariant,
            onSurface = onSurface,
            onSurfaceVariant = onSurfaceVariant
        )
    }
}
