package com.dynamictecnologies.notificationmanager.navigation

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dynamictecnologies.notificationmanager.ui.screen.auth.LoginScreen
import com.dynamictecnologies.notificationmanager.ui.screen.auth.RegisterScreen
import com.dynamictecnologies.notificationmanager.ui.screen.home.AppListScreen
import com.dynamictecnologies.notificationmanager.ui.components.PermissionScreen
import com.dynamictecnologies.notificationmanager.viewmodel.*
import androidx.compose.ui.Modifier
import com.dynamictecnologies.notificationmanager.ui.screen.home.ProfileScreen
import com.dynamictecnologies.notificationmanager.ui.screen.home.ShareScreen
import com.dynamictecnologies.notificationmanager.navigation.NavigationAnimations
import androidx.navigation.navigation
import com.dynamictecnologies.notificationmanager.data.model.UserInfo
import com.dynamictecnologies.notificationmanager.ui.screen.MainScreen

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object Permission : Screen("permission")
    object Main : Screen("main") // Nueva ruta raíz para el flujo principal
    // Las siguientes rutas se mantienen por compatibilidad, pero ahora están dentro de MainRoutes
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
    deviceViewModel: DeviceViewModel,
    startDestination: String
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Rutas de autenticación
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

        // Pantalla de permisos
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
                deviceViewModel = deviceViewModel,
                onPermissionsGranted = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Permission.route) { inclusive = true }
                    }
                },
                onLogout = {
                    // Limpiar todos los datos antes de cerrar sesión
                    userViewModel.clearData()
                    shareViewModel.clearData()
                    appListViewModel.clearData()
                    
                    authViewModel.signOut()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToProfile = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Permission.route) { inclusive = true }
                    }
                },
                onNavigateToShared = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Permission.route) { inclusive = true }
                    }
                }
            )
        }

        // Main flow - Usando un Scaffold compartido
        composable(
            route = Screen.Main.route,
            enterTransition = { NavigationAnimations.enterTransition() },
            exitTransition = { NavigationAnimations.exitTransition() },
            popEnterTransition = { NavigationAnimations.popEnterTransition() },
            popExitTransition = { NavigationAnimations.popExitTransition() }
        ) {
            val mainNavController = rememberNavController()
            
            MainScreen(
                navController = mainNavController
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
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = MainRoutes.AppList.route,
        modifier = modifier
    ) {
        // Pantalla de lista de apps
        composable(
            route = MainRoutes.AppList.route,
            enterTransition = { NavigationAnimations.enterTransition() },
            exitTransition = { NavigationAnimations.exitTransition() },
            popEnterTransition = { NavigationAnimations.popEnterTransition() },
            popExitTransition = { NavigationAnimations.popExitTransition() }
        ) {
            AppListContent(
                viewModel = appListViewModel,
                userViewModel = userViewModel,
                deviceViewModel = deviceViewModel,
                onLogout = {
                    // Limpiar todos los datos antes de cerrar sesión
                    userViewModel.clearData()
                    shareViewModel.clearData()
                    appListViewModel.clearData()
                    
                    authViewModel.signOut()
                    parentNavController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        
        // Pantalla de perfil
        composable(
            route = MainRoutes.Profile.route,
            enterTransition = { NavigationAnimations.enterTransition() },
            exitTransition = { NavigationAnimations.exitTransition() },
            popEnterTransition = { NavigationAnimations.popEnterTransition() },
            popExitTransition = { NavigationAnimations.popExitTransition() }
        ) {
            val userProfile by userViewModel.userProfile.collectAsState()
            val errorMessage by userViewModel.errorState.collectAsState()
            val isLoading by userViewModel.isLoading.collectAsState()

            ProfileContent(
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
                    appListViewModel.clearData()
                    
                    authViewModel.signOut()
                    parentNavController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        
        // Pantalla de compartir
        composable(
            route = MainRoutes.Share.route,
            enterTransition = { NavigationAnimations.enterTransition() },
            exitTransition = { NavigationAnimations.exitTransition() },
            popEnterTransition = { NavigationAnimations.popEnterTransition() },
            popExitTransition = { NavigationAnimations.popExitTransition() }
        ) {
            ShareContent(
                shareViewModel = shareViewModel
            )
        }
    }
}

@Composable
fun AppListContent(
    viewModel: AppListViewModel,
    userViewModel: UserViewModel,
    deviceViewModel: DeviceViewModel,
    onLogout: () -> Unit
) {
    AppListScreen(
        viewModel = viewModel,
        userViewModel = userViewModel,
        deviceViewModel = deviceViewModel,
        onLogout = onLogout,
        onNavigateToProfile = { /* Manejado por BottomBar */ },
        onNavigateToShared = { /* Manejado por BottomBar */ },
        onNavigateToSettings = { /* No implementado aún */ }
    )
}

@Composable
fun ProfileContent(
    userInfo: UserInfo?,
    errorMessage: String?,
    isLoading: Boolean,
    onCreateProfile: (String) -> Unit,
    onRefresh: () -> Unit,
    onErrorDismiss: () -> Unit,
    onLogout: () -> Unit
) {
    ProfileScreen(
        userInfo = userInfo,
        errorMessage = errorMessage,
        isLoading = isLoading,
        onCreateProfile = onCreateProfile,
        onRefresh = onRefresh,
        onErrorDismiss = onErrorDismiss,
        onLogout = onLogout,
        onNavigateBack = { /* Ya no es necesario */ },
        onNavigateToShared = { /* Manejado por BottomBar */ },
        onNavigateToHome = { /* Manejado por BottomBar */ }
    )
}

@Composable
fun ShareContent(
    shareViewModel: ShareViewModel
) {
    ShareScreen(
        shareViewModel = shareViewModel,
        onNavigateToProfile = { /* Manejado por BottomBar */ },
        onNavigateToHome = { /* Manejado por BottomBar */ }
    )
}

@Composable
fun AppNavigation(
    authViewModel: AuthViewModel,
    permissionViewModel: PermissionViewModel,
    appListViewModel: AppListViewModel,
    userViewModel: UserViewModel,
    shareViewModel: ShareViewModel,
    deviceViewModel: DeviceViewModel
) {
    val navController = rememberNavController()
    val authState by authViewModel.authState.collectAsState()
    val permissionsGranted by permissionViewModel.permissionsGranted.collectAsState()

    // Determinar la pantalla inicial basada en el estado de autenticación
    val startDestination = when {
        !authState.isAuthenticated -> Screen.Login.route
        !permissionsGranted -> Screen.Permission.route
        else -> Screen.Main.route // Ahora navegamos al contenedor principal
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
            navController.navigate(Screen.Main.route) {
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
        shareViewModel = shareViewModel,
        deviceViewModel = deviceViewModel,
        startDestination = startDestination
    )
}