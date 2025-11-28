package com.dynamictecnologies.notificationmanager.data.network

import kotlinx.coroutines.delay
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Política de reintentos con backoff exponencial para operaciones de red.
 * 
 * Características:
 * - Reintentos configurables
 * - Backoff exponencial
 * - Delay máximo configurable
 * - Filtro de excepciones reintentatables
 * 
 * Principios aplicados:
 * - SRP: Solo maneja lógica de reintentos
 * - OCP: Extensible mediante configuración
 */
class RetryPolicy(
    private val maxRetries: Int = 3,
    private val initialDelayMs: Long = 1000,
    private val maxDelayMs: Long = 10000,
    private val factor: Double = 2.0
) {
    
    /**
     * Ejecuta una operación con reintentos automáticos
     */
    suspend fun <T> executeWithRetry(
        operation: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        var lastException: Exception? = null
        
        repeat(maxRetries) { attempt ->
            try {
                return operation()
            } catch (e: Exception) {
                lastException = e
                
                // No reintentar en ciertos errores
                if (!shouldRetry(e)) {
                    throw e
                }
                
                // Si no es el último intento, esperar antes de reintentar
                if (attempt < maxRetries - 1) {
                    delay(currentDelay)
                    currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMs)
                }
            }
        }
        
        // Si llegamos aquí, fallaron todos los reintentos
        throw lastException ?: Exception("Retry failed without exception")
    }
    
    /**
     * Determina si una excepción es reintentable
     */
    private fun shouldRetry(exception: Exception): Boolean {
        return when (exception) {
            // Errores de red transitorios
            is IOException -> true
            is SocketTimeoutException -> true
            // Firebase network exceptions (si están disponibles)
            else -> exception.javaClass.simpleName.contains("Network", ignoreCase = true)
        }
    }
}
