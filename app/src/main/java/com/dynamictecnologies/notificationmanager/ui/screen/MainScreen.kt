package com.dynamictecnologies.notificationmanager.ui.screen

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
import com.dynamictecnologies.notificationmanager.navigation.MainRoutes
import com.dynamictecnologies.notificationmanager.ui.components.AppBottomBar
import com.dynamictecnologies.notificationmanager.ui.components.AppTopBar
import com.dynamictecnologies.notificationmanager.ui.components.Screen

@Composable
fun MainScreen(
    navController: NavController,
    content: @Composable (innerPadding: Modifier) -> Unit
) {
    // Determinar la pantalla actual basada en la ruta de navegación
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    // Mapeo de rutas a pantallas
    val currentScreen = when (currentRoute) {
        MainRoutes.AppList.route -> Screen.HOME
        MainRoutes.Profile.route -> Screen.PROFILE
        MainRoutes.Share.route -> Screen.SHARED
        else -> Screen.HOME
    }
    
    Scaffold(
        topBar = {
            AppTopBar(
                currentScreen = currentScreen
            )
        },
        bottomBar = {
            AppBottomBar(
                currentScreen = currentScreen,
                onScreenSelected = { screen ->
                    val route = when (screen) {
                        Screen.HOME -> MainRoutes.AppList.route
                        Screen.PROFILE -> MainRoutes.Profile.route
                        Screen.SHARED -> MainRoutes.Share.route
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