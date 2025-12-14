package com.dynamictecnologies.notificationmanager.data.datasource.mqtt

import android.content.Context
import com.dynamictecnologies.notificationmanager.BuildConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLSocketFactory

/**
 * Manager para gestión de conexión MQTT.
 * 
 * Responsabilidad única: Conectar/desconectar del broker MQTT.
 * 
 * - Clean Architecture: Componente de data layer sin lógica de negocio
 * - Security: Credenciales desde BuildConfig (configuradas en local.properties)
 */
class MqttConnectionManager(
    private val context: Context,
    private val brokerUrl: String = BuildConfig.MQTT_BROKER,
    private val username: String = BuildConfig.MQTT_USERNAME,
    private val password: String = BuildConfig.MQTT_PASSWORD
) {
    companion object {
        private const val TAG = "MqttConnectionManager"
    }
    
    private val clientId = "NotificationManager_" + UUID.randomUUID().toString()
    private var mqttClient: MqttClient? = null
    
    private val _connectionStatus = MutableStateFlow(false)
    val connectionStatus: StateFlow<Boolean> = _connectionStatus.asStateFlow()
    
    private val isConnecting = AtomicBoolean(false)
    
    /**
     * Inicializa el cliente MQTT.
     */
    init {
        initializeClient()
    }
    
    private fun initializeClient() {
        try {
            if (mqttClient == null) {
                val persistence = MemoryPersistence()
                mqttClient = MqttClient(brokerUrl, clientId, persistence)
            }
        } catch (e: Exception) {
            mqttClient = null
        }
    }
    
    /**
     * Establece el callback para eventos MQTT.
     */
    fun setCallback(callback: MqttCallback) {
        mqttClient?.setCallback(callback)
    }
    
    /**
     * Conecta al broker MQTT.
     * 
     * @return Result<Unit> Success si conecta, Failure con excepción si falla
     */
    suspend fun connect(): Result<Unit> {
        // Si ya está conectando o conectado, retornar
        if (isConnecting.get() || _connectionStatus.value) {
            return Result.success(Unit)
        }
        
        if (mqttClient == null) {
            initializeClient()
        }
        
        isConnecting.set(true)
        
        return try {
            
            val options = MqttConnectOptions().apply {
                isCleanSession = true
                connectionTimeout = 60
                keepAliveInterval = 120
                isAutomaticReconnect = false
                userName = this@MqttConnectionManager.username
                password = this@MqttConnectionManager.password.toCharArray()
                socketFactory = SSLSocketFactory.getDefault()
            }
            
            // Desconectar si ya está conectado
            if (mqttClient?.isConnected == true) {
                try {
                    mqttClient?.disconnect()
                    delay(1000) // Non-blocking delay
                } catch (e: Exception) {
                }
            }
            
            mqttClient?.connect(options)
            _connectionStatus.value = true
            isConnecting.set(false)
            
            Result.success(Unit)
        } catch (e: Exception) {
            _connectionStatus.value = false
            isConnecting.set(false)
            Result.failure(e)
        }
    }
    
    /**
     * Desconecta del broker MQTT.
     */
    suspend fun disconnect() {
        try {
            if (mqttClient?.isConnected == true) {
                mqttClient?.disconnect()
            }
            _connectionStatus.value = false
        } catch (e: Exception) {
        }
    }
    
    /**
     * Verifica si está conectado.
     */
    fun isConnected(): Boolean {
        return mqttClient?.isConnected == true && _connectionStatus.value
    }
    
    /**
     * Publica un mensaje en un topic.
     * 
     * @param topic Topic MQTT
     * @param payload Contenido del mensaje
     * @param qos Quality of Service (0, 1, 2)
     * @return Result<Unit> Success si se publica correctamente
     */
    suspend fun publish(topic: String, payload: String, qos: Int = 1): Result<Unit> {
        return try {
            if (!isConnected()) {
                return Result.failure(Exception("MQTT no conectado"))
            }
            
            val message = MqttMessage(payload.toByteArray())
            message.qos = qos
            mqttClient?.publish(topic, message)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Suscribe a un topic.
     */
    fun subscribe(topic: String, qos: Int = 1) {
        try {
            mqttClient?.subscribe(topic, qos)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error subscribing to topic $topic: ${e.message}", e)
        }
    }
    
    /**
     * Obtiene el cliente MQTT (para casos avanzados).
     */
    fun getClient(): MqttClient? = mqttClient
}
