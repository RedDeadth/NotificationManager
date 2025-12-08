package com.dynamictecnologies.notificationmanager.data.cleanup

import android.util.Log
import com.dynamictecnologies.notificationmanager.data.db.NotificationDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Servicio de limpieza de notificaciones antiguas.
 * 
 * Responsabilidad única: Mantener límite de notificaciones por app
 * mediante limpieza periódica.
 * 
 * Principios aplicados:
 * - SRP: Solo limpieza de notificaciones
 * - Configurable: MAX_NOTIFICATIONS_PER_APP ajustable
 * - Background: Ejecuta en coroutine scope
 */
class NotificationCleanupService(
    private val notificationDao: NotificationDao,
    private val maxNotificationsPerApp: Int = DEFAULT_MAX_NOTIFICATIONS
) {
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isCleanupActive = false
    
    /**
     * Inicia limpieza periódica en background
     */
    fun startPeriodicCleanup(
        appName: String?,
        intervalMs: Long = DEFAULT_CLEANUP_INTERVAL_MS
    ) {
        if (isCleanupActive) {
            Log.d(TAG, "Limpieza periódica ya está activa")
            return
        }
        
        isCleanupActive = true
        Log.d(TAG, "Iniciando limpieza periódica cada ${intervalMs / 1000 / 60} minutos")
        
        scope.launch {
            while (isActive) {
                try {
                    appName?.let {
                        cleanupOldNotifications(it)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error en limpieza periódica: ${e.message}", e)
                }
                delay(intervalMs)
            }
        }
    }
    
    /**
     * Detiene limpieza periódica
     */
    fun stopPeriodicCleanup() {
        isCleanupActive = false
        scope.cancel()
        Log.d(TAG, "Limpieza periódica detenida")
    }
    
    /**
     * Limpia notificaciones antiguas de una app específica
     */
    suspend fun cleanupOldNotifications(appName: String) {
        try {
            val count = notificationDao.getNotificationCountForApp(appName)
            
            if (count > maxNotificationsPerApp) {
                Log.d(TAG, "Limpiando $appName: $count notificaciones, manteniendo $maxNotificationsPerApp")
                
                notificationDao.keepOnlyRecentNotifications(appName, maxNotificationsPerApp)
                
                val deleted = count - maxNotificationsPerApp
                Log.d(TAG, "✓ Eliminadas $deleted notificaciones antiguas de $appName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error limpiando notificaciones de $appName: ${e.message}", e)
        }
    }
    
    /**
     * Ejecuta limpieza inmediata si se alcanzó el límite
     */
    suspend fun cleanupIfNeeded(appName: String) {
        try {
            val count = notificationDao.getNotificationCountForApp(appName)
            if (count > maxNotificationsPerApp) {
                Log.d(TAG, "Límite alcanzado para $appName, ejecutando limpieza")
                cleanupOldNotifications(appName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando límite: ${e.message}")
        }
    }
    
    companion object {
        private const val TAG = "NotifCleanup"
        const val DEFAULT_MAX_NOTIFICATIONS = 50
        const val DEFAULT_CLEANUP_INTERVAL_MS = 6 * 60 * 60 * 1000L  // 6 horas
    }
}
