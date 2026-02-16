package com.sawwere.yoloapp.ui.category.list

import com.sawwere.yoloapp.core.data.entity.Category

data class CategoryWithCount(
    val category: Category,
    val photoCount: Int
)