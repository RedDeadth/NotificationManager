package com.dynamictecnologies.notificationmanager.presentation.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dynamictecnologies.notificationmanager.domain.entities.DevicePairing
import java.text.SimpleDateFormat
import java.util.*

/**
 * Card que muestra el dispositivo ESP32 actualmente vinculado.
 * 
 * Muestra:
 * - Nombre Bluetooth
 * - Token y topic MQTT
 * - Fecha de vinculación
 * - Botón de desvinculación
 * 
 * Principios aplicados:
 * - SRP: Solo visualización de pairing activo
 * - Composable puro sin estado
 */
@Composable
fun PairedDeviceCard(
    device: DevicePairing,
    onUnpair: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DeviceHub,
                        contentDescription = "Dispositivo vinculado",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Dispositivo Vinculado",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                IconButton(onClick = onUnpair) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Desvincular",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            Divider()
            
            // Información del dispositivo
            InfoRow(label = "Nombre", value = device.bluetoothName)
            InfoRow(label = "Dirección", value = device.bluetoothAddress)
            InfoRow(label = "Token", value = device.token)
            InfoRow(label = "Topic MQTT", value = device.mqttTopic)
            InfoRow(
                label = "Vinculado", 
                value = dateFormat.format(Date(device.pairedAt))
            )
            
            // Nota
            Text(
                text = "📱 Las notificaciones se envían automáticamente a este dispositivo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
