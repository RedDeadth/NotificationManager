package com.dynamictecnologies.notificationmanager.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

enum class SyncStatus {
    PENDING,
    SYNCING,
    SYNCED,
    FAILED
}

@Entity(tableName = "notifications")
data class NotificationInfo(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val title: String,
    val content: String,
    val timestamp: Date,
    val isSynced: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val syncTimestamp: Long? = null,
    val lastSyncAttempt: Long? = null
)