package com.dynamictecnologies.notificationmanager.navigation

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dynamictecnologies.notificationmanager.ui.screen.auth.LoginScreen
import com.dynamictecnologies.notificationmanager.ui.screen.auth.RegisterScreen
import com.dynamictecnologies.notificationmanager.ui.screen.home.AppListScreen
import com.dynamictecnologies.notificationmanager.ui.components.PermissionScreen
import com.dynamictecnologies.notificationmanager.viewmodel.AuthViewModel
import com.dynamictecnologies.notificationmanager.viewmodel.PermissionViewModel
import com.dynamictecnologies.notificationmanager.viewmodel.AppListViewModel
import com.dynamictecnologies.notificationmanager.viewmodel.UserViewModel
import androidx.compose.ui.Modifier
import com.dynamictecnologies.notificationmanager.ui.screen.home.ProfileScreen
import com.dynamictecnologies.notificationmanager.ui.screen.home.ShareScreen
import com.dynamictecnologies.notificationmanager.viewmodel.ShareViewModel
import com.dynamictecnologies.notificationmanager.navigation.NavigationAnimations


sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object Permission : Screen("permission")
    object AppList : Screen("app_list")
    object Profile : Screen("profile")
    object Share : Screen("share")
    object Settings : Screen("settings")
}

