package com.tuusuario.carlauncher.ui.widgets

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import android.os.Build
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape

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
    // Shake: OVERDRIVE/NEBULA/DEMONIC tienen su propio shake agresivo; SHONEN/OMNIMON tienen el normal
    val shakeX: Float
    val shakeY: Float
    if (style in listOf("OVERDRIVE", "NEBULA", "DEMONIC")) {
        val isUnchainedHighSpeed = animatedSpeed >= 40f
        val spProg = (animatedSpeed / 220f).coerceIn(0f, 1f)
        val shakeMultUnchained = if (isUnchainedHighSpeed) (Math.pow(spProg.toDouble(), 2.0).toFloat() * 15f + 2f) else 0f
        shakeX = if (isUnchainedHighSpeed) (Math.random() * shakeMultUnchained * 2 - shakeMultUnchained).toFloat() else 0f
        shakeY = if (isUnchainedHighSpeed) (Math.random() * shakeMultUnchained * 2 - shakeMultUnchained).toFloat() else 0f
    } else {
        val shakeMult = if (isHighSpeed) ((animatedSpeed - 50f) * 0.08f + 3f) else 0f
        shakeX = if ((style == "SHONEN" || style == "OMNIMON") && isHighSpeed) (Math.random() * shakeMult * 2 - shakeMult).toFloat() else 0f
        shakeY = if ((style == "SHONEN" || style == "OMNIMON") && isHighSpeed) (Math.random() * shakeMult * 2 - shakeMult).toFloat() else 0f
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val boxSize = min(maxWidth, maxHeight) * 1.20f 
        
        val customBgPath = AppSettings.customSpeedoBgPath.value
        val isGif = customBgPath.endsWith(".gif", ignoreCase = true)

        if (style == "CUSTOM" && isGif && customBgPath.isNotEmpty()) {
            val customBgOpacity = AppSettings.customSpeedoBgOpacity.value
            val customShape = AppSettings.customSpeedoShape.value
            
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(java.io.File(customBgPath))
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(boxSize * 0.85f) 
                    .alpha(customBgOpacity)
                    .then(
                        if (customShape == "CIRCLE") Modifier.clip(CircleShape) else Modifier
                    ),
                contentScale = ContentScale.Crop
            )
        }

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
                "OVERDRIVE" -> {
                    val glitchOffset = if (speed > 150f) (Math.random() * 6 - 3).dp else 0.dp
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (-boxSize.value * 0.08f).dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            if (speed > 100f) {
                                Text(text = speed.toInt().toString(), color = Color.Red.copy(alpha=0.5f), fontSize = (boxSize.value * 0.38f).sp, fontWeight = FontWeight.Black, style = TextStyle(fontStyle = FontStyle.Italic), modifier = Modifier.offset(x = glitchOffset * 2))
                                Text(text = speed.toInt().toString(), color = Color.Cyan.copy(alpha=0.5f), fontSize = (boxSize.value * 0.38f).sp, fontWeight = FontWeight.Black, style = TextStyle(fontStyle = FontStyle.Italic), modifier = Modifier.offset(x = -glitchOffset * 2))
                            }
                            Text(text = speed.toInt().toString(), color = Color.White, fontSize = (boxSize.value * 0.38f).sp, fontWeight = FontWeight.Black, style = TextStyle(fontStyle = FontStyle.Italic, shadow = Shadow(color = reactiveColorText, blurRadius = 30f)))
                        }
                        Text(text = "KM/H", color = reactiveColorText, fontSize = (boxSize.value * 0.08f).sp, fontWeight = FontWeight.Black, letterSpacing = 8.sp, style = TextStyle(fontStyle = FontStyle.Italic))
                    }
                }
                "NEBULA" -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = speed.toInt().toString(), color = Color.White, fontSize = (boxSize.value * 0.32f).sp, fontWeight = FontWeight.Thin, style = TextStyle(fontFamily = FontFamily.Serif, shadow = Shadow(color = reactiveColorText, blurRadius = 40f)))
                        Text(text = "KM/H", color = Color.White.copy(alpha=0.8f), fontSize = (boxSize.value * 0.05f).sp, fontWeight = FontWeight.Black, letterSpacing = 15.sp)
                    }
                }
                "DEMONIC" -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (-boxSize.value * 0.06f).dp)) {
                        Text(text = speed.toInt().toString(), color = Color.White, fontSize = (boxSize.value * 0.40f).sp, fontWeight = FontWeight.Black, style = TextStyle(shadow = Shadow(color = Color.Black, offset = Offset(0f, 10f), blurRadius = 20f)))
                        Text(text = "P O W E R", color = Color.White, fontSize = (boxSize.value * 0.06f).sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp, style = TextStyle(shadow = Shadow(color = reactiveColorText, blurRadius = 10f)))
                    }
                }
                "CUSTOM" -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (-boxSize.value * 0.04f).dp)) {
                        val textShadow = if (!isLight) Shadow(color = reactiveColorText.copy(alpha = 0.5f), blurRadius = 25f) else null
                        Text(text = speed.toInt().toString(), color = textColor, fontSize = (boxSize.value * 0.35f).sp, fontWeight = FontWeight.Bold, style = TextStyle(shadow = textShadow))
                        Text(text = "KM/H", color = textColor.copy(alpha = 0.6f), fontSize = (boxSize.value * 0.08f).sp, fontWeight = FontWeight.Normal)
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
    
    val customShape = AppSettings.customSpeedoShape.value
    val customNeedle = AppSettings.customSpeedoNeedle.value
    val customThickness = AppSettings.customSpeedoThickness.value
    val customBgUri = AppSettings.customSpeedoBgUri.value
    val customBgPath = AppSettings.customSpeedoBgPath.value
    val customBgOpacity = AppSettings.customSpeedoBgOpacity.value
    
    val context = LocalContext.current

    var customBgBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(customBgUri, customBgPath) {
        withContext(Dispatchers.IO) {
            try {
                if (customBgPath.isNotEmpty()) {
                    val file = java.io.File(customBgPath)
                    if (file.exists()) {
                        customBgBitmap = BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                        return@withContext
                    }
                }
                
                if (customBgUri.isNotEmpty()) {
                    val uri = Uri.parse(customBgUri)
                    val stream = context.contentResolver.openInputStream(uri)
                    customBgBitmap = stream?.let { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                    stream?.close()
                } else {
                    customBgBitmap = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                customBgBitmap = null
            }
        }
    }

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
        val animatedStyles = listOf("AURA", "VORTEX", "QUANTUM", "PULSAR", "PLASMA", "ANIME", "KAIJU", "OMNIMON", "SHONEN", "MECHA", "CUSTOM", "OVERDRIVE", "NEBULA", "DEMONIC", "PREMIUM", "NEON", "RACING", "CYBER")
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

        // Pantalla ahumada para los estilos Unchained
        // Círculo difuminado en vez de rectángulo completo para respetar la transparencia global
        if (style in listOf("OVERDRIVE", "NEBULA", "DEMONIC")) {
            val bgGrad = Brush.radialGradient(
                colors = if (isLight) listOf(Color.White.copy(alpha = 0.6f), Color.Transparent) else listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent),
                center = center,
                radius = radius * 0.85f
            )
            drawCircle(brush = bgGrad, radius = radius, center = center)
        }
        
        when (style) {
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

            "CUSTOM" -> {
                val isGif = customBgPath.endsWith(".gif", ignoreCase = true)
                if (!isGif) {
                    customBgBitmap?.let { bitmap ->
                        val imgSize = radius * 2f
                        drawImage(
                            image = bitmap,
                            dstOffset = IntOffset((center.x - radius).toInt(), (center.y - radius).toInt()),
                            dstSize = IntSize((imgSize).toInt(), (imgSize).toInt()),
                            alpha = customBgOpacity
                        )
                    }
                }
                
                val sweepAngle = 240f
                val startAngle = 150f
                val trackRadius = radius * 0.85f
                val strokeWidth = radius * customThickness
                val arcSize = Size(trackRadius * 2, trackRadius * 2)
                val arcTopLeft = Offset(center.x - trackRadius, center.y - trackRadius)
                
                if (customShape == "CIRCLE") {
                    drawCircle(color = if (isLight) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f), radius = trackRadius, center = center)
                    drawCircle(color = inactiveColor, radius = trackRadius, center = center, style = Stroke(width = strokeWidth))
                    if (spProg > 0) drawArc(color = reactiveColor, startAngle = 90f, sweepAngle = 360f * spProg, useCenter = false, style = Stroke(width = strokeWidth, cap = StrokeCap.Round), size = arcSize, topLeft = arcTopLeft)
                } else if (customShape == "LINE") {
                    val lineY = center.y + radius * 0.5f
                    drawLine(color = inactiveColor, start = Offset(center.x - radius * 0.8f, lineY), end = Offset(center.x + radius * 0.8f, lineY), strokeWidth = strokeWidth, cap = StrokeCap.Round)
                    if (spProg > 0) drawLine(color = reactiveColor, start = Offset(center.x - radius * 0.8f, lineY), end = Offset(center.x - radius * 0.8f + (radius * 1.6f * spProg), lineY), strokeWidth = strokeWidth, cap = StrokeCap.Round)
                } else { 
                    drawArc(color = inactiveColor, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = strokeWidth, cap = StrokeCap.Round), size = arcSize, topLeft = arcTopLeft)
                    if (spProg > 0) drawArc(color = reactiveColor, startAngle = startAngle, sweepAngle = sweepAngle * spProg, useCenter = false, style = Stroke(width = strokeWidth, cap = StrokeCap.Round), size = arcSize, topLeft = arcTopLeft)
                }

                val ptrRad = if (customShape == "CIRCLE") Math.toRadians((90f + 360f * spProg).toDouble()) else if (customShape == "LINE") 0.0 else Math.toRadians((startAngle + sweepAngle * spProg).toDouble())
                
                if (customShape != "LINE") {
                    if (customNeedle == "PLASMA") {
                        val fireSpeed = 15f + (speed * 0.8f)
                        val flicker1 = sin(cumTime * fireSpeed) * radius * 0.035f
                        val offsetBase = flicker1
                        withTransform({
                            translate(center.x, center.y)
                            rotate(degrees = Math.toDegrees(ptrRad).toFloat() + 90f, pivot = Offset.Zero)
                        }) {
                            val w = radius * 0.05f
                            val h = trackRadius * 1.05f
                            val path = Path().apply {
                                moveTo(-w, 0f)
                                quadraticBezierTo(-w + offsetBase, -h * 0.5f, offsetBase, -h)
                                quadraticBezierTo(w + offsetBase, -h * 0.5f, w, 0f)
                                close()
                            }
                            drawPath(path = path, color = reactiveColor)
                        }
                    } else if (customNeedle == "KATANA") {
                        withTransform({
                            translate(center.x, center.y)
                            rotate(degrees = Math.toDegrees(ptrRad).toFloat() + 90f, pivot = Offset.Zero)
                        }) {
                            val katanaPath = Path().apply {
                                moveTo(0f, -(radius * 0.12f))
                                lineTo(-(radius * 0.015f), -(trackRadius * 0.95f))
                                quadraticBezierTo(0f, -(trackRadius * 1.05f), radius * 0.02f, -(trackRadius * 0.95f))
                                lineTo(radius * 0.025f, -(radius * 0.12f))
                                close()
                            }
                            drawPath(path = katanaPath, color = reactiveColor)
                        }
                    } else if (customNeedle == "NEON") {
                        withTransform({
                            translate(center.x, center.y)
                            rotate(degrees = Math.toDegrees(ptrRad).toFloat() + 90f, pivot = Offset.Zero)
                        }) {
                            val nLen = trackRadius * 0.95f
                            drawLine(color = reactiveColor.copy(alpha = 0.15f), start = Offset(0f, 0f), end = Offset(0f, -nLen), strokeWidth = radius * 0.10f, cap = StrokeCap.Round)
                            drawLine(color = reactiveColor.copy(alpha = 0.3f), start = Offset(0f, 0f), end = Offset(0f, -nLen), strokeWidth = radius * 0.06f, cap = StrokeCap.Round)
                            drawLine(color = reactiveColor, start = Offset(0f, 0f), end = Offset(0f, -nLen), strokeWidth = radius * 0.025f, cap = StrokeCap.Round)
                            drawLine(color = Color.White, start = Offset(0f, -nLen * 0.1f), end = Offset(0f, -nLen), strokeWidth = radius * 0.012f, cap = StrokeCap.Round)
                            drawCircle(color = Color.White, radius = radius * 0.035f, center = Offset(0f, -nLen))
                            drawCircle(color = reactiveColor.copy(alpha = 0.4f), radius = radius * 0.07f, center = Offset(0f, -nLen))
                        }
                    } else if (customNeedle == "LASER") {
                        withTransform({
                            translate(center.x, center.y)
                            rotate(degrees = Math.toDegrees(ptrRad).toFloat() + 90f, pivot = Offset.Zero)
                        }) {
                            val laserLen = trackRadius * 1.0f
                            drawLine(color = reactiveColor, start = Offset(0f, -(radius * 0.05f)), end = Offset(0f, -laserLen), strokeWidth = radius * 0.008f, cap = StrokeCap.Round)
                            drawLine(color = reactiveColor.copy(alpha = 0.25f), start = Offset(0f, -(radius * 0.05f)), end = Offset(0f, -laserLen), strokeWidth = radius * 0.03f, cap = StrokeCap.Round)
                            val numParticles = 8
                            for (p in 0 until numParticles) {
                                val pY = -(radius * 0.1f) - (laserLen * 0.85f * p / numParticles)
                                val pX = sin(cumTime * 20f + p * 3f) * radius * 0.02f
                                val pSize = (radius * 0.008f) + (sin(cumTime * 15f + p * 5f) * radius * 0.005f)
                                drawCircle(color = Color.White.copy(alpha = 0.7f), radius = pSize, center = Offset(pX, pY))
                            }
                            drawCircle(color = reactiveColor, radius = radius * 0.025f, center = Offset(0f, -(radius * 0.05f)))
                            val termPulse = 0.5f + sin(cumTime * 12f) * 0.5f
                            drawCircle(color = Color.White.copy(alpha = termPulse), radius = radius * 0.02f, center = Offset(0f, -laserLen))
                        }
                    } else if (customNeedle == "BOLT") {
                        withTransform({
                            translate(center.x, center.y)
                            rotate(degrees = Math.toDegrees(ptrRad).toFloat() + 90f, pivot = Offset.Zero)
                        }) {
                            val boltLen = trackRadius * 0.95f
                            val numSegments = 7
                            val segLen = boltLen / numSegments
                            val boltPath = Path().apply {
                                moveTo(0f, 0f)
                                for (s in 1..numSegments) {
                                    val zigX = if (s < numSegments) {
                                        sin(cumTime * 25f + s * 7f) * radius * 0.04f * (1f + speed * 0.005f)
                                    } else 0f
                                    lineTo(zigX, -(segLen * s))
                                }
                            }
                            drawPath(path = boltPath, color = reactiveColor.copy(alpha = 0.2f), style = Stroke(width = radius * 0.06f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            drawPath(path = boltPath, color = reactiveColor, style = Stroke(width = radius * 0.02f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            drawPath(path = boltPath, color = Color.White, style = Stroke(width = radius * 0.008f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            val sparkPulse = abs(sin(cumTime * 20f))
                            drawCircle(color = Color.White, radius = radius * 0.02f * (0.5f + sparkPulse * 0.5f), center = Offset(0f, -boltLen))
                            drawCircle(color = reactiveColor.copy(alpha = 0.4f * sparkPulse), radius = radius * 0.06f, center = Offset(0f, -boltLen))
                        }
                    } else { 
                        val ptrX = (center.x + trackRadius * cos(ptrRad)).toFloat()
                        val ptrY = (center.y + trackRadius * sin(ptrRad)).toFloat()
                        drawCircle(color = reactiveColor, radius = radius * 0.05f, center = Offset(ptrX, ptrY))
                        drawLine(color = reactiveColor, start = center, end = Offset(ptrX, ptrY), strokeWidth = radius * 0.02f, cap = StrokeCap.Round)
                    }
                } else {
                    val ptrX = center.x - radius * 0.8f + (radius * 1.6f * spProg)
                    val lineY = center.y + radius * 0.5f
                    drawCircle(color = reactiveColor, radius = radius * 0.05f, center = Offset(ptrX, lineY))
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

            "OVERDRIVE" -> {
                val numBars = 35
                val barTotalWidth = radius * 1.8f
                val barSpacing = barTotalWidth / numBars
                val startX = center.x - barTotalWidth / 2f
                val baseLineY = center.y + radius * 0.4f
                
                val isGlitching = spProg > 0.5f
                val glitchX = if (isGlitching) floatArrayOf(-4f * spProg, 4f * spProg, 0f) else floatArrayOf(0f)
                val glitchColors = if (isGlitching) arrayOf(Color.Red.copy(alpha=0.7f), Color.Cyan.copy(alpha=0.7f), reactiveColor) else arrayOf(reactiveColor)

                for (g in glitchX.indices) {
                    val gX = glitchX[g]
                    val gCol = glitchColors[g]

                    for (i in 0 until numBars) {
                        val barProg = i.toFloat() / numBars
                        val isLit = spProg >= barProg
                        val bX = startX + i * barSpacing + gX
                        val bWidth = barSpacing * 0.7f
                        
                        val noiseH = if (isLit) abs(sin(i * 12.34f + cumTime * 25f)) * (radius * 0.8f) * spProg else 0f
                        val bHeight = radius * 0.1f + noiseH + (if (isLit) radius*0.1f else 0f)

                        // Glow nativo 100% Compose sin usar ShadowLayer
                        if (isLit && gX == 0f) {
                            val glowRadius = bHeight.coerceAtLeast(1f)
                            val glowBrush = Brush.radialGradient(
                                colors = listOf(gCol.copy(alpha = 0.6f), Color.Transparent),
                                center = Offset(bX + bWidth / 2f, baseLineY - bHeight / 2f),
                                radius = glowRadius
                            )
                            drawRect(brush = glowBrush, topLeft = Offset(bX - bWidth, baseLineY - bHeight*1.5f), size = Size(bWidth*3f, bHeight*2f))
                        }

                        val rectColor = if (isLit) gCol else (if (isLight) Color.Black.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.1f))
                        if (isLit || gX == 0f) {
                            drawRect(color = rectColor, topLeft = Offset(bX, baseLineY - bHeight), size = Size(bWidth, bHeight))
                        }
                    }
                }
                
                if (spProg > 0) {
                    val activeIndex = (spProg * numBars).toInt().coerceIn(0, numBars - 1)
                    val activeX = startX + activeIndex * barSpacing
                    
                    for(p in 0 until (15 * spProg).toInt()) {
                        val pX = activeX + (Math.random().toFloat() - 0.5f) * 50f
                        val pY = baseLineY - (Math.random().toFloat() * radius * 1.2f * spProg) - 20f
                        val pSize = (Math.random() * 4 + 1).toFloat()
                        
                        val pColor = if (Math.random() > 0.5) Color.White else reactiveColor
                        drawRect(color = pColor, topLeft = Offset(pX, pY), size = Size(pSize, pSize))
                    }
                }
            }
            
            "NEBULA" -> {
                // Black hole con brillo nativo
                val holeRadius = radius * 0.4f
                val glowRadius = holeRadius + 30f + (sin(cumTime * 10f) * 10f * spProg)
                if (glowRadius > holeRadius) {
                    val holeGlow = Brush.radialGradient(
                        colors = listOf(reactiveColor, reactiveColor.copy(alpha=0.4f), Color.Transparent),
                        center = center,
                        radius = glowRadius
                    )
                    drawCircle(brush = holeGlow, radius = glowRadius, center = center)
                }
                drawCircle(color = Color.Black, radius = holeRadius, center = center)
                
                val numRings = 8 + (spProg * 10).toInt()
                for(i in 0 until numRings) {
                    val dir = if (i % 2 == 0) 1 else -1
                    val rotSpeed = cumTime * (2f + spProg * 8f) * dir + (i * 45f)
                    val stretch = 1f + (spProg * 1.5f)
                    
                    withTransform({
                        translate(center.x, center.y)
                        rotate(Math.toDegrees(rotSpeed.toDouble()).toFloat())
                    }) {
                        val w = radius * 0.9f * stretch
                        val h = radius * 1.2f + (i * 10f)
                        val eColor = if (i % 3 == 0) Color.White else reactiveColor
                        drawOval(
                            color = eColor,
                            topLeft = Offset(-w/2, -h/2),
                            size = Size(w, h),
                            style = Stroke(width = radius * 0.005f + (Math.random().toFloat() * radius * 0.015f * spProg)),
                            alpha = 0.2f + (Math.random().toFloat() * 0.5f)
                        )
                    }
                }

                val sweepAngle = 270f
                val startAngle = 135f
                
                drawArc(
                    color = if (isLight) Color.Black.copy(alpha=0.3f) else Color.White.copy(alpha=0.1f),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = radius * 0.02f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(radius * 0.05f, radius * 0.05f))),
                    size = Size(radius * 1.6f, radius * 1.6f),
                    topLeft = Offset(center.x - radius * 0.8f, center.y - radius * 0.8f)
                )

                if (spProg > 0) {
                    val wobble = ((Math.random() - 0.5f) * radius * 0.05f * spProg).toFloat()
                    val dRad = radius * 0.8f + wobble
                    drawArc(
                        color = reactiveColor,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle * spProg,
                        useCenter = false,
                        style = Stroke(width = radius * 0.08f * (1f + Math.random().toFloat()*spProg*0.5f), cap = StrokeCap.Round),
                        size = Size(dRad * 2, dRad * 2),
                        topLeft = Offset(center.x - dRad, center.y - dRad)
                    )

                    for(p in 0 until 20) {
                        val pAng = startAngle + Math.random() * sweepAngle * spProg
                        val pDist = radius * 0.8f - (Math.random().toFloat() * radius * 0.4f * spProg * (cumTime*10%2))
                        val pRad = Math.toRadians(pAng)
                        drawCircle(color = Color.White, radius = (Math.random()*4+1).toFloat(), center = Offset((center.x + pDist * cos(pRad)).toFloat(), (center.y + pDist * sin(pRad)).toFloat()))
                    }
                }
            }
            
            "DEMONIC" -> {
                val sweepAngle = 220f
                val startAngle = 160f
                val mainRadius = radius * 0.75f
                
                val eyeOpen = radius * 0.1f + (spProg * radius * 0.2f)
                val breath = sin(cumTime * 4f * (1f + spProg*2f)) * radius * 0.02f
                
                val scleraW = radius * 0.3f + breath
                val scleraH = eyeOpen + breath
                
                // Glow 100% nativo de Compose 
                val eyeGlowRadius = scleraW + (20f * spProg)
                if (eyeGlowRadius > 0) {
                    val eyeGlow = Brush.radialGradient(
                        colors = listOf(reactiveColor.copy(alpha=0.6f), Color.Transparent),
                        center = center,
                        radius = eyeGlowRadius
                    )
                    drawOval(brush = eyeGlow, topLeft = Offset(center.x - eyeGlowRadius, center.y - eyeGlowRadius), size = Size(eyeGlowRadius*2, eyeGlowRadius*2))
                }
                
                drawOval(
                    color = if (isLight) Color.Black else Color(0xFF1A0000),
                    topLeft = Offset(center.x - scleraW, center.y - scleraH),
                    size = Size(scleraW * 2, scleraH * 2)
                )

                drawOval(
                    color = reactiveColor,
                    topLeft = Offset(center.x - radius * 0.05f, center.y - eyeOpen * 0.8f),
                    size = Size(radius * 0.1f, eyeOpen * 1.6f)
                )

                val numVertebras = 22
                for (i in 0..numVertebras) {
                    val vProg = i.toFloat() / numVertebras
                    val rad = Math.toRadians((startAngle + vProg * sweepAngle).toDouble()).toFloat()
                    val isLit = spProg >= vProg
                    
                    val spineDist = mainRadius * 0.85f
                    val ribLength = if (isLit) radius * 0.2f + (Math.random().toFloat() * radius * 0.05f * spProg) else radius * 0.1f
                        
                    val ribPath = Path().apply {
                        moveTo((center.x + (spineDist - radius*0.05f) * cos(rad)), (center.y + (spineDist - radius*0.05f) * sin(rad)))
                        lineTo((center.x + (spineDist + ribLength) * cos(rad - 0.05f)), (center.y + (spineDist + ribLength) * sin(rad - 0.05f)))
                        lineTo((center.x + (spineDist + radius*0.05f) * cos(rad + 0.05f)), (center.y + (spineDist + radius*0.05f) * sin(rad + 0.05f)))
                        close()
                    }
                    
                    if (isLit) {
                        drawPath(path = ribPath, color = reactiveColor)
                        
                        val nerveCenter = Offset(center.x + spineDist * cos(rad), center.y + spineDist * sin(rad))
                        val nerveGlow = Brush.radialGradient(
                            colors = listOf(reactiveColor.copy(alpha=0.8f), Color.Transparent),
                            center = nerveCenter,
                            radius = radius * 0.1f
                        )
                        drawCircle(brush = nerveGlow, radius = radius * 0.1f, center = nerveCenter)
                        drawCircle(color = Color.White, radius = radius * 0.04f, center = nerveCenter)
                    } else {
                        drawPath(path = ribPath, color = if (isLight) Color(0xFF64748B) else Color(0xFF334155))
                    }
                }

                drawArc(
                    color = if (isLight) Color(0xFF475569) else Color(0xFF1E293B),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = radius * 0.02f),
                    size = Size(mainRadius * 1.7f, mainRadius * 1.7f),
                    topLeft = Offset(center.x - mainRadius * 0.85f, center.y - mainRadius * 0.85f)
                )

                if (spProg > 0) {
                    drawArc(
                        color = reactiveColor,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle * spProg,
                        useCenter = false,
                        style = Stroke(width = radius * 0.04f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(radius * 0.08f, radius * 0.04f), phase = -cumTime * 100f * (1f + spProg*2f))),
                        size = Size(mainRadius * 1.7f, mainRadius * 1.7f),
                        topLeft = Offset(center.x - mainRadius * 0.85f, center.y - mainRadius * 0.85f)
                    )
                }
            }

            // Fallback para estilos base si no entraron en las condiciones de arriba
            else -> {
               // Ya está manejado en los demás
            }
        }
    }
}