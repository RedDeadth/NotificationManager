package com.dynamictecnologies.notificationmanager.domain.repositories

import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface para operaciones de notificaciones.
 * 
 * Define el contrato para gestión de notificaciones en el domain layer.
 * 
 * - Clean Architecture: Domain layer contract
 */
interface NotificationRepository {
    /**
     * Sincroniza una notificación con el backend (Firebase).
     */
    suspend fun syncNotification(notification: NotificationInfo): Result<Unit>
    
    /**
     * Observa las notificaciones del usuario actual.
     */
    fun observeNotifications(userId: String): Flow<List<NotificationInfo>>
    
    /**
     * Obtiene todas las notificaciones del usuario actual.
     */
    suspend fun getNotifications(): Result<List<NotificationInfo>>
    
    /**
     * Verifica la conexión con el backend.
     */
    suspend fun verifyConnection(): Result<Boolean>
}
