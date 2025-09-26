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
        //startPeriodicSync()
        startPeriodicCleanup()
    }

    private fun isAppAllowedForSync(packageName: String): Boolean {
        val allowedApp = prefs.getString("last_selected_app", null)
        return allowedApp == packageName
    }
    /*

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

     */

    /**
     * Inicia limpieza periódica de notificaciones antiguas
     */
    private fun startPeriodicCleanup() {
        scope.launch {
            while (isActive) {
                try {
                    val selectedApp = prefs.getString("last_selected_app", null)
                    selectedApp?.let { 
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
        Log.d(TAG, "getNotifications llamado para packageName: $packageName")
        val appName = getAppNameFromPackage(packageName)
        Log.d(TAG, "Nombre de aplicación traducido: $appName")
        
        return notificationDao.getNotificationsForApp(appName)
            .onStart {
                Log.d(TAG, "Emitiendo notificaciones locales iniciales para $appName")
                val localNotifications = notificationDao.getNotificationsForAppImmediate(appName)
                Log.d(TAG, "Encontradas ${localNotifications.size} notificaciones locales para $appName")
                emit(localNotifications)

                if (isNetworkAvailable()) {
                    try {
                        Log.d(TAG, "Obteniendo notificaciones remotas de Firebase...")
                        // Obtenemos todas las notificaciones y filtramos por appName localmente
                        val remoteNotifications = firebaseService.getNotifications()
                            .filter { it.appName == appName || it.appName == packageName }
                        
                        Log.d(TAG, "Recibidas ${remoteNotifications.size} notificaciones remotas filtradas para $appName")
                        processRemoteNotifications(remoteNotifications)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error en sincronización inicial: ${e.message}", e)
                    }
                } else {
                    Log.d(TAG, "Red no disponible. Usando solo datos locales.")
                }
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)
    }

    private suspend fun processRemoteNotifications(remoteNotifications: List<NotificationInfo>) {
        if (remoteNotifications.isEmpty()) {
            Log.d(TAG, "No hay notificaciones remotas para procesar")
            return
        }
        
        Log.d(TAG, "Procesando ${remoteNotifications.size} notificaciones remotas")
        var procesadas = 0
        
        remoteNotifications.forEach { notification ->
            try {
                // Marcar las notificaciones remotas como SYNCED
                notificationDao.insertOrUpdateNotification(notification.copy(
                    isSynced = true,
                    syncStatus = SyncStatus.SYNCED
                ))
                procesadas++
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando notificación remota: ${e.message}", e)
            }
        }
        
        Log.d(TAG, "Notificaciones remotas procesadas: $procesadas/${remoteNotifications.size}")
    }

    suspend fun insertNotification(notification: NotificationInfo) {
        try {
            // Determinar el appName correcto (usar el proporcionado o derivarlo)
            val appName = if (notification.appName.isNotEmpty()) {
                notification.appName
            } else {
                // Si no hay appName, intentar usar la app seleccionada
                val selectedPackage = prefs.getString("last_selected_app", null) ?: return
                getAppNameFromPackage(selectedPackage)
            }
            
            // Verificar si la app es la seleccionada
            if (!isSelectedApp(appName)) {
                Log.d(TAG, "Notificación ignorada: $appName no es la app seleccionada")
                return
            }

            // Crear la notificación para guardar
            val notificationToSave = notification.copy(
                appName = appName,
                syncStatus = SyncStatus.PENDING
            )

            // Guardar en la base de datos
            val id = notificationDao.insertNotification(notificationToSave)
            Log.d(TAG, "✓ Notificación guardada localmente: ID=$id, App=$appName, Título='${notification.title}'")
            
            // Verificar si necesitamos limpiar notificaciones antiguas
            scope.launch(Dispatchers.IO) {
                try {
                    val count = notificationDao.getNotificationCountForApp(appName)
                    if (count > MAX_NOTIFICATIONS_PER_APP) {
                        Log.d(TAG, "Limpiando notificaciones antiguas para $appName. Total: $count")
                        cleanupOldNotifications(appName)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error verificando límite de notificaciones: ${e.message}")
                }
            }

            // Sincronizar con Firebase si hay conexión
            /*if (isNetworkAvailable()) {
                notificationDao.updateSyncStatus(id, SyncStatus.SYNCING)
                
                val updatedNotification = notificationToSave.copy(id = id)
                val success = firebaseService.syncNotification(updatedNotification)

                if (success) {
                    notificationDao.updateNotificationSyncResult(id, true)
                    Log.d(TAG, "✓ Notificación sincronizada con Firebase: ID=$id")
                } else {
                    notificationDao.updateNotificationSyncResult(id, false)
                    Log.d(TAG, "✗ Falló la sincronización de notificación: ID=$id")
                }
            }*/
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error insertando notificación: ${e.message}")
        }
    }
    /*
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

     */

    /**
     * Mantiene solo las notificaciones más recientes para una app específica
     */
    suspend fun cleanupOldNotifications(appName: String) {
        try {
            val count = notificationDao.getNotificationCountForApp(appName)
            
            if (count > MAX_NOTIFICATIONS_PER_APP) {
                Log.d(TAG, "Limpiando notificaciones antiguas para $appName. Total: $count, Manteniendo: $MAX_NOTIFICATIONS_PER_APP")
                notificationDao.keepOnlyRecentNotifications(appName, MAX_NOTIFICATIONS_PER_APP)
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

    /**
     * Obtiene el nombre de la aplicación a partir del packageName
     */
    fun getAppNameFromPackage(packageName: String): String {
        return try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo nombre de app: ${e.message}")
            packageName // Usar packageName como fallback
        }
    }

    /**
     * Verifica si un appName coincide con el packageName seleccionado actualmente
     */
    fun isSelectedApp(appName: String): Boolean {
        val selectedPackage = prefs.getString("last_selected_app", null) ?: return false
        
        // Comparar directamente con el nombre de la app seleccionada
        if (appName == selectedPackage) return true
        
        // O verificar si el appName corresponde al packageName seleccionado
        val selectedAppName = getAppNameFromPackage(selectedPackage)
        return appName == selectedAppName
    }
}