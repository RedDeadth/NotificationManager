package com.dynamictecnologies.notificationmanager.data.datasource.mqtt

import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.domain.repositories.NotificationSender
import org.json.JSONObject

/**
 * Sender para envío de notificaciones vía MQTT.
 * 
 * Responsabilidad única: Enviar notificaciones a dispositivos ESP32.
 * 
 * Implementa NotificationSender para inversión de dependencias.
 */
class MqttNotificationSender(
    private val connectionManager: MqttConnectionManager
) : NotificationSender {
    companion object {
        private const val TAG = "MqttNotificationSender"
        // Límite de buffer ESP32 típico (256 bytes con margen de seguridad)
        private const val MAX_PAYLOAD_SIZE = 240
    }
    
    private var currentUserId: String? = null
    private var currentUsername: String? = null
    
    /**
     * Establece el usuario actual.
     */
    override fun setCurrentUser(userId: String, username: String?) {
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
    override suspend fun sendNotification(
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
    override suspend fun sendNotificationToTopic(
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
    override suspend fun sendGeneralNotification(title: String, content: String): Result<Unit> {
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
     * 
     * SEGURIDAD:
     * - NO incluye userId ni username para evitar leakage de datos sensibles
     * - Limita payload a MAX_PAYLOAD_SIZE bytes para evitar buffer overflow en ESP32
     */
    private fun buildNotificationPayload(notification: NotificationInfo): String {
        // Calcular espacio disponible para contenido dinámico
        // JSON base: {"title":"","content":"","appName":"","timestamp":0000000000000,"id":0}
        // ~70 bytes de overhead JSON
        val jsonOverhead = 70
        val maxContentSize = MAX_PAYLOAD_SIZE - jsonOverhead
        
        // Truncar campos para respetar límite
        val titleMaxLen = minOf(50, maxContentSize / 3)
        val contentMaxLen = minOf(120, maxContentSize / 2)
        val appNameMaxLen = minOf(30, maxContentSize / 4)
        
        val truncatedTitle = notification.title.take(titleMaxLen)
        val truncatedContent = notification.content.take(contentMaxLen)
        val truncatedAppName = notification.appName.take(appNameMaxLen)
        
        return JSONObject().apply {
            put("title", truncatedTitle)
            put("content", truncatedContent)
            put("appName", truncatedAppName)
            put("timestamp", notification.timestamp.time)
            put("id", notification.id)
            // SEGURIDAD: userId y username REMOVIDOS - no necesarios para ESP32
        }.toString()
    }
}
