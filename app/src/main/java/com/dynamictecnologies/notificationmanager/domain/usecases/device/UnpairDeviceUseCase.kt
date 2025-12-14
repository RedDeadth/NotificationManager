package com.dynamictecnologies.notificationmanager.domain.usecases.device

import com.dynamictecnologies.notificationmanager.data.datasource.mqtt.MqttConnectionManager
import com.dynamictecnologies.notificationmanager.domain.repositories.DevicePairingRepository

/**
 * Use Case para desvincular dispositivo ESP32.
 * 
 * Flujo:
 * 1. Eliminar de repository
 * 2. Desconectar MQTT
 * 
 * - Cleanup: Libera recursos MQTT
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
