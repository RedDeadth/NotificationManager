package com.dynamictecnologies.notificationmanager.data.mqtt

import android.util.Log
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.data.dto.MqttNotificationDto

/**
 * Publicador de notificaciones MQTT.
 * 
 * Responsabilidad única: Publicar mensajes en topics MQTT.
 * 
 * Principios aplicados:
 * - SRP: Solo publicación, no conexión
 * - DIP: Depende de MqttConnectionManager abstraction
 */
class MqttNotificationPublisher(
    private val connectionManager: MqttConnectionManager
) {
    
    /**
     * Publica notificación en topic MQTT (legacy)
     */
    suspend fun publish(topic: String, notification: NotificationInfo): Result<Unit> {
        if (!connectionManager.connectionStatus.value) {
            return Result.failure(IllegalStateException("MQTT not connected"))
        }
        
        try {
            // Formato JSON simple para ESP32
            val payload = buildJsonPayload(notification.title, notification.content)
            
            connectionManager.publish(topic, payload).onSuccess {
                Log.d(TAG, "✅ Notificación publicada en $topic")
            }.onFailure { error ->
                Log.e(TAG, "❌ Error publicando: ${error.message}")
                return Result.failure(error)
            }
            
            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error inesperado publicando notificación", e)
            return Result.failure(e)
        }
    }
    
    /**
     * Publica notificación usando DTO (recomendado por seguridad)
     */
    suspend fun publishDto(topic: String, dto: MqttNotificationDto): Result<Unit> {
        if (!connectionManager.connectionStatus.value) {
            return Result.failure(IllegalStateException("MQTT not connected"))
        }
        
        try {
            val payload = dto.toJson()
            
            connectionManager.publish(topic, payload).onSuccess {
                Log.d(TAG, "✅ Notificación DTO publicada en $topic")
            }.onFailure { error ->
                Log.e(TAG, "❌ Error publicando DTO: ${error.message}")
                return Result.failure(error)
            }
            
            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error inesperado publicando DTO", e)
            return Result.failure(e)
        }
    }
    
    /**
     * Construye payload JSON simple
     */
    private fun buildJsonPayload(title: String, content: String): String {
        val escapedTitle = title.replace("\"", "\\\"")
        val escapedContent = content.replace("\"", "\\\"")
        return "{\"t\":\"$escapedTitle\",\"c\":\"$escapedContent\"}"
    }
    
    companion object {
}
