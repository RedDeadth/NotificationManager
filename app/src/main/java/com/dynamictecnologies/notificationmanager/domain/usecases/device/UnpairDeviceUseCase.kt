package com.dynamictecnologies.notificationmanager.domain.usecases.device

import com.dynamictecnologies.notificationmanager.data.mqtt.MqttConnectionManager
import com.dynamictecnologies.notificationmanager.domain.repositories.DevicePairingRepository

/**
 * Caso de uso para desvincular el dispositivo actual.
 * 
 * Proceso:
 * 1. Limpiar SharedPreferences
 * 2. Desconectar MQTT
 * 
 * Principios aplicados:
 * - SRP: Solo desvinculación
 * - Clean Architecture: Lógica en dominio
 */
class UnpairDeviceUseCase(
    private val pairingRepository: DevicePairingRepository,
    private val mqttConnectionManager: MqttConnectionManager
) {
    
    /**
     * Desvincula el dispositivo ESP32 actual.
     */
    suspend operator fun invoke(): Result<Unit> {
        // Desconectar MQTT primero
        mqttConnectionManager.disconnect()
        
        // Limpiar pairing guardado
        return pairingRepository.clearPairing()
    }
}
