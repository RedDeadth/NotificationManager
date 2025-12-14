package com.dynamictecnologies.notificationmanager.data.constants

/**
 * Constantes MQTT centralizadas.
 * 
 * - Single Source of Truth: Todos los valores MQTT en un solo lugar
 * - Maintainability: Cambios en un solo archivo
 */
object MqttConstants {
    
    /**
     * Prefijo del topic para notificaciones.
     * Formato: n/{TOKEN}
     */
    const val TOPIC_NOTIFICATION_PREFIX = "n/"
    
    /**
     * Topic para descubrimiento de dispositivos.
     */
    const val TOPIC_DEVICE_DISCOVERY = "discovery"
    
    /**
     * Topic para respuestas de descubrimiento.
     */
    const val TOPIC_DISCOVERY_RESPONSE = "discovery/response"
    
    /**
     * Quality of Service por defecto.
     */
    const val DEFAULT_QOS = 1
    
    /**
     * Timeout de conexión en segundos.
     */
    const val CONNECTION_TIMEOUT_SECONDS = 60
    
    /**
     * Keep alive interval en segundos.
     */
    const val KEEP_ALIVE_INTERVAL_SECONDS = 120
    
    /**
     * Client ID prefix para identificar la app.
     */
    const val CLIENT_ID_PREFIX = "NotificationManager_"
    
    /**
     * Delay para reconexión en milisegundos.
     */
    const val RECONNECT_DELAY_MS = 5000L
}
