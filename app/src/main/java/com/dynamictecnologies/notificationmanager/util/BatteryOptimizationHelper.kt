package com.dynamictecnologies.notificationmanager.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Helper para gestionar la exención de optimización de batería.
 * 
 * Cuando la app está exenta, puede:
 * - Mantener conexiones de red en Doze mode
 * - Ejecutar servicios sin restricciones de batería
 * - Recibir eventos de red más frecuentemente
 * 
 * Importante: Esta exención es necesaria para apps que necesitan
 * mantener conexiones persistentes (como MQTT) funcionando 24/7.
 */
object BatteryOptimizationHelper {
    private const val TAG = "BatteryOptHelper"
    
    /**
     * Verifica si la app está exenta de optimización de batería.
     * 
     * @return true si está exenta (puede funcionar en background sin restricciones)
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            // Android < 6.0 no tiene Doze mode
            true
        }
    }
    
    /**
     * Solicita exención de optimización de batería al usuario.
     * 
     * Abre un diálogo del sistema preguntando si permitir que la app
     * ignore las optimizaciones de batería.
     * 
     * NOTA: Google Play tiene restricciones sobre cuándo se puede usar esto.
     * Solo apps que realmente necesitan funcionar 24/7 deberían solicitarlo.
     * 
     * @return true si se pudo abrir el diálogo, false si hubo error
     */
    fun requestIgnoreBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true // No necesario en versiones antiguas
        }
        
        if (isIgnoringBatteryOptimizations(context)) {
            Log.d(TAG, "Ya está exento de optimización de batería")
            return true
        }
        
        return try {
            val intent = Intent().apply {
                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Diálogo de exención de batería mostrado")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error solicitando exención de batería: ${e.message}", e)
            false
        }
    }
    
    /**
     * Abre la configuración de batería de la app para que el usuario
     * pueda cambiar manualmente las opciones.
     * 
     * Útil como alternativa si el diálogo directo falla.
     */
    fun openBatterySettings(context: Context): Boolean {
        return try {
            val intent = Intent().apply {
                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error abriendo configuración de batería: ${e.message}", e)
            false
        }
    }
}
