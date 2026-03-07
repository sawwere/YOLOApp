package com.sawwere.yoloapp.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.sawwere.yoloapp.core.data.entity.Category
import com.sawwere.yoloapp.core.data.entity.CategoryWithPhotos
import com.sawwere.yoloapp.core.data.entity.Photo
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // Category operations
    @Insert
    fun insertCategory(category: Category): Long

    @Query("SELECT * FROM categories ORDER BY created_at DESC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :categoryId")
    fun getCategoryById(categoryId: Long): Category?

    @Delete
    fun deleteCategory(category: Category)

    @Update
    fun updateCategory(category: Category)

    // Photo operations
    @Insert
    fun insertPhoto(photo: Photo): Long

    @Query("SELECT * FROM photos WHERE id = :photoId")
    fun getPhotoById(photoId: Long): Photo?

    @Transaction
    @Query("SELECT * FROM categories WHERE id = :categoryId")
    fun getCategoryWithPhotos(categoryId: Long): Flow<CategoryWithPhotos?>

    @Query("SELECT * FROM photos WHERE category_id = :categoryId ORDER BY created_at DESC")
    fun getPhotosByCategory(categoryId: Long): Flow<List<Photo>>

    @Delete
    fun deletePhoto(photo: Photo)

    // Для галереи - получаем все категории с их первым фото для превью
    @Transaction
    @Query("SELECT * FROM categories")
    fun getAllCategoriesWithFirstPhoto(): Flow<List<CategoryWithPhotos>>

    // Получаем количество фото в категории
    @Query("SELECT COUNT(*) FROM photos WHERE category_id = :categoryId")
    fun getPhotoCountInCategory(categoryId: Long): Int

    @Query("UPDATE photos SET is_processed = 1 WHERE category_id = :categoryId")
    fun markAllPhotosAsProcessed(categoryId: Long): Int
}