package com.dynamictecnologies.notificationmanager.navigation

sealed class MainRoutes(val route: String) {
    object AppList : MainRoutes("main_app_list")
    object Profile : MainRoutes("main_profile")
    object Share : MainRoutes("main_share")
} 