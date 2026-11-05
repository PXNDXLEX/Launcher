package com.tuusuario.carlauncher.ui.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class CropShape { CIRCLE, SQUARE, WIDE }

/**
 * Diálogo de recorte de imagen con vista previa, zoom, arrastre, y recorte circular/cuadrado.
 * @param imageUri URI de la imagen seleccionada.
 * @param outputFileName Nombre del archivo de salida (ej: "custom_speedo_bg.png", "custom_vehicle.png").
 * @param onCropped Callback con la ruta del archivo guardado.
 * @param onDismiss Callback al cancelar.
 */
@Composable
fun ImageCropperDialog(
    imageUri: Uri,
    outputFileName: String,
    initialCropShape: CropShape = CropShape.CIRCLE,
    onCropped: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var cropShape by remember { mutableStateOf(initialCropShape) }
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    
    var sourceBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var sourceAndroidBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var isGif by remember { mutableStateOf(false) }

    // Cargar la imagen al abrir
    LaunchedEffect(imageUri) {
        withContext(Dispatchers.IO) {
            try {
                val mimeType = context.contentResolver.getType(imageUri)
                isGif = mimeType == "image/gif"
                
                val stream = context.contentResolver.openInputStream(imageUri)
                val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                val bmp = BitmapFactory.decodeStream(stream, null, options)
                stream?.close()
                if (bmp != null) {
                    sourceAndroidBitmap = bmp
                    sourceBitmap = bmp.asImageBitmap()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Recortar Imagen", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar", tint = Color.White.copy(alpha = 0.7f)) }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val controlsContent = @Composable {
                    if (isGif) {
                        Text("Nota: Los GIFs se usarán completos para mantener la animación.", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(selected = cropShape == CropShape.WIDE, onClick = { cropShape = CropShape.WIDE }, label = { Text("Auto 16:10", fontSize = 11.sp) }, leadingIcon = { Icon(Icons.Default.Crop169, null, modifier = Modifier.size(16.dp)) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary))
                        FilterChip(selected = cropShape == CropShape.CIRCLE, onClick = { cropShape = CropShape.CIRCLE }, label = { Text("Círculo", fontSize = 11.sp) }, leadingIcon = { Icon(Icons.Default.Circle, null, modifier = Modifier.size(16.dp)) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary))
                        FilterChip(selected = cropShape == CropShape.SQUARE, onClick = { cropShape = CropShape.SQUARE }, label = { Text("Cuadrado", fontSize = 11.sp) }, leadingIcon = { Icon(Icons.Default.CropSquare, null, modifier = Modifier.size(16.dp)) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary))
                    }

                    if (initialCropShape == CropShape.WIDE && sourceAndroidBitmap != null && !isGif) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                if (!isProcessing) {
                                    isProcessing = true
                                    val saved = saveOriginalPng(context, sourceAndroidBitmap!!, outputFileName)
                                    isProcessing = false
                                    if (saved != null) onCropped(saved)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF67E8F9))
                        ) {
                            Icon(Icons.Default.AutoFixHigh, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Usar imagen completa sin recortar", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.ZoomOut, null, tint = Color.White.copy(alpha = 0.6f))
                        Slider(value = scale, onValueChange = { scale = it }, valueRange = 0.5f..5f, modifier = Modifier.weight(1f).padding(horizontal = 8.dp), colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary))
                        Icon(Icons.Default.ZoomIn, null, tint = Color.White.copy(alpha = 0.6f))
                    }

                    TextButton(onClick = { scale = 1f; offsetX = 0f; offsetY = 0f }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Icon(Icons.Default.RestartAlt, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Resetear posición", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancelar") }
                        Button(
                            onClick = {
                                if ((sourceAndroidBitmap != null || isGif) && !isProcessing) {
                                    isProcessing = true
                                    if (isGif) {
                                        val result = saveOriginalGif(context, imageUri, outputFileName.replace(".png", ".gif"))
                                        if (result != null) onCropped(result)
                                        isProcessing = false
                                    } else {
                                        val result = cropAndSave(context, sourceAndroidBitmap!!, scale, offsetX, offsetY, cropShape, outputFileName)
                                        if (result != null) onCropped(result)
                                        isProcessing = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = (sourceBitmap != null || isGif) && !isProcessing
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Check, null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Confirmar")
                            }
                        }
                    }
                }

                val canvasContent = @Composable {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D1A), RoundedCornerShape(16.dp))
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(0.5f, 5f)
                                    offsetX += pan.x
                                    offsetY += pan.y
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (sourceBitmap != null) {
                            Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                val canvasSize = size
                                val cropW = if (cropShape == CropShape.WIDE) {
                                    minOf(canvasSize.width * 0.88f, canvasSize.height * 0.84f * 1.6f)
                                } else {
                                    minOf(canvasSize.width, canvasSize.height) * 0.8f
                                }
                                val cropH = if (cropShape == CropShape.WIDE) cropW / 1.6f else cropW
                                val cropCenter = Offset(canvasSize.width / 2f, canvasSize.height / 2f)

                                val img = sourceBitmap!!
                                val imgW = img.width.toFloat()
                                val imgH = img.height.toFloat()

                                val baseScale = if (cropShape == CropShape.WIDE) {
                                    minOf(cropW / imgW, cropH / imgH)
                                } else {
                                    cropW / minOf(imgW, imgH)
                                }
                                val finalScale = baseScale * scale
                                val drawW = imgW * finalScale
                                val drawH = imgH * finalScale

                                val imgLeft = cropCenter.x - drawW / 2f + offsetX
                                val imgTop = cropCenter.y - drawH / 2f + offsetY

                                drawImage(img, dstOffset = IntOffset(imgLeft.toInt(), imgTop.toInt()), dstSize = IntSize(drawW.toInt(), drawH.toInt()))

                                val cropRect = Rect(cropCenter.x - cropW / 2f, cropCenter.y - cropH / 2f, cropCenter.x + cropW / 2f, cropCenter.y + cropH / 2f)
                                val cropPath = Path().apply {
                                    if (cropShape == CropShape.CIRCLE) {
                                        addOval(cropRect)
                                    } else {
                                        addRect(cropRect)
                                    }
                                }

                                clipPath(cropPath, clipOp = ClipOp.Difference) { drawRect(Color.Black.copy(alpha = 0.7f)) }

                                if (cropShape == CropShape.CIRCLE) {
                                    drawCircle(color = Color.White.copy(alpha = 0.8f), radius = cropW / 2f, center = cropCenter, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                                } else {
                                    drawRect(color = Color.White.copy(alpha = 0.8f), topLeft = Offset(cropRect.left, cropRect.top), size = Size(cropW, cropH), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                                }
                            }
                        } else {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                }

                if (isLandscape) {
                    Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) { canvasContent() }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.width(260.dp), verticalArrangement = Arrangement.Center) { controlsContent() }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) { canvasContent() }
                        Spacer(modifier = Modifier.height(16.dp))
                        controlsContent()
                    }
                }
            }
        }
    }
}