@Composable
fun NavigationGraph(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    permissionViewModel: PermissionViewModel,
    appListViewModel: AppListViewModel,
    userViewModel: UserViewModel,
    shareViewModel: ShareViewModel,
    startDestination: String
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(
            route = Screen.Login.route,
            enterTransition = { NavigationAnimations.enterTransition() },
            exitTransition = { NavigationAnimations.exitTransition() },
            popEnterTransition = { NavigationAnimations.popEnterTransition() },
            popExitTransition = { NavigationAnimations.popExitTransition() }
        ) {
            LoginScreen(
                authViewModel = authViewModel,
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                },
                onLoginSuccess = {
                    navController.navigate(Screen.Permission.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.Register.route,
            enterTransition = { NavigationAnimations.enterTransition() },
            exitTransition = { NavigationAnimations.exitTransition() },
            popEnterTransition = { NavigationAnimations.popEnterTransition() },
            popExitTransition = { NavigationAnimations.popExitTransition() }
        ) {
            RegisterScreen(
                authViewModel = authViewModel,
                onNavigateToLogin = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.Permission.route,
            enterTransition = { NavigationAnimations.enterTransition() },
            exitTransition = { NavigationAnimations.exitTransition() },
            popEnterTransition = { NavigationAnimations.popEnterTransition() },
            popExitTransition = { NavigationAnimations.popExitTransition() }
        ) {
            PermissionScreen(
                permissionViewModel = permissionViewModel,
                appListViewModel = appListViewModel,
                userViewModel = userViewModel,
                shareViewModel = shareViewModel,
                onPermissionsGranted = {
                    navController.navigate(Screen.AppList.route) {
                        popUpTo(Screen.Permission.route) { inclusive = true }
                    }
                },
                onLogout = {
                    // Limpiar todos los datos antes de cerrar sesión
                    userViewModel.clearData()
                    shareViewModel.clearData()
                    appListViewModel.clearData() // Asumiendo que existe esta función
                    
                    authViewModel.signOut()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToProfile = {  // Añadir este parámetro
                    navController.navigate(Screen.Profile.route)
                },
                onNavigateToShared = {
                    navController.navigate(Screen.Share.route)
                }
            )
        }

        composable(
            route = Screen.AppList.route,
            enterTransition = { NavigationAnimations.enterTransition() },
            exitTransition = { NavigationAnimations.exitTransition() },
            popEnterTransition = { NavigationAnimations.popEnterTransition() },
            popExitTransition = { NavigationAnimations.popExitTransition() }
        ) {
            AppListScreen(
                viewModel = appListViewModel,
                userViewModel = userViewModel,
                onLogout = {
                    // Limpiar todos los datos antes de cerrar sesión
                    userViewModel.clearData()
                    shareViewModel.clearData()
                    appListViewModel.clearData() // Asumiendo que existe esta función
                    
                    authViewModel.signOut()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToProfile = {
                    navController.navigate(Screen.Profile.route)
                },
                onNavigateToShared = {
                    navController.navigate(Screen.Share.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
        composable(
            route = Screen.Profile.route,
            enterTransition = { NavigationAnimations.enterTransition() },
            exitTransition = { NavigationAnimations.exitTransition() },
            popEnterTransition = { NavigationAnimations.popEnterTransition() },
            popExitTransition = { NavigationAnimations.popExitTransition() }
        ) {
            val userProfile by userViewModel.userProfile.collectAsState()
            val errorMessage by userViewModel.errorState.collectAsState()
            val isLoading by userViewModel.isLoading.collectAsState()

            ProfileScreen(
                userInfo = userProfile,
                errorMessage = errorMessage,
                isLoading = isLoading,
                onCreateProfile = { username ->
                    userViewModel.registerUsername(username)
                },
                onRefresh = {
                    userViewModel.refreshProfile()
                },
                onErrorDismiss = {
                    userViewModel.clearError()
                },
                onLogout = {
                    // Limpiar todos los datos antes de cerrar sesión
                    userViewModel.clearData()
                    shareViewModel.clearData()
                    appListViewModel.clearData() // Asumiendo que existe esta función
                    
                    authViewModel.signOut()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToShared = {
                    navController.navigate(Screen.Share.route)
                },
                onNavigateToHome = {
                    navController.navigate(Screen.AppList.route)
                }
            )
        }
        composable(
            route = Screen.Share.route,
            enterTransition = { NavigationAnimations.enterTransition() },
            exitTransition = { NavigationAnimations.exitTransition() },
            popEnterTransition = { NavigationAnimations.popEnterTransition() },
            popExitTransition = { NavigationAnimations.popExitTransition() },
        ) {
            ShareScreen(
                shareViewModel = shareViewModel,
                onNavigateToProfile = {
                    navController.navigate(Screen.Profile.route)
                },
                onNavigateToHome = {
                    navController.navigate(Screen.AppList.route)
                }
            )
        }

    }
}

@Composable
fun AppNavigation(
    authViewModel: AuthViewModel,
    permissionViewModel: PermissionViewModel,
    appListViewModel: AppListViewModel,
    userViewModel: UserViewModel,
    shareViewModel: ShareViewModel
) {
    val navController = rememberNavController()
    val authState by authViewModel.authState.collectAsState()
    val permissionsGranted by permissionViewModel.permissionsGranted.collectAsState()

    // Determinar la pantalla inicial basada en el estado de autenticación
    val startDestination = when {
        !authState.isAuthenticated -> Screen.Login.route
        !permissionsGranted -> Screen.Permission.route
        else -> Screen.AppList.route
    }

    // Efectos de navegación basados en cambios de estado
    LaunchedEffect(authState) {
        when {
            // Si el usuario no está autenticado, navegar al login
            !authState.isAuthenticated -> {
                if (authState.error != null) {
                    // Manejar error de autenticación si es necesario
                } else {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            // Si está autenticado pero loading, mostrar loading
            authState.isLoading -> {
                // Opcionalmente mostrar un indicador de carga
            }
            // Si está autenticado y no está loading, verificar permisos
            else -> {
                if (!permissionsGranted) {
                    navController.navigate(Screen.Permission.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            }
        }
    }

    // Efecto separado para manejar los permisos
    LaunchedEffect(permissionsGranted) {
        if (authState.isAuthenticated && permissionsGranted) {
            navController.navigate(Screen.AppList.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavigationGraph(
        navController = navController,
        authViewModel = authViewModel,
        permissionViewModel = permissionViewModel,
        appListViewModel = appListViewModel,
        userViewModel = userViewModel,
        startDestination = startDestination,
        shareViewModel =  shareViewModel
    )
}