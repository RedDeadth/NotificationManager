package com.dynamictecnologies.notificationmanager.service

import android.content.Context
import android.util.Log
import com.dynamictecnologies.notificationmanager.data.model.DeviceInfo
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ChildEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLSocketFactory

class MqttService(private val context: Context) {
    
    private val TAG = "MqttService"
    
    // Configuración EMQX Cloud (Reemplaza con tus datos reales)
    private val BROKER_URL = "ssl://b5c0bf2b.ala.us-east-1.emqxsl.com:8883" // Dirección actualizada
    private val MQTT_USERNAME = "notificationmanager"
    private val MQTT_PASSWORD = "and123..."

    private val CLIENT_ID = "NotificationManager_" + UUID.randomUUID().toString()

    private var mqttClient: MqttClient? = null
    private val _connectionStatus = MutableStateFlow(false)
    val connectionStatus: StateFlow<Boolean> = _connectionStatus.asStateFlow()
    
    private val _connectedDevice = MutableStateFlow<DeviceInfo?>(null)
    val connectedDevice: StateFlow<DeviceInfo?> = _connectedDevice.asStateFlow()
    
    // Referencia a Firebase Database
    private val database = FirebaseDatabase.getInstance()
    private val dispositvosRef = database.getReference("dispositivos")
    private val usersRef = database.getReference("users")
    private val notificationsRef = database.getReference("notifications")
    
    // Scope para operaciones de corrutinas
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    
    // Username del usuario actual para identificarlo en Firebase
    private var currentUsername: String? = null
    private var currentUserId: String? = null
    
    // Listener de notificaciones de Firebase
    private var notificationsListener: ValueEventListener? = null
    
    private val RETRY_INTERVALS = listOf(2, 5, 15, 30, 60)
    private var currentRetryAttempt = 0
    private var lastRetryTime = 0L
    
    // Control de reconexión para evitar intentos múltiples simultáneos
    private val isReconnecting = AtomicBoolean(false)
    
    /**
     * Verifica si el cliente MQTT está conectado actualmente
     */
    fun isConnected(): Boolean {
        return mqttClient?.isConnected == true && _connectionStatus.value
    }

    /**
     * Conecta y espera hasta que la conexión esté establecida o falle
     */
    fun connectAndWait() {
        // Si ya está conectado, no hacer nada
        if (mqttClient?.isConnected == true && _connectionStatus.value) {
            return
        }
        
        // Si está en proceso de reconexión, esperar
        var attempts = 0
        while (isReconnecting.get() && attempts < 10) {
            delayBlocking(200)  // Esperar 200ms
            attempts++
            
            // Si se conectó mientras esperábamos
            if (mqttClient?.isConnected == true && _connectionStatus.value) {
                return
            }
        }
        
        // Iniciar conexión
        connect()
        
        // Esperar hasta 5 segundos a que se conecte
        attempts = 0
        while (!_connectionStatus.value && attempts < 25) {
            delayBlocking(200)  // Esperar 200ms
            attempts++
        }
    }
    
    /**
     * Publica una notificación a través de MQTT
     * Si no está conectado, intenta conectarse y espera brevemente antes de intentar publicar
     */
    fun publishNotification(title: String, content: String) {
        // Si no está conectado, intenta conectar primero
        if (!_connectionStatus.value || mqttClient?.isConnected != true) {
            Log.d(TAG, "MQTT no conectado, intentando conectar antes de publicar")
            connect()
            
            // Esperar brevemente para darle tiempo a conectarse
            delayBlocking(1000)
            
            // Si todavía no está conectado después de esperar, registrar error y salir
            if (!_connectionStatus.value || mqttClient?.isConnected != true) {
                Log.e(TAG, "No se pudo establecer conexión MQTT, no se puede publicar notificación")
                return
            }
        }
        try {
            val topic = "/notificaciones/general"
            val json = JSONObject().apply {
                put("title", title)
                put("content", content)
            }.toString()
            mqttClient?.publish(topic, MqttMessage(json.toByteArray()))
            Log.d(TAG, "Notificación publicada por MQTT: $json")
        } catch (e: Exception) {
            Log.e(TAG, "Error publicando notificación por MQTT", e)
        }
    }
    private var reconnectJob: Job? = null
    
