package com.sawwere.yoloapp.core.domain.category.usecase

interface InsertCategory {
    suspend fun insert(name: String): Long
}