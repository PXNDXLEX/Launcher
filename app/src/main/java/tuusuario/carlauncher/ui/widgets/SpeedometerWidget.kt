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
import androidx.compose.ui.unit.min
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.tuusuario.carlauncher.ui.AppSettings
import com.tuusuario.carlauncher.ui.NavigationState
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SpeedometerWidget() {
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
                        NavigationState.currentSpeedKmH.value = speed // Informamos al mapa
                    }
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
        onDispose { fusedLocationClient.removeLocationUpdates(locationCallback) }
    }

    val animatedSpeed by animateFloatAsState(
        targetValue = speed,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "SpeedAnimation"
    )

    val style = AppSettings.speedoStyle.value
    val activeColor = Color(AppSettings.speedoColor.value)
    
    // Adaptación a modo claro/oscuro para el fondo
    val isLight = MaterialTheme.colorScheme.background.red > 0.5f 
    val inactiveColor = if (isLight) Color.LightGray.copy(alpha=0.5f) else Color(0xFF2A1B2E)
    val textColor = MaterialTheme.colorScheme.onSurface

    // BoxWithConstraints nos permite saber el tamaño real del widget y encajar el dibujo sin cortarse
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val boxSize = min(maxWidth, maxHeight) * 0.9f // Ocupamos el 90% del espacio disponible
        
        SpeedometerDial(
            speed = animatedSpeed, 
            maxSpeed = 220f, 
            activeColor = activeColor, 
            inactiveColor = inactiveColor,
            textColor = textColor,
            style = style,
            modifier = Modifier.size(boxSize)
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = speed.toInt().toString(), color = textColor, fontSize = (boxSize.value * 0.35f).sp, fontWeight = FontWeight.Medium)
            Text(text = "km/h", color = textColor.copy(alpha = 0.6f), fontSize = (boxSize.value * 0.08f).sp, fontWeight = FontWeight.Normal)
        }
    }
}

@Composable
fun SpeedometerDial(speed: Float, maxSpeed: Float, activeColor: Color, inactiveColor: Color, textColor: Color, style: String, modifier: Modifier) {
    val density = LocalDensity.current
    val textPaint = remember(density, textColor) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.argb((textColor.alpha*255).toInt(), (textColor.red*255).toInt(), (textColor.green*255).toInt(), (textColor.blue*255).toInt())
            textSize = with(density) { 10.sp.toPx() }
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
    }

    Canvas(modifier = modifier) {
        val sweepAngle = 240f
        val startAngle = 150f
        val speedProgress = (speed / maxSpeed).coerceIn(0f, 1f)
        val activeSweepAngle = sweepAngle * speedProgress
        val radius = size.width / 2

        when (style) {
            "PREMIUM" -> {
                val arcWidth = size.width * 0.08f
                drawArc(color = inactiveColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = arcWidth, cap = StrokeCap.Round), size = Size(size.width, size.height))
                drawArc(color = activeColor, startAngle = startAngle, sweepAngle = activeSweepAngle, useCenter = false, style = Stroke(width = arcWidth, cap = StrokeCap.Round), size = Size(size.width, size.height))
                
                val numTicks = 22
                val tickStepAngle = sweepAngle / numTicks
                val center = Offset(size.width / 2, size.height / 2)
                val tickStartRadius = radius - (arcWidth / 2) - 10f

                drawIntoCanvas { canvas ->
                    for (i in 0..numTicks) {
                        val currentAngle = startAngle + (i * tickStepAngle)
                        val angleRad = Math.toRadians(currentAngle.toDouble())
                        val isMajorTick = i % 2 == 0
                        val tickLength = if (isMajorTick) size.width * 0.05f else size.width * 0.025f

                        val startX = (center.x + tickStartRadius * cos(angleRad)).toFloat()
                        val startY = (center.y + tickStartRadius * sin(angleRad)).toFloat()
                        val endX = (center.x + (tickStartRadius - tickLength) * cos(angleRad)).toFloat()
                        val endY = (center.y + (tickStartRadius - tickLength) * sin(angleRad)).toFloat()

                        val lineColor = if (i * 10 <= speed) activeColor else textColor.copy(alpha=0.3f)
                        drawLine(color = lineColor, start = Offset(startX, startY), end = Offset(endX, endY), strokeWidth = 5f)

                        if (isMajorTick) {
                            val textRadius = tickStartRadius - tickLength - 20f
                            val textX = (center.x + textRadius * cos(angleRad)).toFloat()
                            val textY = (center.y + textRadius * sin(angleRad)).toFloat()
                            canvas.nativeCanvas.drawText((i * 10).toString(), textX, textY + 10f, textPaint)
                        }
                    }
                }
            }
            "NEON" -> {
                val arcWidth = size.width * 0.04f
                // Dibujamos arcos múltiples para dar efecto de brillo/anillo neón
                drawArc(color = inactiveColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = arcWidth, cap = StrokeCap.Round), size = Size(size.width, size.height))
                drawArc(color = activeColor.copy(alpha=0.3f), startAngle = startAngle, sweepAngle = activeSweepAngle, useCenter = false, style = Stroke(width = arcWidth * 2.5f, cap = StrokeCap.Round), size = Size(size.width, size.height))
                drawArc(color = activeColor.copy(alpha=0.6f), startAngle = startAngle, sweepAngle = activeSweepAngle, useCenter = false, style = Stroke(width = arcWidth * 1.5f, cap = StrokeCap.Round), size = Size(size.width, size.height))
                drawArc(color = activeColor, startAngle = startAngle, sweepAngle = activeSweepAngle, useCenter = false, style = Stroke(width = arcWidth, cap = StrokeCap.Round), size = Size(size.width, size.height))
            }
            "MINIMAL" -> {
                val arcWidth = size.width * 0.02f
                drawArc(color = inactiveColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = arcWidth, cap = StrokeCap.Round), size = Size(size.width, size.height))
                drawArc(color = activeColor, startAngle = startAngle, sweepAngle = activeSweepAngle, useCenter = false, style = Stroke(width = arcWidth, cap = StrokeCap.Round), size = Size(size.width, size.height))
                
                // Punto indicador estilo minimalista
                val center = Offset(size.width / 2, size.height / 2)
                val angleRad = Math.toRadians((startAngle + activeSweepAngle).toDouble())
                val dotX = (center.x + radius * cos(angleRad)).toFloat()
                val dotY = (center.y + radius * sin(angleRad)).toFloat()
                drawCircle(color = activeColor, radius = arcWidth * 2.5f, center = Offset(dotX, dotY))
                drawCircle(color = Color.White, radius = arcWidth, center = Offset(dotX, dotY))
            }
        }
    }
}