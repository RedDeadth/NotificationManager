package com.dynamictecnologies.notificationmanager.service

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Receptor de acciones de usuario en las notificaciones del servicio.
 * 
 * Maneja 3 acciones:
 * 1. STOP: Usuario presiona "DETENER" en notificación running
 * 2. RESTART: Usuario presiona "Reiniciar" en notificación stopped
 * 3. ACKNOWLEDGE: Usuario presiona "Entendido" en notificación stopped
 */
class ServiceActionReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "ServiceAction"
        
        const val ACTION_STOP_SERVICE = "com.dynamictecnologies.ACTION_STOP"
        const val ACTION_RESTART_SERVICE = "com.dynamictecnologies.ACTION_RESTART"
        const val ACTION_ACKNOWLEDGE = "com.dynamictecnologies.ACTION_ACKNOWLEDGE"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "🔔 Action received: ${intent.action}")
        
        when (intent.action) {
            ACTION_STOP_SERVICE -> handleStopService(context)
            ACTION_RESTART_SERVICE -> handleRestartService(context)
            ACTION_ACKNOWLEDGE -> handleAcknowledge(context)
        }
    }
    
    /**
     * Maneja el botón DETENER de la notificación.
     * El usuario quiere detener el servicio temporalmente.
     */
    private fun handleStopService(context: Context) {
        Log.d(TAG, "🛑 Usuario presionó DETENER")
        
        // Cambiar estado a STOPPED
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.STOPPED)
        
        // Detener todos los servicios
        try {
            context.stopService(Intent(context, NotificationForegroundService::class.java))
            context.stopService(Intent(context, BackgroundMonitoringService::class.java))
            Log.d(TAG, "Servicios detenidos exitosamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo servicios: ${e.message}")
        }
        
        // Ocultar todas las notificaciones (usuario quiso detener)
        ServiceNotificationManager(context).hideAllNotifications()
        
        Log.d(TAG, "✅ Servicios detenidos por el usuario. No se molestarán más.")
    }
    
    /**
     * Maneja el botón REINICIAR de la notificación stopped.
     * El usuario quiere volver a activar el servicio.
     */
    private fun handleRestartService(context: Context) {
        Log.d(TAG, "🔄 Usuario presionó REINICIAR")
        
        // Cambiar estado a RUNNING
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.RUNNING)
        
        // Resetear contador (puede mostrar stopped notification otra vez si vuelve a morir)
        ServiceStateManager.resetStoppedCounter(context)
        
        // Reiniciar el servicio
        try {
            val intent = Intent(context, NotificationForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            
            Log.d(TAG, "✅ Servicio reiniciado exitosamente")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error reiniciando servicio: ${e.message}")
            
            // Si falla, volver a mostrar notificación de error
            ServiceNotificationManager(context).showStoppedNotification()
        }
        
        // La notificación de running se mostrará en onCreate() del servicio
    }
    
    /**
     * Maneja el botón ENTENDIDO de la notificación stopped.
     * El usuario NO quiere usar el servicio, detener TODO.
     */
    private fun handleAcknowledge(context: Context) {
        Log.d(TAG, "✓ Usuario presionó ENTENDIDO - Deteniendo todo definitivamente")
        
        // Cambiar estado a DISABLED
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.DISABLED)
        
        // Detener TODOS los servicios
        try {
            context.stopService(Intent(context, NotificationForegroundService::class.java))
            context.stopService(Intent(context, BackgroundMonitoringService::class.java))
            Log.d(TAG, "Servicios detenidos")
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo servicios: ${e.message}")
        }
        
        // Cancelar AlarmManager
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            // El ServiceRestartReceiver ya no reiniciará nada
            // porque el estado es DISABLED
            Log.d(TAG, "AlarmManager aware of DISABLED state")
        } catch (e: Exception) {
            Log.e(TAG, "Error con AlarmManager: ${e.message}")
        }
        
        // Opcionalmente: WorkManager para persistencia de reintentos
        // No implementado actualmente - servicios en foreground tienen reinicio automático del sistema
        // Solo necesario si se implementa Phase 2 con tareas background complejas
        // try {
        //     WorkManager.getInstance(context).cancelAllWork()
        //     Log.d(TAG, "WorkManager cancelado")
        // } catch (e: Exception) {
        //     Log.e(TAG, "Error cancelando WorkManager: ${e.message}")
        // }
        
        // Ocultar todas las notificaciones
        ServiceNotificationManager(context).hideAllNotifications()
        
        Log.d(TAG, "✅ TODO detenido. Solo se reactivará cuando usuario abra la app de nuevo")
    }
}
