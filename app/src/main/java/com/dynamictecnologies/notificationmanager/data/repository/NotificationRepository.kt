package com.dynamictecnologies.notificationmanager.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.dynamictecnologies.notificationmanager.data.db.NotificationDao
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.data.model.SyncStatus
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

    // Número máximo de notificaciones a mantener por app
    private val MAX_NOTIFICATIONS_PER_APP = 50

    private val prefs = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)

    init {
        startPeriodicSync()
        startPeriodicCleanup()
    }

    private fun isAppAllowedForSync(packageName: String): Boolean {
        val allowedApp = prefs.getString("last_selected_app", null)
        return allowedApp == packageName
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

    /**
     * Inicia limpieza periódica de notificaciones antiguas
     */
    private fun startPeriodicCleanup() {
        scope.launch {
            while (isActive) {
                try {
                    val packageName = prefs.getString("last_selected_app", null)
                    packageName?.let { 
                        cleanupOldNotifications(it)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error en limpieza periódica: ${e.message}", e)
                }
                delay(6 * 60 * 60 * 1000) // Ejecutar cada 6 horas
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
                // Marcar las notificaciones remotas como SYNCED
                notificationDao.insertOrUpdateNotification(notification.copy(
                    isSynced = true,
                    syncStatus = SyncStatus.SYNCED,
                    syncTimestamp = System.currentTimeMillis()
                ))
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando notificación remota: ${e.message}")
            }
        }
    }

    suspend fun insertNotification(notification: NotificationInfo) {
        try {
            if (!isAppAllowedForSync(notification.packageName)) {
                Log.d(TAG, "Notificación ignorada: ${notification.packageName} no es la app seleccionada")
                return
            }

            val notificationWithStatus = notification.copy(syncStatus = SyncStatus.PENDING)
            val id = notificationDao.insertNotification(notificationWithStatus)
            Log.d(TAG, "Notificación guardada localmente con ID: $id")
            
            // Verificar si necesitamos limpiar notificaciones antiguas
            scope.launch(Dispatchers.IO) {
                try {
                    // Verificar cuántas notificaciones tenemos para esta app
                    val count = notificationDao.getNotificationCountForApp(notification.packageName)
                    if (count > MAX_NOTIFICATIONS_PER_APP) {
                        Log.d(TAG, "Limpiando notificaciones antiguas después de insertar nueva. Total: $count")
                        cleanupOldNotifications(notification.packageName)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error verificando límite de notificaciones: ${e.message}")
                }
            }

            if (isNetworkAvailable()) {
                // Actualizar status a SYNCING antes de sincronizar
                notificationDao.updateSyncStatus(id, SyncStatus.SYNCING)

                val updatedNotification = notificationWithStatus.copy(id = id)
                val success = firebaseService.syncNotification(updatedNotification)

                if (success) {
                    // Usar la función del DAO para actualizar el estado de sincronización
                    notificationDao.updateNotificationSyncResult(id, true)
                    Log.d(TAG, "✓ Notificación sincronizada con Firebase")
                } else {
                    // Si falla, actualizar estado a FAILED
                    notificationDao.updateNotificationSyncResult(id, false)
                    Log.d(TAG, "✗ Falló la sincronización con Firebase")
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
                    // Actualizar a SYNCING durante el intento
                    notificationDao.updateSyncStatus(notification.id, SyncStatus.SYNCING)

                    val success = firebaseService.syncNotification(notification)
                    // Usar la función del DAO para actualizar el estado
                    notificationDao.updateNotificationSyncResult(notification.id, success)

                    if (success) {
                        Log.d(TAG, "✓ Sincronizada notificación: ${notification.title}")
                    } else {
                        Log.d(TAG, "✗ Falló la sincronización de notificación: ${notification.title}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en syncPendingNotifications: ${e.message}")
        }
    }

    /**
     * Mantiene solo las notificaciones más recientes para una app específica
     */
    suspend fun cleanupOldNotifications(packageName: String) {
        try {
            val count = notificationDao.getNotificationCountForApp(packageName)
            
            if (count > MAX_NOTIFICATIONS_PER_APP) {
                Log.d(TAG, "Limpiando notificaciones antiguas para $packageName. Total: $count, Manteniendo: $MAX_NOTIFICATIONS_PER_APP")
                notificationDao.keepOnlyRecentNotifications(packageName, MAX_NOTIFICATIONS_PER_APP)
                Log.d(TAG, "✓ Limpieza completada. Eliminadas ${count - MAX_NOTIFICATIONS_PER_APP} notificaciones antiguas")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error limpiando notificaciones antiguas: ${e.message}", e)
        }
    }

    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}