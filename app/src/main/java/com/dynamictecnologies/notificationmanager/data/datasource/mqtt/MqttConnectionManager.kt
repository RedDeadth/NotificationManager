package com.dynamictecnologies.notificationmanager.data.datasource.mqtt

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.dynamictecnologies.notificationmanager.BuildConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLSocketFactory

/**
 * Manager para gestión de conexión MQTT.
 * 
 * Responsabilidad única: Conectar/desconectar del broker MQTT.
 * 
 * Características:
 * - Sesión persistente (cleanSession=false) para no perder mensajes offline
 * - ClientId estático basado en Android ID para reconexiones consistentes
 * - Estado de sincronización para indicar recuperación de mensajes
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
        private const val CLIENT_ID_PREFIX = "NotifMgr_"
    }
    
    // ClientId persistente basado en Android ID - NO cambia entre reinicios
    private val clientId: String by lazy {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        CLIENT_ID_PREFIX + androidId.take(12)  // Limitar longitud
    }
    
    private var mqttClient: MqttClient? = null
    
    private val _connectionStatus = MutableStateFlow(false)
    val connectionStatus: StateFlow<Boolean> = _connectionStatus.asStateFlow()
    
    // Expone último error de conexión para visibilidad en UI
    private val _lastConnectionError = MutableStateFlow<String?>(null)
    val lastConnectionError: StateFlow<String?> = _lastConnectionError.asStateFlow()
    
    // Estado de sincronización - true cuando está recuperando mensajes del broker
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()
    
    private val isConnecting = AtomicBoolean(false)
    
    /**
     * Inicializa el cliente MQTT con persistencia en archivo.
     */
    init {
        initializeClient()
    }
    
    private fun initializeClient() {
        try {
            if (mqttClient == null) {
                // Usar persistencia en archivo para sesiones persistentes
                val persistenceDir = File(context.filesDir, "mqtt_persistence")
                if (!persistenceDir.exists()) {
                    persistenceDir.mkdirs()
                }
                val persistence = MqttDefaultFilePersistence(persistenceDir.absolutePath)
                mqttClient = MqttClient(brokerUrl, clientId, persistence)
                Log.d(TAG, "MQTT Client inicializado con clientId: $clientId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MQTT client: ${e.message}", e)
            _connectionStatus.value = false
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
                // Sesión persistente para no perder mensajes offline
                isCleanSession = false
                connectionTimeout = 60
                // Keep-alive de 3 minutos para detectar desconexiones más rápido
                keepAliveInterval = 180
                // Habilitar reconexión automática del cliente Paho
                isAutomaticReconnect = true
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
                    Log.w(TAG, "Error during pre-connect disconnect: ${e.message}", e)
                    _connectionStatus.value = false
                }
            }
            
            // Indicar que está sincronizando (recuperando mensajes del broker)
            _isSyncing.value = true
            
            mqttClient?.connect(options)
            _connectionStatus.value = true
            _lastConnectionError.value = null // Limpiar error previo en éxito
            isConnecting.set(false)
            
            Result.success(Unit)
        } catch (e: Exception) {
            _connectionStatus.value = false
            _lastConnectionError.value = e.message ?: "Error de conexión MQTT desconocido"
            isConnecting.set(false)
            Result.failure(e)
        }
    }
    
    /**
     * Desconecta del broker MQTT.
     * 
     * @return Result<Unit> Success si desconecta correctamente, Failure con excepción si falla
     */
    suspend fun disconnect(): Result<Unit> {
        return try {
            if (mqttClient?.isConnected == true) {
                mqttClient?.disconnect()
                Log.d(TAG, "MQTT disconnected successfully")
            }
            _connectionStatus.value = false
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting from MQTT: ${e.message}", e)
            _connectionStatus.value = false
            Result.failure(e)
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
     * Suscribe a un topic con QoS especificado.
     * Usa QoS 1 por defecto para garantizar entrega.
     */
    fun subscribe(topic: String, qos: Int = 1) {
        try {
            mqttClient?.subscribe(topic, qos)
            Log.d(TAG, "Suscrito a topic: $topic con QoS $qos")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error subscribing to topic $topic: ${e.message}", e)
        }
    }
    
    /**
     * Indica que la sincronización inicial de mensajes terminó.
     * Llamar después de procesar todos los mensajes pendientes del broker.
     */
    fun finishSyncing() {
        _isSyncing.value = false
        Log.d(TAG, "Sincronización de mensajes completada")
    }
    
    /**
     * Obtiene el cliente MQTT (para casos avanzados).
     */
    fun getClient(): MqttClient? = mqttClient
}
