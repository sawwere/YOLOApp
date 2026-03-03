package com.sawwere.yoloapp.ui.camera.navigation

const val CAMERA_SCREEN_ROUTE = "camera/{${CameraScreenNavigation.CATEGORY_ID_ARG}}/{${CameraScreenNavigation.MODE_ARG}}"


enum class CameraScreenMode(val value: String) {
    ADD("add"),
    CHECK("check")
}

object CameraScreenNavigation {
    val route: String = CAMERA_SCREEN_ROUTE

    const val CATEGORY_ID_ARG = "categoryId"
    const val MODE_ARG = "mode"

    fun passArgs(categoryId: Long, mode: CameraScreenMode): String = CAMERA_SCREEN_ROUTE
        .replace("{$CATEGORY_ID_ARG}", categoryId.toString())
        .replace("{$MODE_ARG}", mode.value)
}