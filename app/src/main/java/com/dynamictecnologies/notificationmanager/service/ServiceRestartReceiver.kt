package com.dynamictecnologies.notificationmanager.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * BroadcastReceiver para reiniciar servicios cuando son matados por el sistema.
 * 
 * Uso: AlarmManager llama a este receiver cuando detecta que el servicio murió.
 * 
 * Casos de uso:
 * - Sistema mata servicio por usar cámara
 * - Sistema mata servicio por memoria baja
 * - Usuario cierra app pero servicio debe continuar
 */
class ServiceRestartReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "ServiceRestart"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "🔄 Reiniciando servicios desde AlarmManager...")
        
        // CRÍTICO: Verificar si el usuario eligió "Entendido" (DISABLED state)
        val currentState = ServiceStateManager.getCurrentState(context)
        
        if (currentState == ServiceStateManager.ServiceState.DISABLED) {
            Log.d(TAG, "❌ Estado DISABLED - Usuario no quiere el servicio. No reiniciar.")
            return
        }
        
        // Si estado no es RUNNING, tampoco reiniciar
        if (currentState != ServiceStateManager.ServiceState.RUNNING) {
            Log.d(TAG, "Estado: $currentState - No reiniciar automáticamente")
            return
        }
        
        try {
            // Reiniciar NotificationForegroundService
            val foregroundIntent = Intent(context, NotificationForegroundService::class.java)
            foregroundIntent.action = NotificationForegroundService.ACTION_START_FOREGROUND_SERVICE
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(foregroundIntent)
            } else {
                context.startService(foregroundIntent)
            }
            
            Log.d(TAG, "✅ NotificationForegroundService reiniciado")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error reiniciando NotificationForegroundService: ${e.message}")
        }
        
        try {
            // Reiniciar BackgroundMonitoringService
            val backgroundIntent = Intent(context, BackgroundMonitoringService::class.java)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(backgroundIntent)
            } else {
                context.startService(backgroundIntent)
            }
            
            Log.d(TAG, "✅ BackgroundMonitoringService reiniciado")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error reiniciando BackgroundMonitoringService: ${e.message}")
        }
    }
}
