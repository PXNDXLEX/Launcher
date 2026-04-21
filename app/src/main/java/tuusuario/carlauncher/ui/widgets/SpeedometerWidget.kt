package com.tuusuario.carlauncher.ui.widgets

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.min
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.tuusuario.carlauncher.ui.AppSettings
import com.tuusuario.carlauncher.ui.NavigationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
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
    
    val inactiveColor = if (isLight) Color.LightGray.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.15f)
    val textColor = MaterialTheme.colorScheme.onSurface
    val tickColor = if (isLight) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.4f)
    val backgroundColor = MaterialTheme.colorScheme.background

    val c0 = Color(baseColor.red * 0.3f, baseColor.green * 0.3f, baseColor.blue * 0.3f, 1f)
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

    val isHighSpeed = animatedSpeed >= 50f
    val shakeMult = if (isHighSpeed) ((animatedSpeed - 50f) * 0.08f + 3f) else 0f
    val shakeX = if ((style == "SHONEN" || style == "OMNIMON") && isHighSpeed) (Math.random() * shakeMult * 2 - shakeMult).toFloat() else 0f
    val shakeY = if ((style == "SHONEN" || style == "OMNIMON") && isHighSpeed) (Math.random() * shakeMult * 2 - shakeMult).toFloat() else 0f

    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val boxSize = min(maxWidth, maxHeight) * 1.20f 
        
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
            modifier = Modifier
                .size(boxSize)
                .aspectRatio(1f)
                .offset(x = shakeX.dp, y = shakeY.dp)
        )

        Box(modifier = Modifier.offset(x = shakeX.dp, y = shakeY.dp)) {
            when (style) {
                "OMNIMON" -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (-boxSize.value * 0.03f).dp)) {
                        val textShadow = if (isLight) Shadow(color = Color.White.copy(alpha = 0.9f), blurRadius = 10f) else Shadow(color = reactiveColorText, blurRadius = 25f)
                        Text(text = speed.toInt().toString(), color = if (isLight) Color.Black else Color.White, fontSize = (boxSize.value * 0.35f).sp, fontWeight = FontWeight.Black, style = TextStyle(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, shadow = textShadow))
                        Text(text = "OMEGA INFORCE", color = if (isLight) Color(0xFFE31B23) else Color(0xFF00E5FF), fontSize = (boxSize.value * 0.05f).sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp, style = TextStyle(shadow = textShadow))
                        Text(text = "KM/H", color = if (isLight) Color.Black else reactiveColorText, fontSize = (boxSize.value * 0.06f).sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, style = TextStyle(shadow = textShadow))
                    }
                }
                "SHONEN" -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (-boxSize.value * 0.05f).dp)) {
                        val textShadow = if (speed > 100f && !isLight) Shadow(color = reactiveColorText, offset = Offset(-4f, 4f), blurRadius = 15f) else null
                        Text(text = speed.toInt().toString(), color = if (isLight) Color.Black else Color.White, fontSize = (boxSize.value * 0.38f).sp, fontWeight = FontWeight.Black, style = TextStyle(fontStyle = FontStyle.Italic, shadow = textShadow))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                            Text(text = "限界突破", color = if (isLight) Color.Black else reactiveColorText, fontSize = (boxSize.value * 0.07f).sp, fontWeight = FontWeight.Bold, style = TextStyle(fontStyle = FontStyle.Italic))
                            Text(text = "KM/H", color = textColor.copy(alpha = 0.8f), fontSize = (boxSize.value * 0.06f).sp, fontWeight = FontWeight.Black, style = TextStyle(fontStyle = FontStyle.Italic))
                        }
                    }
                }
                "MECHA" -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (-boxSize.value * 0.05f).dp)) {
                        Text(text = "[ ${speed.toInt().toString().padStart(3, '0')} ]", color = if (isLight) Color(0xFF003366) else reactiveColorText, fontSize = (boxSize.value * 0.28f).sp, fontWeight = FontWeight.Bold, style = TextStyle(fontFamily = FontFamily.Monospace))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            Box(modifier = Modifier.size(10.dp).background(if (speed > 100f) Color(0xFFED1C24) else (if (isLight) Color(0xFF003366) else reactiveColorText)))
                            Text(text = if (speed > 100f) "SYS_OVERRIDE" else "SYS_THRUST", color = textColor.copy(alpha = 0.7f), fontSize = (boxSize.value * 0.05f).sp, fontWeight = FontWeight.Medium, style = TextStyle(fontFamily = FontFamily.Monospace))
                        }
                    }
                }
                "RACING" -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (boxSize.value * 0.15f).dp)) {
                        Text(text = speed.toInt().toString(), color = textColor, fontSize = (boxSize.value * 0.35f).sp, fontWeight = FontWeight.Black)
                        Text(text = "KM/H", color = textColor.copy(alpha = 0.5f), fontSize = (boxSize.value * 0.08f).sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    }
                }
                "CYBER" -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (boxSize.value * 0.05f).dp)) {
                        Text(text = speed.toInt().toString(), color = textColor, fontSize = (boxSize.value * 0.30f).sp, fontWeight = FontWeight.Bold)
                        Box(modifier = Modifier.width(60.dp).height(2.dp).background(baseColor.copy(alpha = 0.8f)).padding(vertical = 4.dp))
                        Text(text = "K M / H", color = baseColor.copy(alpha = 0.8f), fontSize = (boxSize.value * 0.06f).sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                "AURA" -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (boxSize.value * 0.03f).dp)) {
                        val textShadow = if (!isLight) Shadow(color = baseColor.copy(alpha = 0.4f), offset = Offset(0f, 0f), blurRadius = 25f) else null
                        Text(text = speed.toInt().toString(), color = textColor, fontSize = (boxSize.value * 0.32f).sp, fontWeight = FontWeight.Black, style = TextStyle(shadow = textShadow))
                        Box(modifier = Modifier.padding(top = 4.dp).background(baseColor, shape = RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Text(text = "KM/H", color = Color.White, fontSize = (boxSize.value * 0.05f).sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                        }
                    }
                }
                "VORTEX" -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (-boxSize.value * 0.05f).dp)) {
                        Text(text = speed.toInt().toString(), color = textColor, fontSize = (boxSize.value * 0.32f).sp, fontWeight = FontWeight.Black, style = TextStyle(fontStyle = FontStyle.Italic))
                        Text(text = "KM/H", color = textColor.copy(alpha = 0.5f), fontSize = (boxSize.value * 0.06f).sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 8.sp)
                    }
                }
                "QUANTUM" -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (-boxSize.value * 0.06f).dp)) {
                        Text(text = speed.toInt().toString().padStart(3, '0'), color = textColor, fontSize = (boxSize.value * 0.28f).sp, fontWeight = FontWeight.Light, style = TextStyle(fontFamily = FontFamily.Monospace))
                        Text(text = "K M / H", color = reactiveColorText, fontSize = (boxSize.value * 0.05f).sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                    }
                }
                "PULSAR" -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (-boxSize.value * 0.04f).dp)) {
                        val pulseShadow = if (!isLight) Shadow(color = reactiveColorText.copy(alpha = 0.5f), blurRadius = 30f) else null
                        Text(text = speed.toInt().toString(), color = textColor, fontSize = (boxSize.value * 0.30f).sp, fontWeight = FontWeight.Bold, style = TextStyle(shadow = pulseShadow))
                        Text(text = "KM/H", color = reactiveColorText.copy(alpha = 0.8f), fontSize = (boxSize.value * 0.06f).sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp)
                    }
                }
                "PLASMA" -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (-boxSize.value * 0.04f).dp)) {
                        val plasmaShadow = if (!isLight) Shadow(color = reactiveColorText.copy(alpha = 0.5f), blurRadius = 25f) else null
                        Text(text = speed.toInt().toString(), color = textColor, fontSize = (boxSize.value * 0.30f).sp, fontWeight = FontWeight.Black, style = TextStyle(shadow = plasmaShadow))
                        Box(modifier = Modifier.padding(top = 4.dp).background(if (isLight) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                            Text(text = "KM/H", color = reactiveColorText, fontSize = (boxSize.value * 0.06f).sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 5.sp)
                        }
                    }
                }
                "ANIME" -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (-boxSize.value * 0.05f).dp)) {
                        val textShadow = Shadow(color = if (isLight) reactiveColorText.copy(alpha = 0.4f) else reactiveColorText, offset = Offset(4f, 4f), blurRadius = 0f)
                        Text(text = speed.toInt().toString(), color = textColor, fontSize = (boxSize.value * 0.32f).sp, fontWeight = FontWeight.Black, style = TextStyle(fontStyle = FontStyle.Italic, shadow = textShadow))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                            Text(text = "速度", color = reactiveColorText, fontSize = (boxSize.value * 0.07f).sp, fontWeight = FontWeight.Bold)
                            Text(text = "KM/H", color = textColor.copy(alpha = 0.7f), fontSize = (boxSize.value * 0.06f).sp, fontWeight = FontWeight.Black, style = TextStyle(fontStyle = FontStyle.Italic))
                        }
                    }
                }
                "KAIJU" -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (-boxSize.value * 0.04f).dp)) {
                        val textShadow = if (!isLight) Shadow(color = reactiveColorText, blurRadius = 30f) else null
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
    val outlineColor = Color.Black.copy(alpha = 0.85f)
    val speedProgress by rememberUpdatedState((speed / maxSpeed).coerceIn(0f, 1f))
    
    var omnimonBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val url = URL("https://64.media.tumblr.com/f819cb1a7b6d638ba8664f94745895c3/e5e9f1553298cb87-3f/s540x810/f8d14b50b74aa05278975dce67ba7afbcaa9a950.png")
                val connection = url.openConnection()
                connection.doInput = true
                connection.connect()
                val input = connection.inputStream
                omnimonBitmap = BitmapFactory.decodeStream(input).asImageBitmap()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    var cumTime by remember { mutableStateOf(0f) }
    var cumRadar by remember { mutableStateOf(0f) }
    var cumVortex by remember { mutableStateOf(0f) }
    var cumWarp by remember { mutableStateOf(0f) }
    var cumWave by remember { mutableStateOf(0f) }
    var cumPulse by remember { mutableStateOf(0f) }

    LaunchedEffect(style) {
        val animatedStyles = listOf("AURA", "VORTEX", "QUANTUM", "PULSAR", "PLASMA", "ANIME", "KAIJU", "OMNIMON", "SHONEN", "MECHA")
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
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        val c0 = Color(activeColor.red * 0.3f, activeColor.green * 0.3f, activeColor.blue * 0.3f, 1f)
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
            "OMNIMON" -> {
                val sweepAngle = 240f
                val startAngle = 150f
                val mainRadius = radius * 0.75f
                val arcSize = Size(mainRadius * 2, mainRadius * 2)
                val arcTopLeft = Offset(center.x - mainRadius, center.y - mainRadius)

                val imgShakeAmt = if (speed >= 80f) ((speed - 80f) * 0.2f + 5f) else 0f
                val imgShakeX = ((Math.random() - 0.5) * imgShakeAmt).toFloat()
                val imgShakeY = ((Math.random() - 0.5) * imgShakeAmt).toFloat()

                withTransform({
                    clipPath(Path().apply { addOval(Rect(center.x - mainRadius, center.y - mainRadius, center.x + mainRadius, center.y + mainRadius)) })
                }) {
                    omnimonBitmap?.let { bitmap ->
                        drawImage(
                            image = bitmap,
                            dstOffset = IntOffset((center.x - mainRadius + imgShakeX).toInt(), (center.y - mainRadius + imgShakeY).toInt()),
                            dstSize = IntSize((mainRadius * 2).toInt(), (mainRadius * 2).toInt()),
                            alpha = if (isLight) 0.45f else 0.85f
                        )
                    }

                    val vignetteColors = if (isLight) {
                        listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0.4f), Color.Transparent)
                    } else {
                        listOf(Color.Black.copy(alpha = 0.85f), Color.Black.copy(alpha = 0.3f), Color.Black.copy(alpha = 0.6f))
                    }
                    val vignette = Brush.radialGradient(colors = vignetteColors, center = center, radius = mainRadius)
                    drawRect(brush = vignette, topLeft = Offset(center.x - mainRadius, center.y - mainRadius), size = Size(mainRadius*2, mainRadius*2))
                }

                drawArc(color = if (isLight) Color.Black.copy(alpha=0.15f) else Color.White.copy(alpha=0.2f), startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = radius * 0.04f, cap = StrokeCap.Round), size = arcSize, topLeft = arcTopLeft)

                if (spProg > 0) {
                    val swordGradient = Brush.sweepGradient(
                        0.0f to if (isLight) Color(0xFFD97706) else Color(0xFFF97316),
                        1.0f to if (isLight) Color(0xFF0284C7) else Color(0xFF0EA5E9),
                        center = center
                    )

                    drawArc(brush = swordGradient, startAngle = startAngle, sweepAngle = sweepAngle * spProg, useCenter = false, style = Stroke(width = radius * 0.04f, cap = StrokeCap.Round), size = arcSize, topLeft = arcTopLeft)

                    val ptrRad = Math.toRadians((startAngle + sweepAngle * spProg).toDouble())
                    val fireSpeed = 15f + (speed * 0.8f)
                    val flicker1 = sin(cumTime * fireSpeed) * radius * 0.035f
                    val flicker2 = cos(cumTime * fireSpeed * 1.3f) * radius * 0.025f
                    val flickerTip = sin(cumTime * fireSpeed * 2.5f) * radius * 0.055f

                    withTransform({
                        translate(center.x, center.y)
                        rotate(degrees = Math.toDegrees(ptrRad).toFloat() + 90f)
                    }) {
                        fun drawFlame(width: Float, height: Float, color: Color, offset: Float) {
                            val path = Path().apply {
                                moveTo(-width / 2f, 0f)
                                quadraticBezierTo(-width + offset, -height * 0.5f, offset, -height)
                                quadraticBezierTo(width + offset, -height * 0.5f, width / 2f, 0f)
                                close()
                            }
                            drawPath(path = path, color = color)
                        }

                        drawFlame(radius * 0.06f, mainRadius * 1.05f, Color(0xFF0284C7), flicker1)
                        drawFlame(radius * 0.04f, mainRadius * 0.95f, Color(0xFF00E5FF), flicker2)
                        drawFlame(radius * 0.02f, mainRadius * 0.8f, Color.White, flickerTip)
                    }
                }

                val numTicks = 6
                val textRadius = mainRadius + (radius * 0.15f)
                drawIntoCanvas { canvas ->
                    for (i in 0..numTicks) {
                        val currentAngle = startAngle + (i * (sweepAngle/numTicks))
                        val angleRad = Math.toRadians(currentAngle.toDouble())
                        
                        val innerR = mainRadius - radius * 0.08f
                        val outerR = mainRadius - radius * 0.04f
                        drawLine(color = if(isLight) Color.Black.copy(alpha=0.3f) else Color.White.copy(alpha=0.4f), start = Offset((center.x + innerR * cos(angleRad)).toFloat(), (center.y + innerR * sin(angleRad)).toFloat()), end = Offset((center.x + outerR * cos(angleRad)).toFloat(), (center.y + outerR * sin(angleRad)).toFloat()), strokeWidth = radius * 0.015f)
                        
                        val speedVal = Math.round(i * (maxSpeed / numTicks))
                        val isLit = speed >= speedVal
                        
                        val textX = (center.x + textRadius * cos(angleRad)).toFloat()
                        val textY = (center.y + textRadius * sin(angleRad)).toFloat()
                        val numShakeAmt = if (isLit && speed >= 50f) ((Math.random() - 0.5) * ((speed - 50f) * 0.15f + 4f)).toFloat() else 0f

                        val numColor = if (isLit) { if (isLight) Color.Black else Color.White } else { if (isLight) Color.Black.copy(alpha=0.5f) else Color.White.copy(alpha=0.4f) }
                        
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb((numColor.alpha*255).toInt(), (numColor.red*255).toInt(), (numColor.green*255).toInt(), (numColor.blue*255).toInt())
                            textSize = radius * 0.12f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC)
                            if (isLit) setShadowLayer(10f, 0f, 0f, android.graphics.Color.parseColor(if(isLight) "#FFFFFF" else "#0EA5E9"))
                        }
                        canvas.nativeCanvas.drawText(speedVal.toString(), textX + numShakeAmt, textY + (radius * 0.04f) + numShakeAmt, paint) 
                    }
                }
            }

            "SHONEN" -> {
                val sweepAngle = 250f
                val startAngle = 145f
                val mainRadius = radius * 0.70f
                val arcSize = Size(mainRadius * 2, mainRadius * 2)
                val arcTopLeft = Offset(center.x - mainRadius, center.y - mainRadius)

                val impactColor = if (isLight) Color.Black else Color.White
                val energyColor = if (isLight) activeColor else reactiveColor

                val numLines = (40f + (spProg * 60f)).toInt()
                for (i in 0 until numLines) {
                    val lineAngle = (i * Math.PI * 2) / numLines + (cumRadar * (spProg + 0.1f))
                    val lengthNoise = Math.random().toFloat()
                    val innerRadius = mainRadius * 0.3f + (lengthNoise * radius * 0.2f)
                    val outerRadius = mainRadius * 1.5f

                    val startX = (center.x + innerRadius * cos(lineAngle)).toFloat()
                    val startY = (center.y + innerRadius * sin(lineAngle)).toFloat()
                    val endX = (center.x + outerRadius * cos(lineAngle)).toFloat()
                    val endY = (center.y + outerRadius * sin(lineAngle)).toFloat()

                    val alpha = ((0.05f + spProg * 0.2f) * (lengthNoise * 0.5f + 0.5f)).coerceIn(0f, 1f)
                    drawLine(color = impactColor.copy(alpha = alpha), start = Offset(startX, startY), end = Offset(endX, endY), strokeWidth = radius * 0.005f + (Math.random() * radius * 0.01f).toFloat())
                }

                drawArc(color = inactiveColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = radius * 0.02f, cap = StrokeCap.Butt), size = arcSize, topLeft = arcTopLeft)

                val numSegs = 15
                for (i in 0..numSegs) {
                    val rad = Math.toRadians((startAngle + (i * (sweepAngle/numSegs))).toDouble())
                    val isLit = speed >= (i.toFloat() / numSegs) * maxSpeed
                    val shake = if (isLit && spProg > 0.5f) ((Math.random() - 0.5) * radius * 0.03f).toFloat() else 0f
                    val explosiveWidth = if (isLit) radius * 0.04f else radius * 0.015f
                    val innerExt = if (isLit) radius * 0.1f else radius * 0.05f

                    drawLine(
                        color = if (isLit) impactColor else inactiveColor,
                        start = Offset((center.x + (mainRadius - innerExt + shake) * cos(rad)).toFloat(), (center.y + (mainRadius - innerExt + shake) * sin(rad)).toFloat()),
                        end = Offset((center.x + (mainRadius + shake) * cos(rad)).toFloat(), (center.y + (mainRadius + shake) * sin(rad)).toFloat()),
                        strokeWidth = explosiveWidth
                    )
                }

                if (spProg > 0) {
                    drawArc(color = energyColor, startAngle = startAngle, sweepAngle = sweepAngle * spProg, useCenter = false, style = Stroke(width = radius * 0.06f), size = arcSize, topLeft = arcTopLeft)

                    val basePtrRad = Math.toRadians((startAngle + sweepAngle * spProg).toDouble())
                    val jitter = if (spProg > 0.7f) ((Math.random() - 0.5) * 0.04).toFloat() else 0f
                    val trails = listOf(0f, -0.08f, -0.18f)
                    val alphas = listOf(1f, 0.4f, 0.1f)

                    for (t in 2 downTo 0) {
                        val ptrRad = basePtrRad + trails[t] + jitter
                        
                        withTransform({
                            translate(center.x + mainRadius * cos(ptrRad).toFloat(), center.y + mainRadius * sin(ptrRad).toFloat())
                            rotate(degrees = Math.toDegrees(ptrRad).toFloat() + 90f)
                        }) {
                            val katanaPath = Path().apply {
                                moveTo(0f, -(radius * 0.12f))
                                lineTo(radius * 0.03f, radius * 0.08f)
                                lineTo(0f, radius * 0.04f)
                                lineTo(-(radius * 0.03f), radius * 0.08f)
                                close()
                            }
                            
                            if (t == 0) {
                                drawPath(path = katanaPath, color = if (isLight) Color.Black else Color.White)
                                drawLine(color = energyColor, start = Offset(0f, -(radius * 0.12f)), end = Offset(0f, radius * 0.04f), strokeWidth = radius * 0.01f)
                            } else {
                                drawPath(path = katanaPath, color = energyColor.copy(alpha = alphas[t]))
                            }
                        }
                    }
                }

                drawIntoCanvas { canvas ->
                    for (i in 0..5) {
                        val currentAngle = startAngle + (i * (sweepAngle / 5))
                        val angleRad = Math.toRadians(currentAngle.toDouble())
                        val speedVal = Math.round(i * (maxSpeed / 5))
                        val isLit = speed >= speedVal
                        
                        val jump = if (isLit) (Math.random() * radius * 0.03f).toFloat() else 0f
                        val textX = (center.x + (mainRadius + radius * 0.22f) * cos(angleRad)).toFloat()
                        val textY = (center.y + (mainRadius + radius * 0.22f) * sin(angleRad)).toFloat() - jump
                        
                        val numColor = if (isLit) (if (isLight) Color.Black else reactiveColor) else inactiveColor

                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb((numColor.alpha*255).toInt(), (numColor.red*255).toInt(), (numColor.green*255).toInt(), (numColor.blue*255).toInt())
                            textSize = if (isLit) radius * 0.16f else radius * 0.12f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD_ITALIC)
                        }
                        
                        if (isLit && isLight) {
                            val strokePaint = android.graphics.Paint(paint).apply {
                                style = android.graphics.Paint.Style.STROKE
                                strokeWidth = radius * 0.01f
                                color = android.graphics.Color.argb((activeColor.alpha*255).toInt(), (activeColor.red*255).toInt(), (activeColor.green*255).toInt(), (activeColor.blue*255).toInt())
                            }
                            canvas.nativeCanvas.drawText(speedVal.toString(), textX, textY + (radius * 0.03f), strokePaint)
                        }
                        canvas.nativeCanvas.drawText(speedVal.toString(), textX, textY + (radius * 0.03f), paint) 
                    }
                }
            }

            "MECHA" -> {
                val sweepAngle = 240f
                val startAngle = 150f
                val mainRadius = radius * 0.75f
                
                val rxBlue = if (isLight) Color(0xFF003366) else Color(0xFF0055A4)
                val rxRed = if (isLight) Color(0xFFCC0000) else Color(0xFFED1C24)
                val rxYellow = if (isLight) Color(0xFFD97706) else Color(0xFFF9D000)
                val rxLine = if (isLight) Color(0xFF003366).copy(alpha=0.2f) else Color(0xFF0055A4).copy(alpha=0.3f)
                
                for(i in 1..3) {
                    drawCircle(color = rxLine, radius = (mainRadius * i) / 3f, center = center, style = Stroke(width = radius * 0.005f))
                }
                
                drawLine(color = rxLine, start = Offset(center.x, center.y - mainRadius), end = Offset(center.x, center.y + mainRadius))
                drawLine(color = rxLine, start = Offset(center.x - mainRadius, center.y), end = Offset(center.x + mainRadius, center.y))
                drawLine(color = rxLine, start = Offset(center.x - mainRadius*0.7f, center.y - mainRadius*0.7f), end = Offset(center.x + mainRadius*0.7f, center.y + mainRadius*0.7f))
                drawLine(color = rxLine, start = Offset(center.x + mainRadius*0.7f, center.y - mainRadius*0.7f), end = Offset(center.x - mainRadius*0.7f, center.y + mainRadius*0.7f))

                val hexPulse = 0.2f + abs(sin(cumTime * 3f)) * 0.4f
                val hexColorStr = if (isLight) Color(0xFF003366).copy(alpha = hexPulse) else reactiveColor.copy(alpha = hexPulse)
                
                val hexRadius = radius * 0.25f
                val hexPath = Path()
                for (i in 0..6) {
                    val a = (i * Math.PI / 3) + (cumRadar * 0.2f)
                    val hX = (center.x + hexRadius * cos(a)).toFloat()
                    val hY = (center.y + hexRadius * sin(a)).toFloat()
                    if (i == 0) hexPath.moveTo(hX, hY) else hexPath.lineTo(hX, hY)
                }
                drawPath(path = hexPath, color = hexColorStr, style = Stroke(width = radius * 0.02f))

                withTransform({
                    clipPath(Path().apply { addOval(Rect(center.x - mainRadius, center.y - mainRadius, center.x + mainRadius, center.y + mainRadius)) })
                }) {
                    val scanY = center.y + (((cumTime * 120f) % (mainRadius * 2.2f)) - (mainRadius * 1.1f))
                    val scanGrad = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, if (isLight) Color(0x6600AAFF) else Color(0x4D00E5FF)),
                        startY = scanY - radius*0.1f,
                        endY = scanY
                    )
                    drawRect(brush = scanGrad, topLeft = Offset(center.x - mainRadius, scanY - radius*0.1f), size = Size(mainRadius*2f, radius*0.1f))
                    drawLine(color = if (isLight) Color(0x9900AAFF) else Color(0xCC00E5FF), start = Offset(center.x - mainRadius, scanY), end = Offset(center.x + mainRadius, scanY), strokeWidth = radius * 0.01f)
                }

                val trackRadius = mainRadius * 0.85f
                val trackSize = Size(trackRadius*2, trackRadius*2)
                val trackTopLeft = Offset(center.x - trackRadius, center.y - trackRadius)

                drawArc(color = if (isLight) Color(0xFFE2E8F0) else Color.White.copy(alpha=0.1f), startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = radius * 0.08f, cap = StrokeCap.Butt), size = trackSize, topLeft = trackTopLeft)
                
                val numTicks = 40
                for(i in 0..numTicks) {
                    val rad = Math.toRadians((startAngle + (i * (sweepAngle/numTicks))).toDouble())
                    drawLine(color = backgroundColor, start = Offset((center.x + (trackRadius - radius*0.04f) * cos(rad)).toFloat(), (center.y + (trackRadius - radius*0.04f) * sin(rad)).toFloat()), end = Offset((center.x + (trackRadius + radius*0.04f) * cos(rad)).toFloat(), (center.y + (trackRadius + radius*0.04f) * sin(rad)).toFloat()), strokeWidth = radius * 0.005f)
                }

                if (spProg > 0) {
                    drawArc(color = if (spProg > 0.8f) rxRed else rxYellow, startAngle = startAngle, sweepAngle = sweepAngle * spProg, useCenter = false, style = Stroke(width = radius * 0.08f), size = trackSize, topLeft = trackTopLeft)

                    val lockRad = Math.toRadians((startAngle + sweepAngle * spProg).toDouble())
                    val lockX = (center.x + trackRadius * cos(lockRad)).toFloat()
                    val lockY = (center.y + trackRadius * sin(lockRad)).toFloat()
                    
                    withTransform({
                        translate(lockX, lockY)
                        rotate(degrees = Math.toDegrees(lockRad).toFloat() + 90f)
                    }) {
                        val bracketPulse = if (spProg > 0.8f) abs(sin(cumTime * 15f)) * radius * 0.03f else 0f
                        val bX = radius * 0.08f + bracketPulse
                        val bY = radius * 0.06f + bracketPulse

                        val bracketPath = Path().apply {
                            moveTo(-bX, -bY)
                            lineTo(-bX - radius*0.04f, -bY)
                            lineTo(-bX - radius*0.04f, bY)
                            lineTo(-bX, bY)
                            moveTo(bX, -bY)
                            lineTo(bX + radius*0.04f, -bY)
                            lineTo(bX + radius*0.04f, bY)
                            lineTo(bX, bY)
                        }
                        drawPath(path = bracketPath, color = rxBlue, style = Stroke(width = radius * 0.015f, join = StrokeJoin.Miter))
                        
                        val triPath = Path().apply {
                            moveTo(0f, radius*0.05f)
                            lineTo(-radius*0.03f, radius*0.08f)
                            lineTo(radius*0.03f, radius*0.08f)
                            close()
                        }
                        drawPath(path = triPath, color = rxRed)
                    }
                }

                drawIntoCanvas { canvas ->
                    val paintSys = android.graphics.Paint().apply {
                        val cSys = if (isLight) Color(0xFF64748B) else Color(0xFF94A3B8)
                        color = android.graphics.Color.argb((cSys.alpha*255).toInt(), (cSys.red*255).toInt(), (cSys.green*255).toInt(), (cSys.blue*255).toInt())
                        textSize = radius * 0.06f
                        textAlign = android.graphics.Paint.Align.LEFT
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.MONOSPACE
                    }
                    canvas.nativeCanvas.drawText("RX-78-2 // HUD", center.x - mainRadius, center.y - mainRadius * 0.9f, paintSys)
                    
                    val showWarning = if (spProg > 0.8f) (Math.floor((cumTime * 10f).toDouble()).toInt() % 2 == 0) else true
                    val paintWarn = android.graphics.Paint(paintSys).apply {
                        textAlign = android.graphics.Paint.Align.RIGHT
                        val cWarn = if (spProg > 0.8f && showWarning) rxRed else (if (isLight) Color(0xFF64748B) else Color(0xFF94A3B8))
                        color = android.graphics.Color.argb((cWarn.alpha*255).toInt(), (cWarn.red*255).toInt(), (cWarn.green*255).toInt(), (cWarn.blue*255).toInt())
                    }
                    canvas.nativeCanvas.drawText(if (spProg > 0.8f) "WARNING!!" else "SYS: NORMAL", center.x + mainRadius, center.y - mainRadius * 0.9f, paintWarn)

                    for (i in 0..6) {
                        val currentAngle = startAngle + (i * (sweepAngle / 6))
                        val angleRad = Math.toRadians(currentAngle.toDouble())
                        val speedVal = Math.round(i * (maxSpeed / 6))
                        val isLit = speed >= speedVal
                        
                        val textX = (center.x + (mainRadius - radius * 0.25f) * cos(angleRad)).toFloat()
                        val textY = (center.y + (mainRadius - radius * 0.25f) * sin(angleRad)).toFloat()
                        
                        val numColor = if (isLit) { if (isLight) Color(0xFF003366) else Color.White } else { if (isLight) Color(0xFF94A3B8) else Color(0xFF475569) }

                        val paintTick = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb((numColor.alpha*255).toInt(), (numColor.red*255).toInt(), (numColor.green*255).toInt(), (numColor.blue*255).toInt())
                            textSize = radius * 0.10f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                        }
                        canvas.nativeCanvas.drawText("T-${speedVal}", textX, textY + (radius * 0.03f), paintTick) 
                    }
                }
            }

            "PREMIUM" -> {
                val sweepAngle = 240f
                val startAngle = 150f
                val activeSweepAngle = sweepAngle * spProg
                val arcWidth = radius * 0.08f
                val drawRadius = radius - arcWidth
                val arcSize = Size(drawRadius * 2, drawRadius * 2)
                val arcTopLeft = Offset(center.x - drawRadius, center.y - drawRadius)

                if (isLight) drawArc(color = outlineColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = arcWidth + (radius*0.03f), cap = StrokeCap.Round), size = arcSize, topLeft = arcTopLeft)
                drawArc(color = inactiveColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = arcWidth, cap = StrokeCap.Round), size = arcSize, topLeft = arcTopLeft)
                drawArc(color = activeColor, startAngle = startAngle, sweepAngle = activeSweepAngle, useCenter = false, style = Stroke(width = arcWidth, cap = StrokeCap.Round), size = arcSize, topLeft = arcTopLeft)
                
                val numTicks = 22 
                val tickStepAngle = sweepAngle / numTicks
                val tickStartRadius = drawRadius - (arcWidth / 2f) - (radius * 0.05f)

                drawIntoCanvas { canvas ->
                    for (i in 0..numTicks) {
                        val currentAngle = startAngle + (i * tickStepAngle)
                        val angleRad = Math.toRadians(currentAngle.toDouble())
                        val isMajorTick = i % 2 == 0
                        val tickLength = if (isMajorTick) radius * 0.12f else radius * 0.06f

                        val startX = (center.x + tickStartRadius * cos(angleRad)).toFloat()
                        val startY = (center.y + tickStartRadius * sin(angleRad)).toFloat()
                        val endX = (center.x + (tickStartRadius - tickLength) * cos(angleRad)).toFloat()
                        val endY = (center.y + (tickStartRadius - tickLength) * sin(angleRad)).toFloat()

                        val lineColor = if (i * 10 <= speed) activeColor else tickColor
                        drawLine(color = lineColor, start = Offset(startX, startY), end = Offset(endX, endY), strokeWidth = if (isMajorTick) radius * 0.03f else radius * 0.015f)

                        if (isMajorTick) {
                            val textRadius = tickStartRadius - tickLength - (radius * 0.1f)
                            val textX = (center.x + textRadius * cos(angleRad)).toFloat()
                            val textY = (center.y + textRadius * sin(angleRad)).toFloat()
                            
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.argb((textColor.alpha*255).toInt(), (textColor.red*255).toInt(), (textColor.green*255).toInt(), (textColor.blue*255).toInt())
                                textSize = radius * 0.12f
                                textAlign = android.graphics.Paint.Align.CENTER
                                isAntiAlias = true
                                typeface = android.graphics.Typeface.DEFAULT_BOLD
                            }
                            canvas.nativeCanvas.drawText((i * 10).toString(), textX, textY + (radius * 0.04f), paint)
                        }
                    }
                }
            }

            "NEON" -> {
                val sweepAngle = 240f
                val startAngle = 150f
                val activeSweepAngle = sweepAngle * spProg
                val arcWidth = radius * 0.12f
                val drawRadius = radius - arcWidth
                val arcSize = Size(drawRadius * 2, drawRadius * 2)
                val arcTopLeft = Offset(center.x - drawRadius, center.y - drawRadius)
                
                val dynamicColor = when {
                    speed < 60f -> Color(0xFF00FFCC)
                    speed < 110f -> Color(0xFFFFD54F)
                    else -> Color(0xFFFF1744)
                }

                if (isLight) drawArc(color = outlineColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = arcWidth + (radius*0.03f), cap = StrokeCap.Round), size = arcSize, topLeft = arcTopLeft)
                drawArc(color = inactiveColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = arcWidth, cap = StrokeCap.Butt, pathEffect = PathEffect.dashPathEffect(floatArrayOf(radius*0.05f, radius*0.08f))), size = arcSize, topLeft = arcTopLeft)
                drawArc(color = dynamicColor, startAngle = startAngle, sweepAngle = activeSweepAngle, useCenter = false, style = Stroke(width = arcWidth), size = arcSize, topLeft = arcTopLeft)
                drawArc(color = dynamicColor.copy(alpha = 0.3f), startAngle = startAngle, sweepAngle = activeSweepAngle, useCenter = false, style = Stroke(width = arcWidth * 2f, cap = StrokeCap.Round), size = arcSize, topLeft = arcTopLeft)

                val innerRad = drawRadius - arcWidth * 1.5f
                drawArc(color = dynamicColor.copy(alpha = 0.5f), startAngle = startAngle - 5f, sweepAngle = sweepAngle + 10f, useCenter = false, style = Stroke(width = radius * 0.02f, cap = StrokeCap.Round), size = Size(innerRad*2, innerRad*2), topLeft = Offset(center.x - innerRad, center.y - innerRad))

                val numTicks = 11
                val tickStepAngle = sweepAngle / numTicks
                val textRadius = drawRadius - (radius * 0.15f)

                drawIntoCanvas { canvas ->
                    for (i in 0..numTicks) {
                        val currentAngle = startAngle + (i * tickStepAngle)
                        val angleRad = Math.toRadians(currentAngle.toDouble())
                        val speedValue = i * 20
                        val isLit = speedValue <= speed

                        val textX = (center.x + textRadius * cos(angleRad)).toFloat()
                        val textY = (center.y + textRadius * sin(angleRad)).toFloat()

                        val paint = android.graphics.Paint().apply {
                            val targetColor = if (isLit) dynamicColor else textColor.copy(alpha = 0.5f)
                            color = android.graphics.Color.argb((targetColor.alpha*255).toInt(), (targetColor.red*255).toInt(), (targetColor.green*255).toInt(), (targetColor.blue*255).toInt())
                            textSize = radius * 0.14f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                            if (isLit) setShadowLayer(15f, 0f, 0f, color)
                        }
                        canvas.nativeCanvas.drawText(speedValue.toString(), textX, textY + (radius * 0.04f), paint)
                    }
                }
            }

            "RACING" -> {
                val sweepAngle = 180f
                val startAngle = 180f
                val activeSweepAngle = sweepAngle * spProg
                val arcWidth = radius * 0.12f
                val racingRadius = radius * 0.70f
                val racingCenter = Offset(center.x, center.y + (radius * 0.2f))
                val arcSize = Size(racingRadius * 2, racingRadius * 2)
                val arcTopLeft = Offset(racingCenter.x - racingRadius, racingCenter.y - racingRadius)

                if (isLight) drawArc(color = outlineColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = arcWidth + (radius * 0.03f), cap = StrokeCap.Butt), size = arcSize, topLeft = arcTopLeft)
                drawArc(color = inactiveColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = arcWidth, cap = StrokeCap.Butt), size = arcSize, topLeft = arcTopLeft)

                if (spProg > 0) {
                    val gradient = Brush.sweepGradient(
                        0.0f to activeColor.copy(alpha = 0.3f),
                        0.2f to activeColor,
                        0.5f to Color.White,
                        center = racingCenter
                    )
                    drawArc(brush = gradient, startAngle = startAngle, sweepAngle = activeSweepAngle, useCenter = false, style = Stroke(width = arcWidth), size = arcSize, topLeft = arcTopLeft)
                }

                val numSegments = 30
                val tickStepAngle = sweepAngle / numSegments
                for (i in 0..numSegments) {
                    val currentAngle = startAngle + (i * tickStepAngle)
                    val angleRad = Math.toRadians(currentAngle.toDouble())
                    val isMajor = i % 5 == 0

                    val startRad = racingRadius - (arcWidth / 2f)
                    val endRad = if (isMajor) racingRadius + (arcWidth / 2f) else racingRadius + (arcWidth / 4f)

                    val startX = (racingCenter.x + startRad * cos(angleRad)).toFloat()
                    val startY = (racingCenter.y + startRad * sin(angleRad)).toFloat()
                    val endX = (racingCenter.x + endRad * cos(angleRad)).toFloat()
                    val endY = (racingCenter.y + endRad * sin(angleRad)).toFloat()

                    drawLine(color = backgroundColor, start = Offset(startX, startY), end = Offset(endX, endY), strokeWidth = if (isMajor) radius * 0.03f else radius * 0.015f)
                }

                val numScaleValues = 5
                val scaleStepAngle = sweepAngle / numScaleValues
                val textRadius = racingRadius + arcWidth + (radius * 0.1f)

                drawIntoCanvas { canvas ->
                    for (i in 0..numScaleValues) {
                        val currentAngle = startAngle + (i * scaleStepAngle)
                        val angleRad = Math.toRadians(currentAngle.toDouble())
                        val speedVal = Math.round(i * (maxSpeed / numScaleValues))

                        val textX = (racingCenter.x + textRadius * cos(angleRad)).toFloat()
                        val textY = (racingCenter.y + textRadius * sin(angleRad)).toFloat() + (radius * 0.02f)

                        val isLit = speed >= speedVal
                        val c = if (isLit) activeColor else textColor
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb((if(isLit) 255 else 180), (c.red*255).toInt(), (c.green*255).toInt(), (c.blue*255).toInt())
                            textSize = radius * 0.14f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                        canvas.nativeCanvas.drawText(speedVal.toString(), textX, textY, paint)
                    }
                }
                
                drawArc(color = Color.Red.copy(alpha = 0.8f), startAngle = startAngle + sweepAngle * 0.8f, sweepAngle = sweepAngle * 0.2f, useCenter = false, style = Stroke(width = arcWidth * 0.15f), size = arcSize, topLeft = arcTopLeft)
            }

            "CYBER" -> {
                val sweepAngle = 270f
                val startAngle = 135f
                val activeSweepAngle = sweepAngle * spProg
                val arcWidth = radius * 0.06f
                val drawRadius = radius * 0.75f
                val arcSize = Size(drawRadius * 2, drawRadius * 2)
                val arcTopLeft = Offset(center.x - drawRadius, center.y - drawRadius)

                for(i in 0..40) {
                    val angleRad = Math.toRadians((startAngle + (i * (sweepAngle/40))).toDouble())
                    val x = (center.x + drawRadius * cos(angleRad)).toFloat()
                    val y = (center.y + drawRadius * sin(angleRad)).toFloat()
                    drawCircle(color = inactiveColor, radius = radius * 0.015f, center = Offset(x, y))
                }

                drawArc(color = activeColor, startAngle = startAngle, sweepAngle = activeSweepAngle, useCenter = false, style = Stroke(width = arcWidth, cap = StrokeCap.Square), size = arcSize, topLeft = arcTopLeft)
                
                val innerR = drawRadius - (radius * 0.08f)
                val outerR = drawRadius + (radius * 0.08f)
                val pointerRad = Math.toRadians((startAngle + activeSweepAngle).toDouble())
                drawLine(color = activeColor, start = Offset((center.x + innerR * cos(pointerRad)).toFloat(), (center.y + innerR * sin(pointerRad)).toFloat()), end = Offset((center.x + outerR * cos(pointerRad)).toFloat(), (center.y + outerR * sin(pointerRad)).toFloat()), strokeWidth = radius * 0.03f)
                
                drawIntoCanvas { canvas ->
                    for (i in 0..6) {
                        val currentAngle = startAngle + (i * (sweepAngle/6))
                        val angleRad = Math.toRadians(currentAngle.toDouble())
                        val speedVal = Math.round(i * (maxSpeed / 6))
                        val textX = (center.x + (drawRadius + (radius * 0.18f)) * cos(angleRad)).toFloat()
                        val textY = (center.y + (drawRadius + (radius * 0.18f)) * sin(angleRad)).toFloat()
                        val isLit = speed >= speedVal
                        val numColor = if (isLit) activeColor else textColor.copy(alpha = 0.5f)

                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb((numColor.alpha*255).toInt(), (numColor.red*255).toInt(), (numColor.green*255).toInt(), (numColor.blue*255).toInt())
                            textSize = radius * 0.12f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.MONOSPACE
                        }
                        canvas.nativeCanvas.drawText(speedVal.toString(), textX, textY + (radius * 0.03f), paint)
                    }
                }
            }

            "AURA" -> {
                val sweepAngle = 260f
                val startAngle = 140f
                val mainRadius = radius * 0.65f
                val arcSize = Size(mainRadius * 2, mainRadius * 2)
                val arcTopLeft = Offset(center.x - mainRadius, center.y - mainRadius)

                val auraPulse = (sin(cumTime * 3f) * 0.05f)
                val baseAuraAlpha = if (isLight) 0.15f else 0.2f
                val auraAlpha = (baseAuraAlpha + (spProg * 0.4f) + auraPulse).coerceIn(0f, 1f)
                val auraRadius = mainRadius + (radius * 0.1f) + (spProg * (radius * 0.2f))
                
                val auraBrush = Brush.radialGradient(
                    colors = listOf(activeColor.copy(alpha = auraAlpha), activeColor.copy(alpha = auraAlpha * 0.4f), Color.Transparent),
                    center = center,
                    radius = auraRadius
                )
                drawCircle(brush = auraBrush, radius = auraRadius, center = center)

                rotate(degrees = cumVortex * 57.3f, pivot = center) {
                    val decR = mainRadius - (radius * 0.15f)
                    drawCircle(color = if (isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.06f), radius = decR, center = center, style = Stroke(width = radius * 0.06f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(radius*0.12f, radius*0.08f, radius*0.03f, radius*0.08f))))
                }

                rotate(degrees = -cumVortex * 0.7f * 57.3f, pivot = center) {
                    val decR = mainRadius - (radius * 0.25f)
                    drawCircle(color = activeColor.copy(alpha = 0.4f), radius = decR, center = center, style = Stroke(width = radius * 0.015f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(radius*0.05f, radius*0.05f))))
                }

                val numBars = 45
                val barStep = sweepAngle / numBars

                for (i in 0..numBars) {
                    val currentAngle = startAngle + (i * barStep)
                    val rad = Math.toRadians(currentAngle.toDouble())
                    val barSpeedVal = (i.toFloat() / numBars) * maxSpeed
                    val isLit = speed >= barSpeedVal

                    val barHeight = if (isLit) (radius * 0.08f) + (sin(cumWave + i * 0.5f) * (radius * 0.03f)) else (radius * 0.04f)
                    val innerR = mainRadius - barHeight / 2f
                    val outerR = mainRadius + barHeight / 2f

                    val startX = (center.x + innerR * cos(rad)).toFloat()
                    val startY = (center.y + innerR * sin(rad)).toFloat()
                    val endX = (center.x + outerR * cos(rad)).toFloat()
                    val endY = (center.y + outerR * sin(rad)).toFloat()

                    if (isLit) {
                        if (!isLight) drawLine(color = activeColor.copy(alpha = 0.4f), start = Offset(startX, startY), end = Offset(endX, endY), strokeWidth = radius * 0.06f, cap = StrokeCap.Round)
                        drawLine(color = activeColor, start = Offset(startX, startY), end = Offset(endX, endY), strokeWidth = radius * 0.02f, cap = StrokeCap.Round)
                    } else {
                        val inactiveBarColor = if (isLight) Color.Black.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.1f)
                        drawLine(color = inactiveBarColor, start = Offset(startX, startY), end = Offset(endX, endY), strokeWidth = radius * 0.015f, cap = StrokeCap.Round)
                    }
                }

                val outerRingR = mainRadius + (radius * 0.08f)
                drawArc(color = activeColor, startAngle = startAngle, sweepAngle = sweepAngle * spProg, useCenter = false, style = Stroke(width = radius * 0.015f), size = Size(outerRingR * 2, outerRingR * 2), topLeft = Offset(center.x - outerRingR, center.y - outerRingR))

                if (spProg > 0) {
                    val dotRad = Math.toRadians((startAngle + (sweepAngle * spProg)).toDouble())
                    val dotX = (center.x + outerRingR * cos(dotRad)).toFloat()
                    val dotY = (center.y + outerRingR * sin(dotRad)).toFloat()
                    if (!isLight) drawCircle(color = activeColor.copy(alpha = 0.5f), radius = radius * 0.06f, center = Offset(dotX, dotY))
                    drawCircle(color = Color.White, radius = radius * 0.03f, center = Offset(dotX, dotY))
                }
                
                val numScaleValues = 5
                val scaleStepAngle = sweepAngle / numScaleValues
                val textRadius = mainRadius + (radius * 0.22f)

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
                            textSize = radius * 0.12f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                        canvas.nativeCanvas.drawText(speedVal.toString(), textX, textY + (radius * 0.03f), paint) 
                    }
                }
            }

            "VORTEX" -> {
                val sweepAngle = 260f
                val startAngle = 140f
                val mainRadius = radius * 0.75f
                val arcSize = Size(mainRadius * 2, mainRadius * 2)
                val arcTopLeft = Offset(center.x - mainRadius, center.y - mainRadius)

                val numStars = 60
                for (i in 0 until numStars) {
                    val rawR = (i * (radius * 0.06f) + cumWarp) % mainRadius
                    val alpha = (rawR / mainRadius).coerceIn(0f, 1f)
                    val starAngle = (i * 137.5f) * (Math.PI / 180f)
                    val sx = (center.x + rawR * cos(starAngle + cumVortex)).toFloat()
                    val sy = (center.y + rawR * sin(starAngle + cumVortex)).toFloat()
                    val starColor = if (isLight) Color.Black.copy(alpha = alpha) else Color.White.copy(alpha = alpha)
                    drawCircle(color = starColor, radius = radius * 0.015f, center = Offset(sx, sy))
                }

                rotate(degrees = -cumVortex * 0.8f * 57.3f, pivot = center) {
                    val innerR = mainRadius * 0.5f
                    drawArc(color = reactiveColor.copy(alpha = 0.3f + (spProg * 0.4f)), startAngle = 0f, sweepAngle = 360f, useCenter = false, style = Stroke(width = radius * 0.08f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(radius*0.2f, radius*0.1f, radius*0.05f, radius*0.1f))), size = Size(innerR*2, innerR*2), topLeft = Offset(center.x - innerR, center.y - innerR))
                }
                rotate(degrees = cumVortex * 2.3f * 57.3f, pivot = center) {
                    val outerR = mainRadius * 0.85f
                    drawArc(color = reactiveColor.copy(alpha = 0.5f), startAngle = 0f, sweepAngle = 360f, useCenter = false, style = Stroke(width = radius * 0.02f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(radius*0.03f, radius*0.08f))), size = Size(outerR*2, outerR*2), topLeft = Offset(center.x - outerR, center.y - outerR))
                }

                drawArc(color = inactiveColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = radius * 0.03f, cap = StrokeCap.Round), size = arcSize, topLeft = arcTopLeft)

                if (spProg > 0) {
                    drawArc(color = reactiveColor, startAngle = startAngle, sweepAngle = sweepAngle * spProg, useCenter = false, style = Stroke(width = radius * 0.05f, cap = StrokeCap.Round), size = arcSize, topLeft = arcTopLeft)
                    val dotRad = Math.toRadians((startAngle + (sweepAngle * spProg)).toDouble())
                    val dotX = (center.x + mainRadius * cos(dotRad)).toFloat()
                    val dotY = (center.y + mainRadius * sin(dotRad)).toFloat()
                    
                    rotate(degrees = (startAngle + sweepAngle * spProg) + 45f, pivot = Offset(dotX, dotY)) {
                        drawRect(color = Color.White, topLeft = Offset(dotX - (radius*0.03f), dotY - (radius*0.03f)), size = Size(radius*0.06f, radius*0.06f))
                    }
                }

                val numScaleValues = 6
                val scaleStepAngle = sweepAngle / numScaleValues
                val textRadius = mainRadius + (radius * 0.15f)

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
                            textSize = radius * 0.12f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                        canvas.nativeCanvas.drawText(speedVal.toString(), textX, textY + (radius * 0.03f), paint) 
                    }
                }
            }

            "QUANTUM" -> {
                val sweepAngle = 260f
                val startAngle = 140f
                val mainRadius = radius * 0.70f
                val arcSize = Size(mainRadius * 2, mainRadius * 2)
                val arcTopLeft = Offset(center.x - mainRadius, center.y - mainRadius)

                val radarAngleDeg = (cumRadar * 57.3f) % 360f
                val sweepWidth = 35f 
                val numSlices = 20
                
                drawIntoCanvas { canvas ->
                    val innerArcR = mainRadius - (radius * 0.05f)
                    val radarRect = android.graphics.RectF(center.x - innerArcR, center.y - innerArcR, center.x + innerArcR, center.y + innerArcR)
                    
                    for (i in 0 until numSlices) {
                        val sliceStart = radarAngleDeg - (sweepWidth * (i.toFloat() / numSlices))
                        val sliceSweep = -(sweepWidth / numSlices)
                        
                        val paint = android.graphics.Paint()
                        paint.color = android.graphics.Color.argb(
                            (90f * (1f - (i.toFloat() / numSlices))).toInt(), 
                            (activeColor.red*255).toInt(), 
                            (activeColor.green*255).toInt(), 
                            (activeColor.blue*255).toInt()
                        )
                        paint.style = android.graphics.Paint.Style.FILL
                        paint.isAntiAlias = true
                        
                        canvas.nativeCanvas.drawArc(radarRect, sliceStart, sliceSweep, true, paint)
                    }
                }

                val waveWidth = radius * 0.5f
                val waveAmp = (radius * 0.02f) + (spProg * (radius * 0.1f))
                val waveFreq = 0.05f + (spProg * 0.1f)
                
                var prevX = 0f
                var prevY = 0f
                for (x in (-waveWidth/2).toInt()..(waveWidth/2).toInt()) {
                    val yOffset = sin(x * waveFreq + cumWave) * waveAmp
                    val dampen = 1f - abs(x) / (waveWidth/2f)
                    val px = center.x + x
                    val py = center.y + (radius * 0.25f) + (yOffset * dampen).toFloat()
                    
                    if (x == (-waveWidth/2).toInt()) {
                        prevX = px; prevY = py
                    } else {
                        drawLine(color = reactiveColor, start = Offset(prevX, prevY), end = Offset(px, py), strokeWidth = radius * 0.015f)
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
                    
                    val innerR = mainRadius - if (isLit) (radius * 0.02f) else 0f
                    val outerR = mainRadius + if (isLit) (radius * 0.06f) else (radius * 0.03f)
                    
                    val startX = (center.x + innerR * cos(rad)).toFloat()
                    val startY = (center.y + innerR * sin(rad)).toFloat()
                    val endX = (center.x + outerR * cos(rad)).toFloat()
                    val endY = (center.y + outerR * sin(rad)).toFloat()
                    
                    drawLine(color = if (isLit) reactiveColor else inactiveColor, start = Offset(startX, startY), end = Offset(endX, endY), strokeWidth = if (isLit) radius * 0.02f else radius * 0.015f)
                }

                if (spProg > 0) {
                    val dotRad = Math.toRadians((startAngle + (sweepAngle * spProg)).toDouble())
                    val pX = (center.x + (mainRadius + (radius*0.1f)) * cos(dotRad)).toFloat()
                    val pY = (center.y + (mainRadius + (radius*0.1f)) * sin(dotRad)).toFloat()
                    drawCircle(color = Color.White, radius = radius * 0.03f, center = Offset(pX, pY))
                    drawLine(color = reactiveColor, start = Offset((center.x + (mainRadius + (radius*0.02f)) * cos(dotRad)).toFloat(), (center.y + (mainRadius + (radius*0.02f)) * sin(dotRad)).toFloat()), end = Offset(pX, pY), strokeWidth = radius * 0.015f)
                }

                val numScaleValues = 4
                val scaleStepAngle = sweepAngle / numScaleValues
                val textRadius = mainRadius + (radius * 0.22f)

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
                            textSize = radius * 0.12f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.MONOSPACE
                        }
                        canvas.nativeCanvas.drawText(speedVal.toString(), textX, textY + (radius * 0.03f), paint) 
                    }
                }
            }

            "PULSAR" -> {
                val sweepAngle = 260f
                val startAngle = 140f
                val mainRadius = radius * 0.65f
                val arcSize = Size((mainRadius + (radius*0.15f))*2, (mainRadius + (radius*0.15f))*2)
                val arcTopLeft = Offset(center.x - mainRadius - (radius*0.15f), center.y - mainRadius - (radius*0.15f))

                val heartbeat = Math.pow(sin(cumPulse).toDouble(), 2.0).toFloat()
                
                val orbRadius = (radius * 0.2f) + (spProg * (radius * 0.15f)) + (heartbeat * (radius * 0.05f))
                val orbGradient = Brush.radialGradient(
                    colors = listOf(reactiveColor, reactiveColor.copy(alpha = 0.5f), Color.Transparent),
                    center = center,
                    radius = orbRadius
                )
                drawCircle(brush = orbGradient, radius = orbRadius, center = center)

                val numRipples = 3
                for (i in 0 until numRipples) {
                    val rippleR = ((cumTime * (radius * 0.3f)) + (i * (mainRadius / numRipples))) % mainRadius
                    val rippleAlpha = (1f - (rippleR / mainRadius)).coerceIn(0f, 1f)
                    drawCircle(color = reactiveColor.copy(alpha = rippleAlpha), radius = rippleR, center = center, style = Stroke(width = radius * 0.015f))
                }

                val numBars = 60
                val barStep = sweepAngle / numBars
                for (i in 0..numBars) {
                    val currentAngle = startAngle + (i * barStep)
                    val rad = Math.toRadians(currentAngle.toDouble())
                    val valProgress = (i.toFloat() / numBars) * maxSpeed
                    val isLit = speed >= valProgress

                    val barHeight = if (isLit) (radius * 0.08f) + (heartbeat * (radius * 0.04f) * (spProg + 0.2f)) else (radius * 0.03f)
                    val innerR = mainRadius
                    val outerR = mainRadius + barHeight

                    val startX = (center.x + innerR * cos(rad)).toFloat()
                    val startY = (center.y + innerR * sin(rad)).toFloat()
                    val endX = (center.x + outerR * cos(rad)).toFloat()
                    val endY = (center.y + outerR * sin(rad)).toFloat()
                    
                    drawLine(color = if (isLit) reactiveColor else inactiveColor, start = Offset(startX, startY), end = Offset(endX, endY), strokeWidth = if (isLit) radius * 0.02f else radius * 0.01f, cap = StrokeCap.Butt)
                }

                drawArc(color = inactiveColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = radius * 0.01f), size = arcSize, topLeft = arcTopLeft)
                if (spProg > 0) {
                    drawArc(color = reactiveColor, startAngle = startAngle, sweepAngle = sweepAngle * spProg, useCenter = false, style = Stroke(width = radius * 0.02f), size = arcSize, topLeft = arcTopLeft)
                    val dotRad = Math.toRadians((startAngle + sweepAngle * spProg).toDouble())
                    drawCircle(color = Color.White, radius = radius * 0.025f, center = Offset((center.x + (mainRadius + (radius*0.15f)) * cos(dotRad)).toFloat(), (center.y + (mainRadius + (radius*0.15f)) * sin(dotRad)).toFloat()))
                }

                drawIntoCanvas { canvas ->
                    for (i in 0..5) {
                        val angleRad = Math.toRadians((startAngle + (i * (sweepAngle / 5))).toDouble())
                        val valSpd = Math.round(i * (maxSpeed / 5))
                        val textX = (center.x + (mainRadius + (radius * 0.25f)) * cos(angleRad)).toFloat()
                        val textY = (center.y + (mainRadius + (radius * 0.25f)) * sin(angleRad)).toFloat()
                        val isLit = speed >= valSpd
                        val numColor = if (isLit) reactiveColor else (if (isLight) Color(0xFF94A3B8) else Color(0xFF475569))

                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb((numColor.alpha*255).toInt(), (numColor.red*255).toInt(), (numColor.green*255).toInt(), (numColor.blue*255).toInt())
                            textSize = radius * 0.11f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                        canvas.nativeCanvas.drawText(valSpd.toString(), textX, textY + (radius * 0.03f), paint) 
                    }
                }
            }

            "PLASMA" -> {
                val sweepAngle = 260f
                val startAngle = 140f
                val mainRadius = radius * 0.65f

                val coreRadius = (radius * 0.12f) + (spProg * (radius * 0.08f)) + (sin(cumTime * 15f) * (radius * 0.02f))
                val coreGrad = Brush.radialGradient(
                    colors = listOf(Color.White, reactiveColor, Color.Transparent),
                    center = center,
                    radius = coreRadius * 1.5f
                )
                drawCircle(brush = coreGrad, radius = coreRadius * 1.5f, center = center)

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
                        
                        drawLine(color = reactiveColor, start = Offset(currentX, currentY), end = Offset(nextX, nextY), strokeWidth = (radius * 0.01f) + (Math.random()*(radius * 0.01f)).toFloat())
                        currentX = nextX
                        currentY = nextY
                    }
                    drawCircle(color = Color.White, radius = (radius * 0.01f) + (Math.random()*(radius * 0.01f)).toFloat(), center = Offset(currentX, currentY))
                }

                drawCircle(color = reactiveColor.copy(alpha = 0.25f), radius = mainRadius, center = center, style = Stroke(width = (radius * 0.02f) + sin(cumTime * 10f) * (radius * 0.01f)))

                val trackRadius = mainRadius + (radius * 0.1f)
                val trackSize = Size(trackRadius*2, trackRadius*2)
                val trackTopLeft = Offset(center.x - trackRadius, center.y - trackRadius)

                drawArc(color = inactiveColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = radius * 0.02f, cap = StrokeCap.Round), size = trackSize, topLeft = trackTopLeft)
                
                if (spProg > 0) {
                    drawArc(color = reactiveColor, startAngle = startAngle, sweepAngle = sweepAngle * spProg, useCenter = false, style = Stroke(width = radius * 0.03f, cap = StrokeCap.Round), size = trackSize, topLeft = trackTopLeft)
                    val pRad = Math.toRadians((startAngle + sweepAngle * spProg).toDouble())
                    drawCircle(color = Color.White, radius = radius * 0.035f, center = Offset((center.x + trackRadius * cos(pRad)).toFloat(), (center.y + trackRadius * sin(pRad)).toFloat()))
                }

                drawIntoCanvas { canvas ->
                    for (i in 0..5) {
                        val angleRad = Math.toRadians((startAngle + (i * (sweepAngle / 5))).toDouble())
                        val valSpd = Math.round(i * (maxSpeed / 5))
                        val textX = (center.x + (trackRadius + (radius * 0.15f)) * cos(angleRad)).toFloat()
                        val textY = (center.y + (trackRadius + (radius * 0.15f)) * sin(angleRad)).toFloat()
                        val isLit = speed >= valSpd
                        val numColor = if (isLit) reactiveColor else (if (isLight) Color(0xFF94A3B8) else Color(0xFF475569))

                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb((numColor.alpha*255).toInt(), (numColor.red*255).toInt(), (numColor.green*255).toInt(), (numColor.blue*255).toInt())
                            textSize = radius * 0.12f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                        canvas.nativeCanvas.drawText(valSpd.toString(), textX, textY + (radius * 0.03f), paint) 
                    }
                }
            }

            "ANIME" -> {
                val sweepAngle = 240f
                val startAngle = 150f
                val mainRadius = radius * 0.70f
                val arcSize = Size(mainRadius*2, mainRadius*2)
                val arcTopLeft = Offset(center.x - mainRadius, center.y - mainRadius)

                if (spProg > 0.05f) {
                    val numLines = (50f + (spProg * 50f)).toInt()
                    for (i in 0 until numLines) {
                        val angle = (i * Math.PI * 2) / numLines + (cumWarp * 1.5f)
                        val noise = sin(cumTime * 50f + i * 11f) * 0.5f + 0.5f
                        val innerR = mainRadius * 0.4f + (noise * (radius * 0.1f))
                        val outerR = mainRadius * 0.85f + (spProg * (radius * 0.15f) * noise)
                        
                        drawLine(
                            color = reactiveColor.copy(alpha = (0.1f + (spProg * 0.4f)) * noise),
                            start = Offset((center.x + innerR * cos(angle)).toFloat(), (center.y + innerR * sin(angle)).toFloat()),
                            end = Offset((center.x + outerR * cos(angle)).toFloat(), (center.y + outerR * sin(angle)).toFloat()),
                            strokeWidth = (radius * 0.005f) + (noise * (radius * 0.01f))
                        )
                    }
                }

                drawArc(color = inactiveColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = radius * 0.02f, cap = StrokeCap.Butt), size = arcSize, topLeft = arcTopLeft)

                if (spProg > 0) {
                    drawArc(color = reactiveColor, startAngle = startAngle, sweepAngle = sweepAngle * spProg, useCenter = false, style = Stroke(width = radius * 0.04f, cap = StrokeCap.Butt), size = arcSize, topLeft = arcTopLeft)
                    
                    val ptrRad = Math.toRadians((startAngle + sweepAngle * spProg).toDouble())
                    val ptrX = (center.x + mainRadius * cos(ptrRad)).toFloat()
                    val ptrY = (center.y + mainRadius * sin(ptrRad)).toFloat()
                    
                    val katanaPath = Path().apply {
                        moveTo(0f, -(radius * 0.05f))
                        lineTo(radius * 0.04f, radius * 0.08f)
                        lineTo(0f, radius * 0.02f)
                        lineTo(-(radius * 0.04f), radius * 0.08f)
                        close()
                    }
                    
                    withTransform({
                        translate(left = ptrX, top = ptrY)
                        rotate(degrees = (Math.toDegrees(ptrRad) + 90).toFloat())
                    }) {
                        drawPath(path = katanaPath, color = Color.White)
                        drawLine(color = reactiveColor, start = Offset(0f, -(radius * 0.05f)), end = Offset(0f, radius * 0.02f), strokeWidth = radius * 0.01f)
                    }
                }

                val numSegs = 10
                for (i in 0..numSegs) {
                    val rad = Math.toRadians((startAngle + (i * (sweepAngle/numSegs))).toDouble())
                    val valSpd = (i.toFloat() / numSegs) * maxSpeed
                    val isLit = speed >= valSpd
                    drawLine(
                        color = if (isLit) Color.White else inactiveColor,
                        start = Offset((center.x + (mainRadius - (radius * 0.08f)) * cos(rad)).toFloat(), (center.y + (mainRadius - (radius * 0.08f)) * sin(rad)).toFloat()),
                        end = Offset((center.x + (mainRadius - (radius * 0.02f)) * cos(rad)).toFloat(), (center.y + (mainRadius - (radius * 0.02f)) * sin(rad)).toFloat()),
                        strokeWidth = radius * 0.03f
                    )
                }

                drawIntoCanvas { canvas ->
                    for (i in 0..5) {
                        val angleRad = Math.toRadians((startAngle + (i * (sweepAngle / 5))).toDouble())
                        val valSpd = Math.round(i * (maxSpeed / 5))
                        val textX = (center.x + (mainRadius + (radius * 0.15f)) * cos(angleRad)).toFloat()
                        val textY = (center.y + (mainRadius + (radius * 0.15f)) * sin(angleRad)).toFloat()
                        val isLit = speed >= valSpd
                        val numColor = if (isLit) reactiveColor else (if (isLight) Color(0xFF94A3B8) else Color(0xFF475569))

                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb((numColor.alpha*255).toInt(), (numColor.red*255).toInt(), (numColor.green*255).toInt(), (numColor.blue*255).toInt())
                            textSize = radius * 0.12f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC)
                        }
                        canvas.nativeCanvas.drawText(valSpd.toString(), textX, textY + (radius * 0.03f), paint) 
                    }
                }
            }

            "KAIJU" -> {
                val sweepAngle = 200f
                val startAngle = 170f
                val mainRadius = radius * 0.60f

                val coreNoise = sin(cumTime * 30f) * (radius * 0.03f)
                val atomicRad = (radius * 0.18f) + (spProg * (radius * 0.1f)) + coreNoise
                val coreGrad = Brush.radialGradient(
                    colors = listOf(Color.White, reactiveColor, Color.Transparent),
                    center = center,
                    radius = atomicRad
                )
                drawCircle(brush = coreGrad, radius = atomicRad, center = center)

                val numPlates = 15
                val plateStep = sweepAngle / numPlates
                
                for (i in 0..numPlates) {
                    val angle = startAngle + (i * plateStep)
                    val rad = Math.toRadians(angle.toDouble())
                    val valSpd = (i.toFloat() / numPlates) * maxSpeed
                    val isLit = speed >= valSpd

                    val midDist = 1f - abs((i.toFloat() / numPlates) - 0.5f) * 2f 
                    val basePlateHeight = (radius * 0.08f) + (midDist * (radius * 0.12f))
                    val activePlateHeight = basePlateHeight + (if (isLit) (Math.random() * (radius * 0.04f)).toFloat() else 0f)

                    val innerR = mainRadius
                    val outerR = mainRadius + activePlateHeight

                    val radL = Math.toRadians((angle - 3f).toDouble())
                    val radR = Math.toRadians((angle + 3f).toDouble())

                    val platePath = Path().apply {
                        moveTo((center.x + innerR * cos(radL)).toFloat(), (center.y + innerR * sin(radL)).toFloat())
                        lineTo((center.x + outerR * cos(rad)).toFloat(), (center.y + outerR * sin(rad)).toFloat())
                        lineTo((center.x + innerR * cos(radR)).toFloat(), (center.y + innerR * sin(radR)).toFloat())
                        close()
                    }

                    if (isLit) {
                        drawPath(path = platePath, color = reactiveColor)
                        drawPath(path = platePath, color = Color.White, style = Stroke(width = radius * 0.005f))
                    } else {
                        drawPath(path = platePath, color = inactiveColor)
                        drawPath(path = platePath, color = if (isLight) Color.Black.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f), style = Stroke(width = radius * 0.005f))
                    }
                }

                val trackRadius = mainRadius - (radius * 0.03f)
                val trackSize = Size(trackRadius*2, trackRadius*2)
                val trackTopLeft = Offset(center.x - trackRadius, center.y - trackRadius)

                drawArc(color = inactiveColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = radius * 0.03f, cap = StrokeCap.Round), size = trackSize, topLeft = trackTopLeft)
                if (spProg > 0) {
                    drawArc(color = reactiveColor, startAngle = startAngle, sweepAngle = sweepAngle * spProg, useCenter = false, style = Stroke(width = radius * 0.02f), size = trackSize, topLeft = trackTopLeft)
                }

                drawIntoCanvas { canvas ->
                    for (i in 0..5) {
                        val angleRad = Math.toRadians((startAngle + (i * (sweepAngle / 5))).toDouble())
                        val valSpd = Math.round(i * (maxSpeed / 5))
                        
                        val textRadPos = mainRadius + (radius * 0.15f) + ( (1f - abs((i.toFloat() / 5f) - 0.5f) * 2f) * (radius * 0.12f) )
                        val textX = (center.x + textRadPos * cos(angleRad)).toFloat()
                        val textY = (center.y + textRadPos * sin(angleRad)).toFloat()
                        
                        val isLit = speed >= valSpd
                        val numColor = if (isLit) reactiveColor else (if (isLight) Color(0xFF94A3B8) else Color(0xFF475569))

                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb((numColor.alpha*255).toInt(), (numColor.red*255).toInt(), (numColor.green*255).toInt(), (numColor.blue*255).toInt())
                            textSize = radius * 0.12f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.create("impact", android.graphics.Typeface.BOLD)
                        }
                        canvas.nativeCanvas.drawText(valSpd.toString(), textX, textY + (radius * 0.04f), paint) 
                    }
                }
            }
        }
    }
}