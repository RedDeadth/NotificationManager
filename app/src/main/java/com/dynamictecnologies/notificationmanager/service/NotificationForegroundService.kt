package com.dynamictecnologies.notificationmanager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dynamictecnologies.notificationmanager.MainActivity
import com.dynamictecnologies.notificationmanager.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.*

class NotificationForegroundService : Service() {
    private val TAG = "NotificationFgService"
    private val CHANNEL_ID = "notification_manager_service"
    private val NOTIFICATION_ID = 1
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private val WATCHDOG_INTERVAL = 15 * 60 * 1000L // Verificar cada 15 minutos
    
    // Exponential backoff para reintentos (en minutos)
    private val RETRY_INTERVALS = listOf(2, 5, 15, 30, 60)
    private var currentRetryAttempt = 0
    private var lastRetryTime = 0L
    
    private var checkServiceTimer: Timer? = null
    
    companion object {
        private const val TAG = "NotificationFgService"
        
        // Clave para identificar notificación de estado
        private const val NOTIFICATION_ID_STATUS = 1000
        
        // Constante para el tiempo máximo que puede pasar sin servicios (12 horas)
        private const val MAX_TIME_WITHOUT_SERVICE = 12 * 60 * 60 * 1000L
        
        // Acciones que pueden ser recibidas por el servicio
        const val ACTION_START_FOREGROUND_SERVICE = "ACTION_START_FOREGROUND_SERVICE"
        const val ACTION_STOP_FOREGROUND_SERVICE = "ACTION_STOP_FOREGROUND_SERVICE"
        const val ACTION_RESTART_NOTIFICATION_LISTENER = "com.dynamictecnologies.notificationmanager.RESTART_LISTENER"
        const val ACTION_FORCE_RESET = "com.dynamictecnologies.notificationmanager.FORCE_RESET"
        const val ACTION_SCHEDULED_CHECK = "com.dynamictecnologies.notificationmanager.SCHEDULED_CHECK"
        
        // Identificador de notificación
        private const val FOREGROUND_SERVICE_ID = 101
        
        // Canal de notificación
        private const val CHANNEL_NAME = "Servicio de monitoreo"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Servicio en primer plano creado")
        
        // Adquirir wake lock parcial para mantener servicio activo
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "NotificationManager::ServiceWakeLock"
            )
            wakeLock?.acquire(10 * 60 * 60 * 1000L) // 10 horas con timeout
            Log.d(TAG, "✅ Wake lock adquirido")
        } catch (e: Exception) {
            Log.e(TAG, "Error adquiriendo wake lock: ${e.message}")
        }
        
        // Crear canal de notificación para Android 8.0+
        createNotificationChannel()
        
        // Iniciar servicio en primer plano
        startForeground(FOREGROUND_SERVICE_ID, createNotification())
        
        // Iniciar temporizador para verificaciones programadas
        startPeriodicChecks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Servicio en primer plano iniciado. Intent action: ${intent?.action}")
        
        // Verificar que los permisos estén habilitados antes de realizar cualquier acción
        if (!NotificationListenerService.isNotificationListenerEnabled(applicationContext)) {
            Log.w(TAG, "⚠️ NotificationListenerService no está habilitado. Las acciones pueden fallar.")

        }
        
        intent?.let {
            when (it.action) {
                ACTION_RESTART_NOTIFICATION_LISTENER -> {
                    Log.d(TAG, "Acción de reinicio de NotificationListenerService recibida")
                    tryToRestartNotificationListenerService()
                }
                ACTION_FORCE_RESET -> {
                    Log.d(TAG, "⚠️ Acción de reinicio forzado recibida")
                    // Reinicio más agresivo para casos de emergencia
                    performForceReset()
                }
                ACTION_SCHEDULED_CHECK -> {
                    Log.d(TAG, "Verificación programada recibida")
                    checkNotificationService()
                }
            }
        }
        
        // Asegurar que el servicio siempre se reinicie si es terminado
        return START_STICKY
    }
    
    private fun performForceReset() {
        Log.w(TAG, "Realizando reinicio forzado del servicio de notificaciones")
        
        serviceScope.launch {
            try {
                // 1. Desactivar completamente el componente
                val componentName = ComponentName(applicationContext, NotificationListenerService::class.java)
                packageManager.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
                
                // 2. Esperar para asegurar que el cambio se aplique
                delay(1000)
                
                // 3. Habilitar nuevamente
                packageManager.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                
                // 4. Esperar otro poco
                delay(1000)
                
                // 5. Intentar iniciar el servicio directamente
                val listenerIntent = Intent(applicationContext, NotificationListenerService::class.java)
                try {
                    applicationContext.startService(listenerIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "No se pudo iniciar el servicio directamente: ${e.message}")
                }
                
                // 6. Actualizar la notificación para informar al usuario
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val notification = createStatusNotification("Servicio de notificaciones reiniciado")
                notificationManager.notify(NOTIFICATION_ID_STATUS, notification)
                
                // 7. Registrar el reinicio forzado
                val prefs = getSharedPreferences("notification_listener_prefs", Context.MODE_PRIVATE)
                prefs.edit().apply {
                    putLong("force_reset_time", System.currentTimeMillis())
                    putInt("force_reset_count", prefs.getInt("force_reset_count", 0) + 1)
                    apply()
                }
                
                // Resetear el contador de reintentos
                currentRetryAttempt = 0
                
                Log.d(TAG, "Reinicio forzado completado")
            } catch (e: Exception) {
                Log.e(TAG, "Error durante el reinicio forzado: ${e.message}")
            }
        }
    }
    
    private fun startWatchdogTimer() {
        serviceScope.launch {
            while (isActive) {
                try {
                    // Verificar si el NotificationListenerService está habilitado
                    val isListenerEnabled = isNotificationListenerEnabled(this@NotificationForegroundService)
                    
                    Log.d(TAG, "Watchdog: NotificationListenerService habilitado = $isListenerEnabled")
                    
                    if (!isListenerEnabled) {
                        Log.w(TAG, "Watchdog: NotificationListenerService no habilitado, intentando reiniciar...")
                        tryToRestartNotificationListenerService()
                    } else {
                        // Verificar cuándo fue la última vez que se recibió una notificación
                        val prefs = getSharedPreferences("notification_listener_prefs", Context.MODE_PRIVATE)
                        val lastNotificationTime = prefs.getLong("last_notification_received", 0)
                        val lastConnectionTime = prefs.getLong("last_connection_time", 0)
                        val currentTime = System.currentTimeMillis()
                        
                        // Si el servicio está habilitado pero no ha recibido notificaciones por un tiempo
                        if (lastNotificationTime > 0) {
                            val timeSinceLastNotif = currentTime - lastNotificationTime
                            
                            // Reducido de 2 horas a 1 hora para ser más agresivos
                            if (timeSinceLastNotif > 1 * 60 * 60 * 1000) { // 1 hora
                                val hoursSinceLastNotif = timeSinceLastNotif / (1000 * 60 * 60)
                                Log.w(TAG, "Watchdog: No se han recibido notificaciones en $hoursSinceLastNotif horas")
                                
                                // Verificar si debemos intentar otro reinicio con backoff exponencial
                                if (shouldRetryNow()) {
                                    Log.w(TAG, "Watchdog: Intento de reconexión progresivo #$currentRetryAttempt")
                                    tryToRestartNotificationListenerService()
                                    
                                    // Actualizar la notificación para informar al usuario
                                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                    val notification = createStatusNotification("Reconectando el servicio de notificaciones")
                                    notificationManager.notify(NOTIFICATION_ID_STATUS, notification)
                                    
                                    lastRetryTime = currentTime
                                    currentRetryAttempt++
                                    
                                    // Si llevamos más de 12 horas sin servicio, forzar un reinicio completo
                                    if (timeSinceLastNotif > MAX_TIME_WITHOUT_SERVICE) {
                                        Log.w(TAG, "⚠️ Crisis detectada: Sin notificaciones por más de 12 horas. Reinicio forzado completo...")
                                        performDeepReset()
                                    }
                                }
                            }
                        } else if (lastConnectionTime > 0) {
                            // Si nunca ha recibido notificaciones pero está conectado hace tiempo
                            val timeSinceConnection = currentTime - lastConnectionTime
                            // Reducido de 4 horas a 2 horas
                            if (timeSinceConnection > 2 * 60 * 60 * 1000L) { // 2 horas
                                Log.w(TAG, "Watchdog: Conexión antigua (${timeSinceConnection/3600000}h) " +
                                          "sin notificaciones, intentando reiniciar...")
                                tryToRestartNotificationListenerService()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Watchdog: Error verificando estado del servicio: ${e.message}")
                }
                
                delay(calculateNextWatchdogInterval())
            }
        }
        
        // Programar verificaciones periódicas independientes del watchdog
        schedulePeriodicChecks()
    }
    
    private fun schedulePeriodicChecks() {
        serviceScope.launch {
            while (isActive) {
                try {
                    // Verificar cada 3 horas independientemente del watchdog
                    delay(3 * 60 * 60 * 1000L)
                    
                    val prefs = getSharedPreferences("notification_listener_prefs", Context.MODE_PRIVATE)
                    val lastNotificationTime = prefs.getLong("last_notification_received", 0)
                    val currentTime = System.currentTimeMillis()
                    
                    if (lastNotificationTime > 0 && (currentTime - lastNotificationTime > 6 * 60 * 60 * 1000L)) {
                        // Si han pasado más de 6 horas sin notificaciones, hacer un reinicio forzado
                        Log.w(TAG, "⚠️ Verificación periódica: 6+ horas sin notificaciones. Realizando reinicio forzado.")
                        performForceReset()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error en verificación periódica: ${e.message}")
                }
            }
        }
    }
    
    // Nuevo método para reinicio más profundo en casos extremos
    private fun performDeepReset() {
        Log.w(TAG, "Realizando REINICIO PROFUNDO del servicio de notificaciones")
        
        serviceScope.launch {
            try {
                // 1. Forzar la detención del servicio de notificaciones
                val componentName = ComponentName(applicationContext, NotificationListenerService::class.java)
                packageManager.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
                
                // 2. Esperar a que el sistema aplique los cambios
                delay(2000)
                
                // 3. Limpiar los datos de la aplicación relacionados con el servicio
                val prefs = getSharedPreferences("notification_listener_prefs", Context.MODE_PRIVATE)
                prefs.edit().apply {
                    // Mantener los contadores para diagnóstico pero resetear timestamps
                    putLong("last_connection_time", 0)
                    putLong("last_notification_received", 0)
                    putLong("deep_reset_time", System.currentTimeMillis())
                    putInt("deep_reset_count", prefs.getInt("deep_reset_count", 0) + 1)
                    apply()
                }
                
                // 4. Reiniciar el componente
                delay(1000)
                packageManager.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                
                // 5. Esperar otro poco
                delay(2000)
                
                // 6. Intentar iniciar todo de nuevo
                val listenerIntent = Intent(applicationContext, NotificationListenerService::class.java)
                try {
                    applicationContext.startService(listenerIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "No se pudo iniciar el servicio directamente: ${e.message}")
                }
                
                // 7. Actualizar la notificación para informar al usuario
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val notification = createStatusNotification("Reinicio completo del servicio de notificaciones")
                notificationManager.notify(NOTIFICATION_ID_STATUS, notification)
                
                // Resetear el contador de reintentos
                currentRetryAttempt = 0
                
                Log.d(TAG, "Reinicio profundo completado")
            } catch (e: Exception) {
                Log.e(TAG, "Error durante el reinicio profundo: ${e.message}")
            }
        }
    }
    
    private fun shouldRetryNow(): Boolean {
        if (lastRetryTime == 0L) return true
        
        val now = System.currentTimeMillis()
        val minutesSinceLastRetry = (now - lastRetryTime) / (1000 * 60)
        
        // Usar el intervalo correspondiente o el último si hemos superado la lista
        val requiredMinutes = if (currentRetryAttempt < RETRY_INTERVALS.size) {
            RETRY_INTERVALS[currentRetryAttempt]
        } else {
            RETRY_INTERVALS.last()
        }
        
        return minutesSinceLastRetry >= requiredMinutes
    }
    
    private fun calculateNextWatchdogInterval(): Long {
        // Usar el intervalo de la lista o el valor base si no hay más intentos
        val nextMinutes = if (currentRetryAttempt < RETRY_INTERVALS.size) {
            RETRY_INTERVALS[currentRetryAttempt].toLong()
        } else {
            15L // Volvemos al intervalo base de 15 minutos
        }
        
        // Convertir a milisegundos
        return nextMinutes * 60 * 1000
    }
    
    private fun createStatusNotification(message: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, pendingIntentFlags)
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Estado del servicio")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }
    
    private fun tryToRestartNotificationListenerService() {
        try {
            // Primero, intentamos simular la desactivación y reactivación de los permisos
            // Esto puede requerir algunas acciones de usuario en versiones recientes de Android
            toggleNotificationListenerService()
            
            // Luego, asegurarse de que la clase esté activada
            val packageManager = applicationContext.packageManager
            val componentName = ComponentName(applicationContext, NotificationListenerService::class.java)
            
            try {
                packageManager.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
                
                // Esperar para evitar problemas de concurrencia
                Thread.sleep(300)
                
                packageManager.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error al cambiar estado del componente: ${e.message}")
            }
            
            // Esperar un momento para que los cambios se apliquen
            Thread.sleep(500)
            
            // Finalmente, intentar iniciar el servicio directamente
            // (puede no funcionar en versiones recientes de Android)
            try {
                val listenerIntent = Intent(applicationContext, NotificationListenerService::class.java)
                applicationContext.startService(listenerIntent)
                
                // Actualizar timestamp de intento de reinicio
                val prefs = getSharedPreferences("notification_listener_prefs", Context.MODE_PRIVATE)
                prefs.edit().putLong("last_restart_attempt", System.currentTimeMillis()).apply()
                
                Log.d(TAG, "Intento de reinicio del NotificationListenerService completado")
            } catch (e: Exception) {
                Log.e(TAG, "Error al iniciar servicio directamente: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al intentar reiniciar NotificationListenerService: ${e.message}")
        }
    }
    
    private fun toggleNotificationListenerService() {
        val pm = packageManager
        pm.setComponentEnabledSetting(
            ComponentName(applicationContext, NotificationListenerService::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        pm.setComponentEnabledSetting(
            ComponentName(applicationContext, NotificationListenerService::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }
    
    private fun isNotificationListenerEnabled(context: Context): Boolean {
        val cn = ComponentName(context, NotificationListenerService::class.java)
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        return flat?.contains(cn.flattenToString()) ?: false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT // Cambiado de LOW a DEFAULT para resistir cámara
            ).apply {
                description = "Canal para el servicio de monitoreo de notificaciones"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Canal de notificación creado")
        }
    }

    private fun createNotification(): Notification {
        // Crear intent para cuando el usuario toca la notificación
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Construir y retornar la notificación
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gestor de notificaciones")
            .setContentText("Monitoreando notificaciones activamente")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "⚠️ Servicio en primer plano destruido - Iniciando recuperación...")
        
        // Liberar wake lock
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "Wake lock liberado")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error liberando wake lock: ${e.message}")
        }
        
        // Detener temporizador de verificación
        checkServiceTimer?.cancel()
        checkServiceTimer = null
        
        // MÉTODO 1: Reinicio directo inmediato
        try {
            val intent = Intent(applicationContext, NotificationForegroundService::class.java)
            intent.action = ACTION_START_FOREGROUND_SERVICE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(intent)
            } else {
                applicationContext.startService(intent)
            }
            Log.d(TAG, "Reinicio directo iniciado")
        } catch (e: Exception) {
            Log.e(TAG, "Error en reinicio directo: ${e.message}")
        }
        
        // MÉTODO 2: AlarmManager como backup (más confiable)
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val restartIntent = Intent(applicationContext, ServiceRestartReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                applicationContext,
                1001,
                restartIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Reiniciar en 5 segundos
            alarmManager.setExactAndAllowWhileIdle(
                android.app.AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 5000,
                pendingIntent
            )
            Log.d(TAG, "AlarmManager backup programado para 5 segundos")
        } catch (e: Exception) {
            Log.e(TAG, "Error programando AlarmManager: ${e.message}")
        }
    }

    private fun checkNotificationService() {
        try {
            val isListenerEnabled = NotificationListenerService.isNotificationListenerEnabled(this)
            val prefs = getSharedPreferences("notification_listener_prefs", Context.MODE_PRIVATE)
            
            if (!isListenerEnabled) {
                Log.w(TAG, "Verificación programada: NotificationListenerService no está habilitado")
                if (System.currentTimeMillis() - prefs.getLong("last_permission_request", 0) > 4 * 60 * 60 * 1000) {
                    // Solicitar permisos cada 4 horas como máximo
                    val intent = Intent("com.dynamictecnologies.notificationmanager.SHOW_PERMISSION_DIALOG")
                    applicationContext.sendBroadcast(intent)
                    prefs.edit().putLong("last_permission_request", System.currentTimeMillis()).apply()
                }
                return
            }
            
            // Si está habilitado pero no ha recibido notificaciones en mucho tiempo
            val lastNotificationTime = prefs.getLong("last_notification_received", 0)
            val lastConnectionTime = prefs.getLong("last_connection_time", 0)
            val currentTime = System.currentTimeMillis()
            
            if (lastNotificationTime > 0 && (currentTime - lastNotificationTime > 4 * 60 * 60 * 1000)) {
                Log.w(TAG, "Verificación programada: No se han recibido notificaciones en más de 4 horas")
                performForceReset()
            } else if (lastConnectionTime > 0 && (currentTime - lastConnectionTime > 6 * 60 * 60 * 1000)) {
                Log.w(TAG, "Verificación programada: No hay conexión en más de 6 horas")
                performDeepReset()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en verificación programada: ${e.message}")
        }
    }

    private fun startPeriodicChecks() {
        // Cancelar temporizador existente si hay uno
        checkServiceTimer?.cancel()
        
        // Crear nuevo temporizador
        checkServiceTimer = Timer()
        
        // Programar verificación cada 3 horas
        checkServiceTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                Log.d(TAG, "Realizando verificación programada del servicio...")
                checkNotificationService()
            }
        }, 30 * 60 * 1000L, 3 * 60 * 60 * 1000L) // Inicia a los 30 minutos, luego cada 3 horas
    }
}