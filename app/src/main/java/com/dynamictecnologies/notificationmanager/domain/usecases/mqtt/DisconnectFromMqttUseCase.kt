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
     */
    suspend operator fun invoke() {
        mqttRepository.disconnect()
    }
}
