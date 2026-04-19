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
import androidx.compose.ui.geometry.CornerRadius
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
                        NavigationState.currentSpeedKmH.value = speed 
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
    val baseColor = Color(AppSettings.speedoColor.value)
    val isLight = MaterialTheme.colorScheme.background.red > 0.5f 
    val inactiveColor = if (isLight) Color.LightGray.copy(alpha = 0.5f) else Color(0xFF1E1E1E)
    val textColor = MaterialTheme.colorScheme.onSurface
    val tickColor = if (isLight) Color.Gray else Color.DarkGray

    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val boxSize = min(maxWidth, maxHeight) * 0.9f 
        
        // El diseño del velocímetro
        SpeedometerDraw(
            speed = animatedSpeed, 
            maxSpeed = 220f, 
            activeColor = baseColor, 
            inactiveColor = inactiveColor,
            textColor = textColor,
            tickColor = tickColor,
            style = style,
            modifier = Modifier.size(boxSize)
        )

        // Textos adaptados al estilo
        if (style == "MINIMAL") {
            // Para el estilo Rectangular (Moto), el texto va más arriba y centrado sobre la barra
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (-30).dp)) {
                Text(text = speed.toInt().toString(), color = textColor, fontSize = (boxSize.value * 0.35f).sp, fontWeight = FontWeight.Bold)
                Text(text = "km/h", color = textColor.copy(alpha = 0.6f), fontSize = (boxSize.value * 0.1f).sp, fontWeight = FontWeight.Normal)
            }
        } else {
            // Para los circulares (PREMIUM y NEON), el texto va en el centro
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = speed.toInt().toString(), color = textColor, fontSize = (boxSize.value * 0.28f).sp, fontWeight = FontWeight.Bold)
                Text(text = "km/h", color = textColor.copy(alpha = 0.6f), fontSize = (boxSize.value * 0.08f).sp, fontWeight = FontWeight.Normal)
            }
        }
    }
}

