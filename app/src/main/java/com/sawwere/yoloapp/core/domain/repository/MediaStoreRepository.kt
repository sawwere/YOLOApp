package com.sawwere.yoloapp.core.domain.repository

import android.graphics.Bitmap
import android.net.Uri
import java.io.InputStream

interface MediaStoreRepository {
    /**
     * Сохраняет изображение в публичное хранилище через MediaStore
     * @param bitmap Изображение для сохранения
     * @param categoryName Название категории (будет использовано для организации файлов)
     * @param description Описание изображения (сохраняется в метаданные)
     * @return Uri сохраненного изображения или null в случае ошибки
     */
    suspend fun saveImageToPublicStorage(
        bitmap: Bitmap,
        categoryName: String,
        description: String = ""
    ): Uri?

    /**
     * Загружает изображение из MediaStore по Uri
     * @param imageUri Uri изображения в MediaStore
     * @return Bitmap или null если не удалось загрузить
     */
    suspend fun loadImageFromPublicStorage(imageUri: Uri): Bitmap?

    suspend fun loadImageAsStream(imageUri: Uri, block: (InputStream)->Unit)

    /**
     * Загружает миниатюру изображения (оптимизированно для списков)
     * @param imageUri Uri изображения в MediaStore
     * @param targetSize Целевой размер миниатюры (квадрат)
     * @return Bitmap миниатюры или null
     */
    suspend fun loadThumbnail(imageUri: Uri, targetSize: Int = THUMBNAIL_SIZE): Bitmap?

    /**
     * Удаляет изображение из MediaStore
     * @param imageUri Uri изображения для удаления
     * @return true если удаление успешно
     */
    suspend fun deleteImageFromPublicStorage(imageUri: Uri): Boolean

    /**
     * Получает все изображения из указанной категории
     * @param categoryName Название категории
     * @return Список Uri изображений в категории
     */
    suspend fun getImagesInCategory(categoryName: String): List<Uri>

    /**
     * Проверяет, существует ли изображение в MediaStore
     */
    suspend fun imageExists(imageUri: Uri): Boolean

    companion object {
        const val THUMBNAIL_SIZE = 400
    }
}