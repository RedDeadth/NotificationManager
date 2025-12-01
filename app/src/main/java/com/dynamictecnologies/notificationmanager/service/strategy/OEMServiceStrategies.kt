package com.dynamictecnologies.notificationmanager.service.strategy

/**
 * Estrategia específica para dispositivos Xiaomi con MIUI.
 * 
 * Xiaomi es conocido por tener una de las gestiones de batería más agresivas.
 * MIUI cierra servicios en background de forma muy agresiva para ahorrar batería.
 * 
 * Características:
 * - Requiere permisos especiales de "Autostart"
 * - Necesita bloqueo en aplicaciones recientes
 * - Requiere desactivación de ahorro de batería
 * - Intervalos de verificación más cortos
 */
class XiaomiServiceStrategy : BaseServiceStrategy() {
    
    override fun getOptimalCheckInterval(): Long {
        // Verificar cada 8 minutos (más frecuente debido a agresividad de MIUI)
        return 8 * 60 * 1000L
    }
    
    override fun shouldUseAlarmManager(): Boolean {
        // MIUI beneficia de AlarmManager exact para checks críticos
        return true
    }
    
    override fun shouldRequestBatteryWhitelist(): Boolean {
        // Crítico en Xiaomi
        return true
    }
    
    override fun getRecommendedSettings(): List<String> {
        return listOf(
            "1. Configuración > Aplicaciones > Gestionar aplicaciones > ${getAppName()}",
            "2. Activar 'Inicio automático'",
            "3. Ir a 'Ahorro de batería' > Seleccionar 'Sin restricciones'",
            "4. En Aplicaciones recientes: Bloquear la app (icono candado)",
            "5. Seguridad > Permisos > Inicio automático > Activar",
            "6. Configuración > Batería > Ahorro de energía > Desactivar",
            "7. Configuración > Batería > Escenarios > Desactivar optimización"
        )
    }
    
    override fun getRetryInterval(): Long {
        // Reintentar cada 2 minutos si falla
        return 2 * 60 * 1000L
    }
    
    override fun getMaxRetries(): Int {
        // Permitir más reintentos antes de notificar (Xiaomi es muy agresivo)
        return 5
    }
    
    override fun requiresPersistentForegroundService(): Boolean {
        // Crítico para sobrevivir en MIUI
        return true
    }
    
    override fun getForegroundServiceNotificationPriority(): NotificationPriority {
        // Prioridad alta para evitar que MIUI lo considere prescindible
        return NotificationPriority.HIGH
    }
    
    override fun getStrategyName(): String {
        return "Xiaomi (MIUI) Strategy"
    }
    
}

/**
 * Estrategia específica para dispositivos Samsung con OneUI.
 * 
 * Samsung tiene optimización de batería moderadamente agresiva.
 * OneUI incluye "Sleeping apps" y "Deep sleeping apps" que deben configurarse.
 */
class SamsungServiceStrategy : BaseServiceStrategy() {
    
    override fun getOptimalCheckInterval(): Long {
        // Verificar cada 15 minutos
        return 15 * 60 * 1000L
    }
    
    override fun shouldUseAlarmManager(): Boolean {
        // OneUI permite coroutines pero AlarmManager es más confiable
        return true
    }
    
    override fun shouldRequestBatteryWhitelist(): Boolean {
        return true
    }
    
    override fun getRecommendedSettings(): List<String> {
        return listOf(
            "1. Configuración > Aplicaciones > ${getAppName()}",
            "2. Batería > 'No restringir'",
            "3. Configuración > Cuidado del dispositivo > Batería",
            "4. Límites de uso de batería en segundo plano",
            "5. Quitar de 'Aplicaciones en suspensión'",
            "6. Quitar de 'Aplicaciones en suspensión profunda'",
            "7. Agregar a 'Aplicaciones sin optimizar'"
        )
    }
    
    override fun getRetryInterval(): Long {
        return 3 * 60 * 1000L
    }
    
    override fun getMaxRetries(): Int {
        return 4
    }
    
    override fun requiresPersistentForegroundService(): Boolean {
        return true
    }
    
    override fun getForegroundServiceNotificationPriority(): NotificationPriority {
        return NotificationPriority.DEFAULT
    }
    
    override fun getStrategyName(): String {
        return "Samsung (OneUI) Strategy"
    }
    
}

/**
 * Estrategia específica para dispositivos Huawei/Honor con EMUI.
 * 
 * Huawei tiene gestión muy agresiva, especialmente en modelos sin Google Services.
 * Requiere configuración de "Protected Apps".
 */
