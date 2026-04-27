package com.sawwere.yoloapp.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.sawwere.yoloapp.core.data.entity.Category
import com.sawwere.yoloapp.core.data.entity.CategoryWithPhotos
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
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

    @Transaction
    @Query("SELECT * FROM categories WHERE id = :categoryId")
    fun getCategoryWithPhotos(categoryId: Long): Flow<CategoryWithPhotos?>

    @Query("SELECT COUNT(*) FROM photos WHERE category_id = :categoryId")
    fun getPhotoCountInCategory(categoryId: Long): Int
}