package com.dynamictecnologies.notificationmanager.data.datasource.mqtt

import org.json.JSONObject

/**
 * Manager para vinculación/desvinculación de dispositivos ESP32 vía MQTT.
 * 
 * Responsabilidad única: Gestionar el protocolo de linking de dispositivos.
 * 
 * Principios aplicados:
 * - SRP: Solo maneja linking/unlinking de dispositivos
 * - DIP: Depende de abstracción (MqttConnectionManager)
 * - Clean Code: Lógica clara y enfocada en protocolo MQTT
 * - OCP: Abierto para extensión (callbacks), cerrado para modificación
 */
class MqttDeviceLinkManager(
    private val connectionManager: MqttConnectionManager,
    private val subscriptionManager: MqttSubscriptionManager
) {
    companion object {
        private const val TAG = "MqttDeviceLinkManager"
    }
    
    /**
     * Vincula un dispositivo ESP32 con un usuario.
     * 
     * @param deviceId ID del dispositivo ESP32
     * @param userId ID del usuario
     * @param username Nombre de usuario (opcional)
     * @return Result<Unit> Success si se envía el mensaje correctamente
     */
    suspend fun linkDevice(
        deviceId: String,
        userId: String,
        username: String? = null
    ): Result<Unit> {
        return try {
            if (!connectionManager.isConnected()) {
                return Result.failure(Exception("MQTT no conectado"))
            }
            
            // Suscribirse al topic de estado del dispositivo
            val statusResult = subscriptionManager.subscribe("esp32/device/$deviceId/status", 1)
            if (statusResult.isFailure) {
                return statusResult
            }
            
            // Construir mensaje de vinculación
            val topic = "esp32/device/$deviceId/link"
            val payload = buildLinkPayload(userId, username)
            
            // Publicar mensaje de vinculación y retornar resultado
            return connectionManager.publish(topic, payload, qos = 1)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Desvincula un dispositivo ESP32.
     * 
     * @param deviceId ID del dispositivo ESP32
     * @return Result<Unit> Success si se envía el mensaje correctamente
     */
    suspend fun unlinkDevice(deviceId: String): Result<Unit> {
        return try {
            if (!connectionManager.isConnected()) {
                return Result.failure(Exception("MQTT no conectado"))
            }
            
            // Construir mensaje de desvinculación
            val topic = "esp32/device/$deviceId/link"
            val payload = buildUnlinkPayload()
            
            // Publicar mensaje de desvinculación
            val result = connectionManager.publish(topic, payload, qos = 1)
            
            // Desuscribirse del topic de estado
            if (result.isSuccess) {
                subscriptionManager.unsubscribe("esp32/device/$deviceId/status")
            }
            
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Suscribe a los topics de estado de un dispositivo.
     * 
     * @param deviceId ID del dispositivo ESP32
     * @return Result<Unit> Success si se suscribe correctamente
     */
    suspend fun subscribeToDeviceStatus(deviceId: String): Result<Unit> {
        return subscriptionManager.subscribe("esp32/device/$deviceId/status", 1)
    }
    
    /**
     * Construye el payload JSON para vincular un dispositivo.
     */
    private fun buildLinkPayload(userId: String, username: String?): String {
        return JSONObject().apply {
            put("action", "link")
            put("userId", userId)
            username?.let { put("username", it) }
            put("clientId", connectionManager.getClient()?.clientId ?: "unknown")
            put("timestamp", System.currentTimeMillis())
        }.toString()
    }
    
    /**
     * Construye el payload JSON para desvincular un dispositivo.
     */
    private fun buildUnlinkPayload(): String {
        return JSONObject().apply {
            put("action", "unlink")
            put("timestamp", System.currentTimeMillis())
        }.toString()
    }
}
