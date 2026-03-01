package com.sawwere.yoloapp.ui.camera.navigation

const val CAMERA_SCREEN_ROUTE = "camera/{categoryId}"

object CameraScreenNavigation {
    val route: String = CAMERA_SCREEN_ROUTE

    fun passId(categoryId: Long): String = "camera/$categoryId"
}