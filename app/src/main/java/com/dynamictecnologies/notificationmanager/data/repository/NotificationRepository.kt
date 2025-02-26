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
        initializeFirebase()
        startPeriodicTasks()
    }
    private fun initializeFirebase() {
        scope.launch {
            try {
                firebaseService.initialize()
            } catch (e: Exception) {
                Log.e(TAG, "Error inicializando Firebase: ${e.message}")
            }
        }
    }
    private fun startPeriodicTasks() {
        scope.launch {
            while (isActive) {
                try {
                    cleanOldNotifications()
                    if (isNetworkAvailable()) {
                        syncPendingNotifications()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error en tareas periódicas: ${e.message}")
                }
                delay(1 * 60 * 60 * 1000) // Ejecutar cada hora
            }
        }
    }


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
    private suspend fun syncPendingNotifications() {
        try {
            Log.d(TAG, "Iniciando sincronización de notificaciones pendientes")
            val unSyncedNotifications = notificationDao.getUnSyncedNotifications()
            Log.d(TAG, "Notificaciones pendientes encontradas: ${unSyncedNotifications.size}")

            unSyncedNotifications.forEach { notification ->
                if (firebaseService.syncNotification(notification)) {
                    notificationDao.updateNotification(notification.copy(isSynced = true))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sincronizando notificaciones pendientes: ${e.message}")
        }
    }

    private suspend fun cleanOldNotifications() {
        try {
            val oneWeekAgo = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -7)
            }.time

            Log.d(TAG, "Limpiando notificaciones antiguas (> 1 semana)")
            notificationDao.deleteOldSyncedNotifications(oneWeekAgo)
            Log.d(TAG, "✓ Limpieza completada")
        } catch (e: Exception) {
            Log.e(TAG, "Error limpiando notificaciones antiguas: ${e.message}")
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}