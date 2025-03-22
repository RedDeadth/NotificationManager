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
            
            // Mapa simplificado con solo los campos esenciales
            val notificationMap = mapOf(
                "appName" to notification.appName,
                "title" to notification.title,
                "content" to notification.content,
                "timestamp" to notification.timestamp.time,
                "syncTimestamp" to ServerValue.TIMESTAMP
            )

            // Estructura simplificada: userId/notificationId/datos
            notificationsRef
                .child(userId)
                .child(notification.id.toString())
                .setValue(notificationMap)
                .await()

            Log.d(TAG, "Notificación sincronizada correctamente: ID=${notification.id}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sincronizando notificación: ${e.message}")
            false
        }
    }

    suspend fun getNotifications(): List<NotificationInfo> {
        return try {
            val userId = auth.currentUser?.uid ?: throw Exception("Usuario no autenticado")
            
            Log.d(TAG, "Obteniendo notificaciones para usuario: $userId")
            
            val snapshot = notificationsRef
                .child(userId)
                .orderByChild("timestamp")
                .get()
                .await()
            
            if (!snapshot.exists()) {
                Log.d(TAG, "No se encontraron notificaciones en Firebase para el usuario")
                return emptyList()
            }
            
            Log.d(TAG, "Número de notificaciones encontradas en Firebase: ${snapshot.childrenCount}")
            
            val notifications = mutableListOf<NotificationInfo>()

            snapshot.children.forEach { child ->
                try {
                    val id = child.key?.toLongOrNull()
                    if (id == null) {
                        Log.w(TAG, "Ignorando notificación con ID inválido: ${child.key}")
                        return@forEach
                    }
                    
                    val appName = child.child("appName").getValue(String::class.java) ?: ""
                    val title = child.child("title").getValue(String::class.java) ?: ""
                    val content = child.child("content").getValue(String::class.java) ?: ""
                    val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0
                    val syncTimestamp = child.child("syncTimestamp").getValue(Long::class.java)
                    
                    Log.d(TAG, "Notificación encontrada - ID: $id, App: $appName, Título: $title")

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
                    Log.e(TAG, "Error parseando notificación: ${e.message}")
                }
            }

            Log.d(TAG, "Total notificaciones procesadas: ${notifications.size}")
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
            val pingRef = database.getReference("system_health").child("ping")
            pingRef.setValue(ServerValue.TIMESTAMP).await()
            
            Log.d(TAG, "Verificación de conexión exitosa")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando conexión: ${e.message}")
            false
        }
    }
}