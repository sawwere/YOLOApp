package com.sawwere.yolov11app.camera.presentation

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.AspectRatio
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontWeight
import com.sawwere.yolov11app.MainActivity
import com.sawwere.yolov11app.ui.theme.YOLOv11AppTheme


@Composable
fun CameraScreen(
    preProcessTime: String,
    inferenceTime: String,
    postProcessTime: String,
    segmentedBitmap: Bitmap?,
    zoomProgress: Float,
    onZoomChanged: (Float) -> Unit,
    onCaptureClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

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
                .padding(16.dp),
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

            // Segmentation overlay
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

            // Start camera when previewView is ready
            LaunchedEffect(previewView) {
                if (previewView != null) {
                    (context as? MainActivity)?.startCamera(previewView!!)
                }
            }
        }

        // Speed information
        SpeedInfoPanel(
            preProcessTime = preProcessTime,
            inferenceTime = inferenceTime,
            postProcessTime = postProcessTime
        )

        // Zoom slider
        Slider(
            value = zoomProgress,
            onValueChange = onZoomChanged,
            valueRange = 0f..10f,
            steps = 9,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF6200EE),
                activeTrackColor = Color(0xFF6200EE),
                inactiveTrackColor = Color(0xFF6200EE).copy(alpha = 0.24f)
            )
        )

        // Capture button
        Button(
            onClick = onCaptureClick,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 18.dp)
                .width(140.dp)
                .height(70.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6200EE),
                contentColor = Color.White
            )
        ) {
            Text(
                text = "Capture",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SpeedInfoPanel(
    preProcessTime: String,
    inferenceTime: String,
    postProcessTime: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Speed (in milliseconds)",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color.White
        )

        SpeedInfoRow(label = "Preprocess: ", value = preProcessTime)
        SpeedInfoRow(label = "Inference: ", value = inferenceTime)
        SpeedInfoRow(label = "Postprocess: ", value = postProcessTime)
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