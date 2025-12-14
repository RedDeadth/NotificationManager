package com.dynamictecnologies.notificationmanager.util.security

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Rate limiter para notificaciones.
 * 
 * Previene spam y DoS attacks limitando número de operaciones
 * por ventana de tiempo.
 * 
 * - Security: Protección DoS
 * - Thread-safe: ConcurrentHashMap
 * - Configurable: Window y max requests ajustables
 */
class NotificationRateLimiter(
    private val maxRequests: Int = DEFAULT_MAX_REQUESTS,
    private val windowMs: Long = DEFAULT_WINDOW_MS
) {
    private val timestamps = ConcurrentHashMap<String, MutableList<Long>>()
    
    /**
     * Verifica si se permite una operación para un identificador
     */
    fun allowOperation(identifier: String = DEFAULT_IDENTIFIER): Boolean {
        val now = System.currentTimeMillis()
        
        // Obtener o crear lista de timestamps
        val userTimestamps = timestamps.getOrPut(identifier) { mutableListOf() }
        
        synchronized(userTimestamps) {
            // Limpiar timestamps fuera de ventana
            userTimestamps.removeAll { it < now - windowMs }
            
            // Verificar límite
            if (userTimestamps.size >= maxRequests) {
                val oldestTimestamp = userTimestamps.firstOrNull() ?: now
                val waitTime = (oldestTimestamp + windowMs - now) / 1000
                
                Log.w(TAG, "Rate limit exceeded for $identifier. Retry in ${waitTime}s")
                return false
            }
            
            // Agregar timestamp actual
            userTimestamps.add(now)
            Log.d(TAG, "Operation allowed for $identifier (${userTimestamps.size}/$maxRequests)")
            return true
        }
    }
    
    /**
     * Reinicia rate limit para un identificador
     */
    fun reset(identifier: String = DEFAULT_IDENTIFIER) {
        timestamps.remove(identifier)
        Log.d(TAG, "Rate limiter reset for $identifier")
    }
    
    /**
     * Limpia todos los rate limiters
     */
    fun clearAll() {
        timestamps.clear()
        Log.d(TAG, "All rate limiters cleared")
    }
    
    /**
     * Obtiene cuenta actual de requests para un identificador
     */
    fun getCurrentCount(identifier: String = DEFAULT_IDENTIFIER): Int {
        val now = System.currentTimeMillis()
        val userTimestamps = timestamps[identifier] ?: return 0
        
        synchronized(userTimestamps) {
            userTimestamps.removeAll { it < now - windowMs }
            return userTimestamps.size
        }
    }
    
    companion object {
        private const val TAG = "RateLimiter"
        
        const val DEFAULT_MAX_REQUESTS = 10
        const val DEFAULT_WINDOW_MS = 60_000L  // 1 minuto
        const val DEFAULT_IDENTIFIER = "default"
    }
}

/**
 * Excepción para rate limit excedido
 */
class RateLimitExceededException(
    message: String = "Rate limit exceeded. Too many requests."
) : Exception(message)
