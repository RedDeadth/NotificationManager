package com.dynamictecnologies.notificationmanager.domain.usecases.mqtt

import com.dynamictecnologies.notificationmanager.domain.repositories.MqttRepository

/**
 * Use Case para conectar al broker MQTT.
 * 
 * Responsabilidad única: Gestionar la conexión MQTT.
 * 
 * - Clean Architecture: Use case en domain layer
 */
class ConnectToMqttUseCase(
    private val mqttRepository: MqttRepository
) {
    /**
     * Conecta al broker MQTT.
     * 
     * @return Result<Unit> Success si conecta, Failure con excepción si falla
     */
    suspend operator fun invoke(): Result<Unit> {
        return mqttRepository.connect()
    }
}
