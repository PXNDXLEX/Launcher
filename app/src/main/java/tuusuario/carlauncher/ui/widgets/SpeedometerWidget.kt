package com.tuusuario.carlauncher.ui.widgets

import android.Manifest
import android.content.pm.PackageManager
import android.os.Looper
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SpeedometerWidget() {
    val currentSpeedKmH = rememberGpsSpeed()
    val animatedSpeed by animateFloatAsState(
        targetValue = currentSpeedKmH,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "SpeedAnimation"
    )

    // Colores basados en tu imagen de referencia
    val activeTrackColor = Color(0xFFE91E63) // Rosa/Rojo intenso
    val inactiveTrackColor = Color(0xFF2A1B2E) // Morado muy oscuro
    val tickColor = Color.DarkGray
    val textColor = MaterialTheme.colorScheme.onSurface

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Marcador digital
        SpeedometerDial(
            speed = animatedSpeed, 
            maxSpeed = 220f, // Mantenemos el límite de 220km/h para el Kia Rio
            activeColor = activeTrackColor, 
            inactiveColor = inactiveTrackColor,
            tickColor = tickColor
        )

        // Textos centrales
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = currentSpeedKmH.toInt().toString(),
                color = textColor,
                fontSize = 72.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "km/h", 
                color = textColor.copy(alpha = 0.6f), 
                fontSize = 18.sp, 
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@Composable
fun SpeedometerDial(speed: Float, maxSpeed: Float, activeColor: Color, inactiveColor: Color, tickColor: Color) {
    val density = LocalDensity.current
    val textPaint = remember(density) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.LTGRAY
            textSize = with(density) { 12.sp.toPx() }
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
    }

    Canvas(modifier = Modifier.size(220.dp)) { // Tamaño un poco más grande para el detalle
        val sweepAngle = 240f
        val startAngle = 150f
        val arcWidth = 20.dp.toPx()
        val radius = size.width / 2

        // 1. Arco Inactivo (Fondo Oscuro)
        drawArc(
            color = inactiveColor,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            style = Stroke(width = arcWidth, cap = StrokeCap.Round),
            size = Size(size.width, size.height)
        )

        // 2. Arco Activo (Progreso Rosa/Rojo)
        val speedProgress = (speed / maxSpeed).coerceIn(0f, 1f)
        val activeSweepAngle = sweepAngle * speedProgress

        drawArc(
            color = activeColor,
            startAngle = startAngle,
            sweepAngle = activeSweepAngle,
            useCenter = false,
            style = Stroke(width = arcWidth, cap = StrokeCap.Round),
            size = Size(size.width, size.height)
        )

        // 3. Dibujar las rayitas (ticks) y los números
        val numTicks = 22 // Para ir de 0 a 220 de 10 en 10
        val tickStepAngle = sweepAngle / numTicks
        val center = Offset(size.width / 2, size.height / 2)
        val tickStartRadius = radius - (arcWidth / 2) - 8.dp.toPx() // Empieza justo dentro del arco

        drawIntoCanvas { canvas ->
            for (i in 0..numTicks) {
                val currentAngle = startAngle + (i * tickStepAngle)
                val angleRad = Math.toRadians(currentAngle.toDouble())

                val isMajorTick = i % 2 == 0 // Rayita larga y número cada 20 km/h (0, 20, 40...)
                val tickLength = if (isMajorTick) 12.dp.toPx() else 6.dp.toPx()

                // Coordenadas de inicio y fin para la rayita
                val startX = (center.x + tickStartRadius * cos(angleRad)).toFloat()
                val startY = (center.y + tickStartRadius * sin(angleRad)).toFloat()
                
                val endX = (center.x + (tickStartRadius - tickLength) * cos(angleRad)).toFloat()
                val endY = (center.y + (tickStartRadius - tickLength) * sin(angleRad)).toFloat()

                drawLine(
                    color = if (i * 10 <= speed) activeColor else tickColor,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 2.dp.toPx()
                )

                // Dibujar el número en los Major Ticks
                if (isMajorTick) {
                    val textRadius = tickStartRadius - tickLength - 14.dp.toPx()
                    val textX = (center.x + textRadius * cos(angleRad)).toFloat()
                    val textY = (center.y + textRadius * sin(angleRad)).toFloat()
                    
                    // Ajustamos Y un poco hacia abajo por la forma en que dibuja el texto nativo
                    val speedValue = (i * 10).toString()
                    canvas.nativeCanvas.drawText(speedValue, textX, textY + 12f, textPaint)
                }
            }
        }
    }
}

@Composable
fun rememberGpsSpeed(): Float {
    val context = LocalContext.current
    var speed by remember { mutableStateOf(0f) }

    DisposableEffect(context) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateIntervalMillis(500)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    if (location.hasSpeed()) {
                        speed = location.speed * 3.6f 
                    }
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }

        onDispose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
    return speed
}