package com.dynamictecnologies.notificationmanager.data.repository

import com.dynamictecnologies.notificationmanager.data.datasource.firebase.DeviceDataSource
import com.dynamictecnologies.notificationmanager.data.datasource.mqtt.MqttConnectionManager
import com.dynamictecnologies.notificationmanager.data.datasource.mqtt.MqttDeviceLinkManager
import com.dynamictecnologies.notificationmanager.data.model.DeviceInfo
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.domain.repositories.DeviceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Implementación de DeviceRepository coordinando componentes MQTT y Firebase.
 * 
 * Responsabilidad única: Coordinar operaciones de dispositivos usando componentes especializados.
 * 
 * Principios aplicados:
 * - SRP: Solo coordina device operations, delega a componentes especializados
 * - DIP: Depende de abstracciones (managers, data sources)
 * - Clean Architecture: Data layer implementa domain contracts
 * - MQTT-First: Todas las notificaciones via MQTT, Firebase solo para auth
 */
class DeviceRepositoryImpl(
    private val deviceDataSource: DeviceDataSource,
    private val mqttConnectionManager: MqttConnectionManager,
    private val mqttDeviceLinkManager: MqttDeviceLinkManager
) : DeviceRepository {
    
    private val _connectedDevice = MutableStateFlow<DeviceInfo?>(null)
    override val connectedDevice: Flow<DeviceInfo?> = _connectedDevice.asStateFlow()
    
    override suspend fun connectToDevice(deviceId: String, userId: String): Result<Unit> {
        return try {
            // 1. Conectar a MQTT si no está conectado
            if (!mqttConnectionManager.isConnected()) {
                val connectResult = mqttConnectionManager.connect()
                if (connectResult.isFailure) {
                    return Result.failure(Exception("Failed to connect to MQTT: ${connectResult.exceptionOrNull()?.message}"))
                }
            }
            
            // 2. Buscar username
            val usernameResult = getUsernameByUid(userId)
            val username = usernameResult.getOrNull()
            
            // 3. Obtener email si hay username
            var email: String? = null
            if (username != null) {
                val userInfo = deviceDataSource.getUserInfo(username)
                email = userInfo.getOrNull()?.second
            }
            
            // 4. Registrar dispositivo en Firebase si no existe
            deviceDataSource.registerDeviceIfNeeded(deviceId)
            
            // 5. Vincular dispositivo via MQTT
            val mqttLinkResult = mqttDeviceLinkManager.linkDevice(deviceId, userId, username)
            if (mqttLinkResult.isFailure) {
                return Result.failure(Exception("Failed to link device via MQTT: ${mqttLinkResult.exceptionOrNull()?.message}"))
            }
            
            // 6. Vincular dispositivo en Firebase
            val firebaseLinkResult = deviceDataSource.linkDeviceToUser(
                deviceId,
                userId,
                username,
                email
            )
            
            if (firebaseLinkResult.isSuccess) {
                // 7. Actualizar estado local
                _connectedDevice.value = DeviceInfo(
                    id = deviceId,
                    name = "ESP32 Visualizador",
                    isConnected = true,
                    lastSeen = System.currentTimeMillis()
                )
            }
            
            firebaseLinkResult
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun unlinkDevice(): Result<Unit> {
        val device = _connectedDevice.value ?: return Result.failure(
            Exception("No device connected")
        )
        
        return try {
            // 1. Desvincular via MQTT
            val mqttUnlinkResult = mqttDeviceLinkManager.unlinkDevice(device.id)
            if (mqttUnlinkResult.isFailure) {
                // Log pero continuar con desvinculación de Firebase
                android.util.Log.w("DeviceRepositoryImpl", "MQTT unlink failed: ${mqttUnlinkResult.exceptionOrNull()?.message}")
            }
            
            // 2. Desvincular en Firebase
            val firebaseResult = deviceDataSource.unlinkDevice(device.id)
            
            // 3. Actualizar estado local si exitoso
            if (firebaseResult.isSuccess) {
                _connectedDevice.value = null
            }
            
            firebaseResult
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getUsernameByUid(uid: String): Result<String> {
        return deviceDataSource.findUsernameByUid(uid)
    }
    
    override fun observeDeviceNotifications(
        userId: String,
        deviceId: String
    ): Flow<List<NotificationInfo>> {
        // Las notificaciones se envían DIRECTAMENTE a ESP32 vía MQTT
        // No necesitamos observar desde el repositorio porque:
        // 1. NotificationListenerService captura la notificación
        // 2. MqttNotificationSender la envía directamente al dispositivo via topic MQTT
        // 3. ESP32 recibe y muestra la notificación
        // 
        // Firebase solo se usa para autenticación.
        // Este método retorna empty flow porque no es necesario.
        return MutableStateFlow(emptyList())
    }
}
