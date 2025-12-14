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
import com.dynamictecnologies.notificationmanager.di.BluetoothMqttModule
import com.dynamictecnologies.notificationmanager.service.monitor.ServiceHealthMonitor
import com.dynamictecnologies.notificationmanager.service.strategy.*
import com.dynamictecnologies.notificationmanager.util.device.DeviceManufacturerDetector
import com.dynamictecnologies.notificationmanager.util.notification.ServiceCrashNotifier
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Servicio de escucha de notificaciones refactorizado siguiendo principios SOLID.
 * 
 * Buenas prácticas aplicadas:
 * - DI Manual: Usa BluetoothMqttModule para obtener dependencias
 */
class NotificationListenerService : NotificationListenerService() {
    
    companion object {
        private const val TAG = "NotificationListener"
        private const val PREFS_NAME = "notification_listener_prefs"
        private const val CACHE_TIMEOUT = 3000L
        
        /**
         * Comprueba si el servicio de escucha de notificaciones está habilitado
         */
        fun isNotificationListenerEnabled(context: Context): Boolean {
            return try {
                val packageName = context.packageName
                val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                
                if (flat.isNullOrEmpty()) return false
                
                val names = flat.split(":").filter { it.isNotEmpty() }
                names.any { name ->
                    val componentName = ComponentName.unflattenFromString(name)
                    componentName != null && TextUtils.equals(packageName, componentName.packageName)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error verificando permisos ${e.message}")
                false
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
                Log.e(TAG, "Error abriendo configuración: ${e.message}")
            }
        }
    }
    
    // Core components (Manual DI)
    private lateinit var repository: NotificationRepository
    private lateinit var sendNotificationUseCase: com.dynamictecnologies.notificationmanager.domain.usecases.device.SendNotificationToDeviceUseCase
    private lateinit var devicePairingRepository: com.dynamictecnologies.notificationmanager.domain.repositories.DevicePairingRepository
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // OEM-specific components (created locally)
    private lateinit var deviceDetector: DeviceManufacturerDetector
    private lateinit var serviceStrategy: BackgroundServiceStrategy
    private lateinit var crashNotifier: ServiceCrashNotifier
    private lateinit var healthMonitor: ServiceHealthMonitor
    
    // Deduplication cache
    private val recentNotifications = ConcurrentHashMap<String, Long>()
    
    // Summary patterns
    private val summaryPatterns = listOf(
        """\\d+ (?:nuevos? )?mensajes?(?: de \\d+ chats?)?""".toRegex(),
        "new messages?".toRegex(),
        "messages from".toRegex()
    )
    
    // Statistics
    private var notificationCounter = 0
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Servicio iniciado")
        
        initializeComponents()
        startForegroundService()
        startCacheCleaning()
        
        // Iniciar health monitoring con estrategia OEM
        healthMonitor.startMonitoring()
        
        // Registrar inicio
        getPrefs().edit()
            .putLong("last_service_start", System.currentTimeMillis())
            .putLong("service_start_count", getPrefs().getLong("service_start_count", 0) + 1)
            .apply()
        
        Log.d(TAG, "Fabricante detectado: ${deviceDetector.detectManufacturer()}")
        Log.d(TAG, "Estrategia: ${serviceStrategy.getStrategyName()}")
    }
    
    /**
     * Inicializa todos los componentes necesarios
     */
    private fun initializeComponents() {
        try {
            // Core - Repository
            val database = NotificationDatabase.getDatabase(applicationContext)
            repository = NotificationRepository(
                notificationDao = database.notificationDao(),
                context = applicationContext
            )
            
            // MQTT Components (manual DI)
            val mqttConnectionManager = BluetoothMqttModule.provideMqttConnectionManager(applicationContext)
            val mqttSender = BluetoothMqttModule.provideMqttNotificationSender(mqttConnectionManager)
            devicePairingRepository = BluetoothMqttModule.provideDevicePairingRepository(applicationContext)
            sendNotificationUseCase = BluetoothMqttModule.provideSendNotificationToDeviceUseCase(
                devicePairingRepository,
                mqttSender
            )
            
            // OEM detection
            deviceDetector = DeviceManufacturerDetector()
            val manufacturer = deviceDetector.detectManufacturer()
            
            // Strategy selection basado en fabricante
            serviceStrategy = when (manufacturer) {
                is com.dynamictecnologies.notificationmanager.util.device.DeviceManufacturer.Xiaomi -> 
                    XiaomiServiceStrategy()
                is com.dynamictecnologies.notificationmanager.util.device.DeviceManufacturer.Samsung -> 
                    SamsungServiceStrategy()
                is com.dynamictecnologies.notificationmanager.util.device.DeviceManufacturer.Huawei -> 
                    HuaweiServiceStrategy()
                is com.dynamictecnologies.notificationmanager.util.device.DeviceManufacturer.OnePlus -> 
                    OnePlusServiceStrategy()
                else -> 
                    GenericServiceStrategy()
            }
            
            // Monitoring components
            crashNotifier = ServiceCrashNotifier(applicationContext)
            healthMonitor = ServiceHealthMonitor(
                context = applicationContext,
                strategy = serviceStrategy,
                crashNotifier = crashNotifier,
                scope = serviceScope
            )
            
            Log.d(TAG, "Componentes inicializados correctamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando componentes: ${e.message}", e)
        }
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!shouldProcessNotification(sbn)) return
        