@Composable
fun SpeedometerDraw(speed: Float, maxSpeed: Float, activeColor: Color, inactiveColor: Color, textColor: Color, tickColor: Color, style: String, modifier: Modifier) {
    val density = LocalDensity.current
    val textPaint = remember(density, textColor) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.argb((textColor.alpha*255).toInt(), (textColor.red*255).toInt(), (textColor.green*255).toInt(), (textColor.blue*255).toInt())
            textSize = with(density) { 12.sp.toPx() }
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
    }

    Canvas(modifier = modifier) {
        val speedProgress = (speed / maxSpeed).coerceIn(0f, 1f)
        
        when (style) {
            "PREMIUM" -> {
                // MODELO 1: Analógico Clásico
                val sweepAngle = 240f
                val startAngle = 150f
                val activeSweepAngle = sweepAngle * speedProgress
                val arcWidth = size.width * 0.05f
                val radius = size.width / 2

                drawArc(color = inactiveColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = arcWidth, cap = StrokeCap.Round), size = Size(size.width, size.height))
                drawArc(color = activeColor, startAngle = startAngle, sweepAngle = activeSweepAngle, useCenter = false, style = Stroke(width = arcWidth, cap = StrokeCap.Round), size = Size(size.width, size.height))
                
                val numTicks = 22 
                val tickStepAngle = sweepAngle / numTicks
                val center = Offset(size.width / 2, size.height / 2)
                val tickStartRadius = radius - (arcWidth / 2) - 15f

                drawIntoCanvas { canvas ->
                    for (i in 0..numTicks) {
                        val currentAngle = startAngle + (i * tickStepAngle)
                        val angleRad = Math.toRadians(currentAngle.toDouble())
                        val isMajorTick = i % 2 == 0
                        val tickLength = if (isMajorTick) size.width * 0.06f else size.width * 0.03f

                        val startX = (center.x + tickStartRadius * cos(angleRad)).toFloat()
                        val startY = (center.y + tickStartRadius * sin(angleRad)).toFloat()
                        val endX = (center.x + (tickStartRadius - tickLength) * cos(angleRad)).toFloat()
                        val endY = (center.y + (tickStartRadius - tickLength) * sin(angleRad)).toFloat()

                        val lineColor = if (i * 10 <= speed) activeColor else tickColor
                        drawLine(color = lineColor, start = Offset(startX, startY), end = Offset(endX, endY), strokeWidth = if (isMajorTick) 6f else 3f)

                        if (isMajorTick) {
                            val textRadius = tickStartRadius - tickLength - 30f
                            val textX = (center.x + textRadius * cos(angleRad)).toFloat()
                            val textY = (center.y + textRadius * sin(angleRad)).toFloat()
                            canvas.nativeCanvas.drawText((i * 10).toString(), textX, textY + 12f, textPaint)
                        }
                    }
                }
            }
            "NEON" -> {
                // MODELO 2: Neón que cambia de color
                val sweepAngle = 240f
                val startAngle = 150f
                val activeSweepAngle = sweepAngle * speedProgress
                val arcWidth = size.width * 0.08f
                
                // Color dinámico según la velocidad
                val dynamicColor = when {
                    speed < 60f -> Color(0xFF00FF00) // Verde
                    speed < 100f -> Color(0xFFFFEB3B) // Amarillo
                    else -> Color(0xFFFF1744) // Rojo
                }

                drawArc(color = inactiveColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = arcWidth, cap = StrokeCap.Round), size = Size(size.width, size.height))
                
                // Efecto de Brillo (Glow)
                drawArc(color = dynamicColor.copy(alpha=0.2f), startAngle = startAngle, sweepAngle = activeSweepAngle, useCenter = false, style = Stroke(width = arcWidth * 2.5f, cap = StrokeCap.Round), size = Size(size.width, size.height))
                drawArc(color = dynamicColor.copy(alpha=0.5f), startAngle = startAngle, sweepAngle = activeSweepAngle, useCenter = false, style = Stroke(width = arcWidth * 1.5f, cap = StrokeCap.Round), size = Size(size.width, size.height))
                
                // Línea principal brillante
                drawArc(color = dynamicColor, startAngle = startAngle, sweepAngle = activeSweepAngle, useCenter = false, style = Stroke(width = arcWidth, cap = StrokeCap.Round), size = Size(size.width, size.height))
            }
            "MINIMAL" -> {
                // MODELO 3: ESTILO MOTOCICLETA (Rectangular y gráfica de llenado horizontal)
                val barHeight = size.height * 0.25f
                val cornerRadius = CornerRadius(20f, 20f)
                val barY = (size.height / 2) + (size.height * 0.1f) // Lo ubicamos abajo para que el texto respire arriba

                // Fondo de la barra horizontal
                drawRoundRect(
                    color = inactiveColor,
                    topLeft = Offset(0f, barY),
                    size = Size(size.width, barHeight),
                    cornerRadius = cornerRadius
                )

                // Barra Activa de velocidad (Llena de izquierda a derecha)
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(0f, barY),
                    size = Size(size.width * speedProgress, barHeight),
                    cornerRadius = cornerRadius
                )

                // Máscara de Segmentos (Rayitas verticales transparentes para separar y darle look digital)
                val numSegments = 22
                val segmentWidth = size.width / numSegments
                for(i in 1 until numSegments) {
                    drawLine(
                        color = Color.Black.copy(alpha=0.8f), // Línea oscura que "corta" el color
                        start = Offset(i * segmentWidth, barY),
                        end = Offset(i * segmentWidth, barY + barHeight),
                        strokeWidth = 6f
                    )
                }
                
                // Borde delgado rodeando toda la barra para más elegancia
                drawRoundRect(
                    color = textColor.copy(alpha=0.3f),
                    topLeft = Offset(0f, barY),
                    size = Size(size.width, barHeight),
                    style = Stroke(width = 3f),
                    cornerRadius = cornerRadius
                )
            }
        }
    }
}