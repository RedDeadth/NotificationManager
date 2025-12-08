package com.dynamictecnologies.notificationmanager.data.mqtt

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.*
import javax.net.ssl.SSLSocketFactory

/**
 * Gestor de conexión MQTT simplificado.
 * 
 * Responsabilidad única: Conectar/desconectar al broker MQTT.
 * Sin lógica de negocio, sin búsqueda de dispositivos, sin Firebase.
 * 
 * Principios aplicados:
 * - SRP: Solo maneja conexión MQTT
 * - DRY: Reutilizable por cualquier componente
 * - Clean Code: API simple y clara
 */
class MqttConnectionManager(
    private val brokerUrl: String,
    private val username: String,
    private val password: String
) {
    
    private var mqttClient: MqttClient? = null
    private val clientId = "NotifMgr_" + UUID.randomUUID().toString().substring(0, 8)
    
    private val _connectionStatus = MutableStateFlow(false)
    val connectionStatus: StateFlow<Boolean> = _connectionStatus.asStateFlow()
    
    /**
     * Conecta al broker MQTT
     */
    suspend fun connect(): Result<Unit> {
        if (mqttClient?.isConnected == true) {
            return Result.success(Unit)
        }
        
        return try {
            Log.d(TAG, "Conectando a MQTT broker: $brokerUrl")
            
            if (mqttClient == null) {
                mqttClient = MqttClient(brokerUrl, clientId, MemoryPersistence())
                mqttClient?.setCallback(createCallback())
            }
            
            val options = MqttConnectOptions().apply {
                isCleanSession = true
                connectionTimeout = 30
                keepAliveInterval = 60
                isAutomaticReconnect = true
                userName = username
                password = password.toCharArray()
                socketFactory = SSLSocketFactory.getDefault()
            }
            
            mqttClient?.connect(options)
            _connectionStatus.value = true
            Log.d(TAG, "✅ Conectado a MQTT broker")
            Result.success(Unit)
            
        } catch (e: MqttException) {
            _connectionStatus.value = false
            Log.e(TAG, "❌ Error conectando a MQTT: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Desconecta del broker MQTT
     */
    suspend fun disconnect() {
        try {
            mqttClient?.disconnect()
            _connectionStatus.value = false
            Log.d(TAG, "Desconectado de MQTT broker")
        } catch (e: Exception) {
            Log.e(TAG, "Error desconectando: ${e.message}")
        }
    }
    
    /**
     * Verifica si está conectado
     */
    fun isConnected(): Boolean {
        return mqttClient?.isConnected == true
    }
    
    /**
     * Publica mensaje a un topic
     */
    suspend fun publish(topic: String, payload: String, qos: Int = 1): Result<Unit> {
        if (!isConnected()) {
            return Result.failure(IllegalStateException("MQTT not connected"))
        }
        
        return try {
            val message = MqttMessage(payload.toByteArray()).apply {
                this.qos = qos
            }
            mqttClient?.publish(topic, message)
            Log.d(TAG, "Publicado a [$topic]: $payload")
            Result.success(Unit)
        } catch (e: MqttException) {
            Log.e(TAG, "Error publicando a [$topic]: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Crea callback para eventos MQTT
     */
    private fun createCallback() = object : MqttCallback {
        override fun connectionLost(cause: Throwable?) {
            _connectionStatus.value = false
            Log.w(TAG, "Conexión MQTT perdida: ${cause?.message}")
        }
        
        override fun messageArrived(topic: String?, message: MqttMessage?) {
            // No necesitamos recibir mensajes en este flujo simplificado
            // Solo ESP32 recibe (suscrito a n/{TOKEN})
        }
        
        override fun deliveryComplete(token: IMqttDeliveryToken?) {
            Log.d(TAG, "Mensaje entregado correctamente")
        }
    }
    
    /**
     * Limpia recursos
     */
    fun cleanup() {
        try {
            mqttClient?.disconnect()
            mqttClient?.close()
            mqttClient = null
        } catch (e: Exception) {
            Log.e(TAG, "Error en cleanup: ${e.message}")
        }
    }
    
    companion object {
        private const val TAG = "MqttConnection"
    }
}
