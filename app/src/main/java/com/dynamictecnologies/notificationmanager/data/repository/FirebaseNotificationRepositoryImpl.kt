package com.dynamictecnologies.notificationmanager.data.repository

import com.dynamictecnologies.notificationmanager.data.datasource.firebase.FirebaseNotificationDataSource
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.domain.repositories.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Implementación de NotificationRepository usando Firebase.
 * 
 * Responsabilidad única: Coordinar operaciones de notificaciones.
 * 
 * Principios aplicados:
 * - SRP: Solo coordina notification operations
 * - DIP: Implementa interfaz del domain
 * - Clean Architecture: Data layer implements domain contracts
 */
class FirebaseNotificationRepositoryImpl(
    private val dataSource: FirebaseNotificationDataSource
) : NotificationRepository {
    
    override suspend fun syncNotification(notification: NotificationInfo): Result<Unit> {
        return dataSource.syncNotification(notification)
    }
    
    override fun observeNotifications(userId: String): Flow<List<NotificationInfo>> {
        // TODO: Implement real-time Firebase listener
        // For now, return empty flow
        return flow { emit(emptyList()) }
    }
    
    override suspend fun getNotifications(): Result<List<NotificationInfo>> {
        return dataSource.getNotifications()
    }
    
    override suspend fun verifyConnection(): Result<Boolean> {
        return dataSource.verifyConnection()
    }
}
