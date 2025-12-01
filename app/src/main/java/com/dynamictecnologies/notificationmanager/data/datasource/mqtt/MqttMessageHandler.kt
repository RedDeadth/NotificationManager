package com.dynamictecnologies.notificationmanager.data.datasource.mqtt

import android.content.Context
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import org.json.JSONObject

/**
 * Handler para procesamiento de mensajes MQTT.
 * 
 * Responsabilidad única: Procesar y parsear mensajes recibidos vía MQTT.
 * 
 * Principios aplicados:
 * - SRP: Solo procesa mensajes, no maneja conexión
 * - ISP: Interfaz simple para procesamiento
 * - Clean Code: Métodos pequeños, nombres claros
 */
class MqttMessageHandler(
    private val context: Context
) {
    companion object {
        private const val TAG = "MqttMessageHandler"
    }
    
    /**
     * Procesa un mensaje recibido de MQTT.
     * 
     * @param topic Topic del que proviene el mensaje
     * @param payload Contenido del mensaje
     * @param onDeviceFound Callback cuando se encuentra un dispositivo
     * @param onDeviceStatus Callback cuando cambia estado de dispositivo
     * @return Result con el procesamiento
     */
    suspend fun processMessage(
        topic: String,
        payload: String,
        onDeviceFound: ((String) -> Unit)? = null,
        onDeviceStatus: ((String, Boolean) -> Unit)? = null
    ): Result<Unit> {
        return try {
            when {
                topic.startsWith("esp32/response/") -> {
                    processDeviceDiscoveryResponse(topic, payload, onDeviceFound)
                }
                
                topic.startsWith("esp32/device/") && topic.endsWith("/status") -> {
                    processDeviceStatusUpdate(topic, payload, onDeviceStatus)
                }
                
                else -> {
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Procesa respuesta de descubrimiento de dispositivo.
     */
    private fun processDeviceDiscoveryResponse(
        topic: String,
        payload: String,
        onDeviceFound: ((String) -> Unit)?
    ) {
        val deviceId = topic.removePrefix("esp32/response/")
        
        try {
            val data = JSONObject(payload)
            val available = data.optBoolean("available", false)
            
            if (available) {
                onDeviceFound?.invoke(deviceId)
            }
        } catch (e: Exception) {
        }
    }
    
    /**
     * Procesa actualización de estado de dispositivo.
     */
    private fun processDeviceStatusUpdate(
        topic: String,
        payload: String,
        onDeviceStatus: ((String, Boolean) -> Unit)?
    ) {
        val deviceId = topic.split("/")[2]
        
        try {
            val data = JSONObject(payload)
            val connected = data.optBoolean("connected", false)
            
            onDeviceStatus?.invoke(deviceId, connected)
        } catch (e: Exception) {
        }
    }
    
    /**
     * Parsea payload de notificación.
     */
    fun parseNotificationPayload(payload: String): NotificationInfo? {
        return try {
            val json = JSONObject(payload)
            NotificationInfo(
                id = json.optLong("id", 0L),
                title = json.optString("title", ""),
                content = json.optString("content", ""),
                appName = json.optString("appName", ""),
                timestamp = java.util.Date(json.optLong("timestamp", System.currentTimeMillis()))
            )
        } catch (e: Exception) {
            null
        }
    }
}
