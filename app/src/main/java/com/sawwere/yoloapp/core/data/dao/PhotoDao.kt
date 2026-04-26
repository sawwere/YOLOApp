package com.sawwere.yoloapp.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.sawwere.yoloapp.core.data.entity.Photo
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Insert
    fun insertPhoto(photo: Photo): Long

    @Query("SELECT * FROM photos WHERE id = :photoId")
    fun getPhotoById(photoId: Long): Photo?

    @Query("SELECT * FROM photos WHERE category_id = :categoryId ORDER BY created_at DESC")
    fun getPhotosByCategory(categoryId: Long): Flow<List<Photo>>

    @Delete
    fun deletePhoto(photo: Photo)

    @Query("UPDATE photos SET is_processed = 1 WHERE category_id = :categoryId")
    fun markAllPhotosAsProcessed(categoryId: Long): Int
}