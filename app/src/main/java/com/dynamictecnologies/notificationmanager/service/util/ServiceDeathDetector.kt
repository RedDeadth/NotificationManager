package com.dynamictecnologies.notificationmanager.service.util

import android.content.Context
import android.util.Log
import com.dynamictecnologies.notificationmanager.service.ServiceNotificationManager
import com.dynamictecnologies.notificationmanager.service.ServiceStateManager

/**
 * Detector de muerte inesperada del servicio.
 * 
 * Responsabilidad única (SRP): Detectar y manejar cuando el servicio
 * murió inesperadamente mientras la app estaba cerrada.
 * 
 * Casos de uso:
 * - Detectar muerte al abrir la app (después de Force Stop)
 * - Verificar si el servicio debería estar corriendo
 * - Mostrar notificación roja si aplica
 * 
 * Esta clase NO maneja el reinicio del servicio (eso es responsabilidad
 * de ServiceStateManager y NotificationForegroundService).
 */
object ServiceDeathDetector {
    
    private const val TAG = "ServiceDeathDetector"
    private const val PREFS_NAME = "service_death_detector"
    private const val KEY_LAST_KNOWN_STATE = "last_known_running_state"
    private const val KEY_LAST_HEARTBEAT = "service_last_heartbeat"
    
    /**
     * Verifica si el servicio murió inesperadamente.
     * 
     * Retorna true si:
     * 1. El servicio DEBERÍA estar corriendo (estado RUNNING)
     * 2. El servicio NO está corriendo actualmente
     * 3. No se detectó una parada intencional (DISABLED)
     */
    fun wasServiceKilledUnexpectedly(context: Context): Boolean {
        val currentState = ServiceStateManager.getCurrentState(context)
        val prefs = context.getSharedPreferences("service_state", Context.MODE_PRIVATE)
        val shouldBeRunning = prefs.getBoolean("service_should_be_running", false)
        val lastHeartbeat = prefs.getLong(KEY_LAST_HEARTBEAT, 0)
        
        // Si el estado actual es DISABLED, el usuario lo desactivó intencionalmente
        if (currentState == ServiceStateManager.ServiceState.DISABLED) {
            Log.d(TAG, "Estado DISABLED - usuario desactivó intencionalmente")
            return false
        }
        
        // Si debería estar corriendo pero no hay heartbeat reciente, murió inesperadamente
        if (shouldBeRunning && currentState == ServiceStateManager.ServiceState.RUNNING) {
            val timeSinceHeartbeat = System.currentTimeMillis() - lastHeartbeat
            val heartbeatTimeout = 15 * 60 * 1000L // 15 minutos
            
            if (lastHeartbeat > 0 && timeSinceHeartbeat > heartbeatTimeout) {
                Log.w(TAG, "Servicio murió inesperadamente (sin heartbeat por ${timeSinceHeartbeat/60000}min)")
                return true
            }
            
            // Si nunca hubo heartbeat pero debería estar corriendo
            if (lastHeartbeat == 0L) {
                Log.w(TAG, "Servicio debería estar corriendo pero nunca inició")
                return true
            }
        }
        
        return false
    }
    
    /**
     * Maneja la detección de muerte al abrir la app.
     * 
     * Llamar desde MainActivity.onCreate() ANTES de resetOnAppOpen().
     * 
     * Si detecta que el servicio murió inesperadamente:
     * 1. Muestra la notificación roja
     * 2. Registra el evento para diagnóstico
     */
    fun handleDeathOnAppStart(context: Context) {
        Log.d(TAG, "Verificando si el servicio murió mientras la app estaba cerrada...")
        
        if (wasServiceKilledUnexpectedly(context)) {
            Log.w(TAG, "Servicio murió mientras la app estaba cerrada - mostrando notificación")
            
            // Mostrar notificación roja
            ServiceNotificationManager(context).showStoppedNotification()
            
            // Marcar que la notificación fue mostrada
            ServiceStateManager.markStoppedNotificationShown(context)
            ServiceStateManager.setState(context, ServiceStateManager.ServiceState.STOPPED)
            
            // Registrar evento para diagnóstico
            recordDeathEvent(context)
        } else {
            Log.d(TAG, "Servicio no murió inesperadamente (o ya fue manejado)")
        }
    }
    
    /**
     * Registra el servicio como activo.
     * Llamar desde NotificationForegroundService.onCreate().
     */
    fun markServiceAsRunning(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_LAST_KNOWN_STATE, true)
            .putLong("last_start_time", System.currentTimeMillis())
            .apply()
        
        Log.d(TAG, "Servicio marcado como activo")
    }
    
    /**
     * Registra evento de muerte para diagnóstico.
     */
    private fun recordDeathEvent(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentCount = prefs.getInt("death_on_start_count", 0)
        
        prefs.edit()
            .putInt("death_on_start_count", currentCount + 1)
            .putLong("last_death_on_start", System.currentTimeMillis())
            .apply()
        
        Log.d(TAG, "Muerte al inicio registrada (total: ${currentCount + 1})")
    }
}
