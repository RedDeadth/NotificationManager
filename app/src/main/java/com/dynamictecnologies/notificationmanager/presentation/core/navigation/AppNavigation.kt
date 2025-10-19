package com.dynamictecnologies.notificationmanager.presentation.core.navigation

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dynamictecnologies.notificationmanager.presentation.auth.screen.LoginScreen
import com.dynamictecnologies.notificationmanager.presentation.auth.screen.RegisterScreen
import com.dynamictecnologies.notificationmanager.presentation.home.screen.AppListScreen
import com.dynamictecnologies.notificationmanager.presentation.profile.screen.ProfileScreen
import com.dynamictecnologies.notificationmanager.presentation.share.screen.ShareScreen
import com.dynamictecnologies.notificationmanager.presentation.core.components.AppScaffold
import com.dynamictecnologies.notificationmanager.presentation.core.navigation.NavigationAnimations
import com.dynamictecnologies.notificationmanager.viewmodel.*

/**
 * Navegación simplificada que elimina la complejidad innecesaria.
 * Reemplaza NavGraph.kt con una estructura más limpia.
 */
@Composable
fun AppNavigation(
    authViewModel: AuthViewModel,
    appListViewModel: AppListViewModel,
    userViewModel: UserViewModel,
    shareViewModel: ShareViewModel,
    deviceViewModel: DeviceViewModel
) {
    val navController = rememberNavController()
    val authState by authViewModel.authState.collectAsState()

    // Determinar la pantalla inicial basada en el estado de autenticación
    val startDestination = when {
        !authState.isAuthenticated -> AppRoutes.Login.route
        else -> AppRoutes.Main.route
    }

    // Efectos de navegación basados en cambios de estado
    LaunchedEffect(authState) {
        when {
            // Si el usuario no está autenticado, navegar al login
            !authState.isAuthenticated -> {
                if (authState.error != null) {
                    // Manejar error de autenticación si es necesario
                } else {
                    navController.navigate(AppRoutes.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            // Si está autenticado pero loading, mostrar loading
            authState.isLoading -> {
                // Opcionalmente mostrar un indicador de carga
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Rutas de autenticación
        composable(
            route = AppRoutes.Login.route,
            enterTransition = { NavigationAnimations.enterTransition() },
            exitTransition = { NavigationAnimations.exitTransition() },
            popEnterTransition = { NavigationAnimations.popEnterTransition() },
            popExitTransition = { NavigationAnimations.popExitTransition() }
        ) {
            LoginScreen(
                authViewModel = authViewModel,
                onNavigateToRegister = {
                    navController.navigate(AppRoutes.Register.route)
                },
                onLoginSuccess = {
                    navController.navigate(AppRoutes.Main.route) {
                        popUpTo(AppRoutes.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = AppRoutes.Register.route,
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

        // Main flow - Usando AppScaffold unificado
        composable(
            route = AppRoutes.Main.route,
            enterTransition = { NavigationAnimations.enterTransition() },
            exitTransition = { NavigationAnimations.exitTransition() },
            popEnterTransition = { NavigationAnimations.popEnterTransition() },
            popExitTransition = { NavigationAnimations.popExitTransition() }
        ) {
            val mainNavController = rememberNavController()
            
            AppScaffold(
                navController = mainNavController,
                onSettingsClick = { /* TODO: Implementar configuración */ },
                onShareClick = { /* TODO: Implementar compartir */ }
            ) { paddingModifier ->
                // NavHost anidado para el contenido principal
                MainNavGraph(
                    navController = mainNavController,
                    parentNavController = navController,
                    authViewModel = authViewModel,
                    appListViewModel = appListViewModel,
                    userViewModel = userViewModel,
                    shareViewModel = shareViewModel,
                    deviceViewModel = deviceViewModel,
                    modifier = paddingModifier
                )
            }
        }
    }
}

@Composable
fun MainNavGraph(
    navController: NavHostController,
    parentNavController: NavHostController,
    authViewModel: AuthViewModel,
    appListViewModel: AppListViewModel,
    userViewModel: UserViewModel,
    shareViewModel: ShareViewModel,
    deviceViewModel: DeviceViewModel,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
) {
    NavHost(
        navController = navController,
        startDestination = AppRoutes.MainScreen.Home.route,
        modifier = modifier
    ) {
        // Pantalla de lista de apps
        composable(
            route = AppRoutes.MainScreen.Home.route,
            enterTransition = { NavigationAnimations.enterTransition() },
            exitTransition = { NavigationAnimations.exitTransition() },
            popEnterTransition = { NavigationAnimations.popEnterTransition() },
            popExitTransition = { NavigationAnimations.popExitTransition() }
        ) {
            AppListScreen(
                viewModel = appListViewModel,
                userViewModel = userViewModel,
                deviceViewModel = deviceViewModel,
                onLogout = {
                    // Limpiar todos los datos antes de cerrar sesión
                    userViewModel.clearData()
                    shareViewModel.clearData()
                    appListViewModel.clearData()
                    
                    authViewModel.signOut()
                    parentNavController.navigate(AppRoutes.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        
        // Pantalla de perfil
        composable(
            route = AppRoutes.MainScreen.Profile.route,
            enterTransition = { NavigationAnimations.enterTransition() },
            exitTransition = { NavigationAnimations.exitTransition() },
            popEnterTransition = { NavigationAnimations.popEnterTransition() },
            popExitTransition = { NavigationAnimations.popExitTransition() }
        ) {
            ProfileScreen(
                userViewModel = userViewModel,
                onLogout = {
                    // Limpiar todos los datos antes de cerrar sesión
                    userViewModel.clearData()
                    shareViewModel.clearData()
                    appListViewModel.clearData()
                    
                    authViewModel.signOut()
                    parentNavController.navigate(AppRoutes.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        
        // Pantalla de compartir
        composable(
            route = AppRoutes.MainScreen.Share.route,
            enterTransition = { NavigationAnimations.enterTransition() },
            exitTransition = { NavigationAnimations.exitTransition() },
            popEnterTransition = { NavigationAnimations.popEnterTransition() },
            popExitTransition = { NavigationAnimations.popExitTransition() }
        ) {
            ShareScreen(
                shareViewModel = shareViewModel
            )
        }
    }
}
