package com.sawwere.yoloapp.core.domain.di

import com.sawwere.yoloapp.core.domain.service.usecase.RecalculateChecksumImpl
import com.sawwere.yoloapp.ui.category.detail.usecase.RecalculateChecksum
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DomainModule {

    @Binds
    abstract fun bindRecalculateChecksum(
        impl: RecalculateChecksumImpl
    ): RecalculateChecksum
}