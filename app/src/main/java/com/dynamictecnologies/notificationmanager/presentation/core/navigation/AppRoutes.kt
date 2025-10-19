package com.dynamictecnologies.notificationmanager.presentation.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Definición unificada de rutas de navegación.
 * Elimina la duplicación entre NavGraph.kt y MainRoutes.kt
 */
sealed class AppRoutes(val route: String) {
    // Auth Flow
    object Login : AppRoutes("login")
    object Register : AppRoutes("register")
    
    // Main Flow
    object Main : AppRoutes("main")
    
    // Main Flow - Pantallas específicas
    sealed class MainScreen(route: String) : AppRoutes("main_$route") {
        object Home : MainScreen("home")
        object Profile : MainScreen("profile")
        object Share : MainScreen("share")
    }
}

/**
 * Definición unificada de pantallas para UI.
 * Elimina la duplicación entre NavGraph.kt y ScaffoldDynamic.kt
 */
enum class AppScreen(
    val title: String,
    val icon: ImageVector,
    val route: String,
    val canShare: Boolean = false
) {
    HOME("Inicio", Icons.Default.Home, AppRoutes.MainScreen.Home.route),
    PROFILE("Perfil", Icons.Default.Person, AppRoutes.MainScreen.Profile.route),
    SHARE("Compartir", Icons.Default.People, AppRoutes.MainScreen.Share.route, canShare = true)
}
