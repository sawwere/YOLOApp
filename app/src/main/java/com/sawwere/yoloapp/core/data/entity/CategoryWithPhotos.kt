package com.sawwere.yoloapp.core.data.entity

import androidx.room.Embedded
import androidx.room.Relation

data class CategoryWithPhotos(
    @Embedded
    val category: Category,
    @Relation(
        parentColumn = "id",
        entityColumn = "category_id"
    )
    val photos: List<Photo>
)