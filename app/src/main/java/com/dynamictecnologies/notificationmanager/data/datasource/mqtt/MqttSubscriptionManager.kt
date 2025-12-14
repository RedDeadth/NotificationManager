package com.dynamictecnologies.notificationmanager.data.datasource.mqtt

import java.util.concurrent.ConcurrentHashMap

/**
 * Manager para gestión de suscripciones a topics MQTT.
 * 
 * Responsabilidad única: Administrar suscripciones a topics MQTT.
 * 
 * - Thread Safety: Usa ConcurrentHashMap para manejo concurrente
 */
class MqttSubscriptionManager(
    private val connectionManager: MqttConnectionManager
) {
    companion object {
        private const val TAG = "MqttSubscriptionManager"
    }
    
    // Thread-safe set de suscripciones activas
    private val activeSubscriptions = ConcurrentHashMap.newKeySet<String>()
    
    /**
     * Suscribe a un topic MQTT.
     * 
     * @param topic Topic MQTT
     * @param qos Quality of Service (0, 1, 2)
     * @return Result<Unit> Success si se suscribe correctamente
     */
    suspend fun subscribe(topic: String, qos: Int = 1): Result<Unit> {
        return try {
            // Prevenir suscripciones duplicadas
            if (activeSubscriptions.contains(topic)) {
                return Result.success(Unit)
            }
            
            // Verificar conexión
            if (!connectionManager.isConnected()) {
                return Result.failure(Exception("MQTT no conectado"))
            }
            
            // Suscribirse al topic
            connectionManager.subscribe(topic, qos)
            
            // Agregar a suscripciones activas
            activeSubscriptions.add(topic)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Desuscribe de un topic MQTT.
     * 
     * @param topic Topic MQTT
     * @return Result<Unit> Success si se desuscribe correctamente
     */
    suspend fun unsubscribe(topic: String): Result<Unit> {
        return try {
            // Remover de suscripciones activas
            activeSubscriptions.remove(topic)
            
            // Intentar desuscribirse si está conectado
            if (connectionManager.isConnected()) {
                connectionManager.getClient()?.unsubscribe(topic)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Re-suscribe a todos los topics activos.
     * Útil después de una reconexión MQTT.
     * 
     * @return Result<Unit> Success si re-suscribe a todos correctamente
     */
    suspend fun resubscribeAll(): Result<Unit> {
        return try {
            if (!connectionManager.isConnected()) {
                return Result.failure(Exception("MQTT no conectado"))
            }
            
            // Re-suscribirse a cada topic activo
            activeSubscriptions.forEach { topic ->
                connectionManager.subscribe(topic, 1)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Obtiene el conjunto de suscripciones activas.
     * 
     * @return Set<String> Conjunto inmutable de topics activos
     */
    fun getActiveSubscriptions(): Set<String> {
        return activeSubscriptions.toSet()
    }
    
    /**
     * Limpia todas las suscripciones.
     * Usado típicamente al desconectar.
     */
    fun clear() {
        activeSubscriptions.clear()
    }
}
