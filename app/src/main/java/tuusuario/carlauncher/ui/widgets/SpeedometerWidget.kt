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

    val style = AppSettings.speedoStyle.value 
    val baseColor = Color(AppSettings.speedoColor.value)
    val isLight = MaterialTheme.colorScheme.background.red > 0.5f 
    val inactiveColor = if (isLight) Color.LightGray.copy(alpha = 0.3f) else Color(0xFF1A1A24)
    val textColor = MaterialTheme.colorScheme.onSurface
    // Oscurecemos las líneas marcadoras (ticks) en modo claro para más contraste
    val tickColor = if (isLight) Color.Black.copy(alpha = 0.6f) else Color.DarkGray
    val backgroundColor = MaterialTheme.colorScheme.background

    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val boxSize = min(maxWidth, maxHeight) * 0.9f 
        
        SpeedometerDraw(
            speed = animatedSpeed, 
            maxSpeed = 220f, 
            activeColor = baseColor, 
            inactiveColor = inactiveColor,
            textColor = textColor,
            tickColor = tickColor,
            backgroundColor = backgroundColor,
            style = style,
            isLight = isLight, // Pasamos la variable isLight para dibujar los contornos
            modifier = Modifier.size(boxSize)
        )

        when (style) {
            "RACING" -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (-boxSize.value * 0.1f).dp)) {
                    Text(text = speed.toInt().toString(), color = textColor, fontSize = (boxSize.value * 0.35f).sp, fontWeight = FontWeight.Black)
                    Text(text = "KM/H", color = textColor.copy(alpha = 0.5f), fontSize = (boxSize.value * 0.08f).sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
            }
            "CYBER" -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = 15.dp)) {
                    Text(text = speed.toInt().toString(), color = textColor, fontSize = (boxSize.value * 0.30f).sp, fontWeight = FontWeight.Bold)
                    Box(modifier = Modifier.width(60.dp).height(2.dp).background(baseColor.copy(alpha = 0.8f)).padding(vertical = 4.dp))
                    Text(text = "K M / H", color = baseColor.copy(alpha = 0.8f), fontSize = (boxSize.value * 0.06f).sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                }
            }
            else -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = speed.toInt().toString(), color = textColor, fontSize = (boxSize.value * 0.28f).sp, fontWeight = FontWeight.Bold)
                    Text(text = "km/h", color = textColor.copy(alpha = 0.6f), fontSize = (boxSize.value * 0.08f).sp, fontWeight = FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
fun SpeedometerDraw(
    speed: Float, 
    maxSpeed: Float, 
    activeColor: Color, 
    inactiveColor: Color, 
    textColor: Color, 
    tickColor: Color, 
    backgroundColor: Color, 
    style: String, 
    isLight: Boolean,
    modifier: Modifier
) {
    val density = LocalDensity.current
    val baseTextPaint = remember(density, textColor) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.argb((textColor.alpha*255).toInt(), (textColor.red*255).toInt(), (textColor.green*255).toInt(), (textColor.blue*255).toInt())
            textSize = with(density) { 11.sp.toPx() }
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }

    // Color oscuro estandarizado para los contornos en modo claro
    val outlineColor = Color.Black.copy(alpha = 0.85f)

    Canvas(modifier = modifier) {
        val speedProgress = (speed / maxSpeed).coerceIn(0f, 1f)
        
        when (style) {
            "PREMIUM" -> {
                val sweepAngle = 240f
                val startAngle = 150f
                val activeSweepAngle = sweepAngle * speedProgress
                val arcWidth = size.width * 0.04f
                val radius = size.width / 2

                // Contorno oscuro en modo claro
                if (isLight) {
                    drawArc(color = outlineColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = arcWidth + 6f, cap = StrokeCap.Round), size = Size(size.width, size.height))
                }

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
                            canvas.nativeCanvas.drawText((i * 10).toString(), textX, textY + 12f, baseTextPaint)
                        }
                    }
                }
            }

            "NEON" -> {
                val sweepAngle = 240f
                val startAngle = 150f
                val activeSweepAngle = sweepAngle * speedProgress
                val arcWidth = size.width * 0.08f
                val radius = size.width / 2
                
                val dynamicColor = when {
                    speed < 60f -> Color(0xFF00FFCC) 
                    speed < 110f -> Color(0xFFFFD54F) 
                    else -> Color(0xFFFF1744) 
                }

                val neonTextPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb((dynamicColor.alpha*255).toInt(), (dynamicColor.red*255).toInt(), (dynamicColor.green*255).toInt(), (dynamicColor.blue*255).toInt())
                    textSize = with(density) { 12.sp.toPx() }
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }

                val segmentEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 15f), 0f)

                // Contorno oscuro en modo claro para que resalten los segmentos
                if (isLight) {
                    drawArc(color = outlineColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = arcWidth + 6f, cap = StrokeCap.Round), size = Size(size.width, size.height))
                }

                // Background track
                drawArc(color = inactiveColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = arcWidth, cap = StrokeCap.Butt, pathEffect = segmentEffect), size = Size(size.width, size.height))
                
                // Glow effects
                drawArc(color = dynamicColor.copy(alpha=0.3f), startAngle = startAngle, sweepAngle = activeSweepAngle, useCenter = false, style = Stroke(width = arcWidth * 2f, cap = StrokeCap.Round), size = Size(size.width, size.height))
                
                // Active segmented track
                drawArc(color = dynamicColor, startAngle = startAngle, sweepAngle = activeSweepAngle, useCenter = false, style = Stroke(width = arcWidth, cap = StrokeCap.Butt, pathEffect = segmentEffect), size = Size(size.width, size.height))

                // Inner glow ring
                drawArc(color = dynamicColor.copy(alpha = 0.5f), startAngle = startAngle - 5f, sweepAngle = sweepAngle + 10f, useCenter = false, style = Stroke(width = 4f, cap = StrokeCap.Round), size = Size(size.width - arcWidth * 2.5f, size.height - arcWidth * 2.5f), topLeft = Offset(arcWidth * 1.25f, arcWidth * 1.25f))

                val numTicks = 11
                val tickStepAngle = sweepAngle / numTicks
                val center = Offset(size.width / 2, size.height / 2)
                val textRadius = radius - arcWidth - 35f

                drawIntoCanvas { canvas ->
                    for (i in 0..numTicks) {
                        val currentAngle = startAngle + (i * tickStepAngle)
                        val angleRad = Math.toRadians(currentAngle.toDouble())
                        
                        val speedValue = i * 20
                        val isLit = speedValue <= speed
                        
                        val textX = (center.x + textRadius * cos(angleRad)).toFloat()
                        val textY = (center.y + textRadius * sin(angleRad)).toFloat()
                        
                        val currentTextPaint = if (isLit) neonTextPaint else baseTextPaint
                        canvas.nativeCanvas.drawText(speedValue.toString(), textX, textY + 12f, currentTextPaint)
                    }
                }
            }

            "RACING" -> {
                val sweepAngle = 180f
                val startAngle = 180f 
                val activeSweepAngle = sweepAngle * speedProgress
                val radius = size.width / 2
                val center = Offset(size.width / 2, size.height / 2 + size.height * 0.2f) 
                val arcWidth = size.width * 0.12f

                // Contorno oscuro en modo claro para la barra Racing
                if (isLight) {
                    drawArc(
                        color = outlineColor,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = arcWidth + 6f, cap = StrokeCap.Butt),
                        size = Size(radius * 2, radius * 2),
                        topLeft = Offset(center.x - radius, center.y - radius)
                    )
                }

                // Inactive Background Track
                drawArc(
                    color = inactiveColor,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = arcWidth, cap = StrokeCap.Butt),
                    size = Size(radius * 2, radius * 2),
                    topLeft = Offset(center.x - radius, center.y - radius)
                )

                // Active Gradient Track
                val gradientBrush = Brush.sweepGradient(
                    colors = listOf(activeColor.copy(alpha = 0.3f), activeColor, Color.White),
                    center = center
                )
                
                if (speedProgress > 0) {
                     drawArc(
                        brush = gradientBrush,
                        startAngle = startAngle,
                        sweepAngle = activeSweepAngle,
                        useCenter = false,
                        style = Stroke(width = arcWidth, cap = StrokeCap.Butt),
                        size = Size(radius * 2, radius * 2),
                        topLeft = Offset(center.x - radius, center.y - radius)
                    )
                }

                // Ticks (Segments cutting into the bar)
                val numSegments = 30
                val tickStepAngle = sweepAngle / numSegments
                for (i in 0..numSegments) {
                    val currentAngle = startAngle + (i * tickStepAngle)
                    val angleRad = Math.toRadians(currentAngle.toDouble())
                    val isMajor = i % 5 == 0
                    
                    val startRad = radius - (arcWidth / 2)
                    val endRad = if(isMajor) radius + (arcWidth / 2) else radius + (arcWidth / 4)

                    val startX = (center.x + startRad * cos(angleRad)).toFloat()
                    val startY = (center.y + startRad * sin(angleRad)).toFloat()
                    val endX = (center.x + endRad * cos(angleRad)).toFloat()
                    val endY = (center.y + endRad * sin(angleRad)).toFloat()

                    drawLine(
                        color = backgroundColor, 
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = if (isMajor) 6f else 3f
                    )
                }

                // Numbers above the arc
                val numScaleValues = 5
                val scaleStepAngle = sweepAngle / numScaleValues
                val textRadius = radius + arcWidth
                
                drawIntoCanvas { canvas ->
                    for (i in 0..numScaleValues) {
                        val currentAngle = startAngle + (i * scaleStepAngle)
                        val angleRad = Math.toRadians(currentAngle.toDouble())
                        val speedVal = (i * (220 / numScaleValues)).toInt()
                        
                        val textX = (center.x + textRadius * cos(angleRad)).toFloat()
                        val textY = (center.y + textRadius * sin(angleRad)).toFloat()
                        
                         val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb((textColor.alpha*255).toInt(), (textColor.red*255).toInt(), (textColor.green*255).toInt(), (textColor.blue*255).toInt())
                            textSize = with(density) { 14.sp.toPx() }
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                        
                        canvas.nativeCanvas.drawText(speedVal.toString(), textX, textY, paint)
                    }
                }
                
                // Add a "Redline" section indicator at the end con contorno oscuro opcional
                 if (isLight) {
                    drawArc(
                        color = outlineColor,
                        startAngle = startAngle + (sweepAngle * 0.8f),
                        sweepAngle = sweepAngle * 0.2f,
                        useCenter = false,
                        style = Stroke(width = (arcWidth * 0.15f) + 6f, cap = StrokeCap.Butt),
                        size = Size(radius * 2 - arcWidth + 5f, radius * 2 - arcWidth + 5f),
                        topLeft = Offset(center.x - radius + arcWidth/2 - 2.5f, center.y - radius + arcWidth/2 - 2.5f)
                    )
                 }

                 drawArc(
                    color = Color.Red.copy(alpha = 0.8f),
                    startAngle = startAngle + (sweepAngle * 0.8f), // Last 20%
                    sweepAngle = sweepAngle * 0.2f,
                    useCenter = false,
                    style = Stroke(width = arcWidth * 0.15f, cap = StrokeCap.Butt),
                    size = Size(radius * 2 - arcWidth + 5f, radius * 2 - arcWidth + 5f),
                    topLeft = Offset(center.x - radius + arcWidth/2 - 2.5f, center.y - radius + arcWidth/2 - 2.5f)
                )
            }

            "CYBER" -> {
                val sweepAngle = 260f
                val startAngle = 140f
                val activeSweepAngle = sweepAngle * speedProgress
                val radius = size.width / 2
                val center = Offset(size.width / 2, size.height / 2)

                val radialGradient = Brush.radialGradient(
                    colors = listOf(activeColor.copy(alpha = 0.15f), Color.Transparent),
                    center = center,
                    radius = radius * 0.8f
                )
                drawCircle(
                    brush = radialGradient,
                    radius = radius * 0.8f,
                    center = center
                )

                val outerRadius = radius - 5f
                
                // Contorno oscuro para el arco exterior
                if (isLight) {
                    drawArc(
                        color = outlineColor,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = 6f, cap = StrokeCap.Round),
                        size = Size(outerRadius * 2, outerRadius * 2),
                        topLeft = Offset(center.x - outerRadius, center.y - outerRadius)
                    )
                }

                drawArc(
                    color = activeColor.copy(alpha = 0.2f),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = 2f, cap = StrokeCap.Round),
                    size = Size(outerRadius * 2, outerRadius * 2),
                    topLeft = Offset(center.x - outerRadius, center.y - outerRadius)
                )
                drawArc(
                    color = activeColor,
                    startAngle = startAngle - 2f,
                    sweepAngle = activeSweepAngle + 4f,
                    useCenter = false,
                    style = Stroke(width = 6f, cap = StrokeCap.Round),
                    size = Size(outerRadius * 2, outerRadius * 2),
                    topLeft = Offset(center.x - outerRadius, center.y - outerRadius)
                )

                val innerRadius = radius - 35f
                val innerArcWidth = 20f
                val segmentEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 5f), 0f)

                // Contorno oscuro grueso para el arco segmentado interior
                if (isLight) {
                    drawArc(
                        color = outlineColor,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = innerArcWidth + 6f, cap = StrokeCap.Butt),
                        size = Size(innerRadius * 2, innerRadius * 2),
                        topLeft = Offset(center.x - innerRadius, center.y - innerRadius)
                    )
                }

                drawArc(
                    color = inactiveColor,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = innerArcWidth, cap = StrokeCap.Butt, pathEffect = segmentEffect),
                    size = Size(innerRadius * 2, innerRadius * 2),
                    topLeft = Offset(center.x - innerRadius, center.y - innerRadius)
                )
                drawArc(
                    color = activeColor,
                    startAngle = startAngle,
                    sweepAngle = activeSweepAngle,
                    useCenter = false,
                    style = Stroke(width = innerArcWidth, cap = StrokeCap.Butt, pathEffect = segmentEffect),
                    size = Size(innerRadius * 2, innerRadius * 2),
                    topLeft = Offset(center.x - innerRadius, center.y - innerRadius)
                )

                val angleRad = Math.toRadians((startAngle + activeSweepAngle).toDouble())
                val dotX = (center.x + innerRadius * cos(angleRad)).toFloat()
                val dotY = (center.y + innerRadius * sin(angleRad)).toFloat()
                
                // Contorno para el punto indicador
                if (isLight) {
                    drawCircle(color = outlineColor, radius = 15f, center = Offset(dotX, dotY))
                }
                drawCircle(color = Color.White, radius = 6f, center = Offset(dotX, dotY))
                drawCircle(color = activeColor.copy(alpha = 0.5f), radius = 12f, center = Offset(dotX, dotY))

                val numCyberTicks = 12
                val tickStepAngle = sweepAngle / numCyberTicks
                for (i in 0..numCyberTicks) {
                    val currentAngle = startAngle + (i * tickStepAngle)
                    val rad = Math.toRadians(currentAngle.toDouble())
                    val startX = (center.x + (innerRadius - 20f) * cos(rad)).toFloat()
                    val startY = (center.y + (innerRadius - 20f) * sin(rad)).toFloat()
                    val endX = (center.x + (innerRadius - 30f) * cos(rad)).toFloat()
                    val endY = (center.y + (innerRadius - 30f) * sin(rad)).toFloat()
                    
                    val drawColor = if (isLight) Color.Black.copy(alpha = 0.6f) else activeColor.copy(alpha = 0.3f)
                    drawLine(color = drawColor, start = Offset(startX, startY), end = Offset(endX, endY), strokeWidth = 3f)
                }
            }
        }
    }
}