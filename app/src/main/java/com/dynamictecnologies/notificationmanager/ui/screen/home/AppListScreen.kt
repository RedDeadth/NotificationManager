package com.dynamictecnologies.notificationmanager.ui.screen.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material3.*
import android.content.Intent
import androidx.compose.runtime.rememberCoroutineScope
import com.dynamictecnologies.notificationmanager.service.NotificationForegroundService
import kotlinx.coroutines.launch
import android.content.Context
import android.util.Log
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dynamictecnologies.notificationmanager.viewmodel.AppListViewModel
import com.dynamictecnologies.notificationmanager.viewmodel.DeviceViewModel
import com.dynamictecnologies.notificationmanager.viewmodel.UserViewModel
import com.dynamictecnologies.notificationmanager.ui.components.InitialSelectionCard
import com.dynamictecnologies.notificationmanager.ui.components.AppSelectionDialog
import com.dynamictecnologies.notificationmanager.ui.components.AppBottomBar
import com.dynamictecnologies.notificationmanager.ui.components.AppTopBar
import com.dynamictecnologies.notificationmanager.ui.components.NotificationHistoryCard
import com.dynamictecnologies.notificationmanager.ui.components.Screen
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.ui.components.DeviceSelectionDialog

@Composable
fun AppListScreen(
    viewModel: AppListViewModel,
    deviceViewModel: DeviceViewModel,
    userViewModel: UserViewModel,
    onLogout: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToShared: () -> Unit,
    onNavigateToSettings: () -> Unit = {}
) {
    val apps by viewModel.apps.collectAsState()
    val selectedApp by viewModel.selectedApp.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val showAppList by viewModel.showAppList.collectAsState()
    val notifications by viewModel.notifications.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Estados para el visualizador ESP32
    val devices by deviceViewModel.devices.collectAsState()
    val isSearching by deviceViewModel.isSearching.collectAsState()
    val showDeviceDialog by deviceViewModel.showDeviceDialog.collectAsState()
    val connectedDevice by deviceViewModel.connectedDevice.collectAsState()
    val scanCompleted by deviceViewModel.scanCompleted.collectAsState()
    val userProfile by userViewModel.userProfile.collectAsState()
    val userId = userProfile?.uid

    // Inicializar conexión MQTT cuando se carga la pantalla
    LaunchedEffect(Unit) {
        deviceViewModel.connectToMqtt()
    }
    
    // Enviar notificaciones al dispositivo conectado
    LaunchedEffect(notifications, connectedDevice) {
        if (connectedDevice != null && notifications.isNotEmpty()) {
            // Crear una copia para prevenir ConcurrentModificationException
            val notificationsCopy = notifications.toList()
            
            // Agrupar notificaciones y enviar cada una con un intervalo
            var delay = 0L
            for (notification in notificationsCopy) {
                // Enviar cada notificación con un ligero retraso para evitar conflictos de timestamp
                kotlinx.coroutines.delay(delay)
                deviceViewModel.sendNotification(notification)
                delay = 250 // 250ms de retraso entre notificaciones
            }
        }
    }

    // Verificar conexión cuando cambia el estado del usuario
    LaunchedEffect(userProfile) {
        userProfile?.uid?.let { uid ->
            deviceViewModel.setCurrentUserId(uid)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            selectedApp?.let { app ->
                // Diseño horizontal para ambas tarjetas al mismo nivel
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Tarjeta izquierda - App Seleccionada
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "App Seleccionada",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            app.icon?.let { icon ->
                                Image(
                                    bitmap = icon,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = app.name,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { viewModel.toggleAppList() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Cambiar")
                            }
                        }
                    }

                    // Tarjeta derecha - Visualizador
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Visualizador ESP32",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Icon(
                                imageVector = Icons.Default.ScreenShare,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = if (connectedDevice != null) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (connectedDevice != null) 
                                    "Conectado" 
                                else 
                                    "Sin conexión",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (connectedDevice != null)
                                    MaterialTheme.colorScheme.primary
                                else
                                    Color.Gray
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { 
                                    if (connectedDevice != null) {
                                        // Desconectar
                                        deviceViewModel.disconnectFromMqtt()
                                    } else {
                                        // Mostrar diálogo para conectar
                                        deviceViewModel.toggleDeviceDialog()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = if (connectedDevice != null) {
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                } else {
                                    ButtonDefaults.buttonColors()
                                }
                            ) {
                                Text(
                                    if (connectedDevice != null) "Desconectar" else "Conectar"
                                )
                            }
                        }
                    }
                }
                
                // Botón para reiniciar el servicio de notificaciones
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Servicio de notificaciones",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val serviceIntent = Intent(context, NotificationForegroundService::class.java).apply {
                                    action = NotificationForegroundService.ACTION_FORCE_RESET
                                }

                                try {
                                    context.startService(serviceIntent)
                                    Log.d("Button", "Intent con ACTION_FORCE_RESET enviado a NotificationForegroundService")
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Servicio de notificaciones reiniciado",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                } catch (e: Exception) {
                                    Log.e("Button", "Error al intentar enviar comando ACTION_FORCE_RESET al servicio: ${e.message}", e)
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Error al reiniciar servicio: ${e.message}",
                                            duration = SnackbarDuration.Long
                                        )
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text("Reiniciar servicio")
                        }
                    }
                }

                // Historial de notificaciones
                NotificationHistoryCard(
                    notifications = notifications,
                    modifier = Modifier.weight(1f)
                )
            } ?: run {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    InitialSelectionCard(
                        onSelectAppClick = { viewModel.toggleAppList() }
                    )
                }
            }
        }

        if (isLoading) {
            LoadingScreen()
        }

        if (showAppList) {
            AppSelectionDialog(
                apps = apps,
                onAppSelected = { app ->
                    viewModel.selectApp(app)
                    viewModel.toggleAppList()
                },
                onDismiss = { viewModel.toggleAppList() }
            )
        }

        if (showDeviceDialog) {
            DeviceSelectionDialog(
                isSearching = isSearching,
                devices = devices,
                scanCompleted = scanCompleted,
                onSearchDevices = {
                    userId?.let { uid -> deviceViewModel.searchDevices(uid) }
                },

                onDeviceSelected = { device ->
                    userId?.let { uid ->
                        deviceViewModel.connectToDevice(device.id, uid)
                        deviceViewModel.toggleDeviceDialog()
                    }
                },
                onDismiss = {
                    deviceViewModel.toggleDeviceDialog()
                    deviceViewModel.clearDevices()
                }
            )
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}