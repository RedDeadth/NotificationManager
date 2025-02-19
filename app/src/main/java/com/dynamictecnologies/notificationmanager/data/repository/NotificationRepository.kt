package com.dynamictecnologies.notificationmanager.data.repository

import com.dynamictecnologies.notificationmanager.data.db.NotificationDao
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import kotlinx.coroutines.flow.Flow

// Removemos cualquier anotación de inyección de dependencias
class NotificationRepository(
    private val notificationDao: NotificationDao
) {
    fun getNotificationsForApp(packageName: String): Flow<List<NotificationInfo>> {
        return notificationDao.getNotificationsForApp(packageName)
    }

    suspend fun insertNotification(notification: NotificationInfo) {
        notificationDao.insertNotification(notification)
    }
}