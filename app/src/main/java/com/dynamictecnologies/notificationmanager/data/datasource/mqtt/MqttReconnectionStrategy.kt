package com.dynamictecnologies.notificationmanager.data.datasource.mqtt

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Estrategia de reconexión MQTT con backoff exponencial.
 * 
 * Responsabilidad única: Gestionar la lógica de reconexión automática.
 * 
 * - Thread Safety: Usa AtomicBoolean y AtomicInteger
 */
class MqttReconnectionStrategy(
    private val connectionManager: MqttConnectionManager,
    private val subscriptionManager: MqttSubscriptionManager,
    private val retryIntervals: List<Long> = DEFAULT_RETRY_INTERVALS
) {
    companion object {
        private const val TAG = "MqttReconnectionStrategy"
        
        // Intervalos de reintento en segundos: 2s, 5s, 15s, 30s, 60s
        private val DEFAULT_RETRY_INTERVALS = listOf(2000L, 5000L, 15000L, 30000L, 60000L)
    }
    
    private val scope = CoroutineScope(Dispatchers.IO)
    private var reconnectJob: Job? = null
    
    private val currentRetryAttempt = AtomicInteger(0)
    private val isReconnecting = AtomicBoolean(false)
    
    /**
     * Programa un intento de reconexión de forma thread-safe.
     * 
     * @param delayMillis Delay personalizado en milisegundos (opcional)
     */
    @Synchronized
    fun scheduleReconnect(delayMillis: Long? = null) {
        // Verificación atómica: si ya está reconectando, retornar
        if (isReconnecting.get()) {
            return
        }
        
        // Marcar como reconectando ANTES de cualquier otra operación
        isReconnecting.set(true)
        
        // Cancelar job previo si existe (ahora es seguro, nadie más puede entrar)
        reconnectJob?.cancel()
        reconnectJob = null
        
        // Determinar el delay a usar
        val delay = delayMillis ?: getNextRetryInterval()
        
        // Programar reconexión
        reconnectJob = scope.launch {
            try {
                delay(delay)
                
                // Intentar reconectar
                val result = connectionManager.connect()
                
                if (result.isSuccess) {
                    // Reconexión exitosa - resetear todo
                    resetAttempts()
                    
                    // Re-suscribirse a todos los topics
                    subscriptionManager.resubscribeAll()
                } else {
                    // Reconexión falló, incrementar contador
                    currentRetryAttempt.incrementAndGet()
                    
                    // Liberar flag ANTES de programar nuevo intento
                    isReconnecting.set(false)
                    
                    // Programar nuevo intento (llamada recursiva)
                    scheduleReconnect()
                }
            } catch (e: Exception) {
                // Error durante reconexión
                currentRetryAttempt.incrementAndGet()
                
                // Liberar flag ANTES de programar nuevo intento
                isReconnecting.set(false)
                
                // Programar nuevo intento
                scheduleReconnect()
            }
            // NOTA: No hay finally que setee isReconnecting=false
            // El flag se libera explícitamente en cada path:
            // - Éxito: resetAttempts() lo hace
            // - Fallo: se libera antes de scheduleReconnect()
        }
    }
    
    /**
     * Cancela cualquier intento de reconexión pendiente.
     */
    fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        isReconnecting.set(false)
    }
    
    /**
     * Reinicia el contador de intentos.
     * Llamado después de una reconexión exitosa.
     */
    fun resetAttempts() {
        currentRetryAttempt.set(0)
        isReconnecting.set(false)
    }
    
    /**
     * Obtiene el intervalo de reintento siguiente basado en backoff exponencial.
     * 
     * @return Long Intervalo en milisegundos
     */
    fun getNextRetryInterval(): Long {
        val attempt = currentRetryAttempt.get()
        val index = minOf(attempt, retryIntervals.size - 1)
        return retryIntervals[index]
    }
    
    /**
     * Verifica si está en proceso de reconexión.
     */
    fun isReconnecting(): Boolean {
        return isReconnecting.get()
    }
    
    /**
     * Obtiene el número de intentos de reconexión actuales.
     */
    fun getCurrentAttempt(): Int {
        return currentRetryAttempt.get()
    }
}
