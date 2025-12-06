package com.dynamictecnologies.notificationmanager.domain.usecases.mqtt

import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.domain.repositories.MqttRepository

/**
 * Use Case para enviar una notificación vía MQTT.
 * 
 * Responsabilidad única: Enviar notificaciones a dispositivos ESP32.
 * 
 * Principios aplicados:
 * - SRP: Solo envía notificaciones
 * - DIP: Depende de abstracción
 * - Clean Architecture: Domain layer
 */
class SendNotificationViaMqttUseCase(
    private val mqttRepository: MqttRepository
) {
    /**
     * Envía una notificación a través de MQTT.
     * 
     * @param notification Notificación a enviar
     * @return Result<Unit> Success si se envía, Failure si falla
     */
    suspend operator fun invoke(notification: NotificationInfo): Result<Unit> {
        return mqttRepository.sendNotification(notification)
    }
}
