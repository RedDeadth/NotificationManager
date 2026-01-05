package com.dynamictecnologies.notificationmanager.domain.usecases.device

import com.dynamictecnologies.notificationmanager.data.datasource.mqtt.MqttConnectionManager
import com.dynamictecnologies.notificationmanager.data.datasource.mqtt.MqttNotificationSender
import com.dynamictecnologies.notificationmanager.domain.entities.DevicePairing
import com.dynamictecnologies.notificationmanager.domain.entities.InvalidTokenException
import com.dynamictecnologies.notificationmanager.domain.entities.TokenValidator
import com.dynamictecnologies.notificationmanager.domain.repositories.DevicePairingRepository

/**
 * Use Case para vincular un dispositivo ESP32 con token.
 * 
 * Flujo:
 * 1. Validar token (6 caracteres alfanuméricos)
 * 2. Crear DevicePairing con topic derivado
 * 3. Guardar en repository
 * 4. Conectar MQTT al topic del dispositivo
 * 5. Enviar notificación de conexión al ESP32
 * 
 * - Input validation: Token validado antes de procesar
 * - Clean Architecture: Domain layer, no dependencias Android
 */
class PairDeviceWithTokenUseCase(
    private val pairingRepository: DevicePairingRepository,
    private val mqttConnectionManager: MqttConnectionManager,
    private val notificationSender: MqttNotificationSender? = null  // Opcional para backward compatibility
) {
    
    /**
     * Vincula dispositivo ESP32 con token de 6 caracteres.
     * 
     * @param bluetoothName Nombre del dispositivo Bluetooth (ej: "ESP32_A3F9")
     * @param bluetoothAddress Dirección MAC (ej: "XX:XX:XX:XX:XX:XX")
     * @param token Token de 6 caracteres alfanuméricos (ej: "A3F9K2")
     * @param username Nombre del usuario que se conecta (opcional)
     * @return Result<Unit> éxito o InvalidTokenException
     */
    suspend operator fun invoke(
        bluetoothName: String,
        bluetoothAddress: String,
        token: String,
        username: String = "Usuario"
    ): Result<Unit> {
        // Normalizar token a uppercase primero
        val normalizedToken = token.uppercase()
        
        // Validar formato de token (ahora normalizado)
        if (!TokenValidator.validate(normalizedToken)) {
            return Result.failure(InvalidTokenException(token))
        }
        
        val mqttTopic = TokenValidator.formatAsTopic(normalizedToken)
        
        // Crear pairing
        val pairing = DevicePairing(
            bluetoothName = bluetoothName,
            bluetoothAddress = bluetoothAddress,
            token = normalizedToken,
            mqttTopic = mqttTopic,
            pairedAt = System.currentTimeMillis()
        )
        
        // Guardar localmente
        pairingRepository.savePairing(pairing).onFailure {
            return Result.failure(it)
        }
        
        // Conectar MQTT
        val connectResult = mqttConnectionManager.connect()
        if (connectResult.isFailure) {
            return connectResult
        }
        
        // Enviar notificación de conexión al ESP32
        notificationSender?.sendConnectionNotification(mqttTopic, username)
        
        return Result.success(Unit)
    }
}