    // Función no suspendida para esperar
    private fun delayBlocking(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
    
    // Inicializar una sola vez el cliente MQTT
    init {
        initializeMqttClient()
    }
    
    private fun initializeMqttClient() {
        try {
            if (mqttClient == null) {
                Log.d(TAG, "Inicializando cliente MQTT con ID: $CLIENT_ID")
                val persistence = MemoryPersistence()
                mqttClient = MqttClient(BROKER_URL, CLIENT_ID, persistence)
                
                mqttClient?.setCallback(object : MqttCallback {
                    override fun connectionLost(cause: Throwable?) {
                        Log.e(TAG, "Conexión MQTT perdida", cause)
                        _connectionStatus.value = false
                        
                        // No desconectar el dispositivo automáticamente, solo intentar reconectar MQTT
                        // _connectedDevice.value = null
                        
                        // Programar un solo intento de reconexión
                        scheduleReconnect()
                    }
                    
                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        message?.let {
                            Log.d(TAG, "Mensaje recibido en tópico: $topic")
                            try {
                                val payload = String(it.payload)
                                processMessage(topic, payload)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error procesando mensaje MQTT", e)
                            }
                        }
                    }
                    
                    override fun deliveryComplete(token: IMqttDeliveryToken?) {
                        Log.d(TAG, "Mensaje entregado correctamente")
                    }
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando cliente MQTT", e)
            mqttClient = null
        }
    }
    
    fun connect() {
        // Si ya está reconectando o conectado, no hacer nada
        if (isReconnecting.get() || _connectionStatus.value) {
            Log.d(TAG, "Conexión MQTT ya en proceso o conectada. Ignorando solicitud.")
            return
        }
        
        if (mqttClient == null) {
            initializeMqttClient()
        }
        
        // Marcar que estamos en proceso de reconexión
        isReconnecting.set(true)
        
        try {
            Log.d(TAG, "Iniciando conexión MQTT a $BROKER_URL")
            
            val options = MqttConnectOptions().apply {
                isCleanSession = true
                connectionTimeout = 60 // Aumentar timeout de conexión a 60 segundos
                keepAliveInterval = 120 // Aumentar keep alive a 120 segundos
                isAutomaticReconnect = false // Manejaremos la reconexión nosotros mismos
                
                // Configuración de seguridad para EMQX Cloud
                userName = MQTT_USERNAME
                password = MQTT_PASSWORD.toCharArray()
                
                // Configurar SSL/TLS
                socketFactory = SSLSocketFactory.getDefault()
            }
            
            // Verificar si el cliente ya está conectado
            if (mqttClient?.isConnected == true) {
                try {
                    mqttClient?.disconnect()
                    delayBlocking(1000) // Usar la función no suspendida aquí
                } catch (e: Exception) {
                    Log.e(TAG, "Error al desconectar MQTT antes de reconectar", e)
                }
            }
            
            mqttClient?.connect(options)
            _connectionStatus.value = true
            Log.d(TAG, "Conexión MQTT establecida con EMQX Cloud")
            
            // Ya no estamos en proceso de reconexión
            isReconnecting.set(false)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error en la conexión MQTT: ${e.message}", e)
            _connectionStatus.value = false
            isReconnecting.set(false)
            
            // Programar un intento de reconexión después de un tiempo
            scheduleReconnect(10000) // 10 segundos
        }
    }
    
    private fun scheduleReconnect(delayMillis: Long = 5000) {
        // Cancelar job previo si existe
        reconnectJob?.cancel()
        
        // Crear nuevo job para reconexión
        reconnectJob = serviceScope.launch {
            delay(delayMillis)
            if (!isReconnecting.getAndSet(true)) {
                try {
                    Log.d(TAG, "Intentando reconexión MQTT programada")
                    connect()
                } finally {
                    isReconnecting.set(false)
                }
            }
        }
    }
    
    fun disconnect() {
        try {
            // Cancelar cualquier intento de reconexión pendiente
            reconnectJob?.cancel()
            reconnectJob = null
            
            // Detener el listener de notificaciones si está activo
            currentUserId?.let { userId ->
                notificationsListener?.let { listener ->
                    notificationsRef.child(userId).removeEventListener(listener)
                    notificationsListener = null
                }
            }
            
            // Primero manejar el dispositivo
            _connectedDevice.value?.let { device ->
                serviceScope.launch {
                    try {
                        // Actualizar dispositivo a desvinculado en estructura antigua
                        val updates = hashMapOf<String, Any>(
                            "vinculado" to false,
                            "ultima_conexion" to "Desconectado: ${System.currentTimeMillis()}"
                        )
                        
                        // Actualizar en Firebase primero los valores normales
                        dispositvosRef.child(device.id).updateChildren(updates)
                        
                        // Eliminar el nodo usuario por separado usando removeValue()
                        dispositvosRef.child(device.id).child("usuario").removeValue()
                        
                        // Si tenemos username, también remover el dispositivo del usuario
                        currentUsername?.let { username ->
                            usersRef.child(username).child("devices").child(device.id).removeValue()
                        }
                        
                        // Enviar señal de desvinculación al dispositivo si MQTT sigue conectado
                        if (_connectionStatus.value) {
                            sendUnlinkToDevice(device.id)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error actualizando Firebase al desconectar", e)
                    }
                }
            }
            
            // Luego desconectar MQTT si está conectado
            if (mqttClient?.isConnected == true) {
                mqttClient?.disconnect()
            }
            
            _connectionStatus.value = false
            _connectedDevice.value = null
            Log.d(TAG, "Desconexión MQTT completada")
        } catch (e: Exception) {
            Log.e(TAG, "Error al desconectar MQTT", e)
        }
    }
    
    private fun sendUnlinkToDevice(deviceId: String) {
        if (!_connectionStatus.value || mqttClient?.isConnected != true) {
            Log.e(TAG, "No se puede enviar mensaje de desvinculación: MQTT no conectado")
            return
        }
        
        try {
            val baseTopic = "esp32/device/$deviceId/link"
            
            val message = JSONObject().apply {
                put("action", "unlink")
                put("timestamp", System.currentTimeMillis())
            }.toString()
            
            val mqttMessage = MqttMessage(message.toByteArray())
            mqttMessage.qos = 1
            mqttClient?.publish(baseTopic, mqttMessage)
            
            Log.d(TAG, "Mensaje de desvinculación enviado al dispositivo: $deviceId")
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando mensaje de desvinculación", e)
        }
    }
    
    fun searchDevices() {
        if (!_connectionStatus.value || mqttClient?.isConnected != true) {
            Log.e(TAG, "No se puede buscar dispositivos: MQTT no conectado")
            connect() // Intentar conectar primero
            return
        }
        
        try {
            // Publicar mensaje de descubrimiento
            val topic = "esp32/discover"
            val message = MqttMessage("discover".toByteArray())
            message.qos = 1
            
            mqttClient?.publish(topic, message)
            
            // Suscribirse al tópico de respuesta
            mqttClient?.subscribe("esp32/response/#", 1)
            
            // También buscar en Firebase los dispositivos disponibles
            serviceScope.launch {
                try {
                    val snapshot = dispositvosRef.get().await()
                    if (snapshot.exists()) {
                        Log.d(TAG, "Dispositivos encontrados en Firebase: ${snapshot.childrenCount}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error buscando dispositivos en Firebase", e)
                }
            }
            
            Log.d(TAG, "Búsqueda de dispositivos iniciada")
        } catch (e: Exception) {
            Log.e(TAG, "Error buscando dispositivos", e)
        }
    }
    
    // Método para establecer el username actual
    fun setCurrentUsername(username: String) {
        currentUsername = username
    }
    
    // Método para establecer el userId actual
    fun setCurrentUserId(userId: String) {
        currentUserId = userId
    }
    
    fun connectToDevice(deviceId: String, userId: String) {
        if (!_connectionStatus.value || mqttClient?.isConnected != true) {
            Log.e(TAG, "No se puede conectar al dispositivo: MQTT no conectado")
            
            // Conectar primero, y luego intentar conectar al dispositivo
            serviceScope.launch {
                connect()
                
                // Esperar un poco y verificar conexión antes de intentar
                delay(3000)
                if (_connectionStatus.value && mqttClient?.isConnected == true) {
                    connectToDeviceInternal(deviceId, userId)
                } else {
                    Log.e(TAG, "No se pudo establecer conexión MQTT para vincular dispositivo")
                }
            }
            return
        }
        
        connectToDeviceInternal(deviceId, userId)
    }
    
    private fun connectToDeviceInternal(deviceId: String, userId: String) {
        if (mqttClient?.isConnected != true) {
            Log.e(TAG, "Cliente MQTT no conectado, no se puede vincular dispositivo")
            return
        }
        
        try {
            // Guardar el userId actual
            currentUserId = userId
            
            // Suscribirse a los tópicos del dispositivo
            val baseTopic = "esp32/device/$deviceId"
            mqttClient?.subscribe("$baseTopic/status", 1)
            
            // Enviar información de vinculación
            val message = JSONObject().apply {
                put("action", "link")
                put("userId", userId)
                put("clientId", CLIENT_ID)
                put("timestamp", System.currentTimeMillis())
            }.toString()
            
            val mqttMessage = MqttMessage(message.toByteArray())
            mqttMessage.qos = 1
            mqttClient?.publish("$baseTopic/link", mqttMessage)
            
            // Actualizar estado
            val device = DeviceInfo(deviceId, "ESP32 Visualizador", true)
            _connectedDevice.value = device
            
            // Actualizar Firebase - ahora usando la estructura antigua
            serviceScope.launch {
                try {
                    // Buscar nombre de usuario y correo si disponible
                    var userName = "Usuario MQTT"
                    var userEmail = ""
                    findUsernameByUid(userId)?.let { username ->
                        currentUsername = username
                        userName = username
                        
                        // Intentar buscar el email también
                        try {
                            val userSnapshot = usersRef.child(username).get().await()
                            if (userSnapshot.exists()) {
                                userEmail = userSnapshot.child("email").getValue(String::class.java) ?: ""
                            } else {

                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error buscando email de usuario", e)
                        }
                    }
                    
                    // Estructura antigua: guardar datos del usuario directamente en el nodo del dispositivo
                    val updates = hashMapOf<String, Any>(
                        "vinculado" to true,
                        "ultima_conexion" to "Conectado: ${System.currentTimeMillis()}",
                        "usuario" to mapOf(
                            "uid" to userId,
                            "nombre" to userName,
                            "email" to userEmail
                        )
                    )
                    
                    // Guardar en el nodo del dispositivo
                    dispositvosRef.child(deviceId).updateChildren(updates)
                    
                    // También seguir guardando la referencia bidireccional en el nodo del usuario (extra)
                    if (currentUsername != null) {
                        usersRef.child(currentUsername!!).child("devices").child(deviceId).setValue(true)
                    }
                    
                    // Configurar un listener para las notificaciones en Firebase
                    setupNotificationsListener(userId, deviceId)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error actualizando Firebase al conectar dispositivo", e)
                }
            }
            
            Log.d(TAG, "Solicitud de conexión enviada al dispositivo: $deviceId")
        } catch (e: Exception) {
            Log.e(TAG, "Error conectando al dispositivo", e)
        }
    }
    
    private fun setupNotificationsListener(userId: String, deviceId: String) {
        // Primero eliminar cualquier listener existente
        notificationsListener?.let { listener ->
            notificationsRef.child(userId).removeEventListener(listener)
        }
        
        // Guardar el timestamp actual para solo procesar notificaciones nuevas
        val initialTimestamp = System.currentTimeMillis()
        Log.d(TAG, "Configurando listener de notificaciones desde timestamp: $initialTimestamp")
        
        // Crear nuevo listener
        notificationsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    // Procesar solo la notificación más reciente para evitar sobrecarga
                    var latestNotification: NotificationInfo? = null
                    var latestTimestamp: Long = 0
                    
                    snapshot.children.forEach { notifSnapshot ->
                        val timestamp = notifSnapshot.child("timestamp").getValue(Long::class.java) ?: 0
                        
                        // Solo procesar notificaciones nuevas (posteriores a la vinculación)
                        if (timestamp > initialTimestamp && timestamp > latestTimestamp) {
                            latestTimestamp = timestamp
                            
                            // Convertir de Firebase a NotificationInfo
                            val title = notifSnapshot.child("title").getValue(String::class.java) ?: ""
                            val content = notifSnapshot.child("content").getValue(String::class.java) ?: ""
                            val appName = notifSnapshot.child("appName").getValue(String::class.java) ?: ""
                            
                            latestNotification = NotificationInfo(
                                id = notifSnapshot.key?.toLongOrNull() ?: 0L,
                                title = title,
                                content = content,
                                appName = appName,
                                timestamp = Date(timestamp)
                            )
                        }
                    }
                    
                    // Enviar la notificación más reciente al dispositivo solo si es nueva
                    latestNotification?.let { notification ->
                        if (notification.timestamp.time > initialTimestamp) {
                            Log.d(TAG, "Enviando nueva notificación con timestamp: ${notification.timestamp.time}")
                            sendNotification(notification)
                        }
                    }
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error en el listener de notificaciones: ${error.message}")
            }
        }
        
        // Comenzar a escuchar cambios
        notificationsRef.child(userId).orderByChild("timestamp").addValueEventListener(notificationsListener!!)
        Log.d(TAG, "Listener de notificaciones configurado para usuario $userId")
    }
    
    private suspend fun findUsernameByUid(uid: String): String? {
        return try {
            val snapshot = database.getReference("usernames").orderByValue().equalTo(uid).get().await()
            if (snapshot.exists() && snapshot.childrenCount > 0) {
                snapshot.children.first().key
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error buscando username por UID", e)
            null
        }
    }
    
    // Variable para controlar el tiempo entre envíos de notificaciones
    private var lastNotificationTime = 0L
    private val MIN_INTERVAL_BETWEEN_NOTIFICATIONS = 200L // ms
    
    /**
     * Envía una notificación a través de MQTT al dispositivo conectado
     * Implementa control de flujo para evitar "Demasiadas publicaciones en curso"
     */
    fun sendNotification(notification: NotificationInfo) {
        // Aplicar limitación de tasa para evitar sobrecarga
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastNotificationTime < MIN_INTERVAL_BETWEEN_NOTIFICATIONS) {
            // Esperar un poco si se envían notificaciones demasiado rápido
            val waitTime = MIN_INTERVAL_BETWEEN_NOTIFICATIONS - (currentTime - lastNotificationTime)
            try {
                Thread.sleep(waitTime)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        lastNotificationTime = System.currentTimeMillis()

        // Si no está conectado, intentar conectar primero
        if (!_connectionStatus.value || mqttClient?.isConnected != true) {
            Log.d(TAG, "MQTT no conectado, intentando conectar antes de enviar notificación")
            connect()
            
            // Esperar brevemente para dar tiempo a la conexión
            delayBlocking(1000) 
            
            // Si todavía no está conectado, registrar error y salir
            if (!_connectionStatus.value || mqttClient?.isConnected != true) {
                Log.e(TAG, "No se pudo establecer conexión MQTT, no se puede enviar notificación")
                return
            }
        }
        
        _connectedDevice.value?.let { device ->
            try {
                val baseTopic = "esp32/device/${device.id}/notification"
                
                // Asegurar que cada mensaje tenga un timestamp único
                // Añadir un pequeño incremento si han pasado menos de 5ms desde el último mensaje
                val currentTime = System.currentTimeMillis()
                val adjustedTimestamp = if (notification.timestamp.time >= currentTime - 5) {
                    notification.timestamp.time + 5 // Añadir 5ms para diferenciar
                } else {
                    notification.timestamp.time
                }
                
                val message = JSONObject().apply {
                    put("title", notification.title)
                    put("content", notification.content)
                    put("appName", notification.appName)
                    put("timestamp", adjustedTimestamp)
                    put("id", notification.id) // Incluir ID como referencia adicional
                }.toString()
                
                val mqttMessage = MqttMessage(message.toByteArray())
                mqttMessage.qos = 1 // Usar QoS 1 para equilibrar confiabilidad y rendimiento
                
                mqttClient?.publish(baseTopic, mqttMessage)
                Log.d(TAG, "Notificación enviada a ${device.id}: ${notification.title}, timestamp: $adjustedTimestamp")
                
                // También guardar la referencia de que este dispositivo procesó esta notificación
                serviceScope.launch {
                    try {
                        // Solo si tenemos un username válido
                        if (currentUsername != null) {
                            // Registrar en Firebase que esta notificación fue enviada al dispositivo
                            val deviceNotifPath = "users/$currentUsername/devices/${device.id}/notifications/${notification.id}"
                            database.getReference(deviceNotifPath).setValue(adjustedTimestamp)
                            Log.d(TAG, "Registro en Firebase: Dispositivo ${device.id} procesó notificación ${notification.id}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error registrando notificación en Firebase", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error enviando notificación", e)
            }
        }
    }
    
    private fun processMessage(topic: String?, payload: String) {
        if (topic == null) return
        
        when {
            topic.startsWith("esp32/response/") -> {
                val deviceId = topic.removePrefix("esp32/response/")
                try {
                    val data = JSONObject(payload)
                    val status = data.optBoolean("available", false)
                    
                    if (status) {
                        Log.d(TAG, "Dispositivo encontrado: $deviceId")
                        
                        // Verificar si este dispositivo está registrado en Firebase
                        serviceScope.launch {
                            try {
                                val deviceSnapshot = dispositvosRef.child(deviceId).get().await()
                                if (!deviceSnapshot.exists()) {
                                    // Crear registro de dispositivo en Firebase
                                    val deviceData = hashMapOf(
                                        "disponible" to true,
                                        "vinculado" to false,
                                        "ultima_conexion" to "Primer registro: ${System.currentTimeMillis()}",
                                        "version_firmware" to "1.2.0_MQTT"
                                    )
                                    dispositvosRef.child(deviceId).setValue(deviceData)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error verificando dispositivo en Firebase", e)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error procesando respuesta del dispositivo", e)
                }
            }
            
            topic.startsWith("esp32/device/") && topic.endsWith("/status") -> {
                val deviceId = topic.split("/")[2]
                try {
                    val data = JSONObject(payload)
                    val connected = data.optBoolean("connected", false)
                    
                    if (connected && _connectedDevice.value?.id == deviceId) {
                        Log.d(TAG, "Dispositivo $deviceId conectado correctamente")
                        
                        // Actualizar estado del dispositivo
                        _connectedDevice.value = DeviceInfo(
                            deviceId,
                            "ESP32 Visualizador",
                            true,
                            System.currentTimeMillis()
                        )
                        
                        // Actualizar estado en Firebase
                        serviceScope.launch {
                            try {
                                dispositvosRef.child(deviceId).child("vinculado").setValue(true)
                                dispositvosRef.child(deviceId).child("ultima_conexion")
                                    .setValue("Conectado: ${System.currentTimeMillis()}")
                                
                                // Asegurarse de que tenemos el listener de notificaciones
                                currentUserId?.let { userId ->
                                    if (notificationsListener == null) {
                                        setupNotificationsListener(userId, deviceId)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error actualizando estado en Firebase", e)
                            }
                        }
                    } else if (!connected && _connectedDevice.value?.id == deviceId) {
                        // Dispositivo desconectado
                        _connectedDevice.value = null
                        
                        // Actualizar Firebase
                        serviceScope.launch {
                            try {
                                dispositvosRef.child(deviceId).child("vinculado").setValue(false)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error actualizando estado de desconexión en Firebase", e)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error procesando estado del dispositivo", e)
                }
            }
        }
    }
    
    // Mantener la función suspendida para uso en corrutinas
    private suspend fun delay(millis: Long) {
        withContext(Dispatchers.IO) {
            Thread.sleep(millis)
        }
    }
} 