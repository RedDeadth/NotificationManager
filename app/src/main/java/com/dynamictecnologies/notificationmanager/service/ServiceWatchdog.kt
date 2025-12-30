package com.dynamictecnologies.notificationmanager.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dynamictecnologies.notificationmanager.MainActivity
import com.dynamictecnologies.notificationmanager.R
import kotlinx.coroutines.*

/**
 * Watchdog Timer para monitoreo del NotificationListenerService.
 * 
 * Responsabilidad única: Vigilar el estado del servicio de notificaciones
 * e intentar recuperarlo cuando se detectan problemas.
 * 
 * Estrategias de recuperación:
 * - Reinicio simple: toggle del componente
 * - Reinicio forzado: deshabilitar/esperar/habilitar
 * - Reinicio profundo: limpiar estado + reinicio completo
 * 
 * Implementa exponential backoff para evitar reintentos excesivos.
 * 
 * @see NotificationForegroundService
 */
class ServiceWatchdog(
    private val context: Context,
    private val scope: CoroutineScope,
    private val channelId: String
) {
    companion object {
        private const val TAG = "ServiceWatchdog"
        
        // Constante para el tiempo máximo que puede pasar sin servicios (12 horas)
        private const val MAX_TIME_WITHOUT_SERVICE = 12 * 60 * 60 * 1000L
        
        // Tiempo sin notificaciones que dispara el watchdog (1 hora)
        private const val NO_NOTIFICATION_THRESHOLD = 1 * 60 * 60 * 1000L
        
        // Tiempo de conexión antigua sin notificaciones (2 horas)
        private const val OLD_CONNECTION_THRESHOLD = 2 * 60 * 60 * 1000L
        
        // Tiempo para verificación periódica (3 horas)
        private const val PERIODIC_CHECK_INTERVAL = 3 * 60 * 60 * 1000L
        
        // Tiempo crítico sin notificaciones para reinicio forzado (6 horas)
        private const val CRITICAL_THRESHOLD = 6 * 60 * 60 * 1000L
        
        // Notification ID para estado
        private const val NOTIFICATION_ID_STATUS = 1000
    }
    
    // Exponential backoff para reintentos (en minutos)
    private val retryIntervals = listOf(2, 5, 15, 30, 60)
    private var currentRetryAttempt = 0
    private var lastRetryTime = 0L
    
    private var watchdogJob: Job? = null
    private var periodicCheckJob: Job? = null
    
    /**
     * Callback para notificar al servicio cuando se necesita un reinicio.
     */
    interface RestartCallback {
        fun onRestartNeeded()
    }
    
    private var restartCallback: RestartCallback? = null
    
    /**
     * Configura el callback para notificaciones de reinicio.
     */
    fun setRestartCallback(callback: RestartCallback) {
        restartCallback = callback
    }
    
    /**
     * Inicia el temporizador watchdog.
     * Monitorea el estado del NotificationListenerService y toma acciones correctivas.
     */
    fun start() {
        Log.d(TAG, "Iniciando watchdog timer")
        
        watchdogJob = scope.launch {
            while (isActive) {
                try {
                    performWatchdogCheck()
                } catch (e: Exception) {
                    Log.e(TAG, "Error en verificación watchdog: ${e.message}", e)
                }
                
                delay(calculateNextWatchdogInterval())
            }
        }
        
        // Programar verificaciones periódicas independientes del watchdog
        startPeriodicChecks()
    }
    
    /**
     * Detiene el watchdog y libera recursos.
     */
    fun stop() {
        Log.d(TAG, "Deteniendo watchdog timer")
        watchdogJob?.cancel()
        watchdogJob = null
        periodicCheckJob?.cancel()
        periodicCheckJob = null
    }
    
    /**
     * Realiza la verificación principal del watchdog.
     */
    private suspend fun performWatchdogCheck() {
        val isListenerEnabled = NotificationListenerService.isNotificationListenerEnabled(context)
        
        Log.d(TAG, "Watchdog check: NotificationListenerService habilitado = $isListenerEnabled")
        
        if (!isListenerEnabled) {
            Log.w(TAG, "NotificationListenerService no habilitado, notificando para reinicio...")
            restartCallback?.onRestartNeeded()
            return
        }
        
        // Verificar cuándo fue la última vez que se recibió una notificación
        val prefs = context.getSharedPreferences("notification_listener_prefs", Context.MODE_PRIVATE)
        val lastNotificationTime = prefs.getLong("last_notification_received", 0)
        val lastConnectionTime = prefs.getLong("last_connection_time", 0)
        val currentTime = System.currentTimeMillis()
        
        // Si el servicio está habilitado pero no ha recibido notificaciones por un tiempo
        if (lastNotificationTime > 0) {
            val timeSinceLastNotif = currentTime - lastNotificationTime
            
            if (timeSinceLastNotif > NO_NOTIFICATION_THRESHOLD) {
                val hoursSinceLastNotif = timeSinceLastNotif / (1000 * 60 * 60)
                Log.w(TAG, "No se han recibido notificaciones en $hoursSinceLastNotif horas")
                
                // Verificar si debemos intentar otro reinicio con backoff exponencial
                if (shouldRetryNow()) {
                    Log.w(TAG, "Intento de reconexión progresivo #$currentRetryAttempt")
                    restartCallback?.onRestartNeeded()
                    
                    // Actualizar la notificación para informar al usuario
                    showStatusNotification("Reconectando el servicio de notificaciones")
                    
                    lastRetryTime = currentTime
                    currentRetryAttempt++
                    
                    // Si llevamos más de 12 horas sin servicio, forzar un reinicio completo
                    if (timeSinceLastNotif > MAX_TIME_WITHOUT_SERVICE) {
                        Log.w(TAG, "Crisis detectada: Sin notificaciones por más de 12 horas. Reinicio profundo...")
                        performDeepReset()
                    }
                }
            }
        } else if (lastConnectionTime > 0) {
            // Si nunca ha recibido notificaciones pero está conectado hace tiempo
            val timeSinceConnection = currentTime - lastConnectionTime
            if (timeSinceConnection > OLD_CONNECTION_THRESHOLD) {
                Log.w(TAG, "Conexión antigua (${timeSinceConnection/3600000}h) sin notificaciones, solicitando reinicio...")
                restartCallback?.onRestartNeeded()
            }
        }
    }
    
    /**
     * Programa verificaciones periódicas independientes del watchdog principal.
     */
    private fun startPeriodicChecks() {
        periodicCheckJob = scope.launch {
            while (isActive) {
                try {
                    delay(PERIODIC_CHECK_INTERVAL)
                    
                    val prefs = context.getSharedPreferences("notification_listener_prefs", Context.MODE_PRIVATE)
                    val lastNotificationTime = prefs.getLong("last_notification_received", 0)
                    val currentTime = System.currentTimeMillis()
                    
                    if (lastNotificationTime > 0 && (currentTime - lastNotificationTime > CRITICAL_THRESHOLD)) {
                        // Si han pasado más de 6 horas sin notificaciones, hacer un reinicio forzado
                        Log.w(TAG, "Verificación periódica: 6+ horas sin notificaciones. Realizando reinicio forzado.")
                        performForceReset()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error en verificación periódica: ${e.message}", e)
                }
            }
        }
    }
    
    /**
     * Verifica si se debe reintentar ahora según el exponential backoff.
     */
    private fun shouldRetryNow(): Boolean {
        if (lastRetryTime == 0L) return true
        
        val now = System.currentTimeMillis()
        val minutesSinceLastRetry = (now - lastRetryTime) / (1000 * 60)
        
        // Usar el intervalo correspondiente o el último si hemos superado la lista
        val requiredMinutes = if (currentRetryAttempt < retryIntervals.size) {
            retryIntervals[currentRetryAttempt]
        } else {
            retryIntervals.last()
        }
        
        return minutesSinceLastRetry >= requiredMinutes
    }
    
    /**
     * Calcula el próximo intervalo de verificación del watchdog.
     */
    private fun calculateNextWatchdogInterval(): Long {
        // Usar el intervalo de la lista o el valor base si no hay más intentos
        val nextMinutes = if (currentRetryAttempt < retryIntervals.size) {
            retryIntervals[currentRetryAttempt].toLong()
        } else {
            15L // Volvemos al intervalo base de 15 minutos
        }
        
        // Convertir a milisegundos
        return nextMinutes * 60 * 1000
    }
    
    /**
     * Realiza un reinicio forzado del servicio de notificaciones.
     */
    suspend fun performForceReset() {
        Log.w(TAG, "Realizando reinicio forzado del servicio de notificaciones")
        
        try {
            // 1. Desactivar completamente el componente
            val componentName = ComponentName(context, NotificationListenerService::class.java)
            context.packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            
            // 2. Esperar para asegurar que el cambio se aplique
            delay(1000)
            
            // 3. Habilitar nuevamente
            context.packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            
            // 4. Esperar otro poco
            delay(1000)
            
            // 5. Intentar iniciar el servicio directamente
            val listenerIntent = Intent(context, NotificationListenerService::class.java)
            try {
                context.startService(listenerIntent)
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo iniciar el servicio directamente: ${e.message}", e)
            }
            
            // 6. Actualizar la notificación para informar al usuario
            showStatusNotification("Servicio de notificaciones reiniciado")
            
            // 7. Registrar el reinicio forzado
            val prefs = context.getSharedPreferences("notification_listener_prefs", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putLong("force_reset_time", System.currentTimeMillis())
                putInt("force_reset_count", prefs.getInt("force_reset_count", 0) + 1)
                apply()
            }
            
            // Resetear el contador de reintentos
            resetRetryCounter()
            
            Log.d(TAG, "Reinicio forzado completado")
        } catch (e: Exception) {
            Log.e(TAG, "Error durante el reinicio forzado: ${e.message}", e)
        }
    }
    
    /**
     * Realiza un reinicio profundo para casos extremos (12+ horas sin servicio).
     */
    suspend fun performDeepReset() {
        Log.w(TAG, "Realizando REINICIO PROFUNDO del servicio de notificaciones")
        
        try {
            // 1. Forzar la detención del servicio de notificaciones
            val componentName = ComponentName(context, NotificationListenerService::class.java)
            context.packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            
            // 2. Esperar a que el sistema aplique los cambios
            delay(2000)
            
            // 3. Limpiar los datos de la aplicación relacionados con el servicio
            val prefs = context.getSharedPreferences("notification_listener_prefs", Context.MODE_PRIVATE)
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
            context.packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            
            // 5. Esperar otro poco
            delay(2000)
            
            // 6. Intentar iniciar todo de nuevo
            val listenerIntent = Intent(context, NotificationListenerService::class.java)
            try {
                context.startService(listenerIntent)
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo iniciar el servicio directamente: ${e.message}", e)
            }
            
            // 7. Actualizar la notificación para informar al usuario
            showStatusNotification("Reinicio completo del servicio de notificaciones")
            
            // Resetear el contador de reintentos
            resetRetryCounter()
            
            Log.d(TAG, "Reinicio profundo completado")
        } catch (e: Exception) {
            Log.e(TAG, "Error durante el reinicio profundo: ${e.message}", e)
        }
    }
    
    /**
     * Resetea el contador de reintentos (usado después de un reinicio exitoso).
     */
    fun resetRetryCounter() {
        currentRetryAttempt = 0
        lastRetryTime = 0L
    }
    
    /**
     * Muestra una notificación de estado al usuario.
     */
    private fun showStatusNotification(message: String) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, pendingIntentFlags)
        
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Estado del servicio")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_STATUS, notification)
    }
}
