package com.dynamictecnologies.notificationmanager.service.strategy

/**
 * Clase base abstracta para todas las estrategias de servicio en segundo plano.
 * 
 * Proporciona funcionalidad común a todas las implementaciones de BackgroundServiceStrategy,
 * eliminando código duplicado (DRY) y centralizando constantes compartidas.
 * 
 * Principios aplicados:
 * - DRY: Elimina repetición de getAppName() en 5 strategies
 * - Template Method Pattern: Define estructura común
 * - Open/Closed: Abierto para extensión (nuevas strategies), cerrado para modificación
 */
abstract class BaseServiceStrategy : BackgroundServiceStrategy {
    
    /**
     * Retorna el nombre de la aplicación.
     * Centralizado aquí para evitar repetición en cada strategy.
     * 
     * @return Nombre de la aplicación para mostrar en configuraciones
     */
    protected fun getAppName(): String {
        return "Gestor de Notificaciones"
    }
}
