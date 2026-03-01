package com.sawwere.yoloapp.core.data.di

import android.content.Context
import androidx.room.Room
import com.sawwere.yoloapp.core.data.AppDatabase
import com.sawwere.yoloapp.core.data.repository.AppRepositoryImpl
import com.sawwere.yoloapp.core.data.repository.MediaStoreRepositoryImpl
import com.sawwere.yoloapp.core.domain.repository.AppRepository
import com.sawwere.yoloapp.core.domain.repository.MediaStoreRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class LocalSourceModuleProvider {

    @Provides
    fun provideAppDao(database: AppDatabase) = database.appDao()

    @Provides
    @Singleton
    fun providesLocalDatabase(
        @ApplicationContext context: Context
    ) = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "photo_catalog_db"
            )
        .fallbackToDestructiveMigration()
        .build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LocalSourceModuleBinder {

    @Binds
    abstract fun bindDefaultMediastoreRepository(
        mediaStoreRepository: MediaStoreRepositoryImpl
    ) : MediaStoreRepository

    @Binds
    abstract fun bindDefaultAppRepository(
        defaultAppRepository: AppRepositoryImpl
    ) : AppRepository
}