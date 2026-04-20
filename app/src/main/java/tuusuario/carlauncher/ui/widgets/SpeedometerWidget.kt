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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.min
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.tuusuario.carlauncher.ui.AppSettings
import com.tuusuario.carlauncher.ui.NavigationState
import kotlin.math.abs
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
    val tickColor = if (isLight) Color.Black.copy(alpha = 0.6f) else Color.DarkGray
    val backgroundColor = MaterialTheme.colorScheme.background

    // LÓGICA DE COLOR REACTIVO MULTI-ETAPAS
    val c0 = lerp(baseColor, Color.Black, 0.8f)
    val c1 = baseColor
    val c2 = lerp(baseColor, Color.White, 0.6f)
    val c3 = Color.White

    val reactiveColorText = when {
        animatedSpeed <= 40f -> c0
        animatedSpeed <= 80f -> lerp(c0, c1, (animatedSpeed - 40f) / 40f)
        animatedSpeed <= 90f -> lerp(c1, c2, (animatedSpeed - 80f) / 10f)
        animatedSpeed <= 150f -> lerp(c2, c3, (animatedSpeed - 90f) / 60f)
        else -> c3
    }

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
            isLight = isLight, 
            modifier = Modifier.size(boxSize)
        )

        // Textos superpuestos según el estilo con sombras repotenciadas
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
            "AURA" -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = 10.dp)) {
                    val textShadow = if (!isLight) Shadow(color = baseColor.copy(alpha = 0.8f), offset = Offset(0f, 0f), blurRadius = 15f) else null
                    Text(text = speed.toInt().toString(), color = textColor, fontSize = (boxSize.value * 0.32f).sp, fontWeight = FontWeight.Black, style = TextStyle(shadow = textShadow))
                    Box(modifier = Modifier.padding(top = 4.dp).background(baseColor, shape = RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(text = "KM/H", color = Color.White, fontSize = (boxSize.value * 0.05f).sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                    }
                }
            }
            "VORTEX" -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (-15).dp)) {
                    val textShadow = if (!isLight) Shadow(color = baseColor.copy(alpha = 0.6f), offset = Offset(0f, 0f), blurRadius = 20f) else null
                    Text(text = speed.toInt().toString(), color = textColor, fontSize = (boxSize.value * 0.32f).sp, fontWeight = FontWeight.Black, style = TextStyle(fontStyle = FontStyle.Italic, shadow = textShadow))
                    Text(text = "KM/H", color = textColor.copy(alpha = 0.5f), fontSize = (boxSize.value * 0.06f).sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 8.sp)
                }
            }
            "QUANTUM" -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (-20).dp)) {
                    val textShadow = if (!isLight) Shadow(color = reactiveColorText.copy(alpha = 0.7f), blurRadius = 15f) else null
                    Text(text = speed.toInt().toString().padStart(3, '0'), color = textColor, fontSize = (boxSize.value * 0.28f).sp, fontWeight = FontWeight.Light, style = TextStyle(fontFamily = FontFamily.Monospace, shadow = textShadow))
                    Text(text = "K M / H", color = reactiveColorText, fontSize = (boxSize.value * 0.05f).sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                }
            }
            "PULSAR" -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (-10).dp)) {
                    val pulseShadow = if (!isLight) Shadow(color = reactiveColorText.copy(alpha = 0.8f), blurRadius = 20f) else null
                    Text(text = speed.toInt().toString(), color = textColor, fontSize = (boxSize.value * 0.30f).sp, fontWeight = FontWeight.Bold, style = TextStyle(shadow = pulseShadow))
                    Text(text = "KM/H", color = reactiveColorText.copy(alpha = 0.9f), fontSize = (boxSize.value * 0.06f).sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp)
                }
            }
            "PLASMA" -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (-10).dp)) {
                    val plasmaShadow = if (!isLight) Shadow(color = reactiveColorText.copy(alpha = 0.8f), blurRadius = 15f) else null
                    Text(text = speed.toInt().toString(), color = textColor, fontSize = (boxSize.value * 0.30f).sp, fontWeight = FontWeight.Black, style = TextStyle(shadow = plasmaShadow))
                    Box(modifier = Modifier.padding(top = 4.dp).background(if (isLight) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Text(text = "KM/H", color = reactiveColorText, fontSize = (boxSize.value * 0.06f).sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 5.sp)
                    }
                }
            }
            "ANIME" -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (-15).dp)) {
                    val textShadow = Shadow(color = if (isLight) reactiveColorText.copy(alpha = 0.4f) else reactiveColorText, offset = Offset(4f, 4f), blurRadius = 0f)
                    Text(text = speed.toInt().toString(), color = textColor, fontSize = (boxSize.value * 0.32f).sp, fontWeight = FontWeight.Black, style = TextStyle(fontStyle = FontStyle.Italic, shadow = textShadow))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Text(text = "速度", color = reactiveColorText, fontSize = (boxSize.value * 0.07f).sp, fontWeight = FontWeight.Bold)
                        Text(text = "KM/H", color = textColor.copy(alpha = 0.7f), fontSize = (boxSize.value * 0.06f).sp, fontWeight = FontWeight.Black, style = TextStyle(fontStyle = FontStyle.Italic))
                    }
                }
            }
            "KAIJU" -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (-10).dp)) {
                    val textShadow = if (!isLight) Shadow(color = reactiveColorText, blurRadius = 15f) else null
                    Text(text = speed.toInt().toString(), color = textColor, fontSize = (boxSize.value * 0.35f).sp, fontWeight = FontWeight.Black, style = TextStyle(shadow = textShadow))
                    Text(text = "K M / H", color = reactiveColorText, fontSize = (boxSize.value * 0.06f).sp, fontWeight = FontWeight.Black, letterSpacing = 10.sp)
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

    val outlineColor = Color.Black.copy(alpha = 0.85f)
    val speedProgress by rememberUpdatedState((speed / maxSpeed).coerceIn(0f, 1f))
    
    var cumTime by remember { mutableStateOf(0f) }
    var cumRadar by remember { mutableStateOf(0f) }
    var cumVortex by remember { mutableStateOf(0f) }
    var cumWarp by remember { mutableStateOf(0f) }
    var cumWave by remember { mutableStateOf(0f) }
    var cumPulse by remember { mutableStateOf(0f) }

    LaunchedEffect(style) {
        val animatedStyles = listOf("AURA", "VORTEX", "QUANTUM", "PULSAR", "PLASMA", "ANIME", "KAIJU")
        if (style in animatedStyles) {
            var lastFrameTime = withFrameNanos { it }
            while (true) {
                withFrameNanos { frameTimeNanos ->
                    val dt = (frameTimeNanos - lastFrameTime) / 1_000_000_000f
                    lastFrameTime = frameTimeNanos
                    cumTime += dt

                    val sp = speedProgress
                    cumRadar += (1.5f + sp * 3f) * dt
                    cumVortex += (0.5f + sp * 4f) * dt
                    cumWarp += (20f + sp * 200f) * dt
                    cumWave += (5f + sp * 15f) * dt
                    cumPulse += (2f + sp * 15f) * dt
                }
            }
        } else {
            cumTime = 0f; cumRadar = 0f; cumVortex = 0f; cumWarp = 0f; cumWave = 0f; cumPulse = 0f
        }
    }

    Canvas(modifier = modifier) {
        val spProg = speedProgress
        val center = Offset(size.width / 2, size.height / 2)
        
        // MOTOR DE FUSIÓN LUMINOSA: Hace que los colores sumen luz en modo oscuro (Neón puro)
        val glowBlendMode = if (isLight) BlendMode.SrcOver else BlendMode.Plus

        val c0 = lerp(activeColor, Color.Black, 0.8f)
        val c1 = activeColor
        val c2 = lerp(activeColor, Color.White, 0.6f)
        val c3 = Color.White

        val reactiveColor = when {
            speed <= 40f -> c0
            speed <= 80f -> lerp(c0, c1, (speed - 40f) / 40f)
            speed <= 90f -> lerp(c1, c2, (speed - 80f) / 10f)
            speed <= 150f -> lerp(c2, c3, (speed - 90f) / 60f)
            else -> c3
        }
        
        when (style) {
            "PREMIUM" -> {
                val sweepAngle = 240f
                val startAngle = 150f
                val activeSweepAngle = sweepAngle * spProg
                val arcWidth = size.width * 0.04f
                val radius = size.width / 2

                if (isLight) drawArc(color = outlineColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = arcWidth + 6f, cap = StrokeCap.Round), size = Size(size.width, size.height))
                drawArc(color = inactiveColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = arcWidth, cap = StrokeCap.Round), size = Size(size.width, size.height))
                drawArc(color = activeColor, startAngle = startAngle, sweepAngle = activeSweepAngle, useCenter = false, style = Stroke(width = arcWidth, cap = StrokeCap.Round), size = Size(size.width, size.height))
                
                val numTicks = 22 
                val tickStepAngle = sweepAngle / numTicks
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

            "AURA" -> {
                val sweepAngle = 260f
                val startAngle = 140f
                val radius = size.width / 2
                val mainRadius = radius * 0.75f

                val auraPulse = (sin(cumTime * 3f) * 0.05f)
                val baseAuraAlpha = if (isLight) 0.15f else 0.4f
                val auraAlpha = (baseAuraAlpha + (spProg * 0.4f) + auraPulse).coerceIn(0f, 1f)
                val auraRadius = mainRadius + 20f + (spProg * 40f)
                
                val auraBrush = Brush.radialGradient(
                    colors = listOf(activeColor.copy(alpha = auraAlpha), activeColor.copy(alpha = auraAlpha * 0.4f), Color.Transparent),
                    center = center,
                    radius = auraRadius
                )
                drawCircle(brush = auraBrush, radius = auraRadius, center = center, blendMode = glowBlendMode)

                rotate(degrees = cumVortex * 57.3f, pivot = center) {
                    drawCircle(
                        color = if (isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.06f),
                        radius = mainRadius - 35f,
                        center = center,
                        style = Stroke(width = 12f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(25f, 15f, 5f, 15f)))
                    )
                }

                rotate(degrees = -cumVortex * 0.7f * 57.3f, pivot = center) {
                    drawCircle(
                        color = activeColor.copy(alpha = 0.4f),
                        radius = mainRadius - 55f,
                        center = center,
                        style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                    )
                }

                val numBars = 45
                val barStep = sweepAngle / numBars

                for (i in 0..numBars) {
                    val currentAngle = startAngle + (i * barStep)
                    val rad = Math.toRadians(currentAngle.toDouble())
                    val barSpeedVal = (i.toFloat() / numBars) * maxSpeed
                    val isLit = speed >= barSpeedVal

                    var barHeight = 8f
                    if (isLit) barHeight = 18f + (sin(cumWave + i * 0.5f) * 5f).toFloat() 

                    val innerR = mainRadius - barHeight / 2f
                    val outerR = mainRadius + barHeight / 2f

                    val startX = (center.x + innerR * cos(rad)).toFloat()
                    val startY = (center.y + innerR * sin(rad)).toFloat()
                    val endX = (center.x + outerR * cos(rad)).toFloat()
                    val endY = (center.y + outerR * sin(rad)).toFloat()

                    if (isLit) {
                        if (!isLight) drawLine(color = activeColor.copy(alpha = 0.4f), start = Offset(startX, startY), end = Offset(endX, endY), strokeWidth = 12f, cap = StrokeCap.Round, blendMode = glowBlendMode)
                        drawLine(color = activeColor, start = Offset(startX, startY), end = Offset(endX, endY), strokeWidth = 4f, cap = StrokeCap.Round)
                    } else {
                        val inactiveBarColor = if (isLight) Color.Black.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.1f)
                        drawLine(color = inactiveBarColor, start = Offset(startX, startY), end = Offset(endX, endY), strokeWidth = 3f, cap = StrokeCap.Round)
                    }
                }

                drawArc(
                    color = activeColor,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle * spProg,
                    useCenter = false,
                    style = Stroke(width = 3f),
                    size = Size((mainRadius + 18f) * 2, (mainRadius + 18f) * 2),
                    topLeft = Offset(center.x - (mainRadius + 18f), center.y - (mainRadius + 18f))
                )

                if (spProg > 0) {
                    val dotRad = Math.toRadians((startAngle + (sweepAngle * spProg)).toDouble())
                    val dotX = (center.x + (mainRadius + 18f) * cos(dotRad)).toFloat()
                    val dotY = (center.y + (mainRadius + 18f) * sin(dotRad)).toFloat()
                    if (!isLight) drawCircle(color = activeColor.copy(alpha = 0.5f), radius = 12f, center = Offset(dotX, dotY), blendMode = glowBlendMode)
                    drawCircle(color = Color.White, radius = 6f, center = Offset(dotX, dotY))
                }
                
                val numScaleValues = 5
                val scaleStepAngle = sweepAngle / numScaleValues
                val textRadius = mainRadius + 42f

                drawIntoCanvas { canvas ->
                    for (i in 0..numScaleValues) {
                        val currentAngle = startAngle + (i * scaleStepAngle)
                        val angleRad = Math.toRadians(currentAngle.toDouble())
                        val speedVal = Math.round(i * (maxSpeed / numScaleValues))
                        val textX = (center.x + textRadius * cos(angleRad)).toFloat()
                        val textY = (center.y + textRadius * sin(angleRad)).toFloat()
                        val isLit = speed >= speedVal
                        val numColor = if (isLit) { if (isLight) Color.Black else Color.White } else { if (isLight) Color(0xFF94A3B8) else Color(0xFF475569) }

                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb((numColor.alpha*255).toInt(), (numColor.red*255).toInt(), (numColor.green*255).toInt(), (numColor.blue*255).toInt())
                            textSize = with(density) { 14.sp.toPx() }
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                            if (!isLight && isLit) setShadowLayer(15f, 0f, 0f, color)
                        }
                        canvas.nativeCanvas.drawText(speedVal.toString(), textX, textY + 5f, paint) 
                    }
                }
            }

            "VORTEX" -> {
                val sweepAngle = 260f
                val startAngle = 140f
                val radius = size.width / 2
                val mainRadius = radius * 0.8f

                val numStars = 60
                for (i in 0 until numStars) {
                    val rawR = (i * 12f + cumWarp) % mainRadius
                    val alpha = (rawR / mainRadius).coerceIn(0f, 1f)
                    val starAngle = (i * 137.5f) * (Math.PI / 180f)
                    val sx = (center.x + rawR * cos(starAngle + cumVortex)).toFloat()
                    val sy = (center.y + rawR * sin(starAngle + cumVortex)).toFloat()
                    val starColor = if (isLight) Color.Black.copy(alpha = alpha) else Color.White.copy(alpha = alpha)
                    drawCircle(color = starColor, radius = if (isLight) 2f else 1.5f, center = Offset(sx, sy), blendMode = glowBlendMode)
                }

                rotate(degrees = -cumVortex * 0.8f * 57.3f, pivot = center) {
                    drawCircle(
                        color = reactiveColor.copy(alpha = 0.3f + (spProg * 0.4f)),
                        radius = mainRadius * 0.5f,
                        center = center,
                        style = Stroke(width = 15f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(40f, 20f, 10f, 20f))),
                        blendMode = glowBlendMode
                    )
                }
                rotate(degrees = cumVortex * 2.3f * 57.3f, pivot = center) {
                    drawCircle(
                        color = reactiveColor.copy(alpha = 0.5f),
                        radius = mainRadius * 0.85f,
                        center = center,
                        style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 15f))),
                        blendMode = glowBlendMode
                    )
                }

                drawArc(color = inactiveColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = 6f, cap = StrokeCap.Round), size = Size(mainRadius * 2, mainRadius * 2), topLeft = Offset(center.x - mainRadius, center.y - mainRadius))

                if (spProg > 0) {
                    drawArc(color = reactiveColor, startAngle = startAngle, sweepAngle = sweepAngle * spProg, useCenter = false, style = Stroke(width = 10f, cap = StrokeCap.Round), size = Size(mainRadius * 2, mainRadius * 2), topLeft = Offset(center.x - mainRadius, center.y - mainRadius))
                    val dotRad = Math.toRadians((startAngle + (sweepAngle * spProg)).toDouble())
                    val dotX = (center.x + mainRadius * cos(dotRad)).toFloat()
                    val dotY = (center.y + mainRadius * sin(dotRad)).toFloat()
                    
                    rotate(degrees = (startAngle + sweepAngle * spProg) + 45f, pivot = Offset(dotX, dotY)) {
                        drawRect(color = Color.White, topLeft = Offset(dotX - 6f, dotY - 6f), size = Size(12f, 12f))
                    }
                }

                val numScaleValues = 6
                val scaleStepAngle = sweepAngle / numScaleValues
                val textRadius = mainRadius + 28f

                drawIntoCanvas { canvas ->
                    for (i in 0..numScaleValues) {
                        val currentAngle = startAngle + (i * scaleStepAngle)
                        val angleRad = Math.toRadians(currentAngle.toDouble())
                        val speedVal = Math.round(i * (maxSpeed / numScaleValues))
                        val textX = (center.x + textRadius * cos(angleRad)).toFloat()
                        val textY = (center.y + textRadius * sin(angleRad)).toFloat()
                        val isLit = speed >= speedVal
                        val numColor = if (isLit) reactiveColor else (if (isLight) Color(0xFF94A3B8) else Color(0xFF475569))

                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb((numColor.alpha*255).toInt(), (numColor.red*255).toInt(), (numColor.green*255).toInt(), (numColor.blue*255).toInt())
                            textSize = with(density) { 15.sp.toPx() }
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                            if (!isLight && isLit) setShadowLayer(15f, 0f, 0f, color)
                        }
                        canvas.nativeCanvas.drawText(speedVal.toString(), textX, textY + 5f, paint) 
                    }
                }
            }

            "QUANTUM" -> {
                val sweepAngle = 260f
                val startAngle = 140f
                val radius = size.width / 2
                val mainRadius = radius * 0.75f

                val radarAngleDeg = (cumRadar * 57.3f) % 360f
                val sweepWidth = 35f 
                val numSlices = 20
                
                drawIntoCanvas { canvas ->
                    val innerArcR = mainRadius - 10f
                    val radarRect = android.graphics.RectF(center.x - innerArcR, center.y - innerArcR, center.x + innerArcR, center.y + innerArcR)
                    
                    for (i in 0 until numSlices) {
                        val sliceStart = radarAngleDeg - (sweepWidth * (i.toFloat() / numSlices))
                        val sliceSweep = -(sweepWidth / numSlices)
                        
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb(
                                (90f * (1f - (i.toFloat() / numSlices))).toInt(), 
                                (activeColor.red*255).toInt(), 
                                (activeColor.green*255).toInt(), 
                                (activeColor.blue*255).toInt()
                            )
                            this.style = android.graphics.Paint.Style.FILL // INYECCIÓN CORREGIDA Y ÚNICA
                            isAntiAlias = true
                        }
                        canvas.nativeCanvas.drawArc(radarRect, sliceStart, sliceSweep, true, paint)
                    }
                }

                val waveWidth = 80f
                val waveAmp = 5f + (spProg * 25f)
                val waveFreq = 0.05f + (spProg * 0.1f)
                
                var prevX = 0f
                var prevY = 0f
                for (x in (-waveWidth/2).toInt()..(waveWidth/2).toInt()) {
                    val yOffset = sin(x * waveFreq + cumWave) * waveAmp
                    val dampen = 1f - abs(x) / (waveWidth/2f)
                    val px = center.x + x
                    val py = center.y + 45f + (yOffset * dampen).toFloat()
                    
                    if (x == (-waveWidth/2).toInt()) {
                        prevX = px; prevY = py
                    } else {
                        drawLine(color = reactiveColor, start = Offset(prevX, prevY), end = Offset(px, py), strokeWidth = 2f, blendMode = glowBlendMode)
                        prevX = px; prevY = py
                    }
                }

                val numSegments = 50
                val segStep = sweepAngle / numSegments
                for (i in 0..numSegments) {
                    val currentAngle = startAngle + (i * segStep)
                    val rad = Math.toRadians(currentAngle.toDouble())
                    val valProgress = (i.toFloat() / numSegments) * maxSpeed
                    val isLit = speed >= valProgress
                    
                    val innerR = mainRadius - if (isLit) 4f else 0f
                    val outerR = mainRadius + if (isLit) 12f else 6f
                    
                    val startX = (center.x + innerR * cos(rad)).toFloat()
                    val startY = (center.y + innerR * sin(rad)).toFloat()
                    val endX = (center.x + outerR * cos(rad)).toFloat()
                    val endY = (center.y + outerR * sin(rad)).toFloat()
                    
                    drawLine(color = if (isLit) reactiveColor else inactiveColor, start = Offset(startX, startY), end = Offset(endX, endY), strokeWidth = if (isLit) 3f else 2f)
                }

                if (spProg > 0) {
                    val dotRad = Math.toRadians((startAngle + (sweepAngle * spProg)).toDouble())
                    val pX = (center.x + (mainRadius + 20f) * cos(dotRad)).toFloat()
                    val pY = (center.y + (mainRadius + 20f) * sin(dotRad)).toFloat()
                    drawCircle(color = Color.White, radius = 6f, center = Offset(pX, pY))
                    drawLine(color = reactiveColor, start = Offset((center.x + (mainRadius + 5f) * cos(dotRad)).toFloat(), (center.y + (mainRadius + 5f) * sin(dotRad)).toFloat()), end = Offset(pX, pY), strokeWidth = 2f)
                }

                val numScaleValues = 4
                val scaleStepAngle = sweepAngle / numScaleValues
                val textRadius = mainRadius + 45f

                drawIntoCanvas { canvas ->
                    for (i in 0..numScaleValues) {
                        val currentAngle = startAngle + (i * scaleStepAngle)
                        val angleRad = Math.toRadians(currentAngle.toDouble())
                        val speedVal = Math.round(i * (maxSpeed / numScaleValues))
                        val textX = (center.x + textRadius * cos(angleRad)).toFloat()
                        val textY = (center.y + textRadius * sin(angleRad)).toFloat()
                        val isLit = speed >= speedVal
                        val numColor = if (isLit) reactiveColor else (if (isLight) Color(0xFF94A3B8) else Color(0xFF475569))

                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb((numColor.alpha*255).toInt(), (numColor.red*255).toInt(), (numColor.green*255).toInt(), (numColor.blue*255).toInt())
                            textSize = with(density) { 16.sp.toPx() }
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.MONOSPACE
                            if (!isLight && isLit) setShadowLayer(15f, 0f, 0f, color)
                        }
                        canvas.nativeCanvas.drawText(speedVal.toString(), textX, textY + 5f, paint) 
                    }
                }
            }

            "PULSAR" -> {
                val sweepAngle = 260f
                val startAngle = 140f
                val radius = size.width / 2
                val mainRadius = radius * 0.70f

                val heartbeat = Math.pow(sin(cumPulse).toDouble(), 2.0).toFloat()
                
                val orbRadius = 40f + (spProg * 30f) + (heartbeat * 10f)
                val orbGradient = Brush.radialGradient(
                    colors = listOf(reactiveColor, reactiveColor.copy(alpha = 0.5f), Color.Transparent),
                    center = center,
                    radius = orbRadius
                )
                drawCircle(brush = orbGradient, radius = orbRadius, center = center, blendMode = glowBlendMode)

                val numRipples = 3
                for (i in 0 until numRipples) {
                    val rippleR = ((cumTime * 50f) + (i * (mainRadius / numRipples))) % mainRadius
                    val rippleAlpha = (1f - (rippleR / mainRadius)).coerceIn(0f, 1f)
                    drawCircle(color = reactiveColor.copy(alpha = rippleAlpha), radius = rippleR, center = center, style = Stroke(width = 2f))
                }

                val numBars = 60
                val barStep = sweepAngle / numBars
                for (i in 0..numBars) {
                    val currentAngle = startAngle + (i * barStep)
                    val rad = Math.toRadians(currentAngle.toDouble())
                    val valProgress = (i.toFloat() / numBars) * maxSpeed
                    val isLit = speed >= valProgress

                    val barHeight = if (isLit) 15f + (heartbeat * 8f * (spProg + 0.2f)) else 6f
                    val innerR = mainRadius
                    val outerR = mainRadius + barHeight

                    val startX = (center.x + innerR * cos(rad)).toFloat()
                    val startY = (center.y + innerR * sin(rad)).toFloat()
                    val endX = (center.x + outerR * cos(rad)).toFloat()
                    val endY = (center.y + outerR * sin(rad)).toFloat()
                    
                    drawLine(color = if (isLit) reactiveColor else inactiveColor, start = Offset(startX, startY), end = Offset(endX, endY), strokeWidth = if (isLit) 4f else 2f, cap = StrokeCap.Butt)
                }

                drawArc(color = inactiveColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = 1f), size = Size((mainRadius + 30f)*2, (mainRadius + 30f)*2), topLeft = Offset(center.x - mainRadius - 30f, center.y - mainRadius - 30f))
                if (spProg > 0) {
                    drawArc(color = reactiveColor, startAngle = startAngle, sweepAngle = sweepAngle * spProg, useCenter = false, style = Stroke(width = 3f), size = Size((mainRadius + 30f)*2, (mainRadius + 30f)*2), topLeft = Offset(center.x - mainRadius - 30f, center.y - mainRadius - 30f))
                    val dotRad = Math.toRadians((startAngle + sweepAngle * spProg).toDouble())
                    drawCircle(color = Color.White, radius = 5f, center = Offset((center.x + (mainRadius + 30f) * cos(dotRad)).toFloat(), (center.y + (mainRadius + 30f) * sin(dotRad)).toFloat()))
                }

                drawIntoCanvas { canvas ->
                    for (i in 0..5) {
                        val angleRad = Math.toRadians((startAngle + (i * (sweepAngle / 5))).toDouble())
                        val valSpd = Math.round(i * (maxSpeed / 5))
                        val textX = (center.x + (mainRadius + 45f) * cos(angleRad)).toFloat()
                        val textY = (center.y + (mainRadius + 45f) * sin(angleRad)).toFloat()
                        val isLit = speed >= valSpd
                        val numColor = if (isLit) reactiveColor else (if (isLight) Color(0xFF94A3B8) else Color(0xFF475569))

                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb((numColor.alpha*255).toInt(), (numColor.red*255).toInt(), (numColor.green*255).toInt(), (numColor.blue*255).toInt())
                            textSize = with(density) { 12.sp.toPx() }
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                            if (!isLight && isLit) setShadowLayer(15f, 0f, 0f, color)
                        }
                        canvas.nativeCanvas.drawText(valSpd.toString(), textX, textY, paint) 
                    }
                }
            }

            "PLASMA" -> {
                val sweepAngle = 260f
                val startAngle = 140f
                val radius = size.width / 2
                val mainRadius = radius * 0.70f

                val coreRadius = 25f + (spProg * 15f) + (sin(cumTime * 15f) * 4f)
                val coreGrad = Brush.radialGradient(
                    colors = listOf(Color.White, reactiveColor, Color.Transparent),
                    center = center,
                    radius = coreRadius * 1.5f
                )
                drawCircle(brush = coreGrad, radius = coreRadius * 1.5f, center = center, blendMode = glowBlendMode)

                val numBolts = (6f + spProg * 14f).toInt()
                for (i in 0 until numBolts) {
                    val baseAngle = (i * Math.PI * 2) / numBolts + cumTime * (0.3f + spProg * 0.5f)
                    var currentX = center.x
                    var currentY = center.y
                    val steps = 5
                    
                    for (j in 1..steps) {
                        val r = (mainRadius * j) / steps
                        val wobble = sin(cumTime * (20f + spProg * 40f) + i * 13f + j * 4f) * 0.5f
                        val a = baseAngle + wobble
                        val nextX = (center.x + r * cos(a)).toFloat()
                        val nextY = (center.y + r * sin(a)).toFloat()
                        
                        drawLine(color = reactiveColor, start = Offset(currentX, currentY), end = Offset(nextX, nextY), strokeWidth = 1.5f + (Math.random()*2).toFloat(), blendMode = glowBlendMode)
                        currentX = nextX
                        currentY = nextY
                    }
                    drawCircle(color = Color.White, radius = 2f + (Math.random()*2).toFloat(), center = Offset(currentX, currentY))
                }

                drawCircle(color = reactiveColor.copy(alpha = 0.25f), radius = mainRadius, center = center, style = Stroke(width = 4f + sin(cumTime * 10f) * 2f))

                val trackRadius = mainRadius + 15f
                drawArc(color = inactiveColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = 4f, cap = StrokeCap.Round), size = Size(trackRadius*2, trackRadius*2), topLeft = Offset(center.x - trackRadius, center.y - trackRadius))
                
                if (spProg > 0) {
                    drawArc(color = reactiveColor, startAngle = startAngle, sweepAngle = sweepAngle * spProg, useCenter = false, style = Stroke(width = 6f, cap = StrokeCap.Round), size = Size(trackRadius*2, trackRadius*2), topLeft = Offset(center.x - trackRadius, center.y - trackRadius))
                    val pRad = Math.toRadians((startAngle + sweepAngle * spProg).toDouble())
                    drawCircle(color = Color.White, radius = 7f, center = Offset((center.x + trackRadius * cos(pRad)).toFloat(), (center.y + trackRadius * sin(pRad)).toFloat()))
                }

                drawIntoCanvas { canvas ->
                    for (i in 0..5) {
                        val angleRad = Math.toRadians((startAngle + (i * (sweepAngle / 5))).toDouble())
                        val valSpd = Math.round(i * (maxSpeed / 5))
                        val textX = (center.x + (trackRadius + 25f) * cos(angleRad)).toFloat()
                        val textY = (center.y + (trackRadius + 25f) * sin(angleRad)).toFloat()
                        val isLit = speed >= valSpd
                        val numColor = if (isLit) reactiveColor else (if (isLight) Color(0xFF94A3B8) else Color(0xFF475569))

                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb((numColor.alpha*255).toInt(), (numColor.red*255).toInt(), (numColor.green*255).toInt(), (numColor.blue*255).toInt())
                            textSize = with(density) { 14.sp.toPx() }
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                            if (!isLight && isLit) setShadowLayer(15f, 0f, 0f, color)
                        }
                        canvas.nativeCanvas.drawText(valSpd.toString(), textX, textY, paint) 
                    }
                }
            }

            "ANIME" -> {
                val sweepAngle = 240f
                val startAngle = 150f
                val radius = size.width / 2
                val mainRadius = radius * 0.75f

                if (spProg > 0.05f) {
                    val numLines = (50f + (spProg * 50f)).toInt()
                    for (i in 0 until numLines) {
                        val angle = (i * Math.PI * 2) / numLines + (cumWarp * 1.5f)
                        val noise = sin(cumTime * 50f + i * 11f) * 0.5f + 0.5f
                        val innerR = mainRadius * 0.4f + (noise * 20f)
                        val outerR = mainRadius * 0.85f + (spProg * 30f * noise)
                        
                        drawLine(
                            color = reactiveColor.copy(alpha = (0.1f + (spProg * 0.4f)) * noise),
                            start = Offset((center.x + innerR * cos(angle)).toFloat(), (center.y + innerR * sin(angle)).toFloat()),
                            end = Offset((center.x + outerR * cos(angle)).toFloat(), (center.y + outerR * sin(angle)).toFloat()),
                            strokeWidth = 1f + (noise * 2f),
                            blendMode = glowBlendMode
                        )
                    }
                }

                drawArc(color = inactiveColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = 4f, cap = StrokeCap.Butt), size = Size(mainRadius*2, mainRadius*2), topLeft = Offset(center.x - mainRadius, center.y - mainRadius))

                if (spProg > 0) {
                    drawArc(color = reactiveColor, startAngle = startAngle, sweepAngle = sweepAngle * spProg, useCenter = false, style = Stroke(width = 8f, cap = StrokeCap.Butt), size = Size(mainRadius*2, mainRadius*2), topLeft = Offset(center.x - mainRadius, center.y - mainRadius))
                    
                    val ptrRad = Math.toRadians((startAngle + sweepAngle * spProg).toDouble())
                    val ptrX = (center.x + mainRadius * cos(ptrRad)).toFloat()
                    val ptrY = (center.y + mainRadius * sin(ptrRad)).toFloat()
                    
                    val katanaPath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(0f, -10f)
                        lineTo(8f, 15f)
                        lineTo(0f, 5f)
                        lineTo(-8f, 15f)
                        close()
                    }
                    
                    withTransform({
                        translate(left = ptrX, top = ptrY)
                        rotate(degrees = (Math.toDegrees(ptrRad) + 90).toFloat())
                    }) {
                        drawPath(path = katanaPath, color = Color.White)
                        drawLine(color = reactiveColor, start = Offset(0f, -10f), end = Offset(0f, 5f), strokeWidth = 2f)
                    }
                }

                val numSegs = 10
                for (i in 0..numSegs) {
                    val rad = Math.toRadians((startAngle + (i * (sweepAngle/numSegs))).toDouble())
                    val valSpd = (i.toFloat() / numSegs) * maxSpeed
                    val isLit = speed >= valSpd
                    drawLine(
                        color = if (isLit) Color.White else inactiveColor,
                        start = Offset((center.x + (mainRadius - 15f) * cos(rad)).toFloat(), (center.y + (mainRadius - 15f) * sin(rad)).toFloat()),
                        end = Offset((center.x + (mainRadius - 5f) * cos(rad)).toFloat(), (center.y + (mainRadius - 5f) * sin(rad)).toFloat()),
                        strokeWidth = 6f
                    )
                }

                drawIntoCanvas { canvas ->
                    for (i in 0..5) {
                        val angleRad = Math.toRadians((startAngle + (i * (sweepAngle / 5))).toDouble())
                        val valSpd = Math.round(i * (maxSpeed / 5))
                        val textX = (center.x + (mainRadius + 25f) * cos(angleRad)).toFloat()
                        val textY = (center.y + (mainRadius + 25f) * sin(angleRad)).toFloat()
                        val isLit = speed >= valSpd
                        val numColor = if (isLit) reactiveColor else (if (isLight) Color(0xFF94A3B8) else Color(0xFF475569))

                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb((numColor.alpha*255).toInt(), (numColor.red*255).toInt(), (numColor.green*255).toInt(), (numColor.blue*255).toInt())
                            textSize = with(density) { 16.sp.toPx() }
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC)
                            if (!isLight && isLit) setShadowLayer(15f, 0f, 0f, color)
                        }
                        canvas.nativeCanvas.drawText(valSpd.toString(), textX, textY, paint) 
                    }
                }
            }

            "KAIJU" -> {
                val sweepAngle = 200f
                val startAngle = 170f
                val radius = size.width / 2
                val mainRadius = radius * 0.65f

                val coreNoise = sin(cumTime * 30f) * 5f
                val atomicRad = 35f + (spProg * 20f) + coreNoise
                val coreGrad = Brush.radialGradient(
                    colors = listOf(Color.White, reactiveColor, Color.Transparent),
                    center = center,
                    radius = atomicRad
                )
                drawCircle(brush = coreGrad, radius = atomicRad, center = center, blendMode = glowBlendMode)

                val numPlates = 15
                val plateStep = sweepAngle / numPlates
                
                for (i in 0..numPlates) {
                    val angle = startAngle + (i * plateStep)
                    val rad = Math.toRadians(angle.toDouble())
                    val valSpd = (i.toFloat() / numPlates) * maxSpeed
                    val isLit = speed >= valSpd

                    val midDist = 1f - abs((i.toFloat() / numPlates) - 0.5f) * 2f 
                    val basePlateHeight = 15f + (midDist * 25f)
                    val activePlateHeight = basePlateHeight + (if (isLit) (Math.random() * 8f).toFloat() else 0f)

                    val innerR = mainRadius
                    val outerR = mainRadius + activePlateHeight

                    val radL = Math.toRadians((angle - 3f).toDouble())
                    val radR = Math.toRadians((angle + 3f).toDouble())

                    val platePath = androidx.compose.ui.graphics.Path().apply {
                        moveTo((center.x + innerR * cos(radL)).toFloat(), (center.y + innerR * sin(radL)).toFloat())
                        lineTo((center.x + outerR * cos(rad)).toFloat(), (center.y + outerR * sin(rad)).toFloat())
                        lineTo((center.x + innerR * cos(radR)).toFloat(), (center.y + innerR * sin(radR)).toFloat())
                        close()
                    }

                    if (isLit) {
                        drawPath(path = platePath, color = reactiveColor)
                        drawPath(path = platePath, color = Color.White, style = Stroke(width = 1f))
                    } else {
                        drawPath(path = platePath, color = inactiveColor)
                        drawPath(path = platePath, color = if (isLight) Color.Black.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f), style = Stroke(width = 1f))
                    }
                }

                drawArc(color = inactiveColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = 6f, cap = StrokeCap.Round), size = Size((mainRadius - 5f)*2, (mainRadius - 5f)*2), topLeft = Offset(center.x - mainRadius + 5f, center.y - mainRadius + 5f))
                if (spProg > 0) {
                    drawArc(color = reactiveColor, startAngle = startAngle, sweepAngle = sweepAngle * spProg, useCenter = false, style = Stroke(width = 4f), size = Size((mainRadius - 5f)*2, (mainRadius - 5f)*2), topLeft = Offset(center.x - mainRadius + 5f, center.y - mainRadius + 5f))
                }

                drawIntoCanvas { canvas ->
                    for (i in 0..5) {
                        val angleRad = Math.toRadians((startAngle + (i * (sweepAngle / 5))).toDouble())
                        val valSpd = Math.round(i * (maxSpeed / 5))
                        
                        val textRadPos = mainRadius + 30f + ( (1f - abs((i.toFloat() / 5f) - 0.5f) * 2f) * 25f )
                        val textX = (center.x + textRadPos * cos(angleRad)).toFloat()
                        val textY = (center.y + textRadPos * sin(angleRad)).toFloat()
                        
                        val isLit = speed >= valSpd
                        val numColor = if (isLit) reactiveColor else (if (isLight) Color(0xFF94A3B8) else Color(0xFF475569))

                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb((numColor.alpha*255).toInt(), (numColor.red*255).toInt(), (numColor.green*255).toInt(), (numColor.blue*255).toInt())
                            textSize = with(density) { 16.sp.toPx() }
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.create("impact", android.graphics.Typeface.BOLD)
                            if (!isLight && isLit) setShadowLayer(15f, 0f, 0f, color)
                        }
                        canvas.nativeCanvas.drawText(valSpd.toString(), textX, textY, paint) 
                    }
                }
            }
        }
    }
}