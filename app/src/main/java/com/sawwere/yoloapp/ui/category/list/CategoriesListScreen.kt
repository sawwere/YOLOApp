package com.sawwere.yoloapp.ui.category.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sawwere.yoloapp.R
import com.sawwere.yoloapp.core.data.entity.Category
import com.sawwere.yoloapp.ui.common.DeleteDialog
import com.sawwere.yoloapp.ui.theme.Neutral100
import com.sawwere.yoloapp.ui.theme.Neutral300
import com.sawwere.yoloapp.ui.theme.Neutral400
import com.sawwere.yoloapp.ui.theme.Neutral500
import com.sawwere.yoloapp.ui.theme.Neutral600
import com.sawwere.yoloapp.ui.theme.Neutral700
import com.sawwere.yoloapp.ui.theme.Neutral800
import com.sawwere.yoloapp.ui.theme.NeutralWhite
import com.sawwere.yoloapp.ui.theme.Primary100
import com.sawwere.yoloapp.ui.theme.Primary50
import com.sawwere.yoloapp.ui.theme.Primary500
import com.sawwere.yoloapp.ui.theme.Primary600
import com.sawwere.yoloapp.ui.theme.Primary700
import com.sawwere.yoloapp.ui.theme.Secondary100
import com.sawwere.yoloapp.ui.theme.Secondary50
import com.sawwere.yoloapp.ui.theme.Secondary500
import com.sawwere.yoloapp.ui.theme.Secondary600
import com.sawwere.yoloapp.ui.theme.Secondary700
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesListScreen(
    onCategoryClick: (Long) -> Unit,
    viewModel: CategoriesListViewModel = hiltViewModel()
) {
    val categoriesWithCount by viewModel.categoriesWithCount.collectAsState(emptyList())
    viewModel.loadCategoriesWithCount()

    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var categoryToDelete by remember { mutableStateOf<Category?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Primary100, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = Primary700,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.categories_list_top_bar_label),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = Primary500,
                contentColor = NeutralWhite,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 8.dp
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.categories_list_add_category),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) { paddingValues ->
        if (categoriesWithCount.isEmpty()) {
            EmptyCategoriesContent()
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
            ) {
                items(categoriesWithCount) { item ->
                    CategoryCard(
                        category = item.category,
                        photoCount = item.photoCount,
                        onCategoryClick = { onCategoryClick(item.category.id) },
                        onDeleteClick = {
                            categoryToDelete = item.category
                            showDeleteDialog = true
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddCategoryDialog(
            onConfirm = { categoryName ->
                if (categoryName.isNotBlank()) {
                    viewModel.addCategory(categoryName)
                }
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    if (showDeleteDialog && categoryToDelete != null) {
        DeleteDialog(
            titleText = stringResource(R.string.categories_list_delete_category_ask),
            confirmationText = stringResource(
                R.string.categories_list_delete_category_confirmation
            ),
            onConfirm = {
                categoryToDelete?.let { category ->
                    viewModel.deleteCategory(category.id)
                }
                categoryToDelete = null
                showDeleteDialog = false
            },
            onDismiss = {
                categoryToDelete = null
                showDeleteDialog = false
            }
        )
    }
}

@Composable
private fun EmptyCategoriesContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Primary100),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = Primary700,
                    modifier = Modifier.size(64.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.categories_list_not_categories_label),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Neutral800
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.categories_list_create_first),
                style = MaterialTheme.typography.bodyLarge,
                color = Neutral600,
                lineHeight = 24.sp
            )
        }
    }
}

@Composable
private fun CategoryCard(
    category: Category,
    photoCount: Int,
    onCategoryClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCategoryClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Primary50),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = Primary600,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Neutral500
                        )

                        Text(
                            text = stringResource(
                                R.string.category_detail_images_count_label,
                                photoCount
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = Neutral600
                        )

                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelLarge,
                            color = Neutral400
                        )

                        Text(
                            text = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                                .format(Date(category.createdAt)),
                            style = MaterialTheme.typography.labelLarge,
                            color = Neutral600
                        )
                    }
                }
            }

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Secondary50)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(
                        R.string.categories_list_delete_category_label
                    ),
                    tint = Secondary600,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun AddCategoryDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var categoryName by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = stringResource(R.string.categories_list_create_category_label),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.categories_list_enter_category_name_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Neutral600
                )

                OutlinedTextField(
                    value = categoryName,
                    onValueChange = {
                        categoryName = it
                        isError = false
                    },
                    label = {
                        Text(
                            "Название",
                            color = if (isError) Secondary500 else Neutral600
                        )
                    },
                    placeholder = { Text("Например: Отпуск 2026") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = isError,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary500,
                        unfocusedBorderColor = Neutral300,
                        focusedLabelColor = Primary600,
                        cursorColor = Primary500,
                        errorBorderColor = Secondary500,
                        errorLabelColor = Secondary500
                    ),
                    supportingText = {
                        if (isError) {
                            Text(
                                text = stringResource(
                                    R.string.categories_list_empty_name_error_message
                                ),
                                color = Secondary500,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (categoryName.isNotBlank()) {
                        onConfirm(categoryName)
                    } else {
                        isError = true
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Primary500,
                    contentColor = NeutralWhite,
                    disabledContainerColor = Neutral300,
                    disabledContentColor = Neutral600
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "Создать",
                    fontWeight = FontWeight.Medium
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Neutral700
                ),
                shape = RoundedCornerShape(12.dp),
                border = ButtonDefaults.outlinedButtonBorder().copy(
                    width = 1.dp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "Отмена",
                    fontWeight = FontWeight.Medium
                )
            }
        }
    )
}