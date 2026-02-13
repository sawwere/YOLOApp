package com.sawwere.yoloapp

import android.app.Application
import com.sawwere.yoloapp.core.data.AppDatabase
import com.sawwere.yoloapp.core.data.repository.AppRepository
import com.sawwere.yoloapp.core.data.repository.MediaStoreRepository

class YOLOApp : Application() {

    lateinit var appContainer: AppContainer

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}

class AppContainer(application: Application) {

    private val mediaStoreRepository = MediaStoreRepository(application.applicationContext)
    private val database = AppDatabase.getDatabase(application.applicationContext)
    val appRepository = AppRepository(database.appDao(), mediaStoreRepository)
}