        notificationCounter++
        
        // Registrar actividad
        getPrefs().edit()
            .putLong("last_notification_received", System.currentTimeMillis())
            .apply()
        
        processNotification(sbn)
    }
    
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Listener conectado")
        
        getPrefs().edit()
            .putLong("last_connection_time", System.currentTimeMillis())
            .apply()
        
        // Descartar notificaciones de crash al conectar exitosamente
        crashNotifier.dismissAllNotifications()
    }
    
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Listener desconectado")
        
        // Intentar reconectar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(ComponentName(this, NotificationListenerService::class.java))
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Servicio destruido")
        
        healthMonitor.stopMonitoring()
        serviceScope.cancel()
    }
    
    /**
     * Inicia el servicio en primer plano
     */
    private fun startForegroundService() {
        try {
            val intent = Intent(this, NotificationForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando foreground service: ${e.message}")
        }
    }
    
    /**
     * Inicia limpieza periódica del caché
     */
    private fun startCacheCleaning() {
        serviceScope.launch {
            while (isActive) {
                try {
                    cleanOldCache()
                    delay(CACHE_TIMEOUT)
                } catch (e: Exception) {
                    Log.e(TAG, "Error limpiando caché: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Limpia entradas antiguas del caché
     */
    private fun cleanOldCache() {
        val currentTime = System.currentTimeMillis()
        recentNotifications.entries.removeIf { 
            currentTime - it.value > CACHE_TIMEOUT 
        }
    }
    
    /**
     * Determina si una notificación debe ser procesada
     */
    private fun shouldProcessNotification(sbn: StatusBarNotification): Boolean {
        // Ignorar notificaciones de nuestra app
        if (sbn.packageName == packageName) return false
        
        val notification = sbn.notification ?: return false
        val extras = notification.extras ?: return false
        
        val title = extras.getString(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        
        if (title.isNullOrEmpty() && text.isNullOrEmpty()) return false
        
        // FIX: Verificar app seleccionada solo por package name
        val selectedPackageName = getSelectedApp() ?: return false
        
        // Solo procesar si el packageName coincide exactamente con la app seleccionada
        if (sbn.packageName != selectedPackageName) return false
        
        // Ignorar resumen
        if (text != null && isSummaryNotification(notification, text)) return false
        
        // Verificar duplicados
        val key = createUniqueKey(sbn.packageName, title ?: "", text ?: "", sbn.postTime)
        if (isDuplicate(key)) return false
        
        recentNotifications[key] = System.currentTimeMillis()
        return true
    }
    
    /**
     * Procesa y guarda una notificación
     */
    private fun processNotification(sbn: StatusBarNotification) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val extras = sbn.notification.extras
                val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                val appName = getAppName(sbn.packageName)
                
                val notificationInfo = com.dynamictecnologies.notificationmanager.data.model.NotificationInfo(
                    appName = appName,
                    title = title,
                    content = text,
                    timestamp = Date(sbn.postTime)
                )
                
                // Guardar en base de datos local
                repository.insertNotification(notificationInfo)
                Log.d(TAG, "Notificación guardada localmente: ${notificationInfo.title}")
                
                // Enviar a ESP32 vinculado (nuevo flujo simplificado)
                if (devicePairingRepository.hasPairedDevice()) {
                    sendNotificationUseCase(notificationInfo).onSuccess {
                        Log.d(TAG, "Notificación enviada a ESP32 via MQTT")
                    }.onFailure { error ->
                        Log.w(TAG, "No se pudo enviar a ESP32: ${error.message}")
                    }
                } else {
                    Log.d(TAG, "Sin dispositivo ESP32 vinculado, notificación no enviada")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando notificación: ${e.message}", e)
            }
        }
    }
    
    /**
     * Helpers
     */
    private fun getPrefs() = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private fun getSelectedApp() = 
        getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
            .getString("last_selected_app", null)
    
    private fun getAppName(packageName: String): String {
        return try {
            val pm = applicationContext.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) {
            packageName
        }
    }
    
    private fun isSummaryNotification(notification: Notification, text: String): Boolean {
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return true
        return summaryPatterns.any { it.matches(text) }
    }
    
    private fun createUniqueKey(pkg: String, title: String, text: String, time: Long) = 
        "$pkg:$title:$text:${time / 1000}"
    
    private fun isDuplicate(key: String): Boolean {
        val lastTime = recentNotifications[key] ?: return false
        return System.currentTimeMillis() - lastTime < CACHE_TIMEOUT
    }
}