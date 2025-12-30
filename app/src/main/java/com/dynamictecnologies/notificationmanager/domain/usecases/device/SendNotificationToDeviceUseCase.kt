package com.dynamictecnologies.notificationmanager.domain.usecases.device

import com.dynamictecnologies.notificationmanager.BuildConfig
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.domain.entities.NoDevicePairedException
import com.dynamictecnologies.notificationmanager.domain.repositories.DevicePairingRepository
import com.dynamictecnologies.notificationmanager.domain.repositories.NotificationSender
import com.dynamictecnologies.notificationmanager.util.security.NotificationRateLimiter
import com.dynamictecnologies.notificationmanager.util.security.RateLimitExceededException

/**
 * Use Case para enviar notificación a dispositivo vinculado.
 * 
 * Flujo:
 * 1. Verificar que haya dispositivo vinculado
 * 2. Crear DTO con límites de tamaño
 * 3. Publicar vía MQTT al topic del dispositivo
 * 
 * - Fail-fast: Validaciones tempranas
 * - DTO Pattern: Datos sanitizados
 * - DIP: Depende de interfaz NotificationSender, no de implementación
 */
class SendNotificationToDeviceUseCase(
    private val pairingRepository: DevicePairingRepository,
    private val notificationSender: NotificationSender
) {
    private val rateLimiter = NotificationRateLimiter(
        maxRequests = MAX_NOTIFICATIONS_PER_MINUTE,
        windowMs = 60_000L  // 1 minuto
    )
    
    /**
     * Envía notificación a dispositivo vinculado
     */
    suspend operator fun invoke(notification: NotificationInfo): Result<Unit> {
        if (BuildConfig.DEBUG) {
            android.util.Log.d(TAG, "=== ENVIANDO NOTIFICACIÓN ===")
            android.util.Log.d(TAG, "Título: ${notification.title}")
        }
        
        // 1. Verificar rate limiting
        if (!rateLimiter.allowOperation()) {
            if (BuildConfig.DEBUG) {
                android.util.Log.w(TAG, "Rate limit excedido")
            }
            return Result.failure(RateLimitExceededException(
                "Too many notifications. Limit: $MAX_NOTIFICATIONS_PER_MINUTE per minute"
            ))
        }
        
        // 2. Obtener topic del dispositivo vinculado
        val topic = pairingRepository.getMqttTopic()
        if (BuildConfig.DEBUG) {
            android.util.Log.d(TAG, "Topic obtenido: $topic")
        }
        
        if (topic == null) {
            if (BuildConfig.DEBUG) {
                android.util.Log.e(TAG, "ERROR: No hay dispositivo vinculado (topic es null)")
            }
            return Result.failure(NoDevicePairedException())
        }
        
        // 3. Enviar directamente usando topic MQTT
        if (BuildConfig.DEBUG) {
            android.util.Log.d(TAG, "Enviando a topic: $topic")
        }
        return notificationSender.sendNotificationToTopic(topic, notification)
    }
    
    companion object {
        private const val TAG = "SendNotificationUseCase"
        private const val MAX_NOTIFICATIONS_PER_MINUTE = 10
    }
}
