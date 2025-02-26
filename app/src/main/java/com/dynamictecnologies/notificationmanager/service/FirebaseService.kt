package com.dynamictecnologies.notificationmanager.service

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import kotlinx.coroutines.tasks.await
import java.util.*
import kotlinx.coroutines.tasks.await
import java.util.*

class FirebaseService {
    private val TAG = "FirebaseService"
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
    private val notificationsRef = database.getReference("notifications")

    // Función para codificar el nombre del paquete de manera segura
    private fun encodePackageName(packageName: String): String {
        return packageName.replace(".", "_")
    }

    // Función para decodificar el nombre del paquete
    private fun decodePackageName(encodedPackageName: String): String {
        return encodedPackageName.replace("_", ".")
    }

    suspend fun syncNotification(notification: NotificationInfo): Boolean {
        return try {
            Log.d(TAG, "Sincronizando notificación: ${notification.title} de ${notification.packageName}")

            // Codificar el nombre del paquete para la ruta
            val encodedPackageName = encodePackageName(notification.packageName)

            // Preparar datos de la notificación
            val notificationMap = mapOf(
                "title" to notification.title,
                "content" to notification.content,
                "timestamp" to notification.timestamp.time,
                "syncTimestamp" to ServerValue.TIMESTAMP,
                "appName" to notification.appName,
                "uniqueId" to notification.uniqueId,
                "senderName" to (notification.senderName ?: ""),
                "isGroupMessage" to notification.isGroupMessage,
                "groupName" to (notification.groupName ?: ""),
                "isRead" to notification.isRead,
                "packageName" to notification.packageName // Guardar el nombre original del paquete
            )

            // Primero actualizar el estado de sincronización
            notificationsRef
                .child(encodedPackageName)
                .child("isSynced")
                .setValue(true)
                .await()

            // Luego guardar la notificación usando su ID
            notificationsRef
                .child(encodedPackageName)
                .child(notification.id.toString())
                .setValue(notificationMap)
                .await()

            Log.d(TAG, "✓ Notificación sincronizada exitosamente para ${notification.packageName}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sincronizando notificación: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    suspend fun getNotifications(packageName: String): List<NotificationInfo> {
        return try {
            val encodedPackageName = encodePackageName(packageName)
            Log.d(TAG, "Obteniendo notificaciones para $packageName (encoded: $encodedPackageName)")

            val snapshot = notificationsRef
                .child(encodedPackageName)
                .orderByKey()
                .limitToLast(100)
                .get()
                .await()

            val notifications = mutableListOf<NotificationInfo>()

            snapshot.children.forEach { child ->
                if (child.key != "isSynced") {
                    try {
                        val id = child.key?.toLongOrNull() ?: return@forEach
                        notifications.add(
                            NotificationInfo(
                                id = id,
                                packageName = packageName, // Usar el nombre original del paquete
                                appName = child.child("appName").getValue(String::class.java) ?: "",
                                title = child.child("title").getValue(String::class.java) ?: "",
                                content = child.child("content").getValue(String::class.java) ?: "",
                                timestamp = Date(child.child("timestamp").getValue(Long::class.java) ?: 0),
                                senderName = child.child("senderName").getValue(String::class.java),
                                isGroupMessage = child.child("isGroupMessage").getValue(Boolean::class.java) ?: false,
                                groupName = child.child("groupName").getValue(String::class.java),
                                isRead = child.child("isRead").getValue(Boolean::class.java) ?: false,
                                uniqueId = child.child("uniqueId").getValue(String::class.java) ?: "",
                                isSynced = true
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parseando notificación: ${e.message}")
                    }
                }
            }

            Log.d(TAG, "Recuperadas ${notifications.size} notificaciones para $packageName")
            notifications.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo notificaciones de Firebase: ${e.message}")
            emptyList()
        }
    }

    suspend fun deleteOldNotifications(packageName: String, olderThan: Date) {
        try {
            val encodedPackageName = encodePackageName(packageName)
            Log.d(TAG, "Eliminando notificaciones antiguas para $packageName (encoded: $encodedPackageName)")

            val snapshot = notificationsRef
                .child(encodedPackageName)
                .orderByChild("timestamp")
                .endAt(olderThan.time.toDouble())
                .get()
                .await()

            var count = 0
            snapshot.children.forEach { child ->
                if (child.key != "isSynced") {
                    child.ref.removeValue().await()
                    count++
                }
            }

            Log.d(TAG, "✓ $count notificaciones antiguas eliminadas para $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando notificaciones antiguas: ${e.message}")
        }
    }

    suspend fun clearOtherAppsNotifications(selectedPackageName: String) {
        try {
            val encodedSelectedPackage = encodePackageName(selectedPackageName)
            Log.d(TAG, "Limpiando notificaciones excepto para $selectedPackageName")

            val snapshot = notificationsRef.get().await()

            snapshot.children.forEach { child ->
                if (child.key != encodedSelectedPackage) {
                    child.ref.removeValue().await()
                }
            }
            Log.d(TAG, "✓ Notificaciones de otras apps eliminadas")
        } catch (e: Exception) {
            Log.e(TAG, "Error limpiando otras apps: ${e.message}")
        }
    }
    suspend fun verifyConnection() {
        try {
            val connectedRef = database.getReference(".info/connected")
            val snapshot = connectedRef.get().await()
            val isConnected = snapshot.getValue(Boolean::class.java) ?: false
            Log.d(TAG, "Estado de conexión Firebase: ${if (isConnected) "Conectado" else "Desconectado"}")
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando conexión: ${e.message}")
        }
    }


}