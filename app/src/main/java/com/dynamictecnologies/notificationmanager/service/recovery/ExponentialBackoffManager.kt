package com.dynamictecnologies.notificationmanager.service.recovery

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay

/**
 * Gestor de reintentos con backoff exponencial.
 * 
 * Gestiona el tiempo de espera entre reintentos que aumenta
 * exponencialmente para evitar loops de reintentos rápidos.
 * 
 * - Stateful: Mantiene contador de reintentos
 * - Configurable: Tiempos min/max customizables
 */
class ExponentialBackoffManager(
    private val initialDelayMs: Long = 1000L,    // 1 segundo
    private val maxDelayMs: Long = 300000L,       // 5 minutos
    private val multiplier: Double = 2.0
) {
    private var currentRetryCount = 0
    private var currentDelayMs = initialDelayMs
    
    /**
     * Calcula el próximo delay basado en reintentos actuales
     */
    fun getNextDelay(): Long {
        val delay = minOf(currentDelayMs, maxDelayMs)
        currentDelayMs = (currentDelayMs * multiplier).toLong()
        currentRetryCount++
        
        Log.d(TAG, "Backoff delay: ${delay}ms (retry #$currentRetryCount)")
        return delay
    }
    
    /**
     * Reinicia el backoff a valores iniciales
     */
    fun reset() {
        currentRetryCount = 0
        currentDelayMs = initialDelayMs
        Log.d(TAG, "Backoff reset")
    }
    
    /**
     * Obtiene el conteo actual de reintentos
     */
    fun getRetryCount(): Int = currentRetryCount
    
    /**
     * Indica si se alcanzó el máximo de reintentos
     */
    fun hasReachedMaxRetries(maxRetries: Int): Boolean {
        return currentRetryCount >= maxRetries
    }
    
    /**
     * Ejecuta delay con el próximo tiempo de backoff
     */
    suspend fun delayNext() {
        val delayTime = getNextDelay()
        delay(delayTime)
    }
    
    companion object {
        private const val TAG = "ExponentialBackoff"
    }
}
