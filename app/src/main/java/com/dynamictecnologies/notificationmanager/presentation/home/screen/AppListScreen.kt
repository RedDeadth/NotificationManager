package com.dynamictecnologies.notificationmanager.presentation.home.screen

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
import com.dynamictecnologies.notificationmanager.viewmodel.DevicePairingViewModel
import com.dynamictecnologies.notificationmanager.viewmodel.UserViewModel
import com.dynamictecnologies.notificationmanager.presentation.home.components.InitialSelectionCard
import com.dynamictecnologies.notificationmanager.presentation.home.components.AppSelectionDialog
import com.dynamictecnologies.notificationmanager.presentation.home.components.NotificationHistoryCard
import com.dynamictecnologies.notificationmanager.presentation.home.components.DeviceSelectionDialog
import com.dynamictecnologies.notificationmanager.presentation.home.components.PairedDeviceCard
import com.dynamictecnologies.notificationmanager.presentation.home.components.TokenInputDialog
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo

@Composable
fun AppListScreen(
    viewModel: AppListViewModel,
    onNavigateToProfile: () -> Unit,
    devicePairingViewModel: com.dynamictecnologies.notificationmanager.viewmodel.DevicePairingViewModel,
    requestBluetoothPermissions: () -> Unit
) {
    val apps by viewModel.apps.collectAsState()
    val selectedApp by viewModel.selectedApp.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val showAppList by viewModel.showAppList.collectAsState()
    val notifications by viewModel.notifications.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Estados para Bluetooth Pairing
    val bluetoothDevices by devicePairingViewModel.bluetoothDevices.collectAsState()
    val isScanning by devicePairingViewModel.isScanning.collectAsState()
    val currentPairing by devicePairingViewModel.currentPairing.collectAsState()
    val showTokenDialog by devicePairingViewModel.showTokenDialog.collectAsState()
    val selectedDevice by devicePairingViewModel.selectedDevice.collectAsState()
    val pairingState by devicePairingViewModel.pairingState.collectAsState()
    
    // Control de diálogos
    var showBluetoothDialog by remember { mutableStateOf(false) }
    
    // Mostrar diálogo automáticamente cuando inicia escaneo
    LaunchedEffect(isScanning) {
        if (isScanning && !showBluetoothDialog) {
            showBluetoothDialog = true
        }
    }
    
    // Manejar resultados de pairing
    LaunchedEffect(pairingState) {
        when (pairingState) {
            is DevicePairingViewModel.PairingState.Success -> {
                snackbarHostState.showSnackbar(
                    message = "Dispositivo vinculado exitosamente",
                    duration = SnackbarDuration.Short
                )
                devicePairingViewModel.resetPairingState()
            }
            is DevicePairingViewModel.PairingState.Error -> {
                val errorMsg = (pairingState as DevicePairingViewModel.PairingState.Error).message
                snackbarHostState.showSnackbar(
                    message = "Error: $errorMsg",
                    duration = SnackbarDuration.Long
                )
            }
            else -> {}
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

                    // Tarjeta derecha - Visualizador ESP32 (Bluetooth)
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
                                tint = if (currentPairing != null) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (currentPairing != null) 
                                    currentPairing!!.bluetoothName 
                                else 
                                    "Sin conexión",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (currentPairing != null)
                                    MaterialTheme.colorScheme.primary
                                else
                                    Color.Gray
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { 
                                    if (currentPairing != null) {
                                        // Desemparejar
                                        devicePairingViewModel.unpairDevice()
                                    } else {
                                        // Verificar permisos y habilitar Bluetooth
                                        // El callback iniciará el escaneo automáticamente
                                        requestBluetoothPermissions()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = if (currentPairing != null) {
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                } else {
                                    ButtonDefaults.buttonColors()
                                }
                            ) {
                                Text(
                                    if (currentPairing != null) "Desemparejar" else "Conectar"
                                )
                            }
                        }
                    }
                }
                
                // Mostrar tarjeta de dispositivo emparejado si existe
                currentPairing?.let { pairing ->
                    PairedDeviceCard(
                        device = pairing,
                        onUnpair = {
                            devicePairingViewModel.unpairDevice()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    )
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

        // Diálogo de selección de dispositivo Bluetooth
        if (showBluetoothDialog) {
            DeviceSelectionDialog(
                isScanning = isScanning,
                devices = bluetoothDevices,
                scanCompleted = !isScanning && bluetoothDevices.isNotEmpty(),
                onSearchDevices = {
                    requestBluetoothPermissions()
                    // El escaneo se iniciará automáticamente si hay permisos
                },
                onDeviceSelected = { device ->
                    devicePairingViewModel.showTokenDialog(device)
                    showBluetoothDialog = false
                },
                onDismiss = {
                    showBluetoothDialog = false
                    devicePairingViewModel.stopBluetoothScan()
                }
            )
        }
        
        // Diálogo de ingreso de token
        if (showTokenDialog && selectedDevice != null) {
            TokenInputDialog(
                deviceName = selectedDevice!!.name,
                onTokenEntered = { token ->
                    devicePairingViewModel.pairDevice(token)
                },
                onDismiss = {
                    devicePairingViewModel.dismissTokenDialog()
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
