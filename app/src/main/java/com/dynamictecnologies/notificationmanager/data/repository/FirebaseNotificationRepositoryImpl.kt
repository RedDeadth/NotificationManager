package com.dynamictecnologies.notificationmanager.data.repository

import com.dynamictecnologies.notificationmanager.data.datasource.firebase.FirebaseNotificationDataSource
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.domain.repositories.NotificationRepository
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import java.util.*

/**
 * Implementación de NotificationRepository usando Firebase.
 * 
 * Responsabilidad única: Coordinar operaciones de notificaciones.
 * 
 * Principios aplicados:
 * - SRP: Solo coordina notification operations
 * - DIP: Implementa interfaz del domain
 * - Clean Architecture: Data layer implements domain contracts
 * 
 * NOTA: MQTT es el canal principal para ESP32. 
 * Este listener Firebase es opcional para sincronización/compartir entre usuarios.
 */
class FirebaseNotificationRepositoryImpl(
    private val dataSource: FirebaseNotificationDataSource,
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
) : NotificationRepository {
    
    override suspend fun syncNotification(notification: NotificationInfo): Result<Unit> {
        return dataSource.syncNotification(notification)
    }
    
    override fun observeNotifications(userId: String): Flow<List<NotificationInfo>> = callbackFlow {
        val notificationsRef = database.getReference("notifications").child(userId)
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val notifications = mutableListOf<NotificationInfo>()
                
                snapshot.children.forEach { notifSnapshot ->
                    try {
                        val title = notifSnapshot.child("title").getValue(String::class.java) ?: ""
                        val content = notifSnapshot.child("content").getValue(String::class.java) ?: ""
                        val appName = notifSnapshot.child("appName").getValue(String::class.java) ?: ""
                        val timestamp = notifSnapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                        
                        notifications.add(
                            NotificationInfo(
                                title = title,
                                content = content,
                                appName = appName,
                                timestamp = Date(timestamp)
                            )
                        )
                    } catch (e: Exception) {
                        // Skip malformed notifications
                    }
                }
                
                trySend(notifications.sortedByDescending { it.timestamp })
            }
            
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        
        notificationsRef.addValueEventListener(listener)
        
        awaitClose {
            notificationsRef.removeEventListener(listener)
        }
    }
    
    override suspend fun getNotifications(): Result<List<NotificationInfo>> {
        return dataSource.getNotifications()
    }
    
    override suspend fun verifyConnection(): Result<Boolean> {
        return dataSource.verifyConnection()
    }
}
