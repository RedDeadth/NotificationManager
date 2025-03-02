package com.dynamictecnologies.notificationmanager.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.dynamictecnologies.notificationmanager.data.db.NotificationDao
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.service.FirebaseService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class NotificationRepository(
    private val notificationDao: NotificationDao,
    private val firebaseService: FirebaseService,
    private val context: Context
) {
    private val TAG = "NotificationRepo"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        startPeriodicSync()
    }

    private fun startPeriodicSync() {
        scope.launch {
            while (isActive) {
                try {
                    if (isNetworkAvailable()) {
                        firebaseService.verifyConnection()
                        syncPendingNotifications()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error en sincronización periódica: ${e.message}")
                }
                delay(30_000) // Intentar cada 30 segundos
            }
        }
    }
    fun getNotifications(packageName: String): Flow<List<NotificationInfo>> {
        return notificationDao.getNotificationsForApp(packageName)
            .onStart {
                emit(notificationDao.getNotificationsForAppImmediate(packageName))

                if (isNetworkAvailable()) {
                    try {
                        val remoteNotifications = firebaseService.getNotifications(packageName)
                        processRemoteNotifications(remoteNotifications)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error en sincronización inicial: ${e.message}")
                    }
                }
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)
    }

    private suspend fun processRemoteNotifications(remoteNotifications: List<NotificationInfo>) {
        remoteNotifications.forEach { notification ->
            try {
                notificationDao.insertOrUpdateNotification(notification.copy(isSynced = true))
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando notificación remota: ${e.message}")
            }
        }
    }

    suspend fun insertNotification(notification: NotificationInfo) {
        try {
            val id = notificationDao.insertNotification(notification)
            Log.d(TAG, "Notificación guardada localmente con ID: $id")

            if (isNetworkAvailable()) {
                val updatedNotification = notification.copy(id = id)
                val success = firebaseService.syncNotification(updatedNotification)
                if (success) {
                    notificationDao.updateNotification(updatedNotification.copy(isSynced = true))
                    Log.d(TAG, "✓ Notificación sincronizada con Firebase")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error insertando notificación: ${e.message}")
        }
    }

    private suspend fun syncPendingNotifications() {
        try {
            val unsynced = notificationDao.getUnSyncedNotifications()
            if (unsynced.isNotEmpty()) {
                Log.d(TAG, "Sincronizando ${unsynced.size} notificaciones pendientes...")
                unsynced.forEach { notification ->
                    if (firebaseService.syncNotification(notification)) {
                        notificationDao.updateNotification(notification.copy(isSynced = true))
                        Log.d(TAG, "✓ Sincronizada notificación: ${notification.title}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en syncPendingNotifications: ${e.message}")
        }
    }

    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}