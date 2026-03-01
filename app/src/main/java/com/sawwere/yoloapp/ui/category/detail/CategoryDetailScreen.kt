package com.sawwere.yoloapp.ui.category.detail

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sawwere.yoloapp.R
import com.sawwere.yoloapp.core.data.entity.Category
import com.sawwere.yoloapp.core.data.entity.Photo
import com.sawwere.yoloapp.ui.theme.NeutralWhite
import com.sawwere.yoloapp.ui.theme.Primary500
import com.sawwere.yoloapp.ui.theme.Secondary500
import kotlinx.coroutines.Dispatchers
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    onBackClick: () -> Unit,
    onAddPhotoClick: () -> Unit,
    onCheckClick: () -> Unit,
    viewModel: CategoryDetailScreenViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    val categoryWithPhotos by viewModel.categoryWithPhotos.collectAsState()
    val category = categoryWithPhotos?.category
    val photos = categoryWithPhotos?.photos ?: emptyList()

    var photoToDelete by remember { mutableStateOf<Photo?>(null) }

    val recalculateState by viewModel.recalculateState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val hasPhotos = photos.isNotEmpty()
    val hasVector = category?.checksum != null
    val isCheckEnabled = hasPhotos && hasVector

    LaunchedEffect(recalculateState) {
        when (recalculateState) {
            is RecalculateState.Success -> {
                snackbarHostState.showSnackbar(
                    message = "Вектор контрольных сумм пересчитан",
                    duration = SnackbarDuration.Short
                )
            }
            is RecalculateState.Error -> {
                snackbarHostState.showSnackbar(
                    message = (recalculateState as RecalculateState.Error).message,
                    duration = SnackbarDuration.Long
                )
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        category?.name ?: "Категория",
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.ui_common_return),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = onAddPhotoClick) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = stringResource(
                                R.string.category_detail_add_image_button
                            ),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            CategoryInfoCard(
                category = category,
                photoCount = photos.size
            )

            Spacer(modifier = Modifier.height(16.dp))

            GallerySection(
                modifier = Modifier.weight(1f),
                photos = photos,
                onPhotoClick = { photo ->
                    try {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse(photo.imageUri), "image/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.category_detail_couldnt_open_image_error),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onPhotoDelete = { photo -> photoToDelete = photo }
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (hasPhotos) {
                    Button(
                        onClick = onCheckClick,
                        enabled = isCheckEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Primary500,
                            contentColor = NeutralWhite,
                            disabledContainerColor = Primary500.copy(alpha = 0.5f),
                            disabledContentColor = NeutralWhite.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.category_detail_check_button),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Button(
                    onClick = { viewModel.recalculateChecksum() },
                    enabled = hasPhotos,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Secondary500,
                        contentColor = NeutralWhite,
                        disabledContainerColor = Secondary500.copy(alpha = 0.5f),
                        disabledContentColor = NeutralWhite.copy(alpha = 0.5f)
                    )
                ) {
                    if (recalculateState is RecalculateState.InProgress) {
                        CircularProgressIndicator(
                            color = NeutralWhite,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = stringResource(
                                R.string.category_detail_recalculate_vector_button
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
    if (photoToDelete != null) {
        DeletePhotoDialog(
            onConfirm = {
                viewModel.deletePhoto(photoToDelete!!.id)
                photoToDelete = null
            },
            onDismiss = { photoToDelete = null }
        )
    }
}

@Composable
private fun CategoryInfoCard(
    category: Category?,
    photoCount: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            if (category != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.category_detail_information_label),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "$photoCount фото",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                InfoRow(
                    label = stringResource(R.string.category_detail_name_label),
                    value = category.name
                )

                InfoRow(
                    label = stringResource(R.string.category_detail_created_at_label),
                    value = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                        .format(Date(category.createdAt))
                )
            } else {
                Text(stringResource(R.string.category_detail_category_not_found))
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun GallerySection(
    modifier: Modifier = Modifier,
    photos: List<Photo>,
    onPhotoClick: (Photo) -> Unit,
    onPhotoDelete: (Photo) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.category_detail_images_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (photos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = stringResource(
                            R.string.category_detail_no_images_title
                        ),
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.category_detail_no_images_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(
                            R.string.category_detail_no_images_add_first_image_suggestion
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
            ) {
                items(photos) { photo ->
                    PhotoGridItem(
                        photo = photo,
                        onClick = { onPhotoClick(photo) },
                        onDeleteClick = { onPhotoDelete(photo) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoGridItem(
    photo: Photo,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current

    LaunchedEffect(photo.imageUri) {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(photo.imageUri)
                val inputStream = context.contentResolver.openInputStream(uri)
                inputStream?.use {
                    bitmap = BitmapFactory.decodeStream(it)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = photo.description,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(32.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.ui_common_delete),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun DeletePhotoDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.category_datail_delete_photo_title)) },
        text = { Text(stringResource(R.string.category_detail_delete_photo_confirmation)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.ui_common_delete))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.ui_common_cancel))
            }
        }
    )
}