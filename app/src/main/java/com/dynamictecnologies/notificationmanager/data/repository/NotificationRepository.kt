package com.dynamictecnologies.notificationmanager.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.dynamictecnologies.notificationmanager.data.db.NotificationDao
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.data.model.SyncStatus
import com.dynamictecnologies.notificationmanager.service.NotificationListenerService
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
    private val context: Context
) {
    private val TAG = "NotificationRepo"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Número máximo de notificaciones a mantener por app
    private val MAX_NOTIFICATIONS_PER_APP = 50

    private val prefs = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)

    init {
        // Verificar permisos al inicializar
        checkNotificationPermissions()
        startPeriodicCleanup()
    }

    /**
     * Verifica si los permisos de notificación están activos
     */
    private fun checkNotificationPermissions() {
        val hasPermissions = NotificationListenerService.isNotificationListenerEnabled(context)
        Log.d(TAG, "Estado de permisos de notificación: $hasPermissions")

        if (!hasPermissions) {
            Log.w(TAG, "⚠️ ADVERTENCIA: Los permisos de NotificationListener no están activos")
            Log.w(TAG, "📱 La recolección de notificaciones no funcionará hasta que se otorguen los permisos")

            // Enviar broadcast para notificar a la UI que necesita mostrar permisos
            val permIntent = android.content.Intent("com.dynamictecnologies.notificationmanager.NEED_PERMISSIONS")
            context.sendBroadcast(permIntent)
        }
    }

    /**
     * Verifica permisos antes de procesar notificaciones
     */
    private fun ensurePermissions(): Boolean {
        val hasPermissions = NotificationListenerService.isNotificationListenerEnabled(context)
        if (!hasPermissions) {
            Log.w(TAG, "⚠️ Operación cancelada: Sin permisos de NotificationListener")
            // Enviar broadcast cada vez que se detecte falta de permisos
            val permIntent = android.content.Intent("com.dynamictecnologies.notificationmanager.NEED_PERMISSIONS")
            context.sendBroadcast(permIntent)
        }
        return hasPermissions
    }

    private fun isAppAllowedForSync(packageName: String): Boolean {
        val allowedApp = prefs.getString("last_selected_app", null)
        return allowedApp == packageName
    }

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

        // Verificar permisos antes de proceder
        if (!ensurePermissions()) {
            // Si no hay permisos, devolver solo datos locales existentes
            Log.w(TAG, "Sin permisos - devolviendo solo datos locales existentes")
            val appName = getAppNameFromPackage(packageName)
            return notificationDao.getNotificationsForApp(appName)
                .flowOn(Dispatchers.IO)
        }

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
                        Log.d(TAG, "Firebase sync temporarily disabled - awaiting migration to FirebaseNotificationRepositoryImpl")
                        // TODO: Replace with FirebaseNotificationRepositoryImpl
                        /*
                        val remoteNotifications = firebaseService.getNotifications()
                            .filter { it.appName == appName || it.appName == packageName }
                        Log.d(TAG, "Recibidas ${remoteNotifications.size} notificaciones remotas filtradas para $appName")
                        processRemoteNotifications(remoteNotifications)
                        */
                    } catch (e: Exception) {
                        Log.e(TAG, "Error en sincronización inicial: ${e.message}", e)
                    }
                }
 else {
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
            // CRÍTICO: Verificar permisos antes de insertar
            if (!ensurePermissions()) {
                Log.w(TAG, "❌ Notificación rechazada: Sin permisos de NotificationListener")
                Log.w(TAG, "📱 Para recolectar notificaciones, otorga permisos en Configuración > Notificaciones > Acceso de notificaciones")
                return
            }

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
            Log.d(TAG, "✅ Notificación guardada localmente: ID=$id, App=$appName, Título='${notification.title}'")

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
            // (código de sincronización comentado como en el original)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error insertando notificación: ${e.message}", e)
        }
    }

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

    /**
     * Función pública para verificar el estado de los permisos
     */
    fun hasNotificationPermissions(): Boolean {
        return NotificationListenerService.isNotificationListenerEnabled(context)
    }

    /**
     * Función para forzar verificación de permisos
     */
    fun recheckPermissions() {
        Log.d(TAG, "🔄 Reverificando permisos por solicitud externa")
        checkNotificationPermissions()

        // Log del estado actual
        val hasPermissions = hasNotificationPermissions()
        if (hasPermissions) {
            Log.d(TAG, "✅ Permisos confirmados tras reverificación")
        } else {
            Log.w(TAG, "❌ Permisos aún no otorgados")
        }
    }
}