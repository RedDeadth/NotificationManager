package com.dynamictecnologies.notificationmanager.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "notifications")
data class NotificationInfo(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val title: String,
    val content: String,
    val timestamp: Date,
    val senderName: String? = null,
    val isGroupMessage: Boolean = false,
    val groupName: String? = null,
    val isRead: Boolean = false,
    val uniqueId: String = "$packageName:$title:$content:${timestamp.time}",
    var isSynced: Boolean = false
)