package com.sawwere.yoloapp.ui.camera

import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import com.sawwere.yoloapp.core.system.VibrationComponent


@Composable
fun CameraScreen(
    onBackClick: () -> Unit,
    onCaptureClick: () -> Unit,
    viewModel: CameraScreenViewModel,
) {
    val context = LocalContext.current

    val segmentedBitmap = viewModel.segmentedBitmap

    val vibrationComponent = VibrationComponent.getFromContext(context)

    val preProcessTime = viewModel.preProcessTime.collectAsState()
    val inferenceTime = viewModel.inferenceTime.collectAsState()
    val postProcessTime = viewModel.postProcessTime.collectAsState()
    val zoomProgress = viewModel.zoomProgress.collectAsState()
    val debugMode = viewModel.debugMode.collectAsState()

    val processedSegments = remember { mutableStateOf(viewModel.processedSegments) }
    val currentSegmentIndex = remember { mutableIntStateOf(viewModel.currentSegmentIndex) }

    LaunchedEffect(Unit) {
        snapshotFlow { viewModel.processedSegments }
            .collect { newSegments ->
                processedSegments.value = newSegments
            }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { viewModel.currentSegmentIndex }
            .collect { newIndex ->
                currentSegmentIndex.intValue = newIndex
            }
    }

    val detectedObjectsCount = viewModel.detectedObjectsCount.collectAsState()

    val currentSegment by remember(processedSegments.value, currentSegmentIndex.intValue) {
        mutableStateOf(
            if (processedSegments.value.isNotEmpty() && currentSegmentIndex.intValue < processedSegments.value.size) {
                processedSegments.value[currentSegmentIndex.intValue]
            } else {
                null
            }
        )
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (processedSegments.value.size > 1) {
                        IconButton(
                            onClick = { viewModel.previousSegment() },
                            modifier = Modifier.size(32.dp),
                            enabled = processedSegments.value.size > 1
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Previous Segment",
                                tint = if (processedSegments.value.size > 1) Color.White else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(32.dp))
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Processed Segment (224x224)",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (processedSegments.value.isNotEmpty()) {
                                "Segment ${currentSegmentIndex.intValue + 1}/${processedSegments.value.size}"
                            } else {
                                "Нажмите кнопку для захвата"
                            },
                            color = if (processedSegments.value.isNotEmpty()) Color.Green else Color.Yellow,
                            fontSize = 10.sp
                        )
                    }

                    if (processedSegments.value.size > 1) {
                        IconButton(
                            onClick = { viewModel.nextSegment() },
                            modifier = Modifier.size(32.dp),
                            enabled = processedSegments.value.size > 1
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Next Segment",
                                tint = if (processedSegments.value.size > 1) Color.White else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(32.dp))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (processedSegments.value.isNotEmpty()) {
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

                if (processedSegments.value.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(processedSegments.value.size) { index ->
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (index == currentSegmentIndex.intValue) Color.Green else Color.Gray,
                                        CircleShape
                                    )
                                    .padding(2.dp)
                                    .clickable {
                                        viewModel.setSegmentIndex(index)
                                    }
                            )
                            if (index < processedSegments.value.size - 1) {
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .border(2.dp,
                            when {
                                currentSegment != null -> Color.Green
                                else -> Color.Yellow
                            },
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentSegment != null) {
                        if (!currentSegment!!.isRecycled) {
                            Image(
                                bitmap = currentSegment!!.asImageBitmap(),
                                contentDescription = "Processed Segment",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${currentSegment!!.width}×${currentSegment!!.height}",
                                    color = Color.White,
                                    fontSize = 10.sp
                                )
                            }

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
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Error,
                                    contentDescription = "Bitmap Recycled",
                                    tint = Color.Red,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Ошибка: bitmap переработан",
                                    color = Color.Red,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    } else {
                        // Плейсхолдер, когда нет сегментов
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Image,
                                contentDescription = "No Processed Image",
                                tint = Color.Yellow,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (detectedObjectsCount.value > 0)
                                    "Объекты обнаружены (${detectedObjectsCount.value})"
                                else "Нет объектов",
                                color = Color.Yellow,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Нажмите кнопку для захвата",
                                color = Color.Yellow,
                                fontSize = 10.sp
                            )
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
                onClick = {
                    onCaptureClick()
                    vibrationComponent.triggerHapticFeedback()
                },
                modifier = Modifier.align(Alignment.Center)
            )

            Box(
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                if (processedSegments.value.isNotEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Segments Captured",
                            tint = Color.Green,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "${processedSegments.value.size}",
                            color = Color.Green,
                            fontSize = 10.sp
                        )
                    }
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