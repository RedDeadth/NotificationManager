package com.dynamictecnologies.notificationmanager.navigation

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dynamictecnologies.notificationmanager.ui.screen.auth.LoginScreen
import com.dynamictecnologies.notificationmanager.ui.screen.auth.RegisterScreen
import com.dynamictecnologies.notificationmanager.ui.screen.home.AppListScreen
import com.dynamictecnologies.notificationmanager.ui.components.permission.PermissionScreen
import com.dynamictecnologies.notificationmanager.viewmodel.AuthViewModel
import com.dynamictecnologies.notificationmanager.viewmodel.PermissionViewModel
import com.dynamictecnologies.notificationmanager.viewmodel.AppListViewModel
import com.dynamictecnologies.notificationmanager.viewmodel.UserViewModel
import com.dynamictecnologies.notificationmanager.viewmodel.UsernameState

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
    userViewModel: UserViewModel,
    startDestination: String
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(route = Screen.Login.route) {
            LoginScreen(
                authViewModel = authViewModel,
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                }
            )
        }

        composable(route = Screen.Register.route) {
            RegisterScreen(
                authViewModel = authViewModel,
                onNavigateToLogin = {
                    navController.popBackStack()
                }
            )
        }

        composable(route = Screen.Permission.route) {
            PermissionScreen(
                permissionViewModel = permissionViewModel,
                appListViewModel = appListViewModel,
                userViewModel = userViewModel
            )
        }

        composable(route = Screen.AppList.route) {
            AppListScreen(
                viewModel = appListViewModel,
                userViewModel = userViewModel // Añadir userViewModel
            )
        }
    }
}

@Composable
fun AppNavigation(
    authViewModel: AuthViewModel,
    permissionViewModel: PermissionViewModel,
    appListViewModel: AppListViewModel,
    userViewModel: UserViewModel // Añadir este parámetro
) {
    val navController = rememberNavController()
    val authState by authViewModel.authState.collectAsState()
    val permissionsGranted by permissionViewModel.permissionsGranted.collectAsState()
    val usernameState by userViewModel.usernameState.collectAsState()

    // Determinar la pantalla inicial basada en los estados
    val startDestination = when {
        !authState.isAuthenticated -> Screen.Login.route
        !permissionsGranted -> Screen.Permission.route
        usernameState is UsernameState.Initial -> Screen.Login.route // Verificar si el usuario tiene username
        else -> Screen.AppList.route
    }

    // Efectos de navegación basados en los estados
    LaunchedEffect(authState.isAuthenticated, permissionsGranted, usernameState) {
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
            usernameState is UsernameState.Initial -> {
                // El usuario necesita registrar un username
                navController.navigate(Screen.Login.route) {
                    popUpTo(Screen.Permission.route) { inclusive = true }
                }
            }
            else -> {
                navController.navigate(Screen.AppList.route) {
                    popUpTo(Screen.Permission.route) { inclusive = true }
                }
            }
        }
    }

    NavigationGraph(
        navController = navController,
        authViewModel = authViewModel,
        permissionViewModel = permissionViewModel,
        appListViewModel = appListViewModel,
        userViewModel = userViewModel,
        startDestination = startDestination
    )
}