package com.sawwere.yoloapp.camera.presentation

import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.sawwere.yoloapp.MainActivity


@Composable
fun CameraScreen(
    viewModel: CameraScreenViewModel,
    segmentedBitmap: Bitmap?,
    onCaptureClick: () -> Unit
) {
    val context = LocalContext.current

    val preProcessTime = viewModel.preProcessTime.collectAsState()
    val inferenceTime = viewModel.inferenceTime.collectAsState()
    val postProcessTime = viewModel.postProcessTime.collectAsState()
    val zoomProgress = viewModel.zoomProgress.collectAsState()
    val debugMode = viewModel.debugMode.collectAsState()
    val processedSegments = viewModel.processedSegments
    val detectedObjectsCount = viewModel.detectedObjectsCount.collectAsState()

    val currentSegment = remember(processedSegments) {
        Log.d("CameraScreen", "Remember recalculated. Segments: ${processedSegments.size}")
        if (processedSegments.isNotEmpty()) {
            Log.d("CameraScreen", "First segment: ${processedSegments.first().width}x${processedSegments.first().height}")
            processedSegments.first()
        } else null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        viewModel.handlePinchZoom(zoom)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            var previewView: PreviewView? by remember { mutableStateOf(null) }

            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        previewView = this
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
            )

            segmentedBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Segmentation Result",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                )
            }

            SpeedInfoPanel(
                preProcessTime = preProcessTime.value,
                inferenceTime = inferenceTime.value,
                postProcessTime = postProcessTime.value,
                detectedObjects = detectedObjectsCount.value,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            )

            LaunchedEffect(previewView) {
                if (previewView != null) {
                    (context as? MainActivity)?.startCamera(previewView!!)
                }
            }
        }

        if (debugMode.value) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "Zoom Control (Debug)",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Slider(
                    value = zoomProgress.value,
                    onValueChange = { newProgress ->
                        viewModel.updateCameraZoom(newProgress)
                    },
                    valueRange = 0f..10f,
                    steps = 9,
                    modifier = Modifier
                        .fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF6200EE),
                        activeTrackColor = Color(0xFF6200EE),
                        inactiveTrackColor = Color(0xFF6200EE).copy(alpha = 0.24f)
                    )
                )

                Text(
                    text = "Zoom: ${zoomProgress.value.format(1)}x",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        if (debugMode.value) {
            Column(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                // Заголовок для обработанного изображения
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Captured Segment (224x224)",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (processedSegments.isNotEmpty())
                                "Object 1 of ${detectedObjectsCount.value} (captured)"
                            else "Нажмите кнопку для захвата",
                            color = if (processedSegments.isNotEmpty()) Color.Green else Color.Yellow,
                            fontSize = 10.sp
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Кнопка для очистки всех сегментов
                    if (processedSegments.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                viewModel.clearAllSegments()
                                Toast.makeText(context, "Сегменты очищены", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = "Clear All Segments",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Контейнер для обработанного изображения
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .border(2.dp,
                            when {
                                currentSegment != null -> Color.Green
                                detectedObjectsCount.value > 0 -> Color.Yellow
                                else -> Color.Gray
                            },
                            RoundedCornerShape(8.dp)
                        )
                ) {
                    if (currentSegment != null) {
                        Image(
                            bitmap = currentSegment.asImageBitmap(),
                            contentDescription = "Processed Segment",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                        )

                        // Информация о размере изображения
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${currentSegment.width}×${currentSegment.height}",
                                color = Color.White,
                                fontSize = 10.sp
                            )
                        }

                        // Индикатор, что это вырезанный сегмент
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "CROPPED",
                                color = Color.Yellow,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        // Плейсхолдер, когда нет сегментов
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            when {
                                detectedObjectsCount.value > 0 -> {
                                    // Объекты обнаружены, но не захвачены
                                    Icon(
                                        imageVector = Icons.Filled.PhotoCamera,
                                        contentDescription = "Ready to Capture",
                                        tint = Color.Yellow,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Обнаружено: ${detectedObjectsCount.value}",
                                        color = Color.Yellow,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "Нажмите кнопку для захвата",
                                        color = Color.Yellow,
                                        fontSize = 10.sp
                                    )
                                }
                                else -> {
                                    // Нет объектов
                                    Icon(
                                        imageVector = Icons.Filled.Image,
                                        contentDescription = "No Objects",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Нет обнаруженных объектов",
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }


        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.toggleDebugMode() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.BugReport,
                        contentDescription = "Debug Mode",
                        tint = if (debugMode.value) Color.Yellow else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = if (debugMode.value) "Debug ON" else "Debug OFF",
                    color = if (debugMode.value) Color.Yellow else Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            ShutterButton(
                onClick = onCaptureClick,
                modifier = Modifier.align(Alignment.Center)
            )

            Box(
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                if (processedSegments.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Segments Stored",
                        tint = Color.Green,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier
            .height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
        )
    }
}


@Composable
fun SpeedInfoPanel(
    preProcessTime: Long,
    inferenceTime: Long,
    postProcessTime: Long,
    detectedObjects: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
            .shadow(4.dp, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Processing Info",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .background(
                            if (detectedObjects > 0) Color.Green else Color.Gray,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "$detectedObjects obj",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            SpeedInfoRow(
                label = "Preprocess: ",
                value = "${preProcessTime}ms",
                color = Color(0xFF4CAF50)
            )
            SpeedInfoRow(
                label = "Inference: ",
                value = "${inferenceTime}ms",
                color = Color(0xFF2196F3)
            )
            SpeedInfoRow(
                label = "Postprocess: ",
                value = "${postProcessTime}ms",
                color = Color(0xFFFF9800)
            )

            val totalTime = preProcessTime + inferenceTime + postProcessTime
            SpeedInfoRow(
                label = "Total: ",
                value = "${totalTime}ms",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SpeedInfoRow(
    label: String,
    value: String,
    color: Color = Color.White,
    fontWeight: FontWeight? = null
) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 12.sp,
            fontWeight = fontWeight
        )
        Text(
            text = value,
            color = color,
            fontSize = 12.sp,
            fontWeight = fontWeight
        )
    }
}

fun Float.format(digits: Int) = "%.${digits}f".format(this)