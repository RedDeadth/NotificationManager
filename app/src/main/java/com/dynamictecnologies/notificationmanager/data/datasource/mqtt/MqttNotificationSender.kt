package com.dynamictecnologies.notificationmanager.data.datasource.mqtt

import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import org.json.JSONObject

/**
 * Sender para envío de notificaciones vía MQTT.
 * 
 * Responsabilidad única: Enviar notificaciones a dispositivos ESP32.
 * 
 * Principios aplicados:
 * - SRP: Solo envía notificaciones
 * - DIP: Depende de MqttConnectionManager (abstracción)
 * - Clean Code: Lógica de serialización separada
 */
class MqttNotificationSender(
    private val connectionManager: MqttConnectionManager
) {
    companion object {
        private const val TAG = "MqttNotificationSender"
    }
    
    private var currentUserId: String? = null
    private var currentUsername: String? = null
    
    /**
     * Establece el usuario actual.
     */
    fun setCurrentUser(userId: String, username: String?) {
        this.currentUserId = userId
        this.currentUsername = username
    }
    
    /**
     * Envía una notificación a un dispositivo específico.
     * 
     * @param deviceId ID del dispositivo ESP32
     * @param notification Notificación a enviar
     * @return Result<Unit> Success si se envía correctamente
     */
    suspend fun sendNotification(
        deviceId: String,
        notification: NotificationInfo
    ): Result<Unit> {
        return try {
            if (!connectionManager.isConnected()) {
                return Result.failure(Exception("MQTT no conectado"))
            }
            
            val topic = "esp32/device/$deviceId/notification"
            val payload = buildNotificationPayload(notification)
            
            connectionManager.publish(topic, payload, qos = 1)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Envía una notificación general.
     */
    suspend fun sendGeneralNotification(title: String, content: String): Result<Unit> {
        return try {
            if (!connectionManager.isConnected()) {
                return Result.failure(Exception("MQTT no conectado"))
            }
            
            val topic = "/notificaciones/general"
            val json = JSONObject().apply {
                put("title", title)
                put("content", content)
                put("timestamp", System.currentTimeMillis())
            }.toString()
            
            connectionManager.publish(topic, json)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Construye el payload JSON de la notificación.
     */
    private fun buildNotificationPayload(notification: NotificationInfo): String {
        return JSONObject().apply {
            put("title", notification.title)
            put("content", notification.content)
            put("appName", notification.appName)
            put("timestamp", notification.timestamp.time)
            put("id", notification.id)
            currentUserId?.let { put("userId", it) }
            currentUsername?.let { put("username", it) }
        }.toString()
    }
}
