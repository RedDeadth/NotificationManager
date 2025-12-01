package com.dynamictecnologies.notificationmanager.data.datasource.mqtt

import com.dynamictecnologies.notificationmanager.data.model.DeviceInfo
import org.eclipse.paho.client.mqttv3.MqttMessage

/**
 * Scanner para descubrimiento de dispositivos ESP32.
 * 
 * Responsabilidad única: Búsqueda y descubrimiento de dispositivos vía MQTT.
 * 
 * Principios aplicados:
 * - SRP: Solo busca dispositivos
 * - DIP: Depende de abstracción (MqttConnectionManager)
 * - Clean Code: Lógica clara y enfocada
 */
class MqttDeviceScanner(
    private val connectionManager: MqttConnectionManager
) {
    companion object {
        private const val TAG = "MqttDeviceScanner"
        private const val DISCOVERY_TOPIC = "esp32/discover"
        private const val RESPONSE_TOPIC = "esp32/response/#"
    }
    
    /**
     * Inicia búsqueda de dispositivos ESP32.
     * 
     * @return Result<Unit> Success si inicia la búsqueda correctamente
     */
    suspend fun searchDevices(): Result<Unit> {
        return try {
            if (!connectionManager.isConnected()) {
                return Result.failure(Exception("MQTT no conectado"))
            }
            
            // Publicar mensaje de descubrimiento
            val message = MqttMessage("discover".toByteArray())
            message.qos = 1
            
            connectionManager.publish(DISCOVERY_TOPIC, "discover", 1)
            
            // Suscribirse a respuestas
            connectionManager.subscribe(RESPONSE_TOPIC, 1)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Crea objeto DeviceInfo a partir de un deviceId encontrado.
     */
    fun createDeviceInfo(deviceId: String): DeviceInfo {
        return DeviceInfo(
            id = deviceId,
            name = "ESP32 Visualizador",
            isConnected = false
        )
    }
}
