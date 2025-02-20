package com.dynamictecnologies.notificationmanager.data.model

import androidx.room.ColumnInfo

data class NotificationCount(
    @ColumnInfo(name = "packageName") val packageName: String,
    @ColumnInfo(name = "count") val count: Int
)