package com.dynamictecnologies.notificationmanager.service.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.dynamictecnologies.notificationmanager.service.NotificationListenerService
import kotlinx.coroutines.delay

/**
 * Utilidad para toggle del NotificationListenerService.
 * 
 * Centraliza la lógica de toggle que antes estaba duplicada en:
 * - NotificationForegroundService.performForceReset()
 * - NotificationForegroundService.performDeepReset()
 * - NotificationForegroundService.tryToRestartNotificationListenerService()
 * - ServiceRecoveryManager.toggleNotificationListenerService()
 * - BootReceiver.enableNotificationListenerService()
 * 
 */
object NotificationListenerToggler {
    
    private const val TAG = "NLToggler"
    
    /**
     * Delay por defecto entre disable y enable
     */
    private const val DEFAULT_TOGGLE_DELAY_MS = 500L
    
    /**
     * Toggle simple del NotificationListenerService.
     * Deshabilita y re-habilita el componente.
     * 
     * @param context Contexto de la aplicación
     * @param delayMs Delay entre disable y enable (default: 500ms)
     * @return true si el toggle fue exitoso
     */
    suspend fun toggle(
        context: Context,
        delayMs: Long = DEFAULT_TOGGLE_DELAY_MS
    ): Boolean {
        return try {
            val componentName = ComponentName(context, NotificationListenerService::class.java)
            val pm = context.packageManager
            
            // Deshabilitar
            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            
            Log.d(TAG, "Componente deshabilitado, esperando ${delayMs}ms...")
            
            // Non-blocking delay
            delay(delayMs)
            
            // Re-habilitar
            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            
            Log.d(TAG, "Toggle completado exitosamente")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error en toggle: ${e.message}", e)
            false
        }
    }
    
    /**
     * Solo deshabilita el componente.
     */
    fun disable(context: Context): Boolean {
        return try {
            val componentName = ComponentName(context, NotificationListenerService::class.java)
            context.packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            Log.d(TAG, "Componente deshabilitado")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deshabilitando: ${e.message}", e)
            false
        }
    }
    
    /**
     * Solo habilita el componente.
     */
    fun enable(context: Context): Boolean {
        return try {
            val componentName = ComponentName(context, NotificationListenerService::class.java)
            context.packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            Log.d(TAG, "Componente habilitado")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error habilitando: ${e.message}", e)
            false
        }
    }
}
