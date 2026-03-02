package com.sawwere.yoloapp.ui.camera

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.sawwere.yoloapp.R

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraPermissionScreen(
    onBackClick: () -> Unit,
    onCaptureClick: () -> Unit,
    viewModel: CameraScreenViewModel
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val context = LocalContext.current

    // Флаг, запрашивали ли мы разрешение в текущей сессии экрана
    val permissionRequested = rememberSaveable { mutableStateOf(false) }

    when (cameraPermissionState.status) {
        is PermissionStatus.Granted -> {
            CameraScreen(
                onBackClick = onBackClick,
                onCaptureClick = onCaptureClick,
                viewModel = viewModel
            )
        }
        is PermissionStatus.Denied -> {
            val deniedStatus = cameraPermissionState.status as PermissionStatus.Denied

            // Автоматически запрашиваем разрешение при первом входе
            LaunchedEffect(Unit) {
                if (!permissionRequested.value) {
                    permissionRequested.value = true
                    cameraPermissionState.launchPermissionRequest()
                }
            }

            if (!permissionRequested.value) {
                Box(modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                )
            } else {
                if (deniedStatus.shouldShowRationale) {
                    // Пользователь отказался, но может согласиться при повторном запросе
                    PermissionRationaleScreen(
                        onRequestAgain = { cameraPermissionState.launchPermissionRequest() },
                        onBackClick = onBackClick
                    )
                } else {
                    // Разрешение отклонено навсегда
                    PermissionPermanentlyDeniedScreen(
                        onOpenSettings = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                            )
                        },
                        onBackClick = onBackClick
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionRationaleScreen(
    onRequestAgain: () -> Unit,
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.camera_request_permission_label),
            color = Color.White,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRequestAgain) {
            Text(stringResource(R.string.camera_no_objects_found_label))
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onBackClick) {
            Text(stringResource(R.string.ui_common_return), color = Color.White)
        }
    }
}

@Composable
fun PermissionPermanentlyDeniedScreen(
    onOpenSettings: () -> Unit,
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.camera_no_permission_given_label),
            color = Color.White,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onOpenSettings) {
            Text(stringResource(R.string.ui_common_open_settings))
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onBackClick) {
            Text(stringResource(R.string.ui_common_return), color = Color.White)
        }
    }
}