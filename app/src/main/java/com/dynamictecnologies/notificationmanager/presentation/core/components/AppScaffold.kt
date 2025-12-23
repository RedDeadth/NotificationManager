package com.dynamictecnologies.notificationmanager.presentation.core.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.dynamictecnologies.notificationmanager.presentation.core.navigation.AppRoutes
import com.dynamictecnologies.notificationmanager.presentation.core.navigation.AppScreen

/**
 * Scaffold unificado que maneja la navegación principal.
 * Elimina la duplicación entre MainScreen.kt y ScaffoldDynamic.kt
 */
@Composable
fun AppScaffold(
    navController: NavController,
    onSettingsClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    content: @Composable (innerPadding: Modifier) -> Unit
) {
    // Determinar la pantalla actual basada en la ruta de navegación
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    // Mapeo de rutas a pantallas
    val currentScreen = when (currentRoute) {
        AppRoutes.MainScreen.Home.route -> AppScreen.HOME
        AppRoutes.MainScreen.Profile.route -> AppScreen.PROFILE
        else -> AppScreen.HOME
    }
    
    Scaffold(
        topBar = {
            AppTopBar(
                currentScreen = currentScreen,
                onSettingsClick = onSettingsClick,
                onShareClick = onShareClick
            )
        },
        bottomBar = {
            AppBottomBar(
                currentScreen = currentScreen,
                onScreenSelected = { screen ->
                    val route = when (screen) {
                        AppScreen.HOME -> AppRoutes.MainScreen.Home.route
                        AppScreen.PROFILE -> AppRoutes.MainScreen.Profile.route
                    }
                    
                    // Navegar a la ruta seleccionada
                    navController.navigate(route) {
                        // Evitar múltiples copias de la misma pantalla en la pila de navegación
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        // Restaurar estado si existe
                        restoreState = true
                        // Evitar duplicados de la misma pantalla
                        launchSingleTop = true
                    }
                }
            )
        }
    ) { innerPadding ->
        // Renderizar el contenido dentro del scaffold con el padding correcto
        content(Modifier.padding(innerPadding))
    }
}
