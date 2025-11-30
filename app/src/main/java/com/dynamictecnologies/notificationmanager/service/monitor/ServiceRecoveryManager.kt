package com.dynamictecnologies.notificationmanager.service.monitor

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.dynamictecnologies.notificationmanager.service.NotificationForegroundService
import com.dynamictecnologies.notificationmanager.service.NotificationListenerService
import com.dynamictecnologies.notificationmanager.service.strategy.BackgroundServiceStrategy
import com.dynamictecnologies.notificationmanager.util.notification.ServiceCrashNotifier
import kotlinx.coroutines.delay

/**
 * Estrategias de recuperación del servicio
 */
sealed class RecoveryStrategy {
    object SoftRestart : RecoveryStrategy()
    object ForceRestart : RecoveryStrategy()
    object DeepReset : RecoveryStrategy()
    object ManufacturerSpecific : RecoveryStrategy()
}

/**
 * Resultado de un intento de recuperación
 */
sealed class RecoveryResult {
    object Success : RecoveryResult()
    data class Failure(val reason: String) : RecoveryResult()
    object InProgress : RecoveryResult()
}

/**
 * Gestor de recuperación del servicio.
 * 
 * Principios aplicados:
 * - SRP: Solo maneja la recuperación del servicio
 * - Strategy Pattern: Diferentes estrategias de recuperación
 * - DIP: Depende de abstracciones
 */