/**
 * Recorta la imagen según los parámetros y la guarda en filesDir.
 */
private fun cropAndSave(
    context: Context,
    sourceBitmap: Bitmap,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    cropShape: CropShape,
    outputFileName: String
): String? {
    try {
        val outW = if (cropShape == CropShape.WIDE) 960 else 512
        val outH = if (cropShape == CropShape.WIDE) 600 else 512
        val outputBitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(outputBitmap)

        // Si es círculo, recortar con path circular
        if (cropShape == CropShape.CIRCLE) {
            val path = android.graphics.Path().apply {
                addCircle(
                    outW / 2f, outH / 2f, outW / 2f,
                    android.graphics.Path.Direction.CCW
                )
            }
            canvas.clipPath(path)
        }

        // Calcular posición de la imagen en el output
        val imgW = sourceBitmap.width.toFloat()
        val imgH = sourceBitmap.height.toFloat()
        val baseScale = if (cropShape == CropShape.WIDE) {
            minOf(outW.toFloat() / imgW, outH.toFloat() / imgH)
        } else {
            outW.toFloat() / minOf(imgW, imgH)
        }
        val finalScale = baseScale * scale
        val drawW = imgW * finalScale
        val drawH = imgH * finalScale

        val imgLeft = (outW / 2f) - (drawW / 2f) + (offsetX * outW / 512f)
        val imgTop = (outH / 2f) - (drawH / 2f) + (offsetY * outH / 512f)

        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(
            sourceBitmap,
            null,
            android.graphics.RectF(imgLeft, imgTop, imgLeft + drawW, imgTop + drawH),
            paint
        )

        // Guardar
        val file = File(context.filesDir, outputFileName)
        val fos = FileOutputStream(file)
        outputBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        fos.flush()
        fos.close()
        outputBitmap.recycle()

        return file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

private fun saveOriginalPng(
    context: Context,
    sourceBitmap: Bitmap,
    outputFileName: String
): String? {
    return try {
        val maxDim = 1280
        val bmp = if (sourceBitmap.width > maxDim || sourceBitmap.height > maxDim) {
            val ratio = minOf(maxDim.toFloat() / sourceBitmap.width, maxDim.toFloat() / sourceBitmap.height)
            Bitmap.createScaledBitmap(
                sourceBitmap,
                (sourceBitmap.width * ratio).toInt().coerceAtLeast(1),
                (sourceBitmap.height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else sourceBitmap

        val file = File(context.filesDir, outputFileName)
        FileOutputStream(file).use { fos ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
        }
        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * Guarda el archivo GIF original en filesDir.
 */
private fun saveOriginalGif(context: Context, uri: Uri, outputFileName: String): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val file = File(context.filesDir, outputFileName)
        val outputStream = FileOutputStream(file)
        inputStream?.copyTo(outputStream)
        inputStream?.close()
        outputStream.close()
        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
