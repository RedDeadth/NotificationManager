package com.dynamictecnologies.notificationmanager.domain.usecases.mqtt

import com.dynamictecnologies.notificationmanager.data.model.DeviceInfo
import com.dynamictecnologies.notificationmanager.domain.repositories.MqttRepository

/**
 * Use Case para buscar dispositivos ESP32 disponibles.
 * 
 * Responsabilidad única: Iniciar búsqueda de dispositivos vía MQTT.
 * 
 * Principios aplicados:
 * - SRP: Solo busca dispositivos
 * - DIP: Depende de abstracción
 * - Clean Architecture: Domain layer use case
 */
class SearchDevicesUseCase(
    private val mqttRepository: MqttRepository
) {
    /**
     * Inicia búsqueda de dispositivos ESP32 en la red.
     * 
     * @return Result<List<DeviceInfo>> Lista de dispositivos encontrados o error
     */
    suspend operator fun invoke(): Result<List<DeviceInfo>> {
        return mqttRepository.searchDevices()
    }
}
