package com.dynamictecnologies.notificationmanager.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.dynamictecnologies.notificationmanager.data.cleanup.NotificationCleanupService
import com.dynamictecnologies.notificationmanager.data.db.NotificationDao
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.data.model.SyncStatus
import com.dynamictecnologies.notificationmanager.data.permissions.NotificationPermissionChecker
import com.dynamictecnologies.notificationmanager.util.AppNameResolver
import com.dynamictecnologies.notificationmanager.util.network.NetworkConnectivityChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Repositorio de notificaciones REFACTORIZADO.
 * 
 * Ahora solo coordina componentes especializados:
 * - NotificationPermissionChecker: Verificación de permisos
 * - NotificationCleanupService: Limpieza periódica
 * - NetworkConnectivityChecker: Estado de red
 * - AppNameResolver: Resolución de nombres
 * 
 * Principios aplicados:
 * - SRP: Solo coordinación y operaciones de datos
 * - Composition: Usa componentes especializados
 * - Clean Architecture: Repository pattern
 */
class NotificationRepository(
    private val notificationDao: NotificationDao,
    context: Context
) {
    private val TAG = "NotificationRepo"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Componentes especializados
    private val permissionChecker = NotificationPermissionChecker(context)
    private val cleanupService = NotificationCleanupService(
        notificationDao = notificationDao,
        maxNotificationsPerApp = MAX_NOTIFICATIONS_PER_APP
    )
    private val networkChecker = NetworkConnectivityChecker(context)
    private val appNameResolver = AppNameResolver(context)
    
    private val prefs: SharedPreferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
    
    init {
        // Verificar permisos al inicializar
        permissionChecker.checkAndNotify()
        
        // Iniciar limpieza periódica
        val selectedApp = prefs.getString("last_selected_app", null)
        cleanupService.startPeriodicCleanup(selectedApp)
    }
    
    /**
     * Obtiene notificaciones para una app específica
     */
    fun getNotifications(packageName: String): Flow<List<NotificationInfo>> {
        Log.d(TAG, "getNotifications para $packageName")
        
        // Verificar permisos
        if (!permissionChecker.hasPermission()) {
            Log.w(TAG, "Sin permisos - solo datos locales")
            val appName = appNameResolver.getAppName(packageName)
            return notificationDao.getNotificationsForApp(appName).flowOn(Dispatchers.IO)
        }
        
        val appName = appNameResolver.getAppName(packageName)
        Log.d(TAG, "AppName: $appName")
        
        return notificationDao.getNotificationsForApp(appName)
            .onStart {
                Log.d(TAG, "Emitiendo notificaciones locales para $appName")
                val localNotifications = notificationDao.getNotificationsForAppImmediate(appName)
                Log.d(TAG, "Encontradas ${localNotifications.size} notificaciones locales")
                emit(localNotifications)
                
                // Firebase sync removido - usar FirebaseNotificationRepositoryImpl si es necesario
                if (networkChecker.isNetworkAvailable()) {
                    Log.d(TAG, "Red disponible (Firebase sync disabled)")
                } else {
                    Log.d(TAG, "Red no disponible - solo datos locales")
                }
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)
    }
    
    /**
     * Inserta una notificación
     */
    suspend fun insertNotification(notification: NotificationInfo) {
        try {
            // Verificar permisos
            if (!permissionChecker.hasPermission()) {
                Log.w(TAG, "❌ Notificación rechazada: Sin permisos")
                return
            }
            
            // Determinar appName
            val appName = if (notification.appName.isNotEmpty()) {
                notification.appName
            } else {
                val selectedPackage = prefs.getString("last_selected_app", null) ?: return
                appNameResolver.getAppName(selectedPackage)
            }
            
            // Verificar si es la app seleccionada
            if (!isSelectedApp(appName)) {
                Log.d(TAG, "Notificación ignorada: $appName no es la app seleccionada")
                return
            }
            
            // Guardar en DB
            val notificationToSave = notification.copy(
                appName = appName,
                syncStatus = SyncStatus.PENDING
            )
            
            val id = notificationDao.insertNotification(notificationToSave)
            Log.d(TAG, "✅ Notificación guardada: ID=$id, App=$appName")
            
            // Limpiar si es necesario
            scope.launch(Dispatchers.IO) {
                cleanupService.cleanupIfNeeded(appName)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error insertando notificación: ${e.message}", e)
        }
    }
    
    /**
     * Verifica si un appName coincide con la app seleccionada
     */
    private fun isSelectedApp(appName: String): Boolean {
        val selectedPackage = prefs.getString("last_selected_app", null) ?: return false
        return appNameResolver.matchesPackage(appName, selectedPackage)
    }
    
    /**
     * Métodos públicos para verificación
     */
    fun hasNotificationPermissions(): Boolean = permissionChecker.hasPermission()
    
    fun recheckPermissions() = permissionChecker.recheckPermissions()
    
    fun isNetworkAvailable(): Boolean = networkChecker.isNetworkAvailable()
    
    fun getAppNameFromPackage(packageName: String): String = appNameResolver.getAppName(packageName)
    
    companion object {
        private const val MAX_NOTIFICATIONS_PER_APP = 50
    }
}