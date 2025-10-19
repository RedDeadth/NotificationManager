package com.dynamictecnologies.notificationmanager.presentation.share.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun NotificationItem(
    notification: NotificationInfo,
    expanded: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Contenido principal de la notificación (izquierda)
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // Título de la notificación
            Text(
                text = notification.title.takeIf { it.isNotBlank() } ?: "Sin título",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Contenido de la notificación
            Text(
                text = notification.content.takeIf { it.isNotBlank() } ?: "Sin contenido",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Información de estado y timestamp (derecha)
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Timestamp
            val timestamp = notification.timestamp
            val validTimestamp = timestamp != null && timestamp.time > 631152000000L // 01/01/1990

            Text(
                text = if (validTimestamp) {
                    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(timestamp)
                } else {
                    "Fecha no disponible"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}