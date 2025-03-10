package com.dynamictecnologies.notificationmanager.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.*
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.data.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface NotificationDao {

    @Query("DELETE FROM notifications WHERE timestamp < :date")
    suspend fun deleteOldNotifications(date: Date)

    @Query("DELETE FROM notifications WHERE timestamp < :date AND isSynced = 1")
    suspend fun deleteOldSyncedNotifications(date: Date)

    @Query("SELECT * FROM notifications WHERE packageName = :packageName ORDER BY timestamp DESC")
    fun getNotificationsForApp(packageName: String): Flow<List<NotificationInfo>>

    @Query("SELECT * FROM notifications WHERE packageName = :packageName ORDER BY timestamp DESC")
    suspend fun getNotificationsForAppImmediate(packageName: String): List<NotificationInfo>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationInfo): Long

    @Update
    suspend fun updateNotification(notification: NotificationInfo)

    @Query("SELECT * FROM notifications WHERE isSynced = 0")
    suspend fun getUnSyncedNotifications(): List<NotificationInfo>

    @Query("SELECT * FROM notifications WHERE id = :id")
    suspend fun getNotificationById(id: Long): NotificationInfo?

    @Transaction
    suspend fun insertOrUpdateNotification(notification: NotificationInfo) {
        val existing = getNotificationById(notification.id)
        if (existing == null) {
            insertNotification(notification)
        } else {
            updateNotification(notification)
        }
    }
    @Query("UPDATE notifications SET syncStatus = :status, lastSyncAttempt = :timestamp WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: SyncStatus, timestamp: Long = System.currentTimeMillis())

    @Transaction
    suspend fun updateNotificationSyncResult(id: Long, success: Boolean) {
        val status = if (success) SyncStatus.SYNCED else SyncStatus.FAILED
        val timestamp = System.currentTimeMillis()
        updateSyncStatus(id, status, timestamp)
        if (success) {
            updateNotification(
                getNotificationById(id)?.copy(
                    isSynced = true,
                    syncTimestamp = timestamp
                ) ?: return
            )
        }
    }
    @Query("DELETE FROM notifications")
    suspend fun deleteAllNotifications()

    @Query("UPDATE notifications SET syncStatus = 'PENDING', isSynced = 0, syncTimestamp = NULL")
    suspend fun clearSyncStatus()
}