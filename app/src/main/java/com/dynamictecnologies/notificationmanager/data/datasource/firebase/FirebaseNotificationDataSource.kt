package com.dynamictecnologies.notificationmanager.data.datasource.firebase

import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.data.model.SyncStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.tasks.await
import java.util.*

/**
 * Data Source para operaciones de notificaciones en Firebase.
 * 
 * Responsabilidad única: Gestionar acceso a datos de notificaciones en Firebase.
 * 
 * - Clean Architecture: Data layer
 */
class FirebaseNotificationDataSource(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
) {
    private val notificationsRef = database.getReference("notifications")
    
    /**
     * Sincroniza una notificación en Firebase.
     */
    suspend fun syncNotification(notification: NotificationInfo): Result<Unit> {
        return try {
            val currentUser = auth.currentUser ?: return Result.failure(
                Exception("User not authenticated")
            )
            val userId = currentUser.uid
            
            val notificationMap = mapOf(
                "appName" to notification.appName,
                "title" to notification.title,
                "content" to notification.content,
                "timestamp" to notification.timestamp.time,
                "syncTimestamp" to ServerValue.TIMESTAMP
            )

            notificationsRef
                .child(userId)
                .child(notification.id.toString())
                .setValue(notificationMap)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Obtiene todas las notificaciones del usuario actual.
     */
    suspend fun getNotifications(): Result<List<NotificationInfo>> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(
                Exception("User not authenticated")
            )
            
            val snapshot = notificationsRef
                .child(userId)
                .orderByChild("timestamp")
                .get()
                .await()
            
            if (!snapshot.exists()) {
                return Result.success(emptyList())
            }
            
            val notifications = mutableListOf<NotificationInfo>()

            snapshot.children.forEach { child ->
                try {
                    val id = child.key?.toLongOrNull() ?: return@forEach
                    
                    val appName = child.child("appName").getValue(String::class.java) ?: ""
                    val title = child.child("title").getValue(String::class.java) ?: ""
                    val content = child.child("content").getValue(String::class.java) ?: ""
                    val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0
                    val syncTimestamp = child.child("syncTimestamp").getValue(Long::class.java)

                    notifications.add(
                        NotificationInfo(
                            id = id,
                            appName = appName,
                            title = title,
                            content = content,
                            timestamp = Date(timestamp),
                            isSynced = true,
                            syncStatus = SyncStatus.SYNCED,
                            syncTimestamp = syncTimestamp
                        )
                    )
                } catch (e: Exception) {
                    // Skip invalid notifications
                }
            }

            Result.success(notifications.sortedByDescending { it.timestamp })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Verifica la conexión a Firebase.
     */
    suspend fun verifyConnection(): Result<Boolean> {
        return try {
            val pingRef = database.getReference("system_health").child("ping")
            pingRef.setValue(ServerValue.TIMESTAMP).await()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