class HuaweiServiceStrategy : BaseServiceStrategy() {
    
    override fun getOptimalCheckInterval(): Long {
        // Verificar cada 8 minutos (muy agresivo como Xiaomi)
        return 8 * 60 * 1000L
    }
    
    override fun shouldUseAlarmManager(): Boolean {
        // Crítico en EMUI
        return true
    }
    
    override fun shouldRequestBatteryWhitelist(): Boolean {
        return true
    }
    
    override fun getRecommendedSettings(): List<String> {
        return listOf(
            "1. Configuración > Aplicaciones > ${getAppName()}",
            "2. Batería > Iniciar aplicaciones",
            "3. Desactivar 'Gestionar automáticamente'",
            "4. Activar: Inicio automático, Inicio secundario, Ejecutar en segundo plano",
            "5. Configuración > Batería > Aplicaciones protegidas",
            "6. Activar ${getAppName()} en la lista",
            "7. Configuración > Batería > Más ajustes de batería",
            "8. Desactivar 'Suspender cuando esté bloqueado'"
        )
    }
    
    override fun getRetryInterval(): Long {
        return 2 * 60 * 1000L
    }
    
    override fun getMaxRetries(): Int {
        return 5
    }
    
    override fun requiresPersistentForegroundService(): Boolean {
        return true
    }
    
    override fun getForegroundServiceNotificationPriority(): NotificationPriority {
        return NotificationPriority.HIGH
    }
    
    override fun getStrategyName(): String {
        return "Huawei (EMUI) Strategy"
    }
    
}

/**
 * Estrategia específica para dispositivos OnePlus con OxygenOS.
 * 
 * OnePlus tiene optimización moderada, mejor que Xiaomi/Huawei.
 */
class OnePlusServiceStrategy : BaseServiceStrategy() {
    
    override fun getOptimalCheckInterval(): Long {
        return 15 * 60 * 1000L
    }
    
    override fun shouldUseAlarmManager(): Boolean {
        // OxygenOS funciona bien con coroutines, pero AlarmManager es más seguro
        return true
    }
    
    override fun shouldRequestBatteryWhitelist(): Boolean {
        return true
    }
    
    override fun getRecommendedSettings(): List<String> {
        return listOf(
            "1. Configuración > Aplicaciones > ${getAppName()}",
            "2. Batería > Optimización de batería > No optimizar",
            "3. En Aplicaciones recientes: Bloquear la app",
            "4. Configuración > Batería > Optimización de batería avanzada",
            "5. Desactivar optimización adaptativa de batería"
        )
    }
    
    override fun getRetryInterval(): Long {
        return 5 * 60 * 1000L
    }
    
    override fun getMaxRetries(): Int {
        return 3
    }
    
    override fun requiresPersistentForegroundService(): Boolean {
        return true
    }
    
    override fun getForegroundServiceNotificationPriority(): NotificationPriority {
        return NotificationPriority.DEFAULT
    }
    
    override fun getStrategyName(): String {
        return "OnePlus (OxygenOS) Strategy"
    }
    
}

/**
 * Estrategia para dispositivos Android genéricos (stock o personalizaciones mínimas).
 * 
 * Incluye Google Pixel, Motorola, Nokia, Android One, etc.
 * Gestión de batería siguiendo directrices de Android stock.
 */
class GenericServiceStrategy : BaseServiceStrategy() {
    
    override fun getOptimalCheckInterval(): Long {
        // Verificar cada 20 minutos (menos frecuente, Android stock es más permisivo)
        return 20 * 60 * 1000L
    }
    
    override fun shouldUseAlarmManager(): Boolean {
        // Coroutines funcionan bien en Android stock
        return false
    }
    
    override fun shouldRequestBatteryWhitelist(): Boolean {
        // Recomendado pero no crítico
        return true
    }
    
    override fun getRecommendedSettings(): List<String> {
        return listOf(
            "1. Configuración > Aplicaciones > ${getAppName()}",
            "2. Batería > Uso de batería > Sin restricciones (opcional)",
            "3. Verificar que los permisos de notificación estén activados"
        )
    }
    
    override fun getRetryInterval(): Long {
        return 10 * 60 * 1000L
    }
    
    override fun getMaxRetries(): Int {
        return 3
    }
    
    override fun requiresPersistentForegroundService(): Boolean {
        // Opcional en Android stock, pero recomendado
        return true
    }
    
    override fun getForegroundServiceNotificationPriority(): NotificationPriority {
        return NotificationPriority.LOW
    }
    
    override fun getStrategyName(): String {
        return "Generic Android Strategy"
    }
    
}
