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
     * 
     * @return Result<Unit> Success si se desvincula correctamente, Failure si alguna operación falla
     */
    suspend operator fun invoke(): Result<Unit> {
        // Desconectar MQTT primero
        val disconnectResult = mqttConnectionManager.disconnect()
        if (disconnectResult.isFailure) {
            // Continuar con el unpair aunque falle la desconexión, pero logear
            android.util.Log.w("UnpairDeviceUseCase", "MQTT disconnect failed, continuing with unpair: ${disconnectResult.exceptionOrNull()?.message}")
        }
        
        // Limpiar pairing guardado
        return pairingRepository.clearPairing()
    }
}
