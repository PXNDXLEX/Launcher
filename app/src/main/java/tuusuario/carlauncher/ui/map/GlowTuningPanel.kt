package com.tuusuario.carlauncher.ui.map

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuusuario.carlauncher.ui.AppSettings

// ── Panel de ajuste fino en tiempo real ──────────────────────────────────────
//
// Uso: mostrar cuando el usuario quiere afinar la posición de las luces del
// auto y la escala del modelo 3D. Los valores se guardan en SharedPreferences
// automáticamente, así persisten entre sesiones de debug.
//
// Para fijarlo permanentemente: copiar los valores que muestra el panel
// en los defaults de AppSettings.init() y hacer un nuevo build.

@Composable
fun GlowTuningPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    onGlowChanged: () -> Unit   // callback para que NavigationMap regenere el glow
) {
    AnimatedVisibility(
        visible = visible,
        enter   = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit    = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(300.dp)
                .background(
                    Color(0xE6050510),
                    RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                )
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // ── Header ──────────────────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "🔦 Ajuste de Luces",
                        color = Color(0xFF00E5FF),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, null, tint = Color(0xFF888888), modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ── Preview del glow en tiempo real ─────────────────────────
                GlowPreviewCanvas(onGlowChanged)

                Spacer(Modifier.height(12.dp))
                Divider(color = Color(0xFF1A1A3A))
                Spacer(Modifier.height(8.dp))

                // ── Sliders de faros delanteros ──────────────────────────────
                SectionLabel("FAROS DELANTEROS")

                TuningSlider(
                    label = "Ancho del auto (carHalfW)",
                    value = AppSettings.glowCarHalfW.value,
                    range  = 5f..80f,
                    format = "%.0f px"
                ) { AppSettings.setGlowCarHalfW(it); onGlowChanged() }

                TuningSlider(
                    label = "Origen Y del faro (headY)",
                    value = AppSettings.glowHeadY.value,
                    range  = 150f..400f,
                    format = "%.0f px"
                ) { AppSettings.setGlowHeadY(it); onGlowChanged() }

                TuningSlider(
                    label = "Alcance del haz (headReach)",
                    value = AppSettings.glowHeadReach.value,
                    range  = 0f..250f,
                    format = "%.0f px"
                ) { AppSettings.setGlowHeadReach(it); onGlowChanged() }

                TuningSlider(
                    label = "Apertura del cono (headSpread)",
                    value = AppSettings.glowHeadSpread.value,
                    range  = 0f..150f,
                    format = "%.0f px"
                ) { AppSettings.setGlowHeadSpread(it); onGlowChanged() }

                Spacer(Modifier.height(8.dp))
                Divider(color = Color(0xFF1A1A3A))
                Spacer(Modifier.height(8.dp))

                // ── Sliders de luces traseras ────────────────────────────────
                SectionLabel("LUCES TRASERAS")

                TuningSlider(
                    label = "Posición Y trasera (tailY)",
                    value = AppSettings.glowTailY.value,
                    range  = 150f..400f,
                    format = "%.0f px"
                ) { AppSettings.setGlowTailY(it); onGlowChanged() }

                TuningSlider(
                    label = "Radio del glow (tailRadius)",
                    value = AppSettings.glowTailRadius.value,
                    range  = 4f..60f,
                    format = "%.0f px"
                ) { AppSettings.setGlowTailRadius(it); onGlowChanged() }

                Spacer(Modifier.height(8.dp))
                Divider(color = Color(0xFF1A1A3A))
                Spacer(Modifier.height(8.dp))

                // ── Tamaño del icono en el mapa ──────────────────────────────
                SectionLabel("TAMAÑO EN MAPA")

                TuningSlider(
                    label = "Escala del icono (iconSize)",
                    value = AppSettings.glowIconSize.value,
                    range  = 0.3f..5f,
                    format = "%.2f×"
                ) { AppSettings.setGlowIconSize(it); onGlowChanged() }

                Spacer(Modifier.height(8.dp))
                Divider(color = Color(0xFF1A1A3A))
                Spacer(Modifier.height(8.dp))

                // ── Escala del modelo 3D ─────────────────────────────────────
                SectionLabel("MODELO 3D")

                // Slider de escala del modelo 3D (0.1 = pequeño, 0.3 = grande)
                TuningSlider(
                    label = "Escala modelo (vehicle3DScale)",
                    value = AppSettings.vehicle3DScale.value,
                    range  = 0.1f..0.3f,
                    format = "%.3f"
                ) { AppSettings.setVehicle3DScale(it) }

                Spacer(Modifier.height(12.dp))

                // ── Botón Reset ──────────────────────────────────────────────
                OutlinedButton(
                    onClick = {
                        AppSettings.setGlowCarHalfW(22f)
                        AppSettings.setGlowHeadY(240f)
                        AppSettings.setGlowHeadReach(40f)
                        AppSettings.setGlowHeadSpread(50f)
                        AppSettings.setGlowTailY(272f)
                        AppSettings.setGlowTailRadius(14f)
                        AppSettings.setGlowIconSize(1.5f)
                        AppSettings.setVehicle3DScale(0.15f)
                        onGlowChanged()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252))
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reset a defaults", fontSize = 12.sp)
                }

                Spacer(Modifier.height(8.dp))

                // ── Valores actuales (para copiar al código) ─────────────────
                val vals = buildString {
                    appendLine("// Pegar en AppSettings.init():")
                    appendLine("glowCarHalfW   = ${AppSettings.glowCarHalfW.value}")
                    appendLine("glowHeadY      = ${AppSettings.glowHeadY.value}")
                    appendLine("glowHeadReach  = ${AppSettings.glowHeadReach.value}")
                    appendLine("glowHeadSpread = ${AppSettings.glowHeadSpread.value}")
                    appendLine("glowTailY      = ${AppSettings.glowTailY.value}")
                    appendLine("glowTailRadius = ${AppSettings.glowTailRadius.value}")
                    appendLine("glowIconSize   = ${AppSettings.glowIconSize.value}")
                    appendLine("vehicle3DScale = ${AppSettings.vehicle3DScale.value}")
                }
                Text(
                    vals,
                    color = Color(0xFF556677),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 13.sp
                )
            }
        }
    }
}

