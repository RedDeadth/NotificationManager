package com.dynamictecnologies.notificationmanager.domain.usecases.device

import com.dynamictecnologies.notificationmanager.data.mqtt.MqttConnectionManager
import com.dynamictecnologies.notificationmanager.domain.entities.DevicePairing
import com.dynamictecnologies.notificationmanager.domain.entities.InvalidTokenException
import com.dynamictecnologies.notificationmanager.domain.entities.TokenValidator
import com.dynamictecnologies.notificationmanager.domain.repositories.DevicePairingRepository

/**
 * Caso de uso para vincular dispositivo mediante token de 8 caracteres.
 * 
 * Proceso simplificado:
 * 1. Validar token
 * 2. Generar topic MQTT
 * 3. Guardar localmente
 * 4. Conectar al broker MQTT
 * 
 * Sin Firebase, sin userId, sin complejidad.
 * 
 * Principios aplicados:
 * - SRP: Solo vinculación de dispositivo
 * - Clean Architecture: Lógica de negocio en dominio
 * - DIP: Depende de abstracciones
 */
class PairDeviceWithTokenUseCase(
    private val pairingRepository: DevicePairingRepository,
    private val mqttConnectionManager: MqttConnectionManager
) {
    
    /**
     * Vincula dispositivo ESP32 con token de 8 caracteres.
     * 
     * @param bluetoothName Nombre del dispositivo Bluetooth (ej: "ESP32_A3F9")
     * @param bluetoothAddress Dirección MAC (ej: "XX:XX:XX:XX:XX:XX")
     * @param token Token de 8 caracteres alfanuméricos (ej: "A3F9K2L7")
     * @return Result<Unit> éxito o InvalidTokenException
     */
    suspend operator fun invoke(
        bluetoothName: String,
        bluetoothAddress: String,
        token: String
    ): Result<Unit> {
        // Validar formato de token
        if (!TokenValidator.validate(token)) {
            return Result.failure(InvalidTokenException(token))
        }
        
        // Crear pairing
        val pairing = DevicePairing(
            bluetoothName = bluetoothName,
            bluetoothAddress = bluetoothAddress,
            token = token.uppercase(),  // Normalizar a uppercase
            mqttTopic = TokenValidator.formatAsTopic(token.uppercase()),
            pairedAt = System.currentTimeMillis()
        )
        
        // Guardar localmente
        pairingRepository.savePairing(pairing).onFailure {
            return Result.failure(it)
        }
        
        // Conectar MQTT
        return mqttConnectionManager.connect()
    }
}
