package com.tuusuario.carlauncher.ui.widgets

import android.Manifest
import android.content.pm.PackageManager
import android.os.Looper
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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

    val style = AppSettings.speedoStyle.value // Ahora soporta: PREMIUM, NEON, RACING, CYBER
    val baseColor = Color(AppSettings.speedoColor.value)
    val isLight = MaterialTheme.colorScheme.background.red > 0.5f 
    val inactiveColor = if (isLight) Color.LightGray.copy(alpha = 0.3f) else Color(0xFF1A1A24)
    val textColor = MaterialTheme.colorScheme.onSurface
    val tickColor = if (isLight) Color.Gray else Color.DarkGray

    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val boxSize = min(maxWidth, maxHeight) * 0.9f 
        
        // El diseño del velocímetro dibujado a bajo nivel
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

        // Ubicación y estilo de los Textos según el modelo
        when (style) {
            "RACING" -> {
                // Modelo Rectangular (Moto): Texto en la parte central, desplazado para dejar espacio a la barra
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (-10).dp)) {
                    Text(text = speed.toInt().toString(), color = textColor, fontSize = (boxSize.value * 0.32f).sp, fontWeight = FontWeight.Black)
                    Text(text = "KM/H", color = textColor.copy(alpha = 0.5f), fontSize = (boxSize.value * 0.08f).sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
            }
            "CYBER" -> {
                // Modelo Futurista: Texto en el centro con decoraciones (Líneas debajo)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = 15.dp)) {
                    Text(text = speed.toInt().toString(), color = textColor, fontSize = (boxSize.value * 0.30f).sp, fontWeight = FontWeight.Bold)
                    Box(modifier = Modifier.width(60.dp).height(2.dp).background(baseColor.copy(alpha = 0.8f)).padding(vertical = 4.dp))
                    Text(text = "K M / H", color = baseColor.copy(alpha = 0.8f), fontSize = (boxSize.value * 0.06f).sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                }
            }
            else -> {
                // Para los circulares (PREMIUM y NEON): Texto centrado limpio
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = speed.toInt().toString(), color = textColor, fontSize = (boxSize.value * 0.28f).sp, fontWeight = FontWeight.Bold)
                    Text(text = "km/h", color = textColor.copy(alpha = 0.6f), fontSize = (boxSize.value * 0.08f).sp, fontWeight = FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
fun SpeedometerDraw(speed: Float, maxSpeed: Float, activeColor: Color, inactiveColor: Color, textColor: Color, tickColor: Color, style: String, modifier: Modifier) {
    val density = LocalDensity.current
    
    // Pintura base para textos
    val baseTextPaint = remember(density, textColor) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.argb((textColor.alpha*255).toInt(), (textColor.red*255).toInt(), (textColor.green*255).toInt(), (textColor.blue*255).toInt())
            textSize = with(density) { 11.sp.toPx() }
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }

    Canvas(modifier = modifier) {
        val speedProgress = (speed / maxSpeed).coerceIn(0f, 1f)
        
        when (style) {
            "PREMIUM" -> {
                // ----------------------------------------------------
                // MODELO 1: ANALÓGICO CLÁSICO DETALLADO
                // ----------------------------------------------------
                val sweepAngle = 240f
                val startAngle = 150f
                val activeSweepAngle = sweepAngle * speedProgress
                val arcWidth = size.width * 0.04f
                val radius = size.width / 2

                // Fondo de pista
                drawArc(color = inactiveColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = arcWidth, cap = StrokeCap.Round), size = Size(size.width, size.height))
                // Pista Activa
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
                            canvas.nativeCanvas.drawText((i * 10).toString(), textX, textY + 12f, baseTextPaint)
                        }
                    }
                }
            }

            "NEON" -> {
                // ----------------------------------------------------
                // MODELO 2: DEPORTIVO NEÓN CON NÚMEROS Y COLOR DINÁMICO
                // ----------------------------------------------------
                val sweepAngle = 240f
                val startAngle = 150f
                val activeSweepAngle = sweepAngle * speedProgress
                val arcWidth = size.width * 0.06f
                val radius = size.width / 2
                
                // Color dinámico de semáforo
                val dynamicColor = when {
                    speed < 60f -> Color(0xFF00FFCC) // Cian/Verde brillante
                    speed < 110f -> Color(0xFFFFD54F) // Amarillo
                    else -> Color(0xFFFF1744) // Rojo Peligro
                }

                // Pintura para el texto dinámico que brilla del mismo color
                val neonTextPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb((dynamicColor.alpha*255).toInt(), (dynamicColor.red*255).toInt(), (dynamicColor.green*255).toInt(), (dynamicColor.blue*255).toInt())
                    textSize = with(density) { 11.sp.toPx() }
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }

                // Pista de fondo apagada
                drawArc(color = inactiveColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = arcWidth, cap = StrokeCap.Round), size = Size(size.width, size.height))
                
                // Pista Activa (Glow + Línea sólida)
                drawArc(color = dynamicColor.copy(alpha=0.2f), startAngle = startAngle, sweepAngle = activeSweepAngle, useCenter = false, style = Stroke(width = arcWidth * 2.5f, cap = StrokeCap.Round), size = Size(size.width, size.height))
                drawArc(color = dynamicColor.copy(alpha=0.5f), startAngle = startAngle, sweepAngle = activeSweepAngle, useCenter = false, style = Stroke(width = arcWidth * 1.5f, cap = StrokeCap.Round), size = Size(size.width, size.height))
                drawArc(color = dynamicColor, startAngle = startAngle, sweepAngle = activeSweepAngle, useCenter = false, style = Stroke(width = arcWidth, cap = StrokeCap.Round), size = Size(size.width, size.height))

                // Ticks y números dinámicos
                val numTicks = 22 
                val tickStepAngle = sweepAngle / numTicks
                val center = Offset(size.width / 2, size.height / 2)
                val tickStartRadius = radius - (arcWidth / 2) - 15f

                drawIntoCanvas { canvas ->
                    for (i in 0..numTicks) {
                        val currentAngle = startAngle + (i * tickStepAngle)
                        val angleRad = Math.toRadians(currentAngle.toDouble())
                        val isMajorTick = i % 2 == 0
                        val tickLength = if (isMajorTick) size.width * 0.05f else size.width * 0.02f

                        val startX = (center.x + tickStartRadius * cos(angleRad)).toFloat()
                        val startY = (center.y + tickStartRadius * sin(angleRad)).toFloat()
                        val endX = (center.x + (tickStartRadius - tickLength) * cos(angleRad)).toFloat()
                        val endY = (center.y + (tickStartRadius - tickLength) * sin(angleRad)).toFloat()

                        // Los ticks encendidos brillan con el color de la velocidad actual
                        val isLit = (i * 10) <= speed
                        val drawColor = if (isLit) dynamicColor else tickColor.copy(alpha = 0.3f)
                        
                        drawLine(color = drawColor, start = Offset(startX, startY), end = Offset(endX, endY), strokeWidth = if (isMajorTick) 5f else 3f)

                        if (isMajorTick) {
                            val textRadius = tickStartRadius - tickLength - 30f
                            val textX = (center.x + textRadius * cos(angleRad)).toFloat()
                            val textY = (center.y + textRadius * sin(angleRad)).toFloat()
                            // El número brilla solo si la aguja ya lo pasó
                            val currentTextPaint = if (isLit) neonTextPaint else baseTextPaint
                            canvas.nativeCanvas.drawText((i * 10).toString(), textX, textY + 12f, currentTextPaint)
                        }
                    }
                }
            }

            "RACING" -> {
                // ----------------------------------------------------
                // MODELO 3: RECTANGULAR DE MOTO (Completamente rediseñado y Premium)
                // ----------------------------------------------------
                val barHeight = size.height * 0.12f
                val cornerRadius = CornerRadius(10f, 10f)
                val barY = size.height * 0.70f // Base de la barra inferior

                // Números en escala sobre la barra (0, 50, 100, 150, 220)
                val scaleValues = listOf(0, 50, 100, 150, 220)
                drawIntoCanvas { canvas ->
                    scaleValues.forEach { value ->
                        val ratio = value / 220f
                        val posX = size.width * ratio
                        // Para que el 0 y el 220 no se salgan de la pantalla
                        val adjustedX = when (value) {
                            0 -> posX + 15f
                            220 -> posX - 25f
                            else -> posX
                        }
                        val isLit = speed >= value
                        val colorToUse = if (isLit) activeColor else tickColor
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb((colorToUse.alpha*255).toInt(), (colorToUse.red*255).toInt(), (colorToUse.green*255).toInt(), (colorToUse.blue*255).toInt())
                            textSize = with(density) { 10.sp.toPx() }
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                        canvas.nativeCanvas.drawText(value.toString(), adjustedX, barY - 15f, paint)
                    }
                }

                // Fondo oscuro biselado
                drawRoundRect(
                    color = inactiveColor.copy(alpha = 0.8f),
                    topLeft = Offset(0f, barY),
                    size = Size(size.width, barHeight),
                    cornerRadius = cornerRadius
                )

                // Relleno Activo con Gradiente lineal para un look Racing
                val activeGradient = Brush.horizontalGradient(
                    colors = listOf(activeColor.copy(alpha = 0.3f), activeColor, Color.White),
                    startX = 0f,
                    endX = size.width * speedProgress
                )
                
                if (speedProgress > 0.01f) {
                    drawRoundRect(
                        brush = activeGradient,
                        topLeft = Offset(0f, barY),
                        size = Size(size.width * speedProgress, barHeight),
                        cornerRadius = cornerRadius
                    )
                }

                // Ticks (Segmentos) sobre la barra para darle textura digital
                val numSegments = 44 // Cortes finos
                val segmentWidth = size.width / numSegments
                for(i in 1 until numSegments) {
                    val x = i * segmentWidth
                    // Rayas más largas cada 5 espacios
                    val isMajor = i % 10 == 0
                    val startYOffset = if (isMajor) barY - 5f else barY
                    drawLine(
                        color = MaterialTheme.colorScheme.background, // Color de corte transparente
                        start = Offset(x, startYOffset),
                        end = Offset(x, barY + barHeight),
                        strokeWidth = if (isMajor) 4f else 2f
                    )
                }

                // Borde de cromo/metálico envolviendo la barra
                drawRoundRect(
                    color = textColor.copy(alpha=0.2f),
                    topLeft = Offset(0f, barY),
                    size = Size(size.width, barHeight),
                    style = Stroke(width = 2f),
                    cornerRadius = cornerRadius
                )
            }

            "CYBER" -> {
                // ----------------------------------------------------
                // MODELO 4: ALTA TECNOLOGÍA (El diseño de la foto nueva)
                // ----------------------------------------------------
                val sweepAngle = 260f
                val startAngle = 140f
                val activeSweepAngle = sweepAngle * speedProgress
                val radius = size.width / 2
                val center = Offset(size.width / 2, size.height / 2)

                // 1. Arco Exterior Súper Fino y Brillante
                val outerRadius = radius - 5f
                drawArc(
                    color = activeColor.copy(alpha = 0.2f),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = 2f, cap = StrokeCap.Round),
                    size = Size(outerRadius * 2, outerRadius * 2),
                    topLeft = Offset(center.x - outerRadius, center.y - outerRadius)
                )
                // Brillo de avance exterior
                drawArc(
                    color = activeColor,
                    startAngle = startAngle - 2f,
                    sweepAngle = activeSweepAngle + 4f,
                    useCenter = false,
                    style = Stroke(width = 6f, cap = StrokeCap.Round),
                    size = Size(outerRadius * 2, outerRadius * 2),
                    topLeft = Offset(center.x - outerRadius, center.y - outerRadius)
                )

                // 2. Arco Interior Grueso y Segmentado
                val innerRadius = radius - 35f
                val innerArcWidth = 20f
                val segmentEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 5f), 0f)

                // Pista Interior Apagada
                drawArc(
                    color = inactiveColor,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = innerArcWidth, cap = StrokeCap.Butt, pathEffect = segmentEffect),
                    size = Size(innerRadius * 2, innerRadius * 2),
                    topLeft = Offset(center.x - innerRadius, center.y - innerRadius)
                )
                // Pista Interior Encendida (Gradiente Angular)
                drawArc(
                    color = activeColor,
                    startAngle = startAngle,
                    sweepAngle = activeSweepAngle,
                    useCenter = false,
                    style = Stroke(width = innerArcWidth, cap = StrokeCap.Butt, pathEffect = segmentEffect),
                    size = Size(innerRadius * 2, innerRadius * 2),
                    topLeft = Offset(center.x - innerRadius, center.y - innerRadius)
                )

                // 3. Decoraciones Sci-Fi Internas (Líneas de retícula y puntos)
                val angleRad = Math.toRadians((startAngle + activeSweepAngle).toDouble())
                val dotX = (center.x + innerRadius * cos(angleRad)).toFloat()
                val dotY = (center.y + innerRadius * sin(angleRad)).toFloat()
                
                // Punto indicador brillante en la cabeza de la barra
                drawCircle(color = Color.White, radius = 6f, center = Offset(dotX, dotY))
                drawCircle(color = activeColor.copy(alpha = 0.5f), radius = 12f, center = Offset(dotX, dotY))

                // Ticks internos muy finos para dar textura de radar
                val numCyberTicks = 12
                val tickStepAngle = sweepAngle / numCyberTicks
                for (i in 0..numCyberTicks) {
                    val currentAngle = startAngle + (i * tickStepAngle)
                    val rad = Math.toRadians(currentAngle.toDouble())
                    val startX = (center.x + (innerRadius - 20f) * cos(rad)).toFloat()
                    val startY = (center.y + (innerRadius - 20f) * sin(rad)).toFloat()
                    val endX = (center.x + (innerRadius - 30f) * cos(rad)).toFloat()
                    val endY = (center.y + (innerRadius - 30f) * sin(rad)).toFloat()
                    
                    drawLine(color = activeColor.copy(alpha = 0.3f), start = Offset(startX, startY), end = Offset(endX, endY), strokeWidth = 2f)
                }
            }
        }
    }
}