// ── Preview en canvas del glow actualizado ────────────────────────────────────
@Composable
private fun GlowPreviewCanvas(onGlowChanged: () -> Unit) {
    // Suscribirse a los estados — si cambian, recomponemos y redibujamos
    val halfW   = AppSettings.glowCarHalfW.value
    val headY   = AppSettings.glowHeadY.value
    val reach   = AppSettings.glowHeadReach.value
    val spread  = AppSettings.glowHeadSpread.value
    val tailY   = AppSettings.glowTailY.value
    val tailR   = AppSettings.glowTailRadius.value

    val bmp: Bitmap = remember(halfW, headY, reach, spread, tailY, tailR) {
        drawCarLightsGlow(halfW, headY, reach, spread, tailY, tailR)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF08080F)),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Preview glow",
            modifier = Modifier.size(120.dp)
        )
        Text(
            "PREVIEW (512×512 → escala)",
            color = Color(0xFF334455),
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
        )
    }
}

// ── Helper: slider con label y valor ─────────────────────────────────────────
@Composable
private fun TuningSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color(0xFF8899AA), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text(
                String.format(format, value),
                color = Color(0xFF00E5FF),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth().height(28.dp),
            colors = SliderDefaults.colors(
                thumbColor       = Color(0xFF00E5FF),
                activeTrackColor = Color(0xFF007799),
                inactiveTrackColor = Color(0xFF1A2233)
            )
        )
    }
}

// ── Helper: título de sección ─────────────────────────────────────────────────
@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = Color(0xFF445566),
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}
