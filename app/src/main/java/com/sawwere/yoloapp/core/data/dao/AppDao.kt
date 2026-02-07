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
    suspend fun insertCategory(category: Category): Long

    @Query("SELECT * FROM categories ORDER BY created_at DESC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :categoryId")
    suspend fun getCategoryById(categoryId: Long): Category?

    @Delete
    suspend fun deleteCategory(category: Category)

    // Photo operations
    @Insert
    suspend fun insertPhoto(photo: Photo): Long

    @Update
    suspend fun updatePhoto(photo: Photo)

    @Query("SELECT * FROM photos WHERE id = :photoId")
    suspend fun getPhotoById(photoId: Long): Photo?

    @Transaction
    @Query("SELECT * FROM categories WHERE id = :categoryId")
    fun getCategoryWithPhotos(categoryId: Long): Flow<CategoryWithPhotos?>

    @Query("SELECT * FROM photos WHERE category_id = :categoryId ORDER BY created_at DESC")
    fun getPhotosByCategory(categoryId: Long): Flow<List<Photo>>

    @Delete
    suspend fun deletePhoto(photo: Photo)

    // Для галереи - получаем все категории с их первым фото для превью
    @Transaction
    @Query("SELECT * FROM categories")
    fun getAllCategoriesWithFirstPhoto(): Flow<List<CategoryWithPhotos>>

    // Получаем количество фото в категории
    @Query("SELECT COUNT(*) FROM photos WHERE category_id = :categoryId")
    suspend fun getPhotoCountInCategory(categoryId: Long): Int
}