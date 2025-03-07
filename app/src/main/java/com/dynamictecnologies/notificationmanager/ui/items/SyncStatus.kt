package com.dynamictecnologies.notificationmanager.ui.items

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dynamictecnologies.notificationmanager.data.model.SyncStatus

@Composable
fun SyncStatusIndicator(
    status: SyncStatus,
    modifier: Modifier = Modifier
) {
    val (icon, tint) = when (status) {
        SyncStatus.SYNCED -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
        SyncStatus.SYNCING -> Icons.Default.Sync to MaterialTheme.colorScheme.secondary
        SyncStatus.FAILED -> Icons.Default.Error to MaterialTheme.colorScheme.error
        SyncStatus.PENDING -> Icons.Default.Schedule to MaterialTheme.colorScheme.outline
    }

    Icon(
        imageVector = icon,
        contentDescription = status.name,
        modifier = modifier.size(16.dp),
        tint = tint
    )
}