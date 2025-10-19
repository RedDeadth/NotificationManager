package com.dynamictecnologies.notificationmanager.presentation.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dynamictecnologies.notificationmanager.data.model.DeviceInfo

@Composable
fun DeviceSelectionDialog(
    isSearching: Boolean,
    devices: List<DeviceInfo>,
    scanCompleted: Boolean,
    onSearchDevices: () -> Unit,
    onDeviceSelected: (DeviceInfo) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Conectar Dispositivo ESP32")
        },
        text = {
            Column {
                if (isSearching) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Buscando dispositivos...")
                    }
                } else if (devices.isEmpty() && scanCompleted) {
                    Text("No se encontraron dispositivos")
                } else if (devices.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        items(devices) { device ->
                            DeviceListItem(
                                device = device,
                                onClick = { onDeviceSelected(device) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isSearching) {
                Button(onClick = onSearchDevices) {
                    Text("Buscar Dispositivos")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun DeviceListItem(
    device: DeviceInfo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyLarge
                )
                
                Text(
                    text = "ID: ${device.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Button(onClick = onClick) {
                Text("Conectar")
            }
        }
    }
}
