package com.dynamictecnologies.notificationmanager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dynamictecnologies.notificationmanager.MainActivity
import com.dynamictecnologies.notificationmanager.R
import com.dynamictecnologies.notificationmanager.util.BatteryOptimizationHelper
import com.dynamictecnologies.notificationmanager.data.datasource.mqtt.MqttConnectionManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
    
    // Heartbeat para watchdog externo (WorkManager backup)
    private var heartbeatJob: Job? = null
    private val HEARTBEAT_INTERVAL = 5 * 60 * 1000L // Actualizar cada 5 minutos
    
    // Observer para detectar cambios de permisos
    private var permissionObserver: ContentObserver? = null
    
    // Receiver para detectar cambios de estado de energía (Power Save, Doze)
    private var powerStateReceiver: BroadcastReceiver? = null
    
    // Receiver para detectar cambios de red (WiFi/Datos) - Patrón Observer
    private var networkChangeReceiver: BroadcastReceiver? = null
    
    // Job para renovación periódica del WakeLock
    private var wakeLockRenewalJob: Job? = null
    private val WAKELOCK_RENEWAL_INTERVAL = 8 * 60 * 60 * 1000L // Renovar cada 8 horas
    
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
        
        // Registrar observer para cambios de permisos (OBSERVER PATTERN)
        registerPermissionObserver()
        
        // Registrar receiver para cambios de estado de energía (Power Save, Doze)
        registerPowerStateObserver()
        
        // Registrar receiver para cambios de red (WiFi/Datos) - Observer Pattern
        registerNetworkObserver()
        val prefs = getSharedPreferences("service_state", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("service_should_be_running", true).apply()
        
        // Iniciar heartbeat para watchdog externo (WorkManager backup)
        startHeartbeat()
        
        // Iniciar renovación periódica del WakeLock
        startWakeLockRenewal()
        
        // Verificar exención de optimización de batería
        checkBatteryOptimization()
        
        Log.d(TAG, "Notificación RUNNING mostrada con botón DETENER")
    }
    
    /**
     * Registra un ContentObserver para detectar cambios en permisos de NotificationListener.
     * Esto permite reaccionar INMEDIATAMENTE cuando el permiso es revocado (patrón Observer).
     */
    private fun registerPermissionObserver() {
        permissionObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                Log.d(TAG, "Cambio detectado en permisos de NotificationListener")
                checkPermissionState()
            }
        }
        
        try {
            contentResolver.registerContentObserver(
                Settings.Secure.getUriFor("enabled_notification_listeners"),
                false,
                permissionObserver!!
            )
            Log.d(TAG, "✅ ContentObserver registrado para permisos")
        } catch (e: Exception) {
            Log.e(TAG, "Error registrando ContentObserver: ${e.message}")
        }
    }
    
    /**
     * Verifica el estado actual de permisos y actualiza la notificación acordemente.
     * Llamado por el ContentObserver cuando detecta cambios.
     */
    private fun checkPermissionState() {
        val hasPermission = NotificationListenerService.isNotificationListenerEnabled(this)
        
        if (!hasPermission) {
            Log.w(TAG, "⚠️ Permiso de NotificationListener revocado")
            
            // OBSERVER: Permiso revocado → 🟡 Amarillo
            ServiceStateManager.setDegradedState(this, ServiceStateManager.DegradedReason.PERMISSION_REVOKED)
            
            // Detener el foreground para quitar la notificación verde
            stopForeground(STOP_FOREGROUND_REMOVE)
            
            // Mostrar notificación amarilla
            ServiceNotificationManager(this).showStoppedNotification(
                ServiceNotificationManager.StopReason.PERMISSION_REVOKED
            )
            
            // Detener el servicio (ya no puede funcionar sin permisos)
            stopSelf()
        } else {
            // Si el permiso fue restaurado y estábamos en DEGRADED, volver a RUNNING
            val currentState = ServiceStateManager.getCurrentState(this)
            if (currentState == ServiceStateManager.ServiceState.DEGRADED) {
                Log.d(TAG, "✅ Permiso restaurado - volviendo a estado RUNNING")
                ServiceStateManager.setState(this, ServiceStateManager.ServiceState.RUNNING)
                ServiceNotificationManager(this).showRunningNotification()
            }
        }
    }
    
    /**
     * Registra un BroadcastReceiver para detectar cambios en el estado de energía.
     * Detecta Power Save Mode y Doze Mode (patrón Observer).
     */
    private fun registerPowerStateObserver() {
        powerStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                        Log.d(TAG, "Cambio detectado en Power Save Mode")
                        checkPowerState()
                    }
                    PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> {
                        Log.d(TAG, "Cambio detectado en Doze Mode")
                        checkPowerState()
                    }
                }
            }
        }
        
        try {
            val filter = IntentFilter().apply {
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
                }
            }
            registerReceiver(powerStateReceiver, filter)
            Log.d(TAG, "✅ BroadcastReceiver registrado para estados de energía")
        } catch (e: Exception) {
            Log.e(TAG, "Error registrando BroadcastReceiver: ${e.message}")
        }
    }
    
    /**
     * Verifica el estado actual de energía (Power Save, Doze).
     * Solo muestra advertencia, no detiene el servicio.
     */
    private fun checkPowerState() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        
        val isPowerSave = powerManager.isPowerSaveMode
        val isDoze = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isDeviceIdleMode
        } else false
        
        if (isPowerSave || isDoze) {
            Log.w(TAG, "⚠️ Modo ahorro detectado: PowerSave=$isPowerSave, Doze=$isDoze")
            
            // Solo mostrar advertencia si el servicio sigue corriendo
            val currentState = ServiceStateManager.getCurrentState(this)
            if (currentState == ServiceStateManager.ServiceState.RUNNING) {
                // Actualizar notificación a amarillo pero NO detener servicio
                ServiceNotificationManager(this).showStoppedNotification(
                    ServiceNotificationManager.StopReason.POWER_RESTRICTED
                )
            }
        } else {
            // Si salimos del modo ahorro, restaurar notificación verde
            val currentState = ServiceStateManager.getCurrentState(this)
            if (currentState == ServiceStateManager.ServiceState.RUNNING) {
                Log.d(TAG, "✅ Modo ahorro desactivado - restaurando estado normal")
                ServiceNotificationManager(this).showRunningNotification()
            }
        }
    }
    
    /**
     * Registra un BroadcastReceiver para detectar cambios de conectividad.
     * Permite reconectar MQTT inmediatamente cuando cambia la red (patrón Observer).
     */
    @Suppress("DEPRECATION")
    private fun registerNetworkObserver() {
        networkChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == ConnectivityManager.CONNECTIVITY_ACTION) {
                    Log.d(TAG, "Cambio de conectividad detectado")
                    checkNetworkState()
                }
            }
        }
        
        try {
            val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
            registerReceiver(networkChangeReceiver, filter)
            Log.d(TAG, "✅ BroadcastReceiver registrado para cambios de red")
        } catch (e: Exception) {
            Log.e(TAG, "Error registrando BroadcastReceiver de red: ${e.message}")
        }
    }
    
    /**
     * Verifica el estado de red y fuerza reconexión MQTT si hay conexión disponible.
     */
    private fun checkNetworkState() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        val isConnected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork
            val capabilities = cm.getNetworkCapabilities(network)
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
        
        if (isConnected) {
            Log.d(TAG, "✅ Red disponible - solicitando reconexión MQTT")
            // Enviar broadcast para que MqttConnectionManager reconecte
            val reconnectIntent = Intent("com.dynamictecnologies.notificationmanager.MQTT_RECONNECT")
            sendBroadcast(reconnectIntent)
        } else {
            Log.w(TAG, "⚠️ Sin conexión de red")
        }
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
    
    /**
     * Sistema de renovación periódica del WakeLock.
     * Evita que el WakeLock expire después de 10 horas.
     */
    private fun startWakeLockRenewal() {
        wakeLockRenewalJob = serviceScope.launch {
            while (isActive) {
                delay(WAKELOCK_RENEWAL_INTERVAL)
                renewWakeLock()
            }
        }
    }
    
    /**
     * Renueva el WakeLock para evitar que expire.
     */
    private fun renewWakeLock() {
        try {
            // Liberar WakeLock actual si está held
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "WakeLock anterior liberado")
                }
            }
            
            // Adquirir nuevo WakeLock
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "NotificationManager::ServiceWakeLock"
            )
            wakeLock?.acquire(10 * 60 * 60 * 1000L) // 10 horas
            Log.d(TAG, "🔋 WakeLock renovado exitosamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error renovando WakeLock: ${e.message}", e)
        }
    }
    
    /**
     * Verifica si la app tiene exención de optimización de batería.
     * Si no la tiene, solicita al usuario que la conceda.
     */
    private fun checkBatteryOptimization() {
        if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)) {
            Log.w(TAG, "⚠️ App NO está exenta de optimización de batería - servicio puede detenerse en Doze mode")
            
            // Guardar que necesitamos solicitar exención (para mostrar en UI)
            val prefs = getSharedPreferences("service_state", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("needs_battery_exemption", true).apply()
            
            // Intentar solicitar exención directamente
            // NOTA: Esto abrirá un diálogo del sistema
            serviceScope.launch(Dispatchers.Main) {
                try {
                    BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(applicationContext)
                } catch (e: Exception) {
                    Log.e(TAG, "Error solicitando exención de batería: ${e.message}", e)
                }
            }
        } else {
            Log.d(TAG, "✅ App está exenta de optimización de batería")
            val prefs = getSharedPreferences("service_state", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("needs_battery_exemption", false).apply()
        }
    }
    
    private fun performForceReset() {
        Log.w(TAG, "Realizando reinicio forzado del servicio")
        serviceScope.launch {
            try {
                val componentName = ComponentName(applicationContext, NotificationListenerService::class.java)
                packageManager.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
                delay(1000)
                packageManager.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                Log.d(TAG, "Reinicio forzado completado")
            } catch (e: Exception) {
                Log.e(TAG, "Error en reinicio forzado: ${e.message}")
            }
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
        
        // Desregistrar observer de permisos
        permissionObserver?.let {
            contentResolver.unregisterContentObserver(it)
            Log.d(TAG, "ContentObserver desregistrado")
        }
        permissionObserver = null
        
        // Desregistrar receiver de estados de energía
        powerStateReceiver?.let {
            try {
                unregisterReceiver(it)
                Log.d(TAG, "BroadcastReceiver de energía desregistrado")
            } catch (e: Exception) {
                Log.w(TAG, "Error desregistrando BroadcastReceiver: ${e.message}")
            }
        }
        powerStateReceiver = null
        
        // Desregistrar receiver de cambios de red
        networkChangeReceiver?.let {
            try {
                unregisterReceiver(it)
                Log.d(TAG, "BroadcastReceiver de red desregistrado")
            } catch (e: Exception) {
                Log.w(TAG, "Error desregistrando BroadcastReceiver de red: ${e.message}")
            }
        }
        networkChangeReceiver = null
        
        // Detener heartbeat
        heartbeatJob?.cancel()
        heartbeatJob = null
        
        // Detener job de renovación de WakeLock
        wakeLockRenewalJob?.cancel()
        wakeLockRenewalJob = null
        
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
            currentState == ServiceStateManager.ServiceState.DISABLED ||
            currentState == ServiceStateManager.ServiceState.DEGRADED) {
            Log.d(TAG, "Servicio detenido intencionalmente o degradado (estado: $currentState)")
            
            // Limpiar flag para que watchdog no lo detecte como muerte
            val prefs = getSharedPreferences("service_state", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("service_should_be_running", false).apply()
            
            return // Salir sin intentar reiniciar
        }
        
        // Si llegamos aquí, el servicio murió inesperadamente
        Log.w(TAG, "Servicio murió inesperadamente")
        
        // OBSERVER: Muerte inesperada → 🔴 Rojo
        ServiceStateManager.setState(this, ServiceStateManager.ServiceState.STOPPED)
        ServiceNotificationManager(this).showStoppedNotification(
            ServiceNotificationManager.StopReason.UNEXPECTED
        )
        
        // Intentar reinicio automático
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
        // Esta función ya no es necesaria - usamos ContentObserver
        // Se mantiene por compatibilidad con ACTION_SCHEDULED_CHECK
        checkPermissionState()
    }

}