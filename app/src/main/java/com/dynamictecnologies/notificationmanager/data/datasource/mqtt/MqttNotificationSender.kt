package com.dynamictecnologies.notificationmanager.data.datasource.mqtt

import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import org.json.JSONObject

/**
 * Sender para envío de notificaciones vía MQTT.
 * 
 * Responsabilidad única: Enviar notificaciones a dispositivos ESP32.
 * 
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
            
            // Retornar el resultado de publish
            connectionManager.publish(topic, payload, qos = 1)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Envía una notificación directamente a un topic MQTT.
     * Usado para el nuevo flujo de pairing con tokens.
     * 
     * @param topic Topic MQTT (ej: "n/ABC12345")
     * @param notification Notificación a enviar
     * @return Result<Unit> Success si se envía correctamente
     */
    suspend fun sendNotificationToTopic(
        topic: String,
        notification: NotificationInfo
    ): Result<Unit> {
        android.util.Log.d(TAG, "=== ENVIANDO NOTIFICACIÓN A ESP32 ===")
        android.util.Log.d(TAG, "Topic: $topic")
        android.util.Log.d(TAG, "Título: ${notification.title}")
        android.util.Log.d(TAG, "App: ${notification.appName}")
        
        return try {
            val isConnected = connectionManager.isConnected()
            android.util.Log.d(TAG, "MQTT conectado: $isConnected")
            
            if (!isConnected) {
                android.util.Log.e(TAG, "ERROR: MQTT no conectado - intentando conectar...")
                // Intentar reconectar
                val connectResult = connectionManager.connect()
                if (connectResult.isFailure) {
                    android.util.Log.e(TAG, "Falló reconexión: ${connectResult.exceptionOrNull()?.message}")
                    return Result.failure(Exception("MQTT no conectado"))
                }
                android.util.Log.d(TAG, "Reconexión exitosa")
            }
            
            val payload = buildNotificationPayload(notification)
            android.util.Log.d(TAG, "Payload: $payload")
            
            // Retornar el resultado de publish
            val result = connectionManager.publish(topic, payload, qos = 1)
            if (result.isSuccess) {
                android.util.Log.d(TAG, "✓ Notificación enviada exitosamente a $topic")
            } else {
                android.util.Log.e(TAG, "✗ Error publicando: ${result.exceptionOrNull()?.message}")
            }
            result
        } catch (e: Exception) {
            android.util.Log.e(TAG, "✗ Excepción: ${e.message}", e)
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
            
            // Retornar el resultado de publish
            connectionManager.publish(topic, json)
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
