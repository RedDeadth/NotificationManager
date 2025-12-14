package com.dynamictecnologies.notificationmanager.data.dto

import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo

/**
 * DTO para envío MQTT de notificaciones.
 * 
 * Reduce tamaño de payload y valida límites.
 * 
 * - Security: Límites estrictos de tamaño
 * - DTO Pattern: Separación presentación/transporte
 * - Immutable: Data class read-only
 */
data class MqttNotificationDto(
    val t: String,  // title (max 100 chars)
    val c: String   // content (max 500 chars)
) {
    init {
        require(t.length <= MAX_TITLE_LENGTH) { "Title exceeds $MAX_TITLE_LENGTH characters" }
        require(c.length <= MAX_CONTENT_LENGTH) { "Content exceeds $MAX_CONTENT_LENGTH characters" }
    }
    
    /**
     * Serializa a JSON para MQTT payload
     */
    fun toJson(): String {
        // Escapar comillas para JSON válido
        val escapedTitle = t.replace("\"", "\\\"")
        val escapedContent = c.replace("\"", "\\\"")
        return "{\"t\":\"$escapedTitle\",\"c\":\"$escapedContent\"}"
    }
    
    companion object {
        const val MAX_TITLE_LENGTH = 100
        const val MAX_CONTENT_LENGTH = 500
        
        /**
         * Crea DTO desde NotificationInfo con truncado automático
         */
        fun fromNotificationInfo(notification: NotificationInfo): MqttNotificationDto {
            return MqttNotificationDto(
                t = sanitize(notification.title).take(MAX_TITLE_LENGTH),
                c = sanitize(notification.content).take(MAX_CONTENT_LENGTH)
            )
        }
        
        /**
         * Sanitiza string para prevenir injection
         */
        private fun sanitize(input: String): String {
            return input
                .replace("\n", " ")  // Remove newlines
                .replace("\r", " ")  // Remove carriage returns
                .replace("\t", " ")  // Replace tabs
                .trim()
        }
    }
}
