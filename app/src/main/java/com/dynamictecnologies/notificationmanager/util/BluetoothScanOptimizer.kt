package com.dynamictecnologies.notificationmanager.util

import android.content.Context
import android.os.PowerManager
import android.util.Log

/**
 * Optimizador de batería para escaneo Bluetooth.
 * 
 * Gestiona el escaneo Bluetooth de manera eficiente para minimizar
 * consumo de batería, con tiempos de escaneo limitados y pausas.
 * 
 * - Configurable: Tiempos ajustables
 * - Battery-aware: Detecta modo de ahorro de energía
 */
class BluetoothScanOptimizer(
    private val context: Context
) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    
    /**
     * Determina si se debe permitir escaneo Bluetooth basado en estado de batería
     */
    fun shouldAllowScan(): Boolean {
        // Verificar modo ahorro de energía
        if (powerManager.isPowerSaveMode) {
            Log.d(TAG, "Battery saver mode active, limiting scan")
            return false
        }
        
        return true
    }
    
    /**
     * Obtiene duración de escaneo recomendada basada en estado de batería
     */
    fun getRecommendedScanDuration(): Long {
        return if (powerManager.isPowerSaveMode) {
            POWER_SAVE_SCAN_DURATION_MS
        } else {
            NORMAL_SCAN_DURATION_MS
        }
    }
    
    /**
     * Obtiene intervalo de pausa recomendado entre escaneos
     */
    fun getRecommendedPauseDuration(): Long {
        return if (powerManager.isPowerSaveMode) {
            POWER_SAVE_PAUSE_DURATION_MS
        } else {
            NORMAL_PAUSE_DURATION_MS
        }
    }
    
    /**
     * Verifica si el dispositivo está en modo de baja potencia
     */
    fun isPowerSaveMode(): Boolean {
        return powerManager.isPowerSaveMode
    }
    
    companion object {
        private const val TAG = "BT_ScanOptimizer"
        
        // Duración de escaneo en modo normal (12 segundos)
        const val NORMAL_SCAN_DURATION_MS = 12000L
        
        // Duración de escaneo en modo ahorro de energía (6 segundos)
        const val POWER_SAVE_SCAN_DURATION_MS = 6000L
        
        // Pausa entre escaneos en modo normal (3 segundos)
        const val NORMAL_PAUSE_DURATION_MS = 3000L
        
        // Pausa entre escaneos en ahorro de energía (10 segundos)
        const val POWER_SAVE_PAUSE_DURATION_MS = 10000L
    }
}
