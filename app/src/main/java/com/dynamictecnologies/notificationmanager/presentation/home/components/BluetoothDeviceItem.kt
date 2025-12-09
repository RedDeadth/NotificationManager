package com.dynamictecnologies.notificationmanager.presentation.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dynamictecnologies.notificationmanager.data.bluetooth.BluetoothDeviceScanner

/**
 * Item de lista para dispositivo Bluetooth escaneado.
 * 
 * Muestra:
 * Card de dispositivo Bluetooth encontrado
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothDeviceCard(
    device: BluetoothDeviceScanner.ScannedDevice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icono y nombre
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = "Bluetooth",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                
                Column {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = device.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Indicador de señal
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.SignalCellularAlt,
                    contentDescription = "Signal strength",
                    tint = getSignalColor(device.rssi),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "${device.rssi} dBm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Determina color según fuerza de señal RSSI
 */
@Composable
private fun getSignalColor(rssi: Int): androidx.compose.ui.graphics.Color {
    return when {
        rssi >= -50 -> MaterialTheme.colorScheme.primary  // Excelente
        rssi >= -70 -> MaterialTheme.colorScheme.tertiary  // Buena
        rssi >= -85 -> MaterialTheme.colorScheme.secondary  // Regular
        else -> MaterialTheme.colorScheme.error  // Débil
    }
}
