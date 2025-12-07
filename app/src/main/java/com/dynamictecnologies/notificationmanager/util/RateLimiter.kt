package com.dynamictecnologies.notificationmanager.util

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Rate Limiter para prevenir ataques de fuerza bruta y spam.
 * 
 * Implementa:
 * - Límite de intentos por tiempo
 * - Bloqueo temporal tras exceder límite
 * - Thread-safe con ConcurrentHashMap
 * 
 * Uso:
 * ```
 * val loginLimiter = RateLimiter(maxAttempts = 5, windowMs = 60_000)
 * if (loginLimiter.allowRequest("user@email.com")) {
 *     // Procesar login
 * } else {
 *     // Mostrar error: "Too many attempts"
 * }
 * ```
 * 
 * Principios aplicados:
 * - SRP: Solo maneja rate limiting
 * - Thread-safe: ConcurrentHashMap
 */
class RateLimiter(
    private val maxAttempts: Int = 5,
    private val windowMs: Long = 60_000, // 1 minuto
    private val blockDurationMs: Long = 300_000 // 5 minutos
) {
    
    private data class AttemptRecord(
        var count: Int = 0,
        var firstAttemptTime: Long = 0,
        var blockedUntil: Long = 0
    )
    
    private val attempts = ConcurrentHashMap<String, AttemptRecord>()
    
    /**
     * Verifica si se permite una petición para el identificador dado.
     * 
     * @param identifier Identificador único (email, IP, userId, etc.)
     * @return true si se permite, false si está rate-limited
     */
    fun allowRequest(identifier: String): Boolean {
        val now = System.currentTimeMillis()
        val record = attempts.getOrPut(identifier) { AttemptRecord() }
        
        synchronized(record) {
            // Si está bloqueado, verificar si ya pasó el tiempo
            if (record.blockedUntil > now) {
                return false
            }
            
            // Si ya pasó el tiempo de bloqueo, resetear
            if (record.blockedUntil > 0 && record.blockedUntil <= now) {
                record.count = 0
                record.firstAttemptTime = 0
                record.blockedUntil = 0
            }
            
            // Si pasó la ventana de tiempo, resetear contador
            if (now - record.firstAttemptTime > windowMs) {
                record.count = 0
                record.firstAttemptTime = now
            }
            
            // Si es el primer intento o se reseteó
            if (record.count == 0) {
                record.firstAttemptTime = now
            }
            
            // Incrementar contador
            record.count++
            
            // Si excede el límite, bloquear
            if (record.count > maxAttempts) {
                record.blockedUntil = now + blockDurationMs
                return false
            }
            
            return true
        }
    }
    
    /**
     * Obtiene el tiempo restante de bloqueo en milisegundos.
     * 
     * @return 0 si no está bloqueado, tiempo restante si está bloqueado
     */
    fun getBlockedTimeRemaining(identifier: String): Long {
        val record = attempts[identifier] ?: return 0
        val now = System.currentTimeMillis()
        
        synchronized(record) {
            if (record.blockedUntil > now) {
                return record.blockedUntil - now
            }
        }
        
        return 0
    }
    
    /**
     * Obtiene el tiempo restante de bloqueo en un formato legible.
     */
    fun getBlockedTimeRemainingFormatted(identifier: String): String {
        val remainingMs = getBlockedTimeRemaining(identifier)
        if (remainingMs == 0L) return ""
        
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMs)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(remainingMs) % 60
        
        return if (minutes > 0) {
            "${minutes}m ${seconds}s"
        } else {
            "${seconds}s"
        }
    }
    
    /**
     * Resetea el contador para un identificador (usar tras login exitoso).
     */
    fun reset(identifier: String) {
        attempts.remove(identifier)
    }
    
    /**
     * Limpia registros antiguos (llamar periódicamente para evitar memory leaks).
     */
    fun cleanup() {
        val now = System.currentTimeMillis()
        val iterator = attempts.entries.iterator()
        
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val record = entry.value
            
            synchronized(record) {
                // Remover si no está bloqueado y pasó la ventana + tiempo extra
                if (record.blockedUntil <= now && 
                    now - record.firstAttemptTime > windowMs + blockDurationMs) {
                    iterator.remove()
                }
            }
        }
    }
    
    companion object {
        // Rate limiters pre-configurados
        
        /** Para login: 5 intentos en 1 minuto, bloqueo de 5 minutos */
        val LOGIN = RateLimiter(maxAttempts = 5, windowMs = 60_000, blockDurationMs = 300_000)
        
        /** Para registro: 3 intentos en 5 minutos, bloqueo de 15 minutos */
        val REGISTER = RateLimiter(maxAttempts = 3, windowMs = 300_000, blockDurationMs = 900_000)
        
        /** Para MQTT: 10 intentos en 1 minuto, bloqueo de 2 minutos */
        val MQTT_CONNECTION = RateLimiter(maxAttempts = 10, windowMs = 60_000, blockDurationMs = 120_000)
        
        /** Para API calls: 30 intentos en 1 minuto, bloqueo de 1 minuto */
        val API_CALLS = RateLimiter(maxAttempts = 30, windowMs = 60_000, blockDurationMs = 60_000)
    }
}
