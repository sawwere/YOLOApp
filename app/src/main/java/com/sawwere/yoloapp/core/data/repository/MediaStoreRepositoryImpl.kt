package com.sawwere.yoloapp.core.data.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.sawwere.yoloapp.core.domain.repository.MediaStoreRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class MediaStoreRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
): MediaStoreRepository {

    companion object {
        private const val TAG = "MediaStoreRepositoryImpl"
        private const val APP_DIRECTORY = "PhotoCatalog"
        private const val IMAGE_QUALITY = 85
        private const val MAX_IMAGE_WIDTH = 1920
        private const val MAX_IMAGE_HEIGHT = 1080
        private const val THUMBNAIL_SIZE = 400
    }

    /**
     * Сохраняет изображение в публичное хранилище через MediaStore
     * @param bitmap Изображение для сохранения
     * @param categoryName Название категории (будет использовано для организации файлов)
     * @param description Описание изображения (сохраняется в метаданные)
     * @return Uri сохраненного изображения или null в случае ошибки
     */
    override suspend fun saveImageToPublicStorage(
        bitmap: Bitmap,
        categoryName: String,
        description: String
    ): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                // Сжимаем изображение до разумных размеров
                val compressedBitmap = compressImage(bitmap, MAX_IMAGE_WIDTH, MAX_IMAGE_HEIGHT)

                val resolver = context.contentResolver
                val fileName = generateFileName()
                val mimeType = "image/jpeg"

                // Создаем ContentValues для нового изображения
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                    put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
                    put(MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/$APP_DIRECTORY/${sanitizeCategoryName(categoryName)}")

                    if (description.isNotEmpty()) {
                        put(MediaStore.Images.Media.DESCRIPTION, description)
                    }

                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                // Вставляем запись в MediaStore
                val collection =
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

                val imageUri = resolver.insert(collection, contentValues)

                imageUri?.let { uri ->
                    // Сохраняем изображение
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        compressedBitmap.compress(Bitmap.CompressFormat.JPEG, IMAGE_QUALITY, outputStream)
                    }

                    // Для Android Q+ нужно обновить IS_PENDING после сохранения
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)

                    Log.d(TAG, "Image saved successfully: $uri")
                    return@withContext uri
                }

                Log.e(TAG, "Failed to insert image into MediaStore")
                null

            } catch (e: Exception) {
                Log.e(TAG, "Error saving image to public storage: ${e.message}", e)
                null
            }
        }
    }

    /**
     * Загружает изображение из MediaStore по Uri
     * @param imageUri Uri изображения в MediaStore
     * @return Bitmap или null если не удалось загрузить
     */
    override suspend fun loadImageFromPublicStorage(imageUri: Uri): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                resolver.openInputStream(imageUri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading image from MediaStore: ${e.message}", e)
                null
            }
        }
    }

    override suspend fun loadImageAsStream(imageUri: Uri, block: (InputStream)->Unit) {
        return withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                resolver.openInputStream(imageUri)?.use { inputStream ->
                    block(inputStream)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error while workinng with image from MediaStore: ${e.message}", e)
            }
        }
    }

    /**
     * Загружает миниатюру изображения (оптимизированно для списков)
     * @param imageUri Uri изображения в MediaStore
     * @param targetSize Целевой размер миниатюры (квадрат)
     * @return Bitmap миниатюры или null
     */
    override suspend fun loadThumbnail(imageUri: Uri, targetSize: Int): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                // Используем MediaStore для загрузки миниатюры (если доступно)
                val resolver = context.contentResolver
                resolver.loadThumbnail(imageUri, android.util.Size(targetSize, targetSize), null)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading thumbnail: ${e.message}", e)
                null
            }
        }
    }

    /**
     * Удаляет изображение из MediaStore
     * @param imageUri Uri изображения для удаления
     * @return true если удаление успешно
     */
    override suspend fun deleteImageFromPublicStorage(imageUri: Uri): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val deleted = resolver.delete(imageUri, null, null)
                deleted > 0
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting image from MediaStore: ${e.message}", e)
                false
            }
        }
    }

    /**
     * Получает все изображения из указанной категории
     * @param categoryName Название категории
     * @return Список Uri изображений в категории
     */
    override suspend fun getImagesInCategory(categoryName: String): List<Uri> {
        return withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED
            )

            // Ищем изображения в папке нашей категории
            val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf(
                "%${Environment.DIRECTORY_PICTURES}/$APP_DIRECTORY/${sanitizeCategoryName(categoryName)}/%"
            )

            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            val images = mutableListOf<Uri>()

            try {
                resolver.query(
                    collection,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val contentUri = Uri.withAppendedPath(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id.toString()
                        )
                        images.add(contentUri)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying images in category: ${e.message}", e)
            }

            images
        }
    }

    /**
     * Проверяет, существует ли изображение в MediaStore
     */
    override suspend fun imageExists(imageUri: Uri): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                resolver.openInputStream(imageUri)?.close()
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    // Вспомогательные методы
    private fun generateFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "IMG_${timestamp}"
    }

    private fun sanitizeCategoryName(categoryName: String): String {
        return categoryName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }

    private suspend fun compressImage(
        bitmap: Bitmap,
        maxWidth: Int,
        maxHeight: Int
    ): Bitmap = withContext(Dispatchers.Default) {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height

        if (originalWidth <= maxWidth && originalHeight <= maxHeight) {
            return@withContext bitmap
        }

        val widthRatio = maxWidth.toFloat() / originalWidth
        val heightRatio = maxHeight.toFloat() / originalHeight
        val ratio = minOf(widthRatio, heightRatio)

        val newWidth = (originalWidth * ratio).toInt()
        val newHeight = (originalHeight * ratio).toInt()

        Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun createThumbnail(bitmap: Bitmap, targetSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // Рассчитываем размеры для сохранения пропорций
        val scaleFactor = minOf(
            targetSize.toFloat() / width,
            targetSize.toFloat() / height
        )

        val newWidth = (width * scaleFactor).toInt()
        val newHeight = (height * scaleFactor).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Конвертирует Bitmap в ByteArray
     */
    private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, IMAGE_QUALITY, outputStream)
        return outputStream.toByteArray()
    }

    /**
     * Очищает все изображения приложения из MediaStore
     */
    suspend fun clearAllAppImages(): Int {
        return withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("%$APP_DIRECTORY%")

            try {
                resolver.delete(collection, selection, selectionArgs)
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing app images: ${e.message}", e)
                0
            }
        }
    }
}
