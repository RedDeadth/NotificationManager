package com.dynamictecnologies.notificationmanager.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.*
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications WHERE packageName = :packageName ORDER BY timestamp DESC")
    fun getNotificationsForApp(packageName: String): Flow<List<NotificationInfo>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationInfo): Long

    @Query("DELETE FROM notifications WHERE timestamp < :date")
    suspend fun deleteOldNotifications(date: Date)

    @Query("SELECT * FROM notifications WHERE isSynced = 0")
    suspend fun getUnSyncedNotifications(): List<NotificationInfo>

    @Update
    suspend fun updateNotification(notification: NotificationInfo)

    @Query("DELETE FROM notifications WHERE timestamp < :date AND isSynced = 1")
    suspend fun deleteOldSyncedNotifications(date: Date)
}