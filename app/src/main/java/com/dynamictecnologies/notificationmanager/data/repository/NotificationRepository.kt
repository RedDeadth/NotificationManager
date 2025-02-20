package com.dynamictecnologies.notificationmanager.data.repository

import android.util.Log
import com.dynamictecnologies.notificationmanager.data.db.NotificationDao
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class NotificationRepository(
    private val notificationDao: NotificationDao
) {
    private val TAG = "NotificationRepo"

    fun getNotificationsForApp(packageName: String): Flow<List<NotificationInfo>> {
        Log.d(TAG, "Solicitando notificaciones para package: $packageName")
        return notificationDao.getNotificationsForApp(packageName)
            .onEach { notifications ->
                Log.d(TAG, "Notificaciones obtenidas: ${notifications.size}")
                notifications.forEach { notification ->
                    Log.d(TAG, "- ${notification.timestamp}: ${notification.title}")
                }
            }
            .catch { e ->
                Log.e(TAG, "Error obteniendo notificaciones: ${e.message}")
                emit(emptyList())
            }
            .flowOn(Dispatchers.IO)
    }

    suspend fun insertNotification(notification: NotificationInfo) {
        try {
            Log.d(TAG, "Insertando notificación: ${notification.title}")
            notificationDao.insertNotification(notification)
            Log.d(TAG, "✓ Notificación insertada correctamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error insertando notificación: ${e.message}")
            throw e
        }
    }
}