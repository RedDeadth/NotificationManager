package com.dynamictecnologies.notificationmanager.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications WHERE packageName = :packageName ORDER BY timestamp DESC")
    fun getNotificationsForApp(packageName: String): Flow<List<NotificationInfo>>

    @Insert
    suspend fun insertNotification(notification: NotificationInfo)
}