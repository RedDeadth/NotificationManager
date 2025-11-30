package com.dynamictecnologies.notificationmanager.service.strategy

/**
 * Interfaz para estrategias de gestión de servicios en background según el fabricante.
 * 
 * Principios aplicados:
 * - SRP: Cada estrategia maneja solo la configuración de su fabricante
 * - OCP: Fácil de extender con nuevos fabricantes sin modificar código existente
 * - DIP: El código depende de esta abstracción, no de implementaciones concretas
 */
interface BackgroundServiceStrategy {
    
    /**
     * Intervalo óptimo de verificación del servicio (en milisegundos)
     * @return El intervalo en ms entre verificaciones
     */
    fun getOptimalCheckInterval(): Long
    
    /**
     * Indica si se debe usar AlarmManager en lugar de coroutines para checks
     * @return true si el fabricante requiere AlarmManager para mayor confiabilidad
     */
    fun shouldUseAlarmManager(): Boolean
    
    /**
     * Indica si se debe solicitar exclusión de optimización de batería
     * @return true si es necesario para el correcto funcionamiento
     */
    fun shouldRequestBatteryWhitelist(): Boolean
    
    /**
     * Obtiene las configuraciones recomendadas específicas del fabricante
     * @return Lista de instrucciones para el usuario
     */
    fun getRecommendedSettings(): List<String>
    
    /**
     * Intervalo entre reintentos cuando el servicio falla (en milisegundos)
     * @return El intervalo en ms entre reintentos
     */
    fun getRetryInterval(): Long
    
    /**
     * Número máximo de reintentos antes de notificar al usuario
     * @return Cantidad de reintentos
     */
    fun getMaxRetries(): Int
    
    /**
     * Indica si se debe usar un servicio foreground persistente
     * @return true si el fabricante lo requiere
     */
    fun requiresPersistentForegroundService(): Boolean
    
    /**
     * Obtiene la prioridad de la notificación del servicio foreground
     * @return Nivel de prioridad (LOW, DEFAULT, HIGH)
     */
    fun getForegroundServiceNotificationPriority(): NotificationPriority
    
    /**
     * Nombre descriptivo de la estrategia
     */
    fun getStrategyName(): String
}

/**
 * Enum para prioridades de notificación
 */
enum class NotificationPriority {
    LOW,
    DEFAULT,
    HIGH
}
