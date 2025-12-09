package com.dynamictecnologies.notificationmanager.worker

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.dynamictecnologies.notificationmanager.service.NotificationForegroundService
import com.dynamictecnologies.notificationmanager.service.ServiceNotificationManager
import com.dynamictecnologies.notificationmanager.util.device.DeviceManufacturerDetector
import com.dynamictecnologies.notificationmanager.util.device.DeviceManufacturer

/**
 * Worker que verifica la salud del servicio periódicamente.
 * 
 * Se ejecuta independientemente del servicio principal y sobrevive reinicios.
 * Detecta cuando el servicio muere sin llamar onDestroy() (común en fabricantes agresivos).
 * 
 * Funciona en TODAS las marcas:
 * - Xiaomi/Redmi: Muy agresivo, intervalo corto
 * - Huawei/Honor: Muy agresivo, intervalo corto
 * - Oppo/Realme/Vivo: Agresivo, intervalo medio
 * - OnePlus: Moderado, intervalo medio
 * - Samsung: Menos agresivo, intervalo estándar
 * - Google Pixel: Stock Android, intervalo estándar
 * 
 * Principios aplicados:
 * - SRP: Solo verifica salud del servicio
 * - Robustez: Funciona incluso si servicio es matado agresivamente
 */
class ServiceHealthCheckWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        private const val TAG = "ServiceHealthCheck"
    }
    
    /**
     * Obtiene el timeout de heartbeat según el fabricante.
     * Fabricantes más agresivos tienen timeouts más cortos.
     */
    private fun getHeartbeatTimeout(): Long {
        val manufacturer = DeviceManufacturerDetector().detectManufacturer()
        
        return when (manufacturer) {
            is DeviceManufacturer.Xiaomi -> 8 * 60 * 1000L  // 8 minutos (muy agresivo)
            is DeviceManufacturer.Huawei -> 8 * 60 * 1000L  // 8 minutos (muy agresivo)
            is DeviceManufacturer.Oppo -> 10 * 60 * 1000L   // 10 minutos (agresivo)
            is DeviceManufacturer.Vivo -> 10 * 60 * 1000L   // 10 minutos (agresivo)
            is DeviceManufacturer.OnePlus -> 12 * 60 * 1000L // 12 minutos (moderado)
            is DeviceManufacturer.Samsung -> 15 * 60 * 1000L // 15 minutos (menos agresivo)
            is DeviceManufacturer.Generic -> 15 * 60 * 1000L // 15 minutos (stock Android)
        }
    }

    override fun doWork(): Result {
        val manufacturer = DeviceManufacturerDetector().detectManufacturer()
        val timeout = getHeartbeatTimeout()
        Log.d(TAG, "🔍 Verificación de salud del servicio ($manufacturer, timeout: ${timeout/60000}min)...")
        
        try {
            // 1. Verificar si el servicio debería estar corriendo
            val prefs = applicationContext.getSharedPreferences("service_state", Context.MODE_PRIVATE)
            val shouldBeRunning = prefs.getBoolean("service_should_be_running", false)
            
            if (!shouldBeRunning) {
                Log.d(TAG, "✓ Servicio no debería estar corriendo (usuario lo detuvo)")
                return Result.success()
            }
            
            // 2. Verificar heartbeat (timestamp)
            val lastHeartbeat = prefs.getLong("service_last_heartbeat", 0)
            val timeSinceHeartbeat = System.currentTimeMillis() - lastHeartbeat
            
            if (lastHeartbeat == 0L) {
                Log.w(TAG, "⚠️ No hay heartbeat registrado, servicio probablemente nunca inició")
                handleDeadService()
                return Result.success()
            }
            
            if (timeSinceHeartbeat > timeout) {
                Log.w(TAG, "⚠️ Servicio sin heartbeat por ${timeSinceHeartbeat/60000}m (límite: ${timeout/60000}m)")
                handleDeadService()
                return Result.success()
            }
            
            // 3. Verificar si el servicio está realmente corriendo
            val isRunning = isServiceRunning()
            
            if (!isRunning) {
                Log.w(TAG, "⚠️ Servicio debería estar corriendo pero NO lo está!")
                handleDeadService()
                return Result.success()
            }
            
            Log.d(TAG, "✅ Servicio saludable (heartbeat hace ${timeSinceHeartbeat/1000}s)")
            return Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error en verificación: ${e.message}", e)
            return Result.failure()
        }
    }
    
    /**
     * Verifica si el servicio está corriendo consultando ActivityManager.
     */
    private fun isServiceRunning(): Boolean {
        return try {
            val manager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
                if (NotificationForegroundService::class.java.name == service.service.className) {
                    return true
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando si servicio corre: ${e.message}")
            false
        }
    }
    
    /**
     * Maneja el caso de servicio muerto:
     * - Muestra notificación roja
     * - Registra evento
     */
    private fun handleDeadService() {
        Log.w(TAG, "🚨 Servicio muerto detectado por watchdog externo")
        
        // Mostrar notificación roja
        ServiceNotificationManager(applicationContext).showStoppedNotification()
        
        // Registrar evento para diagnóstico
        val prefs = applicationContext.getSharedPreferences("service_state", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putLong("last_death_detected", System.currentTimeMillis())
            putInt("death_count", prefs.getInt("death_count", 0) + 1)
            apply()
        }
        
        Log.w(TAG, "📱 Notificación roja mostrada (muerte detectada por watchdog)")
    }
}
