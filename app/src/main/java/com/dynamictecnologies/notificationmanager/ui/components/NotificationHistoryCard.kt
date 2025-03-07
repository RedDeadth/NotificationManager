package com.dynamictecnologies.notificationmanager.ui.components

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.data.model.SyncStatus
import com.dynamictecnologies.notificationmanager.ui.components.SyncStatusIndicator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotificationHistoryCard(
    notifications: List<NotificationInfo>,
    modifier: Modifier = Modifier
) {
    Log.d("NotificationHistoryCard", "Renderizando ${notifications.size} notificaciones")

    var expanded by remember { mutableStateOf(false) }
    val groupedNotifications = notifications.groupBy { it.syncStatus }

    // Tarjeta normal (contraída)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 150.dp) // Altura mínima para que sea similar a las tarjetas superiores
            .clickable { expanded = true }, // Al hacer clic, expandir la vista
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Historial de Notificaciones (${notifications.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (notifications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No hay notificaciones registradas",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Mostrar solo algunas notificaciones en la vista contraída
                Column {
                    notifications.take(3).forEach { notification ->
                        NotificationItem(notification)
                        if (notification != notifications.take(3).lastOrNull()) {
                            Divider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // Vista expandida como diálogo flotante
    if (expanded) {
        Dialog(
            onDismissRequest = { expanded = false },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.8f),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 1.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Historial de Notificaciones (${notifications.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        IconButton(onClick = { expanded = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar"
                            )
                        }
                    }

                    Divider(modifier = Modifier.padding(bottom = 1.dp))

                    if (notifications.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp)
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No hay notificaciones registradas",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f)
                        ) {
                            items(
                                items = notifications,
                                key = { "${it.packageName}_${it.timestamp.time}" }
                            ) { notification ->
                                NotificationItem(notification)
                                Divider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun NotificationItem(notification: NotificationInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Contenido principal (ahora sin el indicador de estado al inicio)
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // Encabezado solo con título (sin nombre de app)
            Text(
                text = notification.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Contenido de la notificación
            Text(
                text = notification.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Pie con timestamps
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Timestamp de la notificación
                Text(
                    text = formatDate(notification.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Timestamp de sincronización si existe
                notification.syncTimestamp?.let { syncTime ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatDate(Date(syncTime)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Mostrar mensaje de error si la sincronización falló
            if (notification.syncStatus == SyncStatus.FAILED) {
                Text(
                    text = "Error de sincronización",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Indicador de estado de sincronización (ahora a la derecha)
        SyncStatusIndicator(
            status = notification.syncStatus,
            modifier = Modifier.size(20.dp)
        )
    }
}


@Composable
private fun SyncStatusIndicator(status: SyncStatus, modifier: Modifier = Modifier) {
    val (icon, tint) = when (status) {
        SyncStatus.SYNCED -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
        SyncStatus.SYNCING -> Icons.Default.Sync to MaterialTheme.colorScheme.secondary
        SyncStatus.PENDING -> Icons.Default.Schedule to MaterialTheme.colorScheme.outline
        SyncStatus.FAILED -> Icons.Default.Error to MaterialTheme.colorScheme.error
    }

    Icon(
        imageVector = icon,
        contentDescription = "Estado de sincronización: ${status.name}",
        modifier = modifier,
        tint = tint
    )
}

private fun formatDate(date: Date): String {
    val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return formatter.format(date)
}