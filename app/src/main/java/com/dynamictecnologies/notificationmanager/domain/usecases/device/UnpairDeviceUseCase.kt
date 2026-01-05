package com.dynamictecnologies.notificationmanager.domain.usecases.device

import com.dynamictecnologies.notificationmanager.data.datasource.mqtt.MqttConnectionManager
import com.dynamictecnologies.notificationmanager.data.datasource.mqtt.MqttNotificationSender
import com.dynamictecnologies.notificationmanager.domain.repositories.DevicePairingRepository
import kotlinx.coroutines.flow.firstOrNull

/**
 * Use Case para desvincular dispositivo ESP32.
 * 
 * Flujo:
 * 1. Enviar notificación de desconexión al ESP32
 * 2. Desconectar MQTT
 * 3. Eliminar de repository
 * 
 * - Cleanup: Libera recursos MQTT
 */
class UnpairDeviceUseCase(
    private val pairingRepository: DevicePairingRepository,
    private val mqttConnectionManager: MqttConnectionManager,
    private val notificationSender: MqttNotificationSender? = null
) {
    
    /**
     * Desvincula el dispositivo ESP32 actual.
     * 
     * @param username Nombre del usuario que se desconecta (opcional)
     * @return Result<Unit> Success si se desvincula correctamente
     */
    suspend operator fun invoke(username: String = "Usuario"): Result<Unit> {
        // Obtener el topic actual antes de limpiar
        val currentPairing = pairingRepository.getCurrentPairing().firstOrNull()
        
        // Enviar notificación de desconexión al ESP32 antes de desconectar
        currentPairing?.let { pairing ->
            try {
                notificationSender?.sendDisconnectNotification(pairing.mqttTopic, username)
                android.util.Log.d("UnpairDeviceUseCase", "Notificación de desconexión enviada a ${pairing.mqttTopic}")
                // Pequeño delay para asegurar que el mensaje se envíe
                kotlinx.coroutines.delay(500)
            } catch (e: Exception) {
                android.util.Log.w("UnpairDeviceUseCase", "Error enviando notificación de desconexión: ${e.message}")
            }
        }
        
        // Desconectar MQTT
        val disconnectResult = mqttConnectionManager.disconnect()
        if (disconnectResult.isFailure) {
            android.util.Log.w("UnpairDeviceUseCase", "MQTT disconnect failed: ${disconnectResult.exceptionOrNull()?.message}")
        }
        
        // Limpiar pairing guardado
        return pairingRepository.clearPairing()
    }
}

