package com.dynamictecnologies.notificationmanager.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.dynamictecnologies.notificationmanager.viewmodel.AppListViewModel
import com.dynamictecnologies.notificationmanager.viewmodel.PermissionViewModel
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dynamictecnologies.notificationmanager.ui.screen.home.AppListScreen
import com.dynamictecnologies.notificationmanager.viewmodel.UserViewModel

@Composable
fun PermissionScreen(
    permissionViewModel: PermissionViewModel,
    appListViewModel: AppListViewModel,
    userViewModel: UserViewModel,
    onPermissionsGranted: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToShared: () -> Unit
) {
    val permissionsGranted by permissionViewModel.permissionsGranted.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionViewModel.checkPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Verificar si los permisos están concedidos
    LaunchedEffect(permissionsGranted) {
        if (permissionsGranted) {
            onPermissionsGranted()
        }
    }

    // Mostrar AppListScreen como base
    AppListScreen(
        viewModel = appListViewModel,
        userViewModel = userViewModel,
        onLogout = onLogout,
        onNavigateToProfile = onNavigateToProfile,
        onNavigateToShared = onNavigateToShared
    )

    // Mostrar el diálogo de permisos si no están concedidos
    if (!permissionsGranted) {
        PermissionRequiredDialog(
            onConfirmClick = {
                permissionViewModel.openNotificationSettings()
            },
            onDismissClick = {
                permissionViewModel.closeApp()
            }
        )
    }
}
@Composable
private fun PermissionRequiredDialog(
    onConfirmClick: () -> Unit,
    onDismissClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* No permitir cerrar con back o tap fuera */ },
        title = {
            Text(
                text = "Permisos necesarios",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = "Para funcionar correctamente, la aplicación necesita acceder a tus notificaciones. " +
                        "Sin este permiso, la aplicación no podrá funcionar.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirmClick
            ) {
                Text("Configurar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissClick
            ) {
                Text("Salir")
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    )
}