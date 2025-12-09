package com.dynamictecnologies.notificationmanager.presentation.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dynamictecnologies.notificationmanager.data.bluetooth.BluetoothDeviceScanner

@Composable
fun DeviceSelectionDialog(
    isScanning: Boolean,
    devices: List<BluetoothDeviceScanner.ScannedDevice>,
    scanCompleted: Boolean,
    onSearchDevices: () -> Unit,
    onDeviceSelected: (BluetoothDeviceScanner.ScannedDevice) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Conectar Dispositivo ESP32")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp)
            ) {
                if (isScanning) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Escaneando dispositivos Bluetooth...")
                    }
                } else if (devices.isEmpty() && scanCompleted) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "No se encontraron dispositivos ESP32",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Asegúrate de que:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "• El ESP32 esté encendido",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "• El Bluetooth del teléfono esté activado",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "• El dispositivo esté cerca",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (devices.isNotEmpty()) {
                    Text(
                        text = "Dispositivos encontrados: ${devices.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(devices) { device ->
                            BluetoothDeviceCard(
                                device = device,
                                onClick = { onDeviceSelected(device) }
                            )
                        }
                    }
                } else {
                    Text(
                        "Presiona 'Escanear Bluetooth' para buscar dispositivos ESP32 cercanos",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            if (!isScanning) {
                Button(onClick = onSearchDevices) {
                    Text("Escanear Bluetooth")
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
