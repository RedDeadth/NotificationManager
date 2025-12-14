package com.dynamictecnologies.notificationmanager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.dynamictecnologies.notificationmanager.service.NotificationForegroundService
import com.dynamictecnologies.notificationmanager.service.NotificationListenerService
import com.dynamictecnologies.notificationmanager.util.notification.ServiceCrashNotifier

/**
 * BroadcastReceiver para manejar acciones desde las notificaciones de crash del servicio.
 * 
 */
class ServiceRecoveryReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "ServiceRecoveryReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: ${intent.action}")
        
        when (intent.action) {
            ServiceCrashNotifier.ACTION_DISMISS -> {
                handleDismissAction(context)
            }
            
            ServiceCrashNotifier.ACTION_RESTART -> {
                handleRestartAction(context)
            }
            
            ServiceCrashNotifier.ACTION_SETTINGS -> {
                handleSettingsAction(context)
            }
        }
    }
    
    /**
     * Maneja la acción de "Entendido" - descarta la notificación
     */
    private fun handleDismissAction(context: Context) {
        Log.d(TAG, "Usuario dismiss notificación de crash")
        
        val crashNotifier = ServiceCrashNotifier(context)
        crashNotifier.dismissCrashNotification()
        
        // Registrar que el usuario fue informado
        val prefs = context.getSharedPreferences("service_crash_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putLong("last_user_dismiss", System.currentTimeMillis())
            .putInt("user_dismiss_count", prefs.getInt("user_dismiss_count", 0) + 1)
            .apply()
    }
    
    /**
     * Maneja la acción de "Reiniciar" - intenta reiniciar el servicio
     */
    private fun handleRestartAction(context: Context) {
        Log.d(TAG, "Usuario solicitó reinicio del servicio")
        
        val crashNotifier = ServiceCrashNotifier(context)
        crashNotifier.dismissCrashNotification()
        crashNotifier.showRecoveryNotification(1)
        
        try {
            // Intentar reiniciar el NotificationForegroundService
            val intent = Intent(context, NotificationForegroundService::class.java)
            intent.action = NotificationForegroundService.ACTION_RESTART_NOTIFICATION_LISTENER
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            
            Log.d(TAG, "Solicitud de reinicio enviada al ForegroundService")
            
            // Registrar que el usuario inició un reinicio manual
            val prefs = context.getSharedPreferences("service_crash_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putLong("last_user_restart", System.currentTimeMillis())
                .putInt("user_restart_count", prefs.getInt("user_restart_count", 0) + 1)
                .apply()
                
        } catch (e: Exception) {
            Log.e(TAG, "Error al reiniciar el servicio: ${e.message}", e)
            crashNotifier.dismissRecoveryNotification()
            
            // Mostrar notificación de error
            crashNotifier.showCrashNotification(
                com.dynamictecnologies.notificationmanager.util.notification.ServiceStopReason.Unknown(
                    "Error al reiniciar: ${e.message}"
                )
            )
        }
    }
    
    /**
     * Maneja la acción de "Configuración" - abre settings de notificaciones
     */
    private fun handleSettingsAction(context: Context) {
        Log.d(TAG, "Usuario solicitó abrir configuración")
        
        val crashNotifier = ServiceCrashNotifier(context)
        crashNotifier.dismissCrashNotification()
        
        try {
            // Abrir la configuración de NotificationListenerService
            NotificationListenerService.openNotificationListenerSettings(context)
            
            // Registrar que el usuario abrió settings
            val prefs = context.getSharedPreferences("service_crash_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putLong("last_settings_open", System.currentTimeMillis())
                .putInt("settings_open_count", prefs.getInt("settings_open_count", 0) + 1)
                .apply()
                
        } catch (e: Exception) {
            Log.e(TAG, "Error al abrir configuración: ${e.message}", e)
        }
    }
}
