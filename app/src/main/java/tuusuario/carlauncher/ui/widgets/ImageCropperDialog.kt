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

enum class CropShape { CIRCLE, SQUARE }

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

                var gifDuration by remember { mutableStateOf(3) }
                val controlsContent = @Composable {
                    if (isGif) {
                        Text("Duración del GIF (Segundos): $gifDuration s", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
                        Slider(
                            value = gifDuration.toFloat(),
                            onValueChange = { gifDuration = it.toInt() },
                            valueRange = 1f..10f,
                            steps = 9,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(selected = cropShape == CropShape.CIRCLE, onClick = { cropShape = CropShape.CIRCLE }, label = { Text("Círculo") }, leadingIcon = { Icon(Icons.Default.Circle, null, modifier = Modifier.size(16.dp)) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary))
                        Spacer(modifier = Modifier.width(12.dp))
                        FilterChip(selected = cropShape == CropShape.SQUARE, onClick = { cropShape = CropShape.SQUARE }, label = { Text("Cuadrado") }, leadingIcon = { Icon(Icons.Default.CropSquare, null, modifier = Modifier.size(16.dp)) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary))
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
                                    val outPath = File(context.filesDir, outputFileName.replace(".png", ".gif")).absolutePath
                                    if (isGif) {
                                        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                                            try {
                                                // Get original gif path
                                                val cacheFile = File(context.cacheDir, "temp_input.gif")
                                                val ins = context.contentResolver.openInputStream(imageUri)
                                                if (ins != null) {
                                                    val os = FileOutputStream(cacheFile)
                                                    ins.copyTo(os)
                                                    ins.close()
                                                    os.close()
                                                    
                                                    // Usar FFmpeg para recortar y ajustar la duración
                                                    // Calculamos el crop equivalente. scale, offsetX, offsetY
                                                    // Al ser complejo mapear la coordenada UI exacta al video, usaremos un scale basico
                                                    // Para hacerlo circular, FFmpeg tiene geq filter.
                                                    
                                                    val wStr = "iw"
                                                    val hStr = "ih"
                                                    // Comando para recortar cuadrado central y hacerlo circular si aplica, y cortar duracion
                                                    val shapeFilter = if (cropShape == CropShape.CIRCLE) {
                                                        ",geq=r='r(X,Y)':a='if(gt(hypot(X-W/2,Y-H/2),min(W,H)/2),0,alpha(X,Y))'"
                                                    } else ""
                                                    
                                                    val cmd = "-y -t $gifDuration -i ${cacheFile.absolutePath} -vf \"crop=min(iw\\,ih):min(iw\\,ih)$shapeFilter,scale=256:256\" $outPath"
                                                    
                                                    com.arthenica.ffmpegkit.FFmpegKit.execute(cmd)
                                                }
                                                
                                                withContext(Dispatchers.Main) {
                                                    onCropped(outPath)
                                                    isProcessing = false
                                                }
                                            } catch(e: Exception) {
                                                withContext(Dispatchers.Main) { isProcessing = false }
                                            }
                                        }
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
                                val cropSize = minOf(canvasSize.width, canvasSize.height) * 0.8f
                                val cropCenter = Offset(canvasSize.width / 2f, canvasSize.height / 2f)

                                val img = sourceBitmap!!
                                val imgW = img.width.toFloat()
                                val imgH = img.height.toFloat()

                                val baseScale = cropSize / minOf(imgW, imgH)
                                val finalScale = baseScale * scale
                                val drawW = imgW * finalScale
                                val drawH = imgH * finalScale

                                val imgLeft = cropCenter.x - drawW / 2f + offsetX
                                val imgTop = cropCenter.y - drawH / 2f + offsetY

                                drawImage(img, dstOffset = IntOffset(imgLeft.toInt(), imgTop.toInt()), dstSize = IntSize(drawW.toInt(), drawH.toInt()))

                                val cropPath = Path().apply {
                                    if (cropShape == CropShape.CIRCLE) {
                                        addOval(Rect(cropCenter.x - cropSize / 2f, cropCenter.y - cropSize / 2f, cropCenter.x + cropSize / 2f, cropCenter.y + cropSize / 2f))
                                    } else {
                                        addRect(Rect(cropCenter.x - cropSize / 2f, cropCenter.y - cropSize / 2f, cropCenter.x + cropSize / 2f, cropCenter.y + cropSize / 2f))
                                    }
                                }

                                clipPath(cropPath, clipOp = ClipOp.Difference) { drawRect(Color.Black.copy(alpha = 0.7f)) }

                                if (cropShape == CropShape.CIRCLE) {
                                    drawCircle(color = Color.White.copy(alpha = 0.8f), radius = cropSize / 2f, center = cropCenter, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                                } else {
                                    drawRect(color = Color.White.copy(alpha = 0.8f), topLeft = Offset(cropCenter.x - cropSize / 2f, cropCenter.y - cropSize / 2f), size = Size(cropSize, cropSize), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
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
        val outputSize = 512 // tamaño de salida en px
        val outputBitmap = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(outputBitmap)

        // Si es círculo, recortar con path circular
        if (cropShape == CropShape.CIRCLE) {
            val path = android.graphics.Path().apply {
                addCircle(
                    outputSize / 2f, outputSize / 2f, outputSize / 2f,
                    android.graphics.Path.Direction.CCW
                )
            }
            canvas.clipPath(path)
        }

        // Calcular posición de la imagen en el output
        val imgW = sourceBitmap.width.toFloat()
        val imgH = sourceBitmap.height.toFloat()
        val baseScale = outputSize.toFloat() / minOf(imgW, imgH)
        val finalScale = baseScale * scale
        val drawW = imgW * finalScale
        val drawH = imgH * finalScale

        val imgLeft = (outputSize / 2f) - (drawW / 2f) + (offsetX * outputSize / 512f)
        val imgTop = (outputSize / 2f) - (drawH / 2f) + (offsetY * outputSize / 512f)

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
