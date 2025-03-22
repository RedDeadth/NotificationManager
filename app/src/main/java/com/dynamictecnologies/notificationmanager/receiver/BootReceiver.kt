package com.dynamictecnologies.notificationmanager.receiver

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dynamictecnologies.notificationmanager.service.NotificationListenerService
import com.dynamictecnologies.notificationmanager.service.NotificationForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    private val TAG = "BootReceiver"
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            
            Log.d(TAG, "Evento de arranque detectado: ${intent.action}")
            
            // Utilizar un Handler para retrasar el inicio y evitar problemas
            // cuando el sistema está muy ocupado durante el arranque
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    Log.d(TAG, "Iniciando servicios después del arranque (con retraso)...")
                    enableNotificationListenerService(context)
                    startForegroundService(context)
                    recordBootEvent(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Error iniciando servicios en el arranque: ${e.message}", e)
                }
            }, STARTUP_DELAY)
            
            // También iniciar usando Coroutine para mayor fiabilidad
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                try {
                    // Esperar a que el sistema esté más estable
                    delay(SECONDARY_STARTUP_DELAY)
                    
                    Log.d(TAG, "Verificando servicios (segunda comprobación)...")
                    if (!isServiceRunning(context)) {
                        Log.d(TAG, "Servicios no iniciados en la primera verificación, intentando nuevamente...")
                        enableNotificationListenerService(context)
                        startForegroundService(context)
                    }
                    
                    // Añadir verificaciones adicionales con más retraso para asegurar el inicio
                    // Tercer intento a los 10 minutos
                    delay(7 * 60 * 1000L)
                    
                    Log.d(TAG, "Tercera verificación de servicios...")
                    if (!isServiceRunning(context)) {
                        Log.d(TAG, "Servicios no detectados en tercera verificación, solicitando reinicio forzado...")
                        enableNotificationListenerService(context)
                        startForegroundService(context, true)
                    }
                    
                    // Cuarta verificación después de 30 minutos
                    delay(20 * 60 * 1000L)
                    
                    Log.d(TAG, "Verificación final de servicios...")
                    if (!isServiceRunning(context)) {
                        Log.d(TAG, "Los servicios siguen sin iniciarse después de múltiples intentos, realizando reinicio forzado...")
                        performForceRestart(context)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error en las verificaciones secundarias: ${e.message}", e)
                }
            }
        }
    }
    
    private fun enableNotificationListenerService(context: Context) {
        try {
            val componentName = ComponentName(context, NotificationListenerService::class.java)
            val pm = context.packageManager
            
            // Primero deshabilitar, luego habilitar (ayuda a reiniciar el servicio)
            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            
            // Esperar un momento para que el cambio se aplique
            Thread.sleep(300)
            
            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            
            Log.d(TAG, "NotificationListenerService habilitado")
        } catch (e: Exception) {
            Log.e(TAG, "Error habilitando NotificationListenerService: ${e.message}", e)
        }
    }
    
    private fun startForegroundService(context: Context, forceReset: Boolean = false) {
        try {
            val serviceIntent = Intent(context, NotificationForegroundService::class.java)
            
            if (forceReset) {
                serviceIntent.action = NotificationForegroundService.ACTION_FORCE_RESET
                Log.d(TAG, "Solicitando reinicio forzado de los servicios")
            } else {
                serviceIntent.action = NotificationForegroundService.ACTION_RESTART_NOTIFICATION_LISTENER
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
            Log.d(TAG, "NotificationForegroundService iniciado")
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando NotificationForegroundService: ${e.message}", e)
        }
    }
    
    private fun performForceRestart(context: Context) {
        try {
            Log.w(TAG, "Realizando reinicio forzado completo de los servicios")
            
            // 1. Desactivar completamente el componente
            val componentName = ComponentName(context, NotificationListenerService::class.java)
            val pm = context.packageManager
            
            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            
            // 2. Esperar para asegurar que el cambio se aplique
            Thread.sleep(1000)
            
            // 3. Reiniciar las preferencias
            val prefs = context.getSharedPreferences("notification_listener_prefs", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putLong("last_connection_time", 0)
                putLong("last_notification_received", 0)
                putLong("force_restart_time", System.currentTimeMillis())
                putInt("force_restart_count", prefs.getInt("force_restart_count", 0) + 1)
                apply()
            }
            
            // 4. Habilitar nuevamente el componente
            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            
            // 5. Iniciar el servicio de primer plano con acción de reinicio forzado
            val serviceIntent = Intent(context, NotificationForegroundService::class.java)
            serviceIntent.action = NotificationForegroundService.ACTION_FORCE_RESET
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
            Log.d(TAG, "Reinicio forzado completado")
        } catch (e: Exception) {
            Log.e(TAG, "Error en reinicio forzado: ${e.message}", e)
        }
    }
    
    private fun isServiceRunning(context: Context): Boolean {
        // Verificar si el servicio está funcionando
        val prefs = context.getSharedPreferences("notification_listener_prefs", Context.MODE_PRIVATE)
        val lastConnectionTime = prefs.getLong("last_connection_time", 0)
        
        if (lastConnectionTime == 0L) {
            return false
        }
        
        // Si la última conexión fue hace menos de 5 minutos, asumimos que está funcionando
        return (System.currentTimeMillis() - lastConnectionTime) < 5 * 60 * 1000
    }
    
    private fun recordBootEvent(context: Context) {
        val prefs = context.getSharedPreferences("notification_listener_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putLong("last_boot_time", System.currentTimeMillis())
            putInt("boot_count", prefs.getInt("boot_count", 0) + 1)
            apply()
        }
    }
    
    companion object {
        // Retraso inicial para el arranque (reducido a 10 segundos)
        private const val STARTUP_DELAY = 10 * 1000L
        
        // Retraso secundario para verificación (reducido a 2 minutos)
        private const val SECONDARY_STARTUP_DELAY = 2 * 60 * 1000L
    }
} 