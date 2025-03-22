package com.dynamictecnologies.notificationmanager.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.util.Log
import com.dynamictecnologies.notificationmanager.data.db.NotificationDatabase
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.data.repository.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import com.dynamictecnologies.notificationmanager.service.FirebaseService
import com.dynamictecnologies.notificationmanager.service.NotificationForegroundService
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import android.content.SharedPreferences
import android.content.pm.PackageManager

class NotificationListenerService : NotificationListenerService() {
    private val TAG = "NotificationListener"
    private lateinit var repository: NotificationRepository
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Usar ConcurrentHashMap para thread-safety
    private val recentNotifications = ConcurrentHashMap<String, Long>()
    private val CACHE_TIMEOUT = 3000L // 3 segundos de timeout
    
    // Flag para monitorear si el servicio está activo
    private var isServiceRunning = false
    private val CHECK_SERVICE_INTERVAL = 60 * 1000L // Verificar cada minuto
    
    // Contador de notificaciones para diagnostico
    private var notificationCounter = 0
    private var lastDebugReportTime = 0L
    private val DEBUG_REPORT_INTERVAL = 30 * 60 * 1000L // Reporte cada 30 minutos
    
    // Agregar un contador de reintentos desde onCreate
    private var serviceRestartCount = 0
    private var lastRestartTime = 0L

