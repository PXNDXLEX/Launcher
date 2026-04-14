package com.tuusuario.carlauncher.ui.widgets

import android.annotation.SuppressLint
import android.os.Looper
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.location.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SpeedometerWidget() {
    // 1. Obtenemos la velocidad real del GPS
    val currentSpeedKmH = rememberGpsSpeed()

    // 2. Animamos la aguja para que el movimiento sea ultra fluido
    val animatedSpeed by animateFloatAsState(
        targetValue = currentSpeedKmH,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "SpeedNeedleAnimation"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 3. Dibujamos el diseño analógico (Arco y Aguja)
        SpeedometerDial(speed = animatedSpeed, maxSpeed = 220f)

        // 4. Texto digital en el centro
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = currentSpeedKmH.toInt().toString(),
                color = Color.White,
                fontSize = 64.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "KM/H",
                color = Color.Gray,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SpeedometerDial(speed: Float, maxSpeed: Float) {
    Canvas(modifier = Modifier.size(200.dp)) {
        val sweepAngle = 240f // El arco no es un círculo completo
        val startAngle = 150f // Empieza desde abajo a la izquierda

        // Dibujar el fondo del arco (Gris oscuro)
        drawArc(
            color = Color(0xFF3E3E46),
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round),
            size = Size(size.width, size.height)
        )

        // Calcular cuánto se ha llenado el arco basado en la velocidad
        val speedProgress = (speed / maxSpeed).coerceIn(0f, 1f)
        val activeSweepAngle = sweepAngle * speedProgress

        // Dibujar el arco activo (Rojo/Naranja estilo deportivo)
        drawArc(
            color = Color(0xFFE53935),
            startAngle = startAngle,
            sweepAngle = activeSweepAngle,
            useCenter = false,
            style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round),
            size = Size(size.width, size.height)
        )

        // Dibujar la Aguja
        val needleAngle = startAngle + activeSweepAngle
        rotate(degrees = needleAngle + 90f) { // +90 por la orientación del Canvas
            drawLine(
                color = Color.White,
                start = Offset(center.x, center.y),
                end = Offset(center.x, 20.dp.toPx()), // Longitud de la aguja
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

// --- LÓGICA DEL GPS EN SEGUNDO PLANO ---
@SuppressLint("MissingPermission") // Asumimos que pedimos permisos en la pantalla de bienvenida
@Composable
fun rememberGpsSpeed(): Float {
    val context = LocalContext.current
    var speed by remember { mutableStateOf(0f) }

    DisposableEffect(context) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateIntervalMillis(500) // Actualizar cada medio segundo para mayor fluidez
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    if (location.hasSpeed()) {
                        // El GPS devuelve metros por segundo. Multiplicamos por 3.6 para KM/H
                        speed = location.speed * 3.6f 
                    }
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        onDispose {
            // Limpiar el sensor cuando el widget no esté en pantalla
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    return speed
}