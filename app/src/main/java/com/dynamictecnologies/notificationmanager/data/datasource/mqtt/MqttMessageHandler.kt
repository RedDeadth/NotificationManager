package com.dynamictecnologies.notificationmanager.data.datasource.mqtt

import android.content.Context
import android.util.Log
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONException
import org.json.JSONObject

/**
 * Handler para procesamiento de mensajes MQTT con backpressure.
 * 
 * Características:
 * - Channel con capacidad 1000 para manejar ráfagas tras reconexión
 * - BufferOverflow.SUSPEND para no perder mensajes críticos
 * - Procesamiento secuencial para evitar saturar CPU
 * - Estado de pending messages para UI
 * 
 * Responsabilidad única: Procesar y parsear mensajes recibidos vía MQTT.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MqttMessageHandler(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    companion object {
        private const val TAG = "MqttMessageHandler"
        private const val CHANNEL_CAPACITY = 1000
    }
    
    // Canal con backpressure para manejar ráfagas de mensajes
    private val messageChannel = Channel<QueuedMessage>(
        capacity = CHANNEL_CAPACITY,
        onBufferOverflow = BufferOverflow.SUSPEND // No perder mensajes, esperar
    )
    
    // Estado de mensajes pendientes para UI
    private val _pendingMessagesCount = MutableStateFlow(0)
    val pendingMessagesCount: StateFlow<Int> = _pendingMessagesCount.asStateFlow()
    
    // Estado de procesamiento activo
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()
    
    // Job para el procesador de cola
    private var processorJob: Job? = null
    
    /**
     * Datos de un mensaje en cola
     */
    data class QueuedMessage(
        val topic: String,
        val payload: String,
        val timestamp: Long = System.currentTimeMillis(),
        val onDeviceFound: ((String) -> Unit)? = null,
        val onDeviceStatus: ((String, Boolean) -> Unit)? = null
    )
    
    /**
     * Inicia el procesador de cola en background.
     */
    fun startProcessor() {
        if (processorJob?.isActive == true) return
        
        processorJob = scope.launch {
            Log.d(TAG, "Procesador de mensajes iniciado")
            for (message in messageChannel) {
                try {
                    _isProcessing.value = true
                    processMessageInternal(message)
                    _pendingMessagesCount.value = maxOf(0, _pendingMessagesCount.value - 1)
                } catch (e: Exception) {
                    Log.e(TAG, "Error procesando mensaje: ${e.message}")
                } finally {
                    if (messageChannel.isEmpty) {
                        _isProcessing.value = false
                    }
                }
            }
        }
    }
    
    /**
     * Detiene el procesador de cola.
     */
    fun stopProcessor() {
        processorJob?.cancel()
        processorJob = null
        _isProcessing.value = false
    }
    
    /**
     * Encola un mensaje para procesamiento con backpressure.
     * Si hay más de CHANNEL_CAPACITY mensajes, suspende hasta que haya espacio.
     */
    suspend fun queueMessage(
        topic: String,
        payload: String,
        onDeviceFound: ((String) -> Unit)? = null,
        onDeviceStatus: ((String, Boolean) -> Unit)? = null
    ) {
        _pendingMessagesCount.value++
        messageChannel.send(QueuedMessage(topic, payload, onDeviceFound = onDeviceFound, onDeviceStatus = onDeviceStatus))
    }
    
    /**
     * Procesa un mensaje recibido de MQTT.
     * Mantiene compatibilidad con API anterior.
     */
    suspend fun processMessage(
        topic: String,
        payload: String,
        onDeviceFound: ((String) -> Unit)? = null,
        onDeviceStatus: ((String, Boolean) -> Unit)? = null
    ): Result<Unit> {
        return try {
            queueMessage(topic, payload, onDeviceFound, onDeviceStatus)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error encolando mensaje: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Procesamiento interno del mensaje.
     */
    private fun processMessageInternal(message: QueuedMessage) {
        when {
            message.topic.startsWith("esp32/response/") -> {
                processDeviceDiscoveryResponse(message.topic, message.payload, message.onDeviceFound)
            }
            
            message.topic.startsWith("esp32/device/") && message.topic.endsWith("/status") -> {
                processDeviceStatusUpdate(message.topic, message.payload, message.onDeviceStatus)
            }
            
            else -> {
                Log.d(TAG, "Topic no reconocido: ${message.topic}")
            }
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
        
        if (deviceId.isEmpty()) {
            Log.w(TAG, "Topic de discovery con deviceId vacío: $topic")
            return
        }
        
        try {
            val data = JSONObject(payload)
            val available = data.optBoolean("available", false)
            
            if (available) {
                onDeviceFound?.invoke(deviceId)
                Log.d(TAG, "Dispositivo disponible: $deviceId")
            }
        } catch (e: JSONException) {
            Log.e(TAG, "JSON malformado en discovery response. Topic: $topic, Payload truncado: ${payload.take(100)}")
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
        val segments = topic.split("/")
        if (segments.size < 3) {
            Log.e(TAG, "Topic de status malformado (esperados >=3 segmentos): $topic")
            return
        }
        
        val deviceId = segments[2]
        
        if (deviceId.isEmpty()) {
            Log.w(TAG, "DeviceId vacío en topic de status: $topic")
            return
        }
        
        try {
            val data = JSONObject(payload)
            val connected = data.optBoolean("connected", false)
            
            onDeviceStatus?.invoke(deviceId, connected)
            Log.d(TAG, "Estado de dispositivo $deviceId: connected=$connected")
        } catch (e: JSONException) {
            Log.e(TAG, "JSON malformado en status update. Topic: $topic, Payload truncado: ${payload.take(100)}")
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
        } catch (e: JSONException) {
            Log.e(TAG, "Error parseando notificación JSON: ${e.message}. Payload truncado: ${payload.take(100)}")
            null
        }
    }
}
