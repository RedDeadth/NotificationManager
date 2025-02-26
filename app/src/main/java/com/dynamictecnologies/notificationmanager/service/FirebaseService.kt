package com.dynamictecnologies.notificationmanager.service

import android.util.Log
import com.dynamictecnologies.notificationmanager.data.db.FirebaseDatabaseManager
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.tasks.await
import java.util.*

class FirebaseService {
    private val TAG = "FirebaseService"
    private val database: FirebaseDatabase = Firebase.database
    private val notificationsRef = database.getReference("notifications/data")
    private val databaseManager = FirebaseDatabaseManager()

    init {
        // Habilitar persistencia offline
        database.setPersistenceEnabled(true)
    }
    suspend fun initialize() {
        databaseManager.initializeDatabase()
    }
    suspend fun syncNotification(notification: NotificationInfo): Boolean {
        return try {
            Log.d(TAG, "Sincronizando notificación con Firebase: ${notification.title}")

            // Crear una referencia específica para esta notificación
            val notificationRef = notificationsRef
                .child(notification.packageName)
                .child(notification.uniqueId)

            // Convertir la notificación a un Map
            val notificationMap = mapOf(
                "id" to notification.id,
                "packageName" to notification.packageName,
                "appName" to notification.appName,
                "title" to notification.title,
                "content" to notification.content,
                "timestamp" to notification.timestamp.time,
                "senderName" to notification.senderName,
                "isGroupMessage" to notification.isGroupMessage,
                "groupName" to notification.groupName,
                "isRead" to notification.isRead,
                "uniqueId" to notification.uniqueId,
                "syncTimestamp" to ServerValue.TIMESTAMP
            )

            // Guardar en Firebase
            notificationRef.setValue(notificationMap).await()
            database.reference.child("notifications/metadata/lastUpdate")
                .setValue(ServerValue.TIMESTAMP)
                .await()

            Log.d(TAG, "✓ Notificación sincronizada exitosamente con Firebase")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sincronizando con Firebase: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    suspend fun getNotifications(packageName: String): List<NotificationInfo> {
        return try {
            val snapshot = notificationsRef
                .child(packageName)
                .orderByChild("timestamp")
                .limitToLast(100)  // Limitar a las últimas 100 notificaciones
                .get()
                .await()

            snapshot.children.mapNotNull { child ->
                try {
                    val id = child.child("id").getValue(Long::class.java) ?: 0
                    val title = child.child("title").getValue(String::class.java) ?: ""
                    val content = child.child("content").getValue(String::class.java) ?: ""
                    val timestamp = child.child("timestamp").getValue(Long::class.java)?.let { Date(it) } ?: Date()
                    val appName = child.child("appName").getValue(String::class.java) ?: ""
                    val uniqueId = child.child("uniqueId").getValue(String::class.java) ?: ""
                    val senderName = child.child("senderName").getValue(String::class.java)
                    val isGroupMessage = child.child("isGroupMessage").getValue(Boolean::class.java) ?: false
                    val groupName = child.child("groupName").getValue(String::class.java)
                    val isRead = child.child("isRead").getValue(Boolean::class.java) ?: false

                    NotificationInfo(
                        id = id,
                        packageName = packageName,
                        appName = appName,
                        title = title,
                        content = content,
                        timestamp = timestamp,
                        senderName = senderName,
                        isGroupMessage = isGroupMessage,
                        groupName = groupName,
                        isRead = isRead,
                        uniqueId = uniqueId
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parseando notificación: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo notificaciones de Firebase: ${e.message}")
            emptyList()
        }
    }

    suspend fun deleteOldNotifications(packageName: String, olderThan: Date) {
        try {
            val snapshot = notificationsRef
                .child(packageName)
                .orderByChild("timestamp")
                .endAt(olderThan.time.toDouble())
                .get()
                .await()

            snapshot.children.forEach { child ->
                child.ref.removeValue().await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando notificaciones antiguas: ${e.message}")
        }
    }
}