    // Patrones de notificaciones de resumen
    private val summaryPatterns = listOf(
        "\\d+ (?:nuevos? )?mensajes?(?: de \\d+ chats?)?".toRegex(),
        "new messages?".toRegex(),
        "messages from".toRegex(),
        "\\d+ chats?".toRegex()
    )

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "✅ Servicio de escucha de notificaciones creado")
        
        // Verificar el estado de los permisos
        val enabled = isNotificationListenerEnabled(applicationContext)
        Log.i(TAG, "- NotificationListenerEnabled: $enabled")
        
        if (!enabled) {
            Log.w(TAG, "⚠️ ¡Servicio de notificaciones deshabilitado! Intentando reiniciar...")
            // Enviar un broadcast para mostrar diálogo de permisos
            val intent = Intent("com.dynamictecnologies.notificationmanager.SHOW_PERMISSION_DIALOG")
            applicationContext.sendBroadcast(intent)
        } else {
            // Actualizar marca de tiempo de conexión
            val prefs = getSharedPreferences("notification_listener_prefs", Context.MODE_PRIVATE)
            prefs.edit().putLong("last_connection_time", System.currentTimeMillis()).apply()
        }
        
        try {
            val database = NotificationDatabase.getDatabase(applicationContext)
            val firebaseService = FirebaseService()
            repository = NotificationRepository(
                notificationDao = database.notificationDao(),
                firebaseService = firebaseService,
                context = applicationContext
            )
            
            // Realizar limpieza inicial de notificaciones antiguas
            val appPrefs = applicationContext.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
            val currentApp = appPrefs.getString("last_selected_app", null)
            if (currentApp != null) {
                serviceScope.launch(Dispatchers.IO) {
                    try {
                        Log.d(TAG, "Iniciando limpieza inicial de notificaciones para $currentApp")
                        repository.cleanupOldNotifications(currentApp)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error en limpieza inicial: ${e.message}", e)
                    }
                }
            }
            
            startForegroundService()
            startCacheCleaning()
            startServiceMonitoring()
            startDiagnosticReporting()
            
            // Guardar timestamp de inicio
            val prefs = applicationContext.getSharedPreferences("notification_listener_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putLong("last_service_start", System.currentTimeMillis())
                .putLong("service_start_count", prefs.getLong("service_start_count", 0) + 1)
                .apply()
            
            Log.d(TAG, "NotificationListenerService inicializado correctamente")
            
            // Verificar si el servicio ha sido reiniciado muchas veces en un período corto
            checkServiceRestartPattern(prefs)
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando NotificationListenerService: ${e.message}", e)
        }
    }

    private fun startServiceMonitoring() {
        serviceScope.launch {
            while (isActive) {
                try {
                    if (!isNotificationListenerEnabled(applicationContext)) {
                        Log.w(TAG, "¡Servicio de notificaciones deshabilitado! Intentando reiniciar...")
                        
                        // Intentar reiniciar el servicio a través del ForegroundService
                        val intent = Intent(applicationContext, NotificationForegroundService::class.java)
                        intent.action = NotificationForegroundService.ACTION_RESTART_NOTIFICATION_LISTENER
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            applicationContext.startForegroundService(intent)
                        } else {
                            applicationContext.startService(intent)
                        }
                    } else {
                        // El servicio está habilitado, verificar si está recibiendo notificaciones
                        val prefs = applicationContext.getSharedPreferences(
                            "notification_listener_prefs", 
                            Context.MODE_PRIVATE
                        )
                        val lastNotificationTime = prefs.getLong("last_notification_received", 0)
                        val currentTime = System.currentTimeMillis()
                        
                        // Reducido de 3 horas a 90 minutos para recuperarse más rápido
                        if (lastNotificationTime > 0 && (currentTime - lastNotificationTime > 90 * 60 * 1000)) {
                            Log.w(TAG, "No se han recibido notificaciones en 90 minutos. Solicitando reinicio...")
                            
                            // Incrementar contador de reintentos para diagnóstico
                            serviceRestartCount++
                            lastRestartTime = currentTime
                            
                            // Si hemos intentado reiniciar muchas veces en poco tiempo, solicitar un reinicio forzado
                            if (serviceRestartCount > 3 && currentTime - prefs.getLong("last_force_reset", 0) > 4 * 60 * 60 * 1000) {
                                Log.w(TAG, "Múltiples reintentos fallidos. Solicitando REINICIO FORZADO...")
                                requestForceReset()
                                
                                // Actualizar timestamp del último reinicio forzado
                                prefs.edit().putLong("last_force_reset", currentTime).apply()
                                serviceRestartCount = 0
                            } else {
                                requestReconnect()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error en monitoreo del servicio: ${e.message}")
                }
                delay(CHECK_SERVICE_INTERVAL)
            }
        }
    }

    private fun startCacheCleaning() {
        serviceScope.launch {
            while (isActive) {
                try {
                    cleanOldCache()
                } catch (e: Exception) {
                    Log.e(TAG, "Error limpiando caché: ${e.message}")
                }
                delay(CACHE_TIMEOUT)
            }
        }
    }

    private fun cleanOldCache() {
        val currentTime = System.currentTimeMillis()
        recentNotifications.entries.removeIf {
            currentTime - it.value > CACHE_TIMEOUT
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val enabled = isNotificationListenerEnabled(applicationContext)
        if (!enabled) {
            Log.w(TAG, "⚠️ Notificación recibida pero el servicio está deshabilitado: ${sbn.packageName}")
            return
        }
        
        Log.d(TAG, "📱 Notificación recibida de: ${sbn.packageName}")
        
        // Actualizar marca de tiempo de última notificación
        val prefs = getSharedPreferences("notification_listener_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("last_notification_received", System.currentTimeMillis()).apply()
        
        try {
            if (!shouldProcessNotification(sbn)) {
                return
            }
            
            // Procesamos la notificación
            procesarNotificacion(sbn)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error procesando notificación: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun isSummaryNotification(notification: Notification, text: String): Boolean {
        // Verificar si es una notificación de grupo
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            return true
        }

        // Verificar patrones de texto conocidos
        return summaryPatterns.any { pattern ->
            pattern.matches(text)
        }
    }

    private fun createUniqueKey(packageName: String, title: String, text: String, timestamp: Long): String {
        // Redondear timestamp a segundos para mayor tolerancia
        return "$packageName:$title:$text:${timestamp / 1000}"
    }

    private fun isDuplicate(key: String): Boolean {
        val currentTime = System.currentTimeMillis()
        return recentNotifications[key]?.let { lastTime ->
            currentTime - lastTime < CACHE_TIMEOUT
        } ?: false
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isServiceRunning = true
        Log.d(TAG, "NotificationListenerService conectado")
        
        // Registrar conexión exitosa
        val prefs = applicationContext.getSharedPreferences(
            "notification_listener_prefs", 
            Context.MODE_PRIVATE
        )
        prefs.edit().putLong("last_connection_time", System.currentTimeMillis()).apply()
        
        // Informar al servicio en primer plano que nos hemos conectado correctamente
        try {
            val intent = Intent(applicationContext, NotificationForegroundService::class.java)
            intent.action = "ACTION_LISTENER_CONNECTED"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(intent)
            } else {
                applicationContext.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error informando conexión: ${e.message}")
        }
        
        // Resetear el contador de reintentos ya que la conexión fue exitosa
        serviceRestartCount = 0
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isServiceRunning = false
        Log.w(TAG, "⚠️ Servicio de escucha de notificaciones desconectado")
        
        // Intentar reconectar el servicio
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Log.d(TAG, "Intentando reconectar servicio de notificaciones...")
                requestRebind(ComponentName(this, NotificationListenerService::class.java))
            } else {
                // Para versiones anteriores, intentar reinicio a través del ForegroundService
                val intent = Intent(applicationContext, NotificationForegroundService::class.java)
                intent.action = NotificationForegroundService.ACTION_RESTART_NOTIFICATION_LISTENER
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    applicationContext.startForegroundService(intent)
                } else {
                    applicationContext.startService(intent)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al intentar reconectar el servicio: ${e.message}")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        isServiceRunning = false
        Log.d(TAG, "NotificationListenerService destruido - solicitando reinicio...")
        
        // Intentar reiniciar el servicio
        val intent = Intent(applicationContext, NotificationForegroundService::class.java)
        intent.action = "ACTION_RESTART_NOTIFICATION_LISTENER"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext.startForegroundService(intent)
        } else {
            applicationContext.startService(intent)
        }
    }

    private fun startForegroundService() {
        try {
            val serviceIntent = Intent(this, NotificationForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Log.d(TAG, "Servicio en primer plano iniciado correctamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando servicio en primer plano: ${e.message}", e)
        }
    }
    
    private fun requestReconnect() {
        try {
            // En Android 7.0+, podemos usar este método para solicitar reconexión
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                requestRebind(ComponentName(this, NotificationListenerService::class.java))
                Log.d(TAG, "Reconexión solicitada via requestRebind()")
            } else {
                // En versiones anteriores, simulamos desactivar/activar el servicio mediante UI
                // Esto es solo un intento, no garantiza resultados
                Log.d(TAG, "Solicitando reinicio del servicio (alternativa)")
                val intent = Intent(this, NotificationForegroundService::class.java)
                intent.action = "ACTION_RESTART_NOTIFICATION_LISTENER"
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al solicitar reconexión: ${e.message}")
        }
    }

    private fun checkServiceRestartPattern(prefs: SharedPreferences) {
        val now = System.currentTimeMillis()
        val lastStart = prefs.getLong("last_service_start", 0)
        val startCount = prefs.getLong("service_start_count", 0)
        
        // Si el servicio se ha reiniciado más de 5 veces en la última hora, puede haber un problema
        if (startCount > 5 && (now - lastStart < 60 * 60 * 1000)) {
            Log.w(TAG, "⚠️ ADVERTENCIA: El servicio se ha reiniciado $startCount veces en un período corto. " +
                       "Posible bucle de reinicio o problema con el sistema.")
            
            // Registrar este problema para análisis posterior
            prefs.edit()
                .putLong("restart_loop_detected", now)
                .putLong("restart_loop_count", startCount)
                .apply()
        }
    }

    private fun startDiagnosticReporting() {
        serviceScope.launch {
            while (isActive) {
                try {
                    // Generar un informe de diagnóstico periódico
                    if (System.currentTimeMillis() - lastDebugReportTime > DEBUG_REPORT_INTERVAL) {
                        generateDiagnosticReport()
                        lastDebugReportTime = System.currentTimeMillis()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error en diagnóstico: ${e.message}")
                }
                delay(DEBUG_REPORT_INTERVAL / 2) // Verificar a la mitad del intervalo
            }
        }
    }
    
    private fun generateDiagnosticReport() {
        val prefs = applicationContext.getSharedPreferences(
            "notification_listener_prefs", 
            Context.MODE_PRIVATE
        )
        
        val now = System.currentTimeMillis()
        val lastStart = prefs.getLong("last_service_start", 0)
        val lastNotifTime = prefs.getLong("last_notification_received", 0)
        val lastConnTime = prefs.getLong("last_connection_time", 0)
        val lastForceReset = prefs.getLong("last_force_reset", 0)
        val forceResetCount = prefs.getInt("force_reset_count", 0)
        val deepResetCount = prefs.getInt("deep_reset_count", 0)
        
        Log.i(TAG, "📊 INFORME DE DIAGNÓSTICO:")
        Log.i(TAG, "- Estado del servicio: ${if(isServiceRunning) "ACTIVO" else "INACTIVO"}")
        Log.i(TAG, "- Notificaciones procesadas desde el inicio: $notificationCounter")
        Log.i(TAG, "- Tiempo desde el último inicio: ${formatTime(now - lastStart)}")
        Log.i(TAG, "- Tiempo desde la última notificación: ${if(lastNotifTime > 0) formatTime(now - lastNotifTime) else "NUNCA"}")
        Log.i(TAG, "- Tiempo desde la última conexión: ${if(lastConnTime > 0) formatTime(now - lastConnTime) else "NUNCA"}")
        Log.i(TAG, "- Tiempo desde el último reinicio forzado: ${if(lastForceReset > 0) formatTime(now - lastForceReset) else "NUNCA"}")
        Log.i(TAG, "- Reintentos desde el último informe: $serviceRestartCount")
        Log.i(TAG, "- Total reinicios forzados: $forceResetCount")
        Log.i(TAG, "- Total reinicios profundos: $deepResetCount")
        Log.i(TAG, "- NotificationListenerEnabled: ${isNotificationListenerEnabled(applicationContext)}")
        
        // Verificar si estamos en un ciclo de reconexión y no está funcionando
        if (serviceRestartCount > 5) {
            Log.w(TAG, "⚠️ ALERTA: Muchos intentos de reinicio sin éxito (${serviceRestartCount})")
            
            // Si no hemos hecho un reinicio forzado recientemente, intentarlo
            if (now - lastForceReset > 2 * 60 * 60 * 1000L) {
                Log.w(TAG, "Solicitando reinicio forzado debido a ciclo de reconexión")
                requestForceReset()
                
                // Actualizar timestamp del último reinicio forzado
                prefs.edit().putLong("last_force_reset", now).apply()
                serviceRestartCount = 0
            }
        }
        
        // Si no ha recibido notificaciones en más de 2 horas pero el servicio está "activo", 
        // podría estar en un estado inconsistente (reducido de 3 a 2 horas)
        if (isServiceRunning && lastNotifTime > 0 && (now - lastNotifTime > 2 * 60 * 60 * 1000)) {
            Log.w(TAG, "⚠️ POSIBLE PROBLEMA DETECTADO: El servicio está activo pero no ha recibido notificaciones " +
                      "en las últimas 2 horas. Intentando reconectar...")
            
            // Forzar una reconexión
            requestReconnect()
            
            // Actualizar contadores para evitar bucles de reinicio
            prefs.edit()
                .putLong("forced_reconnect_time", now)
                .putLong("forced_reconnect_count", prefs.getLong("forced_reconnect_count", 0) + 1)
                .apply()
        }
    }
    
    private fun formatTime(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        
        return when {
            days > 0 -> "$days días, ${hours % 24} horas"
            hours > 0 -> "$hours horas, ${minutes % 60} minutos"
            minutes > 0 -> "$minutes minutos, ${seconds % 60} segundos"
            else -> "$seconds segundos"
        }
    }

    private fun requestForceReset() {
        try {
            val intent = Intent(applicationContext, NotificationForegroundService::class.java)
            intent.action = NotificationForegroundService.ACTION_FORCE_RESET
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(intent)
            } else {
                applicationContext.startService(intent)
            }
            Log.d(TAG, "Solicitud de reinicio forzado enviada")
        } catch (e: Exception) {
            Log.e(TAG, "Error al solicitar reinicio forzado: ${e.message}")
        }
    }

    private fun shouldProcessNotification(sbn: StatusBarNotification): Boolean {
        try {
            // Ignorar notificaciones de nuestra propia app
            if (sbn.packageName == packageName) {
                return false
            }
            
            // Asegurarse que tiene extras
            val notification = sbn.notification ?: return false
            val extras = notification.extras ?: return false
            
            // Solo procesar si tiene título o texto
            val title = extras.getString(Notification.EXTRA_TITLE)
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            
            if (title.isNullOrEmpty() && text.isNullOrEmpty()) {
                return false
            }
            
            // Obtener nombre de la aplicación para comparar
            val appName = try {
                val packageManager = applicationContext.packageManager
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
            } catch (e: Exception) {
                Log.e(TAG, "Error obteniendo nombre de app: ${e.message}")
                sbn.packageName
            }
            
            // Verificar que la app está configurada para ser monitorizada
            val appPrefs = applicationContext.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
            val selectedApp = appPrefs.getString("last_selected_app", null)
            
            if (selectedApp == null) {
                Log.d(TAG, "No hay una app seleccionada para monitorizar")
                return false
            }
            
            // Comparamos por appName, que es lo que usamos ahora para almacenar
            if (appName != selectedApp && sbn.packageName != selectedApp) {
                Log.d(TAG, "Ignorando notificación de $appName (${sbn.packageName}), app seleccionada: $selectedApp")
                return false
            }
            
            // Notificación ha pasado todos los filtros, debe ser procesada
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error en shouldProcessNotification: ${e.message}")
            return false
        }
    }

    private fun procesarNotificacion(sbn: StatusBarNotification) {
        try {
            // Incrementar contador para estadísticas
            notificationCounter++
            
            // Obtener detalles de la notificación
            val notification = sbn.notification
            val extras = notification.extras
            
            // Extraer información relevante
            val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            
            // Obtener nombre de la aplicación
            val appName = try {
                val packageManager = applicationContext.packageManager
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
            } catch (e: Exception) {
                Log.e(TAG, "Error obteniendo nombre de app: ${e.message}")
                sbn.packageName // Usar packageName como fallback
            }
            
            // Verificar si esta es una notificación duplicada o de resumen
            if (text.isNotEmpty() && isSummaryNotification(notification, text)) {
                Log.d(TAG, "Ignorando notificación de resumen: $appName - $title")
                return
            }
            
            // Crear clave única para evitar duplicados
            val key = createUniqueKey(sbn.packageName, title, text, sbn.postTime)
            if (isDuplicate(key)) {
                Log.d(TAG, "Ignorando notificación duplicada: $appName - $title")
                return
            }
            recentNotifications[key] = System.currentTimeMillis()
            
            // Crear objeto de notificación con formato simplificado
            val notificationInfo = NotificationInfo(
                appName = appName,
                title = title,
                content = text,
                timestamp = Date(sbn.postTime)
            )
            
            // Guardar en la base de datos y sincronizar
            serviceScope.launch(Dispatchers.IO) {
                try {
                    // Intentar guardar la notificación usando el repositorio
                    repository.insertNotification(notificationInfo)
                    Log.d(TAG, "✓ Notificación guardada: $title ($appName)")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error guardando notificación: ${e.message}", e)
                }
            }
            
            // Guardar información en SharedPreferences para diagnóstico
            val prefs = getSharedPreferences("notification_listener_prefs", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("last_notification_package", sbn.packageName)
                putString("last_notification_app", appName)
                putLong("last_notification_time", System.currentTimeMillis())
                apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error procesando notificación: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "NotificationListener"
        
        /**
         * Comprueba si el servicio de escucha de notificaciones está habilitado
         */
        fun isNotificationListenerEnabled(context: Context): Boolean {
            try {
                val packageName = context.packageName
                val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                if (flat != null && flat.isNotEmpty()) {
                    val names = flat.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    for (name in names) {
                        val componentName = ComponentName.unflattenFromString(name)
                        if (componentName != null && TextUtils.equals(packageName, componentName.packageName)) {
                            // El servicio está habilitado en la configuración del sistema
                            // Guardar estado para referencia futura
                            val prefs = context.getSharedPreferences("notification_listener_prefs", Context.MODE_PRIVATE)
                            prefs.edit().putBoolean("notification_listener_enabled", true).apply()
                            return true
                        }
                    }
                }
                
                // El servicio no está habilitado
                val prefs = context.getSharedPreferences("notification_listener_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("notification_listener_enabled", false).apply()
                return false
            } catch (e: Exception) {
                Log.e(TAG, "Error al verificar permisos de notificaciones: ${e.message}")
                return false
            }
        }
        
        /**
         * Abre la configuración de escucha de notificaciones
         */
        fun openNotificationListenerSettings(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error al abrir configuración de notificaciones: ${e.message}")
                // Intenta abrir la configuración general como alternativa
                try {
                    val intent = Intent(Settings.ACTION_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (e2: Exception) {
                    Log.e(TAG, "Error al abrir configuración general: ${e2.message}")
                }
            }
        }
        
        /**
         * Solicita reiniciar el servicio desde el exterior
         */
        fun requestServiceReset(context: Context) {
            val intent = Intent(context, NotificationForegroundService::class.java)
            intent.action = NotificationForegroundService.ACTION_RESTART_NOTIFICATION_LISTENER
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.d(TAG, "Solicitud de reinicio del servicio enviada")
        }
    }
}