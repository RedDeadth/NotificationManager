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
    userViewModel: UserViewModel
) {
    val permissionsGranted by permissionViewModel.permissionsGranted.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Observar el ciclo de vida para verificar permisos cuando la app vuelve a primer plano
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

    // Siempre mostrar AppListScreen como base
    AppListScreen(
        viewModel = appListViewModel,
        userViewModel = userViewModel )

    // Mostrar el diálogo de permisos si no están concedidos
    if (!permissionsGranted) {
        PermissionRequiredDialog(
            onConfirmClick = {
                permissionViewModel.openNotificationSettings()
            },
            onDismissClick = {
                // Opcional: permitir cerrar la app si el usuario no quiere dar permisos
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