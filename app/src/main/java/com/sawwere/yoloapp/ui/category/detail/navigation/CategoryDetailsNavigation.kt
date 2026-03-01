package com.sawwere.yoloapp.ui.category.detail.navigation


const val CATEGORY_ID_ARG = "categoryId"
const val CATEGORY_DETAILS_SCREEN_ROUTE = "category_detail/{$CATEGORY_ID_ARG}"


object CategoryDetailsNavigation {
    val route: String = CATEGORY_DETAILS_SCREEN_ROUTE

    fun passId(categoryId: Long): String = "category_detail/$categoryId"
}