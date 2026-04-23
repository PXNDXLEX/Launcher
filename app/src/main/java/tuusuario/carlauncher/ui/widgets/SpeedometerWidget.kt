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
        val animatedStyles = listOf("AURA", "VORTEX", "QUANTUM", "PULSAR", "PLASMA", "ANIME", "KAIJU", "OMNIMON", "SHONEN", "MECHA", "CUSTOM", "OVERDRIVE", "NEBULA", "DEMONIC")
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
        if (style in listOf("OVERDRIVE", "NEBULA", "DEMONIC")) {
            val bgGrad = Brush.radialGradient(
                colors = if (isLight) listOf(Color.White.copy(alpha = 0.2f), Color.Black.copy(alpha = 0.6f)) else listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                center = center,
                radius = radius
            )
            drawRect(brush = bgGrad, size = Size(size.width, size.height))
        }
        
        when (style) {
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

                        // Glow 100% nativo de Compose sin usar ShadowLayer
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
                    
                    val ptrX = center.x + mainRadius * cos(ptrRad).toFloat()
                    val ptrY = center.y + mainRadius * sin(ptrRad).toFloat()
                    val swordGlow = Brush.radialGradient(listOf(Color(0xFFF97316), Color.Transparent), Offset(ptrX, ptrY), radius * 0.1f)
                    drawCircle(brush = swordGlow, radius = radius * 0.1f, center = Offset(ptrX, ptrY))

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
                                val swordGlow = Brush.radialGradient(
                                    colors = listOf(energyColor.copy(alpha=0.6f), Color.Transparent),
                                    center = Offset(0f, -(radius * 0.04f)),
                                    radius = radius * 0.2f
                                )
                                drawCircle(brush = swordGlow, radius = radius * 0.2f, center = Offset(0f, -(radius * 0.04f)))
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
                                this.style = android.graphics.Paint.Style.STROKE
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

            // Fallback para estilos base si no entraron en las condiciones de arriba
            else -> {
               // Ya está manejado en los demás
            }
        }
    }
}