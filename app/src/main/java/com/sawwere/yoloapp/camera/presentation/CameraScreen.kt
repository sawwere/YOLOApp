package com.sawwere.yoloapp.camera.presentation

import android.graphics.Bitmap
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val processedImage = viewModel.processedImage.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Camera preview with overlay
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
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
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            )

            IconButton(
                onClick = { viewModel.toggleDebugMode() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(48.dp)
                    .background(
                        if (debugMode.value) Color(0xFF6200EE).copy(alpha = 0.8f)
                        else Color.Black.copy(alpha = 0.5f),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = "Debug Mode",
                    tint = if (debugMode.value) Color.Yellow else Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

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
                    Text(
                        text = "Processed Image (224x224)",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    if (processedImage.value != null) {
                        IconButton(
                            onClick = {
                                viewModel.clearProcessedImage()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear Processed Image",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .border(2.dp, if (processedImage.value != null) Color.Green else Color.Gray, RoundedCornerShape(8.dp))
                ) {
                    if (processedImage.value != null) {
                        Image(
                            bitmap = processedImage.value!!.asImageBitmap(),
                            contentDescription = "Processed Image",
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
                                text = "${processedImage.value!!.width}×${processedImage.value!!.height}",
                                color = Color.White,
                                fontSize = 10.sp
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Image,
                                contentDescription = "No Processed Image",
                                tint = Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Сделайте фото для обработки",
                                color = Color.Gray,
                                fontSize = 12.sp
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
                        imageVector = Icons.Default.BugReport,
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
                if (processedImage.value != null) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Image Processed",
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(
            text = "Speed Info",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color.White
        )

        SpeedInfoRow(
            label = "Preprocess: ",
            value = "${preProcessTime}ms"
        )
        SpeedInfoRow(
            label = "Inference: ",
            value = "${inferenceTime}ms"
        )
        SpeedInfoRow(
            label = "Postprocess: ",
            value = "${postProcessTime}ms"
        )
    }
}

@Composable
fun SpeedInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = value,
            color = Color.White
        )
    }
}

fun Float.format(digits: Int) = "%.${digits}f".format(this)