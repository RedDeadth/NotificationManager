package com.dynamictecnologies.notificationmanager.presentation.core.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dynamictecnologies.notificationmanager.presentation.auth.screen.LoginScreen
import com.dynamictecnologies.notificationmanager.presentation.auth.screen.RegisterScreen
import com.dynamictecnologies.notificationmanager.presentation.home.screen.AppListScreen
import com.dynamictecnologies.notificationmanager.presentation.profile.screen.ProfileScreen
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
    devicePairingViewModel: com.dynamictecnologies.notificationmanager.viewmodel.DevicePairingViewModel,
    requestBluetoothPermissions: () -> Unit
) {
    val navController = rememberNavController()
    val authState by authViewModel.authState.collectAsState()

    // Mostrar splash mientras se inicializa la verificación de sesión
    if (authState.isInitializing) {
        Box(
            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    // Determinar la pantalla inicial basada en el estado de autenticación
    // Solo se ejecuta cuando isInitializing = false
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
                onShareClick = { /* Compartir removido */ }
            ) { paddingModifier ->
                // NavHost anidado para el contenido principal
                MainNavGraph(
                    navController = mainNavController,
                    parentNavController = navController,
                    authViewModel = authViewModel,
                    appListViewModel = appListViewModel,
                    userViewModel = userViewModel,
                    devicePairingViewModel = devicePairingViewModel,
                    requestBluetoothPermissions = requestBluetoothPermissions,
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
    devicePairingViewModel: com.dynamictecnologies.notificationmanager.viewmodel.DevicePairingViewModel,
    requestBluetoothPermissions: () -> Unit,
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
                onNavigateToProfile = { navController.navigate(AppRoutes.MainScreen.Profile.route) },
                devicePairingViewModel = devicePairingViewModel,
                requestBluetoothPermissions = requestBluetoothPermissions
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
                    appListViewModel.clearData()
                    
                    authViewModel.signOut()
                    parentNavController.navigate(AppRoutes.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
