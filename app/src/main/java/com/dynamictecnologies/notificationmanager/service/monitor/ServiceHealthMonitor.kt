package com.dynamictecnologies.notificationmanager.service.monitor

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.dynamictecnologies.notificationmanager.service.strategy.BackgroundServiceStrategy
import com.dynamictecnologies.notificationmanager.util.device.DeviceManufacturerDetector
import com.dynamictecnologies.notificationmanager.util.notification.ServiceCrashNotifier
import com.dynamictecnologies.notificationmanager.util.notification.ServiceStopReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Estados de salud del servicio
 */
sealed class ServiceHealth {
    object Healthy : ServiceHealth()
    data class Degraded(val reason: String, val since: Long) : ServiceHealth()
    data class Critical(val reason: String, val lastActivity: Long) : ServiceHealth()
    object Dead : ServiceHealth()
}

/**
 * Monitor de salud del servicio de notificaciones.
 * 
 * Principios aplicados:
 * - SRP: Solo monitorea la salud del servicio
 * - DIP: Depende de abstracciones (Strategy, Notifier)
 * - OCP: Extensible con nuevas métricas de salud
 */
class ServiceHealthMonitor(
    private val context: Context,
    private val strategy: BackgroundServiceStrategy,
    private val crashNotifier: ServiceCrashNotifier,
    private val scope: CoroutineScope
) {
    
    companion object {
        private const val TAG = "ServiceHealthMonitor"
        private const val PREFS_NAME = "service_health_prefs"
        
        // Timeouts según criticidad
        private const val DEGRADED_TIMEOUT = 30 * 60 * 1000L  // 30 min sin actividad
        private const val CRITICAL_TIMEOUT = 60 * 60 * 1000L  // 1 hora sin actividad
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val manufacturerDetector = DeviceManufacturerDetector()
    
    private var isMonitoring = false
    private var currentHealth: ServiceHealth = ServiceHealth.Healthy
    
    /**
     * Inicia el monitoreo de salud
     */
    fun startMonitoring() {
        if (isMonitoring) {
            Log.d(TAG, "Monitoreo ya está activo")
            return
        }
        
        isMonitoring = true
        Log.d(TAG, "Iniciando monitoreo de salud del servicio")
        
        val manufacturer = manufacturerDetector.detectManufacturer()
        Log.d(TAG, "Dispositivo detectado: $manufacturer")
        Log.d(TAG, "Estrategia: ${strategy.getStrategyName()}")
        Log.d(TAG, "Intervalo de verificación: ${strategy.getOptimalCheckInterval() / 1000}s")
        
        scope.launch {
            while (isActive) {
                try {
                    val health = checkServiceHealth()
                    handleHealthStatus(health)
                    
                    delay(strategy.getOptimalCheckInterval())
                } catch (e: Exception) {
                    Log.e(TAG, "Error en monitoreo de salud: ${e.message}", e)
                    delay(60 * 1000L) // Esperar 1 minuto si hay error
                }
            }
        }
    }
    
    /**
     * Detiene el monitoreo
     */
    fun stopMonitoring() {
        isMonitoring = false
        Log.d(TAG, "Monitoreo de salud detenido")
    }
    
    /**
     * Verifica la salud actual del servicio
     */
    fun checkServiceHealth(): ServiceHealth {
        val now = System.currentTimeMillis()
        val lastNotification = prefs.getLong("last_notification_received", 0)
        val lastConnection = prefs.getLong("last_connection_time", 0)
        val isListenerEnabled = com.dynamictecnologies.notificationmanager.service.NotificationListenerService
            .isNotificationListenerEnabled(context)
        
        // Si el listener no está habilitado
        if (!isListenerEnabled) {
            Log.w(TAG, "Servicio MUERTO: Listener deshabilitado")
            return ServiceHealth.Dead
        }
        
        // Si nunca ha recibido notificaciones
        if (lastNotification == 0L) {
            // Verificar cuánto tiempo lleva conectado
            if (lastConnection > 0 && now - lastConnection > CRITICAL_TIMEOUT * 2) {
                Log.w(TAG, "Servicio CRÍTICO: Conectado pero sin recibir notificaciones por ${(now - lastConnection) / 1000 / 60} minutos")
                return ServiceHealth.Critical("Sin notificaciones desde conexión", lastConnection)
            }
            return ServiceHealth.Healthy // Recién iniciado
        }
        
        val timeSinceLastNotif = now - lastNotification
        
        return when {
            timeSinceLastNotif > CRITICAL_TIMEOUT -> {
                Log.w(TAG, "Servicio CRÍTICO: ${timeSinceLastNotif / 1000 / 60} minutos sin notificaciones")
                ServiceHealth.Critical("Sin notificaciones", lastNotification)
            }
            timeSinceLastNotif > DEGRADED_TIMEOUT -> {
                Log.w(TAG, "Servicio DEGRADADO: ${timeSinceLastNotif / 1000 / 60} minutos sin notificaciones")
                ServiceHealth.Degraded("Poca actividad", lastNotification)
            }
            else -> {
                ServiceHealth.Healthy
            }
        }
    }
    
    /**
     * Maneja el estado de salud detectado
     */
    private fun handleHealthStatus(health: ServiceHealth) {
        val previousHealth = currentHealth
        currentHealth = health
        
        when (health) {
            is ServiceHealth.Healthy -> {
                if (previousHealth != ServiceHealth.Healthy) {
                    Log.i(TAG, "✅ Servicio restaurado a salud normal")
                    crashNotifier.dismissAllNotifications()
                }
            }
            
            is ServiceHealth.Degraded -> {
                if (previousHealth is ServiceHealth.Healthy) {
                    Log.w(TAG, "⚠️ Servicio degradado: ${health.reason}")
                    // No notificar todavía, solo logear
                }
            }
            
            is ServiceHealth.Critical -> {
                Log.e(TAG, "🔴 Servicio en estado crítico: ${health.reason}")
                
                // Determinar razón probable en base al fabricante
                val restrictionInfo = manufacturerDetector.getRestrictionInfo()
                val reason = if (restrictionInfo.hasAggressiveKilling) {
                    ServiceStopReason.ManufacturerRestriction
                } else {
                    ServiceStopReason.BatteryOptimization
                }
                
                // Notificar al usuario solo si debe
                if (crashNotifier.shouldShowCrashNotification()) {
                    crashNotifier.showCrashNotification(reason, health.lastActivity)
                }
            }
            
            is ServiceHealth.Dead -> {
                Log.e(TAG, "💀 Servicio MUERTO - Listener deshabilitado")
                
                if (crashNotifier.shouldShowCrashNotification()) {
                    crashNotifier.showCrashNotification(
                        ServiceStopReason.PermissionRevoked,
                        prefs.getLong("last_notification_received", 0)
                    )
                }
            }
        }
    }
    
    /**
     * Reporta métricas de salud
     */
    fun reportHealthMetrics(): Map<String, Any> {
        val now = System.currentTimeMillis()
        val lastNotif = prefs.getLong("last_notification_received", 0)
        val lastConn = prefs.getLong("last_connection_time", 0)
        
        return mapOf<String, Any>(
            "currentHealth" to (currentHealth::class.simpleName ?: "Unknown"),
            "isMonitoring" to isMonitoring,
            "manufacturer" to manufacturerDetector.detectManufacturer().toString(),
            "strategy" to strategy.getStrategyName(),
            "checkInterval" to strategy.getOptimalCheckInterval(),
            "timeSinceLastNotification" to if (lastNotif > 0) now - lastNotif else -1L,
            "timeSinceConnection" to if (lastConn > 0) now - lastConn else -1L,
            "lastNotificationTime" to lastNotif,
            "lastConnectionTime" to lastConn
        )
    }
    
    /**
     * Obtiene el estado de salud actual
     */
    fun getCurrentHealth(): ServiceHealth = currentHealth
}
