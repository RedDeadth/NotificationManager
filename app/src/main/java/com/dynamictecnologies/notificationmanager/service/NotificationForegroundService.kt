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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.util.*

class NotificationForegroundService : Service() {
    private val CHANNEL_ID = "notification_manager_service"
    private val NOTIFICATION_ID = 1
    
    // Exception handler para capturar errores en coroutines
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("NotificationFgService", "Error no manejado en coroutine: ${throwable.message}")
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)
    private var wakeLock: PowerManager.WakeLock? = null
    private val WATCHDOG_INTERVAL = 15 * 60 * 1000L // Verificar cada 15 minutos
    
    // Heartbeat para watchdog externo
    private var heartbeatJob: Job? = null
    private val HEARTBEAT_INTERVAL = 5 * 60 * 1000L // Actualizar cada 5 minutos
    
    // Watchdog delegado para vigilancia del servicio
    private var serviceWatchdog: ServiceWatchdog? = null
    
    private var periodicCheckJob: Job? = null
    
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
        
        // Establecer estado como RUNNING
        ServiceStateManager.setState(this, ServiceStateManager.ServiceState.RUNNING)
        
        // Adquirir wake lock parcial para mantener servicio activo
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "NotificationManager::ServiceWakeLock"
            )
            wakeLock?.acquire(10 * 60 * 60 * 1000L) // 10 horas con timeout
            Log.d(TAG, "Wake lock adquirido")
        } catch (e: Exception) {
            Log.e(TAG, "Error adquiriendo wake lock: ${e.message}")
        }
        
        // Mostrar notificación dinámica de RUNNING
        val notification = ServiceNotificationManager(this).showRunningNotification()
        
        // Iniciar servicio en primer plano con la notificación dinámica
        startForeground(ServiceNotificationManager.NOTIFICATION_ID_RUNNING, notification)
        
        // Iniciar temporizador para verificaciones programadas
        startPeriodicChecks()
        
        // Inicializar y arrancar el watchdog delegado
        serviceWatchdog = ServiceWatchdog(this, serviceScope, CHANNEL_ID).apply {
            setRestartCallback(object : ServiceWatchdog.RestartCallback {
                override fun onRestartNeeded() {
                    tryToRestartNotificationListenerService()
                }
            })
            start()
        }
        
        // Marcar que el servicio DEBERÍA estar corriendo (para watchdog)
        val prefs = getSharedPreferences("service_state", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("service_should_be_running", true).apply()
        
        // Iniciar heartbeat para watchdog externo
        startHeartbeat()
        
        Log.d(TAG, "Notificación RUNNING mostrada con botón DETENER")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Servicio en primer plano iniciado. Intent action: ${intent?.action}")
        
        // Verificar que los permisos estén habilitados antes de realizar cualquier acción
        if (!NotificationListenerService.isNotificationListenerEnabled(applicationContext)) {
            Log.w(TAG, "NotificationListenerService no está habilitado. Las acciones pueden fallar.")

        }
        
        intent?.let {
            when (it.action) {
                ACTION_RESTART_NOTIFICATION_LISTENER -> {
                    Log.d(TAG, "Acción de reinicio de NotificationListenerService recibida")
                    tryToRestartNotificationListenerService()
                }
                ACTION_FORCE_RESET -> {
                    Log.d(TAG, "Acción de reinicio forzado recibida")
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
    
    /**
     * Sistema de heartbeat para watchdog externo.
     * Actualiza timestamp cada 5 minutos para que WorkManager pueda verificar salud.
     */
    private fun startHeartbeat() {
        heartbeatJob = serviceScope.launch {
            val prefs = getSharedPreferences("service_state", Context.MODE_PRIVATE)
            
            while (isActive) {
                // Actualizar timestamp
                prefs.edit().putLong("service_last_heartbeat", System.currentTimeMillis()).apply()
                Log.d(TAG, "💓 Heartbeat actualizado")
                
                delay(HEARTBEAT_INTERVAL)
            }
        }
    }
    
    private fun performForceReset() {
        Log.w(TAG, "Delegando reinicio forzado al ServiceWatchdog")
        serviceScope.launch {
            serviceWatchdog?.performForceReset()
        }
    }
    
    // NOTA: Los siguientes métodos fueron movidos a ServiceWatchdog:
    // - startWatchdogTimer()
    // - schedulePeriodicChecks()
    // - performDeepReset()
    // - shouldRetryNow()
    // - calculateNextWatchdogInterval()
    // El ServiceWatchdog ahora encapsula toda la lógica de vigilancia y reinicio.
    
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
        serviceScope.launch {
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
                    
                    // Esperar para evitar problemas de concurrencia (non-blocking)
                    delay(300)
                    
                    packageManager.setComponentEnabledSetting(
                        componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error al cambiar estado del componente: ${e.message}")
                }
                
                // Esperar un momento para que los cambios se apliquen (non-blocking)
                delay(500)
                
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
    
    // REMOVED: Duplicate isNotificationListenerEnabled function (now using NotificationListenerService.isNotificationListenerEnabled)


    /**
     * Detecta cuando el usuario hace swipe para cerrar desde Recientes.
     * 
     * IMPORTANTE: 
     * - Este método se llama cuando el usuario elimina la app de la lista de Recientes.
     * - El SERVICIO SIGUE CORRIENDO porque es un ForegroundService.
     * - NO debemos mostrar notificación roja porque el servicio NO murió.
     * - Solo hacemos log para debugging.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "onTaskRemoved: Activity cerrada pero servicio sigue corriendo")
        
        // NO mostrar notificación roja - el servicio sigue activo.
        // La notificación verde ya está visible y el servicio sigue monitoreando.
        
        // Solo registrar el evento para diagnóstico
        val prefs = getSharedPreferences("service_state", Context.MODE_PRIVATE)
        prefs.edit().putLong("last_task_removed", System.currentTimeMillis()).apply()
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
        Log.w(TAG, "Servicio en primer plano destruido")
        
        // Detener heartbeat primero
        heartbeatJob?.cancel()
        
        // Detener el watchdog delegado
        serviceWatchdog?.stop()
        serviceWatchdog = null
        
        // Detener job de verificación periódica
        periodicCheckJob?.cancel()
        periodicCheckJob = null
        
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
        
        // Verificar estado actual
        val currentState = ServiceStateManager.getCurrentState(this)
        
        // Si el estado es STOPPED o DISABLED, el usuario detuvo intencionalmente
        // NO intentar reiniciar automáticamente
        if (currentState == ServiceStateManager.ServiceState.STOPPED || 
            currentState == ServiceStateManager.ServiceState.DISABLED) {
            Log.d(TAG, "Servicio detenido intencionalmente por usuario (estado: $currentState)")
            
            // Limpiar flag para que watchdog no lo detecte como muerte
            val prefs = getSharedPreferences("service_state", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("service_should_be_running", false).apply()
            
            return // Salir sin intentar reiniciar
        }
        
        // Si llegamos aquí, el servicio murió inesperadamente (camera, memory, etc)
        Log.w(TAG, "Servicio murió inesperadamente (camera, memory, etc)")
        
        // Solo mostrar notificación de STOPPED si no se ha mostrado ya
        if (ServiceStateManager.canShowStoppedNotification(this)) {
            ServiceNotificationManager(this).showStoppedNotification()
            ServiceStateManager.markStoppedNotificationShown(this)
            ServiceStateManager.setState(this, ServiceStateManager.ServiceState.STOPPED)
            Log.d(TAG, "Notificación STOPPED mostrada con opciones Reiniciar/Entendido")
        } else {
            Log.d(TAG, "Notificación STOPPED ya fue mostrada en esta sesión")
        }
        
        // Intentar reinicio automático (solo para muertes inesperadas)
        tryAutomaticRestart()
    }
    
    private fun tryAutomaticRestart() {
        // Solo reiniciar automáticamente si el estado es RUNNING
        if (ServiceStateManager.getCurrentState(this) != ServiceStateManager.ServiceState.RUNNING) {
            Log.d(TAG, "No reiniciar automáticamente - Usuario no quiere el servicio")
            return
        }
        
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
        
        // MÉTODO 2: AlarmManager como backup
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val restartIntent = Intent(applicationContext, ServiceRestartReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                applicationContext,
                1001,
                restartIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
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
                // Delegar al watchdog para reinicio profundo
                serviceScope.launch {
                    serviceWatchdog?.performDeepReset()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en verificación programada: ${e.message}")
        }
    }

    private fun startPeriodicChecks() {
        // Cancelar job existente si hay uno
        periodicCheckJob?.cancel()
        
        // Usar coroutine en lugar de Timer deprecated
        periodicCheckJob = serviceScope.launch {
            // Esperar 30 minutos iniciales
            delay(30 * 60 * 1000L)
            
            // Loop de verificación cada 3 horas
            while (isActive) {
                Log.d(TAG, "Realizando verificación programada del servicio...")
                checkNotificationService()
                delay(3 * 60 * 60 * 1000L) // Cada 3 horas
            }
        }
    }
}