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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*

@Composable
fun SpeedometerWidget() {
    val currentSpeedKmH = rememberGpsSpeed()
    val animatedSpeed by animateFloatAsState(
        targetValue = currentSpeedKmH,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "SpeedAnimation"
    )

    // Colores dinámicos según el tema
    val textColor = MaterialTheme.colorScheme.onSurface
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        SpeedometerDial(speed = animatedSpeed, maxSpeed = 220f, trackColor = trackColor, textColor = textColor)

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = currentSpeedKmH.toInt().toString(),
                color = textColor, // Dinámico
                fontSize = 64.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text("KM/H", color = textColor.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SpeedometerDial(speed: Float, maxSpeed: Float, trackColor: Color, textColor: Color) {
    Canvas(modifier = Modifier.size(180.dp)) {
        val sweepAngle = 240f
        val startAngle = 150f

        drawArc(
            color = trackColor, // Dinámico
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round),
            size = Size(size.width, size.height)
        )

        val speedProgress = (speed / maxSpeed).coerceIn(0f, 1f)
        val activeSweepAngle = sweepAngle * speedProgress

        drawArc(
            color = Color(0xFFE53935),
            startAngle = startAngle,
            sweepAngle = activeSweepAngle,
            useCenter = false,
            style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round),
            size = Size(size.width, size.height)
        )

        val needleAngle = startAngle + activeSweepAngle
        rotate(degrees = needleAngle + 90f) {
            drawLine(
                color = textColor, // Aguja se adapta a la luz
                start = Offset(center.x, center.y),
                end = Offset(center.x, 20.dp.toPx()),
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round
            )
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
                        speed = location.speed * 3.6f // metros/segundo a KM/H
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