class ServiceRecoveryManager(
    private val context: Context,
    private val strategy: BackgroundServiceStrategy,
    private val crashNotifier: ServiceCrashNotifier
) {
    
    companion object {
        private const val TAG = "ServiceRecoveryManager"
        private const val PREFS_NAME = "service_recovery_prefs"
    }
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var currentAttempt = 0
    private var isRecovering = false
    
    /**
     * Intenta recuperar el servicio usando la estrategia especificada
     */
    suspend fun attemptRecovery(recoveryStrategy: RecoveryStrategy = RecoveryStrategy.SoftRestart): RecoveryResult {
        if (isRecovering) {
            Log.d(TAG, "Ya hay una recuperación en progreso")
            return RecoveryResult.InProgress
        }
        
        isRecovering = true
        currentAttempt++
        
        Log.i(TAG, "Intentando recuperación #$currentAttempt con estrategia: ${recoveryStrategy::class.simpleName}")
        
        // Mostrar notificación de recuperación
        crashNotifier.showRecoveryNotification(currentAttempt)
        
        val result = try {
            when (recoveryStrategy) {
                is RecoveryStrategy.SoftRestart -> performSoftRestart()
                is RecoveryStrategy.ForceRestart -> performForceRestart()
                is RecoveryStrategy.DeepReset -> performDeepReset()
                is RecoveryStrategy.ManufacturerSpecific -> performManufacturerSpecificRecovery()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error durante recuperación: ${e.message}", e)
            RecoveryResult.Failure(e.message ?: "Error desconocido")
        } finally {
            isRecovering = false
        }
        
        // Actualizar métricas
        prefs.edit()
            .putLong("last_recovery_attempt", System.currentTimeMillis())
            .putInt("recovery_attempt_count", currentAttempt)
            .putString("last_recovery_strategy", recoveryStrategy::class.simpleName)
            .apply()
        
        return result
    }
    
    /**
     * Reinicio suave - solo RequestRebind
     */
    private suspend fun performSoftRestart(): RecoveryResult {
        Log.d(TAG, "Ejecutando SoftRestart...")
        
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val componentName = ComponentName(context, NotificationListenerService::class.java)
                context.packageManager.getPackageInfo(context.packageName, 0)
                
                // Simular requestRebind a través del servicio
                val intent = Intent(context, NotificationForegroundService::class.java)
                intent.action = NotificationForegroundService.ACTION_RESTART_NOTIFICATION_LISTENER
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                
                delay(2000) // Esperar 2 segundos
                
                RecoveryResult.Success
            } else {
                performForceRestart()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en SoftRestart: ${e.message}")
            RecoveryResult.Failure(e.message ?: "SoftRestart failed")
        }
    }
    
    /**
     * Reinicio forzado - Disable/Enable component
     */
    private suspend fun performForceRestart(): RecoveryResult {
        Log.d(TAG, "Ejecutando ForceRestart...")
        
        return try {
            val componentName = ComponentName(context, NotificationListenerService::class.java)
            val pm = context.packageManager
            
            // Deshabilitar
            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            
            delay(1000)
            
            // Habilitar
            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            
            delay(1000)
            
            // Intentar iniciar el servicio
            val intent = Intent(context, NotificationForegroundService::class.java)
            intent.action = NotificationForegroundService.ACTION_RESTART_NOTIFICATION_LISTENER
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            
            delay(2000)
            
            RecoveryResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "Error en ForceRestart: ${e.message}")
            RecoveryResult.Failure(e.message ?: "ForceRestart failed")
        }
    }
    
    /**
     * Reinicio profundo - Limpia estado y reinicia
     */
    private suspend fun performDeepReset(): RecoveryResult {
        Log.d(TAG, "Ejecutando DeepReset...")
        
        return try {
            val componentName = ComponentName(context, NotificationListenerService::class.java)
            val pm = context.packageManager
            
            // Deshabilitar completamente
            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            
            delay(2000)
            
            // Limpiar estado del servicio
            val servicePrefs = context.getSharedPreferences("notification_listener_prefs", Context.MODE_PRIVATE)
            servicePrefs.edit()
                .remove("last_connection_time")
                .remove("last_notification_received")
                .putLong("deep_reset_time", System.currentTimeMillis())
                .putInt("deep_reset_count", servicePrefs.getInt("deep_reset_count", 0) + 1)
                .apply()
            
            delay(1000)
            
            // Habilitar nuevamente
            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            
            delay(2000)
            
            // Forzar inicio del ForegroundService
            val intent = Intent(context, NotificationForegroundService::class.java)
            intent.action = NotificationForegroundService.ACTION_FORCE_RESET
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            
            delay(3000)
            
            RecoveryResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "Error en DeepReset: ${e.message}")
            RecoveryResult.Failure(e.message ?: "DeepReset failed")
        }
    }
    
    /**
     * Recuperación específica del fabricante
     */
    private suspend fun performManufacturerSpecificRecovery(): RecoveryResult {
        Log.d(TAG, "Ejecutando recuperación específica del fabricante...")
        
        // Para fabricantes agresivos, usar DeepReset
        // Para otros, ForceRestart es suficiente
        return if (strategy.getForegroundServiceNotificationPriority() == 
            com.dynamictecnologies.notificationmanager.service.strategy.NotificationPriority.HIGH) {
            performDeepReset()
        } else {
            performForceRestart()
        }
    }
    
    /**
     * Determina la estrategia apropiada según el número de intentos
     */
    fun getAppropriateStrategy(): RecoveryStrategy {
        return when {
            currentAttempt == 0 -> RecoveryStrategy.SoftRestart
            currentAttempt < strategy.getMaxRetries() -> RecoveryStrategy.ForceRestart
            currentAttempt < strategy.getMaxRetries() * 2 -> RecoveryStrategy.DeepReset
            else -> RecoveryStrategy.ManufacturerSpecific
        }
    }
    
    /**
     * Resetea el contador de intentos
     */
    fun resetAttempts() {
        currentAttempt = 0
        Log.d(TAG, "Contador de intentos reseteado")
    }
    
    /**
     * Verifica si se debe notificar al usuario
     */
    fun shouldNotifyUser(): Boolean {
        return currentAttempt >= strategy.getMaxRetries()
    }
    
    /**
     * Obtiene métricas de recuperación
     */
    fun getRecoveryMetrics(): Map<String, Any> {
        return mapOf<String, Any>(
            "currentAttempt" to currentAttempt,
            "isRecovering" to isRecovering,
            "maxRetries" to strategy.getMaxRetries(),
            "lastAttempt" to prefs.getLong("last_recovery_attempt", 0),
            "totalAttempts" to prefs.getInt("recovery_attempt_count", 0),
            "lastStrategy" to (prefs.getString("last_recovery_strategy", "None") ?: "None")
        )
    }
}
