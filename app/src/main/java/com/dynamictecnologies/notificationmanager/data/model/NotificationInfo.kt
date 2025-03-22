package com.dynamictecnologies.notificationmanager.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "notifications")
data class NotificationInfo(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
 
    val appName: String,
    val title: String,
    val content: String,
    val timestamp: Date,
    val isSynced: Boolean = false,
    
    // Campos adicionales mantenidos para compatibilidad con código existente
    val packageName: String = "",
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val syncTimestamp: Long? = null,
    val lastSyncAttempt: Long? = null
)

enum class SyncStatus {
    PENDING,
    SYNCING,
    SYNCED,
    FAILED
}