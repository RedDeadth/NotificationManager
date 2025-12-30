package com.dynamictecnologies.notificationmanager.domain.usecases.mqtt

import com.dynamictecnologies.notificationmanager.domain.repositories.MqttRepository

/**
 * Use Case para desconectar del broker MQTT.
 * 
 * Responsabilidad única: Gestionar la desconexión MQTT.
 * 
 * - Clean Architecture: Use case en domain layer
 */
class DisconnectFromMqttUseCase(
    private val mqttRepository: MqttRepository
) {
    /**
     * Desconecta del broker MQTT.
     * 
     * @return Result<Unit> Success si desconecta correctamente, Failure si falla
     */
    suspend operator fun invoke(): Result<Unit> {
        return mqttRepository.disconnect()
    }
}
