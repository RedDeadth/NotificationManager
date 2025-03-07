package com.dynamictecnologies.notificationmanager.service

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.data.model.SyncStatus
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import java.util.*

class FirebaseService(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
) {
    private val TAG = "FirebaseService"
    private val notificationsRef = database.getReference("notifications")

    suspend fun syncNotification(notification: NotificationInfo): Boolean {
        return try {
            val currentUser = auth.currentUser ?: return false
            val userId = currentUser.uid
            val encodedPackageName = encodePackageName(notification.packageName)

            val notificationMap = mapOf(
                "title" to notification.title,
                "content" to notification.content,
                "timestamp" to notification.timestamp.time,
                "syncTimestamp" to ServerValue.TIMESTAMP,
                "appName" to notification.appName,
                "packageName" to notification.packageName,
                "syncStatus" to "SYNCED"
            )

            notificationsRef
                .child(userId)
                .child(encodedPackageName)
                .child(notification.id.toString())
                .setValue(notificationMap)
                .await()

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sincronizando notificación: ${e.message}")
            false
        }
    }

    suspend fun getNotifications(packageName: String): List<NotificationInfo> {
        return try {
            val userId = auth.currentUser?.uid ?: throw Exception("Usuario no autenticado")
            val encodedPackageName = encodePackageName(packageName)

            val snapshot = notificationsRef
                .child(userId)
                .child(encodedPackageName)
                .orderByChild("timestamp")
                .get()
                .await()

            val notifications = mutableListOf<NotificationInfo>()

            snapshot.children.forEach { child ->
                try {
                    val id = child.key?.toLongOrNull() ?: return@forEach
                    val syncTimestamp = child.child("syncTimestamp").getValue(Long::class.java)

                    notifications.add(
                        NotificationInfo(
                            id = id,
                            packageName = packageName,
                            appName = child.child("appName").getValue(String::class.java) ?: "",
                            title = child.child("title").getValue(String::class.java) ?: "",
                            content = child.child("content").getValue(String::class.java) ?: "",
                            timestamp = Date(child.child("timestamp").getValue(Long::class.java) ?: 0),
                            isSynced = true,
                            syncStatus = SyncStatus.SYNCED,
                            syncTimestamp = syncTimestamp
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parseando notificación: ${e.message}")
                }
            }

            notifications.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo notificaciones: ${e.message}")
            emptyList()
        }
    }

    private fun encodePackageName(packageName: String): String {
        return packageName.replace(".", "_")
    }

    private fun decodedPackageName(encodedPackageName: String): String {
        return encodedPackageName.replace("_", ".")
    }

    suspend fun verifyConnection(): Boolean {
        return try {
            val connectedRef = database.getReference(".info/connected")
            val snapshot = connectedRef.get().await()
            snapshot.getValue(Boolean::class.java) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando conexión: ${e.message}")
            false
        }
    }
    suspend fun getSharedWithMeNotifications(): Result<Map<String, List<NotificationInfo>>> {
        return try {
            val currentUser = auth.currentUser ?:
            return Result.failure(Exception("Usuario no autenticado"))

            // Obtener la lista de usuarios que comparten conmigo
            val sharedWithMeSnapshot = database.reference
                .child("users")
                .orderByChild("sharedWith")
                .equalTo(currentUser.uid)
                .get()
                .await()

            val notificationsByUser = mutableMapOf<String, List<NotificationInfo>>()

            sharedWithMeSnapshot.children.forEach { userSnapshot ->
                val username = userSnapshot.child("username").getValue(String::class.java) ?: return@forEach
                val notifications = getNotificationsForUser(userSnapshot.key ?: return@forEach)
                    .getOrDefault(emptyList())
                if (notifications.isNotEmpty()) {
                    notificationsByUser[username] = notifications
                }
            }

            Result.success(notificationsByUser)
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo notificaciones compartidas: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun getNotificationsForUser(userId: String): Result<List<NotificationInfo>> {
        return try {
            val snapshot = notificationsRef
                .child(userId)
                .get()
                .await()

            val notifications = mutableListOf<NotificationInfo>()

            snapshot.children.forEach { packageSnapshot ->
                packageSnapshot.children.forEach { notificationSnapshot ->
                    try {
                        val notification = parseNotification(notificationSnapshot)
                        notifications.add(notification)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parseando notificación: ${e.message}")
                    }
                }
            }

            Result.success(notifications.sortedByDescending { it.timestamp })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    private fun parseNotification(snapshot: com.google.firebase.database.DataSnapshot): NotificationInfo {
        return NotificationInfo(
            id = snapshot.key?.toLongOrNull() ?: 0L,
            packageName = snapshot.child("packageName").getValue(String::class.java) ?: "",
            appName = snapshot.child("appName").getValue(String::class.java) ?: "",
            title = snapshot.child("title").getValue(String::class.java) ?: "",
            content = snapshot.child("content").getValue(String::class.java) ?: "",
            timestamp = Date(snapshot.child("timestamp").getValue(Long::class.java) ?: 0L),
            syncStatus = try {
                SyncStatus.valueOf(
                    snapshot.child("syncStatus").getValue(String::class.java)
                        ?: SyncStatus.PENDING.name
                )
            } catch (e: Exception) {
                SyncStatus.FAILED
            },
            syncTimestamp = snapshot.child("syncTimestamp").getValue(Long::class.java),
            lastSyncAttempt = null
        )
    }
}