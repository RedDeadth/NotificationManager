package com.dynamictecnologies.notificationmanager.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dynamictecnologies.notificationmanager.ui.screen.auth.LoginScreen
import com.dynamictecnologies.notificationmanager.ui.screen.auth.RegisterScreen
import com.dynamictecnologies.notificationmanager.ui.screen.home.AppListScreen
import com.dynamictecnologies.notificationmanager.ui.screen.permission.PermissionScreen
import com.dynamictecnologies.notificationmanager.viewmodel.AuthViewModel
import com.dynamictecnologies.notificationmanager.viewmodel.PermissionViewModel
import com.dynamictecnologies.notificationmanager.viewmodel.AppListViewModel

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object Permission : Screen("permission")
    object AppList : Screen("app_list")
}

@Composable
fun NavigationGraph(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    permissionViewModel: PermissionViewModel,
    appListViewModel: AppListViewModel,
    startDestination: String
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                authViewModel = authViewModel,
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                authViewModel = authViewModel,
                onNavigateToLogin = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Permission.route) {
            PermissionScreen(
                permissionViewModel = permissionViewModel,
                appListViewModel = appListViewModel
            )
        }

        composable(Screen.AppList.route) {
            AppListScreen(
                viewModel = appListViewModel
            )
        }
    }
}

@Composable
fun AppNavigation(
    authViewModel: AuthViewModel,
    permissionViewModel: PermissionViewModel,
    appListViewModel: AppListViewModel
) {
    val navController = rememberNavController()
    val authState by authViewModel.authState.collectAsState()
    val permissionsGranted by permissionViewModel.permissionsGranted.collectAsState()

    // Determinar la pantalla inicial basada en los estados
    val startDestination = when {
        !authState.isAuthenticated -> Screen.Login.route
        !permissionsGranted -> Screen.Permission.route
        else -> Screen.AppList.route
    }

    // Efectos de navegación basados en los estados
    LaunchedEffect(authState.isAuthenticated, permissionsGranted) {
        when {
            !authState.isAuthenticated -> {
                navController.navigate(Screen.Login.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
            !permissionsGranted -> {
                navController.navigate(Screen.Permission.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            }
            else -> {
                navController.navigate(Screen.AppList.route) {
                    popUpTo(Screen.Permission.route) { inclusive = true }
                }
            }
        }
    }

    // Agregar animaciones de transición
    NavigationGraph(
        navController = navController,
        authViewModel = authViewModel,
        permissionViewModel = permissionViewModel,
        appListViewModel = appListViewModel,
        startDestination = startDestination
    )
}

// Opcional: Agregar transiciones de animación
object NavTransitions {
    val enterTransition = fadeIn(animationSpec = tween(300)) +
            slideInHorizontally(animationSpec = tween(300)) { it }

    val exitTransition = fadeOut(animationSpec = tween(300)) +
            slideOutHorizontally(animationSpec = tween(300)) { -it }

    val popEnterTransition = fadeIn(animationSpec = tween(300)) +
            slideInHorizontally(animationSpec = tween(300)) { -it }

    val popExitTransition = fadeOut(animationSpec = tween(300)) +
            slideOutHorizontally(animationSpec = tween(300)) { it }
}