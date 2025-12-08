package com.dynamictecnologies.notificationmanager.domain.usecases.device

import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.data.mqtt.MqttNotificationPublisher
import com.dynamictecnologies.notificationmanager.data.dto.MqttNotificationDto
import com.dynamictecnologies.notificationmanager.domain.entities.NoDevicePairedException
import com.dynamictecnologies.notificationmanager.domain.repositories.DevicePairingRepository
import com.dynamictecnologies.notificationmanager.util.security.NotificationRateLimiter
import com.dynamictecnologies.notificationmanager.util.security.RateLimitExceededException

/**
 * Caso de uso para enviar notificación a dispositivo vinculado.
 * 
 * Incluye:
 * - Validación de dispositivo vinculado
 * - Rate limiting (10 notif/min)
 * - DTO para payload seguro
 * - Sanitización de inputs
 * 
 * Principios aplicados:
 * - SRP: Solo envío de notificaciones
 * - Security: Rate limiting + input validation
 */
class SendNotificationToDeviceUseCase(
    private val pairingRepository: DevicePairingRepository,
    private val mqttPublisher: MqttNotificationPublisher
) {
    private val rateLimiter = NotificationRateLimiter(
        maxRequests = MAX_NOTIFICATIONS_PER_MINUTE,
        windowMs = 60_000L  // 1 minuto
    )
    
    /**
     * Envía notificación a dispositivo vinculado
     */
    suspend operator fun invoke(notification: NotificationInfo): Result<Unit> {
        // 1. Verificar rate limiting
        if (!rateLimiter.allowOperation()) {
            return Result.failure(RateLimitExceededException(
                "Too many notifications. Limit: $MAX_NOTIFICATIONS_PER_MINUTE per minute"
            ))
        }
        
        // 2. Obtener topic del dispositivo vinculado
        val topic = pairingRepository.getMqttTopic()
            ?: return Result.failure(NoDevicePairedException())
        
        // 3. Convertir a DTO (con sanitización automática)
        val dto = try {
            MqttNotificationDto.fromNotificationInfo(notification)
        } catch (e: IllegalArgumentException) {
            return Result.failure(e)
        }
        
        // 4. Publicar usando DTO payload
        return mqttPublisher.publishDto(topic, dto)
    }
    
    companion object {
        private const val MAX_NOTIFICATIONS_PER_MINUTE = 10
    }
}
