package com.dynamictecnologies.notificationmanager.service

import android.content.Context

/**
 * Gestor de estados del servicio de notificaciones.
 * 
 * Controla:
 * - Estado actual del servicio (RUNNING/STOPPED/DISABLED)
 * - Contador de notificaciones "stopped" mostradas
 * - Lógica de cuándo mostrar notificaciones
 * 
 * Estados:
 * - RUNNING: Servicio activo normal
 * - STOPPED: Servicio detenido temporalmente
 * - DISABLED: Usuario eligió "Entendido", no molestar hasta que abra app
 */
object ServiceStateManager {
    
    private const val PREFS_NAME = "service_state_prefs"
    private const val KEY_CURRENT_STATE = "current_state"
    private const val KEY_STOPPED_SHOWN = "stopped_notification_shown"
    private const val KEY_STOPPED_COUNT = "stopped_notification_count"
    private const val KEY_LAST_STATE_CHANGE = "last_state_change_time"
    
    enum class ServiceState {
        RUNNING,    // Servicio activo
        STOPPED,    // Servicio detenido
        DISABLED    // Usuario eligió "Entendido"
    }
    
    /**
     * Obtiene el estado actual del servicio.
     */
    fun getCurrentState(context: Context): ServiceState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stateName = prefs.getString(KEY_CURRENT_STATE, ServiceState.RUNNING.name)
        return try {
            ServiceState.valueOf(stateName ?: ServiceState.RUNNING.name)
        } catch (e: IllegalArgumentException) {
            ServiceState.RUNNING
        }
    }
    
    /**
     * Establece el estado actual del servicio.
     */
    fun setState(context: Context, state: ServiceState) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CURRENT_STATE, state.name)
            .putLong(KEY_LAST_STATE_CHANGE, System.currentTimeMillis())
            .apply()
    }
    
    /**
     * Verifica si se puede mostrar la notificación de "servicio detenido".
     * Solo se muestra una vez por sesión hasta que se vuelva a abrir la app.
     */
    fun canShowStoppedNotification(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentState = getCurrentState(context)
        val alreadyShown = prefs.getBoolean(KEY_STOPPED_SHOWN, false)
        
        // Solo mostrar si:
        // 1. Estado actual es RUNNING (servicio debería estar activo)
        // 2. No se ha mostrado ya en esta sesión
        // 3. No está en estado DISABLED (usuario no quiere el servicio)
        return currentState == ServiceState.RUNNING && !alreadyShown
    }
    
    /**
     * Marca que la notificación de "servicio detenido" ya fue mostrada.
     */
    fun markStoppedNotificationShown(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_STOPPED_SHOWN, true)
            .putInt(KEY_STOPPED_COUNT, getStoppedCount(context) + 1)
            .putLong("last_stopped_time", System.currentTimeMillis())
            .apply()
    }
    
    /**
     * Resetea el contador de notificaciones mostradas.
     * Se llama cuando usuario presiona "Reiniciar" o cuando abre la app.
     */
    fun resetStoppedCounter(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_STOPPED_SHOWN, false)
            .apply()
    }
    
    /**
     * Obtiene el número total de veces que se ha mostrado la notificación de stopped.
     */
    fun getStoppedCount(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_STOPPED_COUNT, 0)
    }
    
    /**
     * Se llama cuando MainActivity.onCreate() para resetear estado si es necesario.
     */
    fun resetOnAppOpen(context: Context) {
        val currentState = getCurrentState(context)
        
        // Si el usuario eligió "Entendido" pero volvió a abrir la app,
        // asumimos que quiere usar la app de nuevo
        if (currentState == ServiceState.DISABLED) {
            setState(context, ServiceState.RUNNING)
        }
        
        // Resetear contador para dar otra oportunidad de mostrar notificación
        resetStoppedCounter(context)
    }
    
    /**
     * Obtiene tiempo desde el último cambio de estado (para debugging).
     */
    fun getTimeSinceLastStateChange(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastChange = prefs.getLong(KEY_LAST_STATE_CHANGE, 0)
        return if (lastChange > 0) {
            System.currentTimeMillis() - lastChange
        } else {
            0
        }
    }
}
