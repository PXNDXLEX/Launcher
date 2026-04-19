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
import androidx.compose.ui.graphics.PathEffect
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
    
    // Adaptación a modo claro/oscuro para el fondo y las letras
    val isLight = MaterialTheme.colorScheme.background.red > 0.5f 
    val inactiveColor = if (isLight) Color.LightGray.copy(alpha = 0.4f) else Color(0xFF2A1B2E)
    val textColor = MaterialTheme.colorScheme.onSurface
    val tickColor = if (isLight) Color.Gray else Color.DarkGray

    // BoxWithConstraints escala el reloj para que encaje perfecto sin cortarse
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val boxSize = min(maxWidth, maxHeight) * 0.85f // Usa el 85% del espacio disponible
        
        SpeedometerDial(
            speed = animatedSpeed, 
            maxSpeed = 220f, 
            activeColor = activeColor, 
            inactiveColor = inactiveColor,
            textColor = textColor,
            tickColor = tickColor,
            style = style,
            modifier = Modifier.size(boxSize)
        )

        // Textos Centrales Ajustables
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = speed.toInt().toString(), color = textColor, fontSize = (boxSize.value * 0.25f).sp, fontWeight = FontWeight.Bold)
            Text(text = "km/h", color = textColor.copy(alpha = 0.6f), fontSize = (boxSize.value * 0.08f).sp, fontWeight = FontWeight.Normal)
        }
    }
}

@Composable
fun SpeedometerDial(speed: Float, maxSpeed: Float, activeColor: Color, inactiveColor: Color, textColor: Color, tickColor: Color, style: String, modifier: Modifier) {
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
        val sweepAngle = 240f
        val startAngle = 150f
        val speedProgress = (speed / maxSpeed).coerceIn(0f, 1f)
        val activeSweepAngle = sweepAngle * speedProgress
        val radius = size.width / 2

        when (style) {
            "PREMIUM" -> {
                // MODELO 1: Analógico Clásico con marcas completas (Líneas largas y cortas)
                val arcWidth = size.width * 0.04f // Arco más delgado como soporte
                drawArc(color = inactiveColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = arcWidth, cap = StrokeCap.Round), size = Size(size.width, size.height))
                drawArc(color = activeColor, startAngle = startAngle, sweepAngle = activeSweepAngle, useCenter = false, style = Stroke(width = arcWidth, cap = StrokeCap.Round), size = Size(size.width, size.height))
                
                val numTicks = 22 // Ticks de 0 a 220 (de 10 en 10)
                val tickStepAngle = sweepAngle / numTicks
                val center = Offset(size.width / 2, size.height / 2)
                val tickStartRadius = radius - (arcWidth / 2) - 15f // Separado hacia adentro

                drawIntoCanvas { canvas ->
                    for (i in 0..numTicks) {
                        val currentAngle = startAngle + (i * tickStepAngle)
                        val angleRad = Math.toRadians(currentAngle.toDouble())
                        val isMajorTick = i % 2 == 0 // Rayas principales cada 20 km/h
                        val tickLength = if (isMajorTick) size.width * 0.06f else size.width * 0.03f

                        val startX = (center.x + tickStartRadius * cos(angleRad)).toFloat()
                        val startY = (center.y + tickStartRadius * sin(angleRad)).toFloat()
                        val endX = (center.x + (tickStartRadius - tickLength) * cos(angleRad)).toFloat()
                        val endY = (center.y + (tickStartRadius - tickLength) * sin(angleRad)).toFloat()

                        val lineColor = if (i * 10 <= speed) activeColor else tickColor
                        drawLine(color = lineColor, start = Offset(startX, startY), end = Offset(endX, endY), strokeWidth = if (isMajorTick) 6f else 3f)

                        // Números debajo de los ticks mayores
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
                // MODELO 2: Deportivo Segmentado (Línea punteada gruesa simulando LEDs digitales)
                val arcWidth = size.width * 0.09f
                // PathEffect crea los bloques: línea de 20px, espacio de 10px
                val segmentedDash = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f)
                
                // Fondo oscuro segmentado
                drawArc(color = inactiveColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = arcWidth, pathEffect = segmentedDash), size = Size(size.width, size.height))
                // Relleno segmentado del color elegido
                drawArc(color = activeColor, startAngle = startAngle, sweepAngle = activeSweepAngle, useCenter = false, style = Stroke(width = arcWidth, pathEffect = segmentedDash), size = Size(size.width, size.height))
                
                // Un anillo interno fino para darle más estilo
                drawArc(color = activeColor.copy(alpha=0.3f), startAngle = startAngle - 2f, sweepAngle = sweepAngle + 4f, useCenter = false, style = Stroke(width = 3f), size = Size(size.width, size.height))
            }
            "MINIMAL" -> {
                // MODELO 3: Futurista Minimalista (Línea muy delgada y punto brillante)
                val arcWidth = size.width * 0.015f // Línea súper fina
                
                // Arco guía
                drawArc(color = inactiveColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = arcWidth, cap = StrokeCap.Round), size = Size(size.width, size.height))
                // Arco principal
                drawArc(color = activeColor, startAngle = startAngle, sweepAngle = activeSweepAngle, useCenter = false, style = Stroke(width = arcWidth, cap = StrokeCap.Round), size = Size(size.width, size.height))
                
                // Punto indicador estilo "radar" al final del recorrido actual
                val center = Offset(size.width / 2, size.height / 2)
                val angleRad = Math.toRadians((startAngle + activeSweepAngle).toDouble())
                val dotX = (center.x + radius * cos(angleRad)).toFloat()
                val dotY = (center.y + radius * sin(angleRad)).toFloat()
                
                // Halo difuminado detrás del punto
                drawCircle(color = activeColor.copy(alpha=0.4f), radius = arcWidth * 6f, center = Offset(dotX, dotY))
                // Punto principal
                drawCircle(color = activeColor, radius = arcWidth * 3f, center = Offset(dotX, dotY))
                // Brillo blanco central
                drawCircle(color = Color.White, radius = arcWidth * 1.5f, center = Offset(dotX, dotY))
            }
        }
    }
}