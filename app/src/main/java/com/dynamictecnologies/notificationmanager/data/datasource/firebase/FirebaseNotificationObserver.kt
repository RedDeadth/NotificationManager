package com.dynamictecnologies.notificationmanager.data.datasource.firebase

import android.util.Log
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.data.model.SyncStatus
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.*

/**
 * Observer para notificaciones de Firebase con soporte para múltiples formatos.
 * 
 */
class FirebaseNotificationObserver(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
) {
    companion object {
        private const val TAG = "FirebaseNotificationObserver"
        private const val MAX_NOTIFICATIONS = 20
        private const val MIN_VALID_TIMESTAMP = 631152000000L // 01/01/1990
    }

    private val notificationListeners = mutableMapOf<String, ValueEventListener>()

    /**
     * Configura un listener de notificaciones para un usuario.
     * 
     * @param targetUid UID del usuario a observar
     * @param onNotificationsReceived Callback con las notificaciones procesadas
     */
    fun observeNotifications(
        targetUid: String,
        onNotificationsReceived: (List<NotificationInfo>) -> Unit
    ) {
        // Remover listener anterior si existe
        removeListener(targetUid)

        Log.d(TAG, "Añadiendo listener para $targetUid en path: notifications/$targetUid")
        
        val listener = database.reference
            .child("notifications")
            .child(targetUid)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val notifications = processSnapshot(snapshot)
                    onNotificationsReceived(notifications)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error observing notifications for $targetUid: ${error.message}")
                }
            })

        notificationListeners[targetUid] = listener
    }

    /**
     * Remueve el listener de un usuario.
     */
    fun removeListener(targetUid: String) {
        notificationListeners[targetUid]?.let { oldListener ->
            database.reference
                .child("notifications")
                .child(targetUid)
                .removeEventListener(oldListener)
            notificationListeners.remove(targetUid)
            Log.d(TAG, "Eliminado listener para $targetUid")
        }
    }

    /**
     * Remueve todos los listeners.
     */
    fun removeAllListeners() {
        notificationListeners.forEach { (uid, listener) ->
            database.reference
                .child("notifications")
                .child(uid)
                .removeEventListener(listener)
        }
        notificationListeners.clear()
    }

    /**
     * Procesa un snapshot de notificaciones.
     */
    private fun processSnapshot(snapshot: DataSnapshot): List<NotificationInfo> {
        val notifications = mutableListOf<NotificationInfo>()
        
        Log.d(TAG, "Procesando notificaciones, cantidad de apps: ${snapshot.childrenCount}")

        val isNestedStructure = checkIfNestedNotifications(snapshot)
        Log.d(TAG, "Estructura detectada: ${if (isNestedStructure) "anidada" else "plana"}")
        
        if (isNestedStructure) {
            processNestedNotifications(snapshot, notifications)
        } else {
            processFlatNotifications(snapshot, notifications)
        }

        return notifications
            .sortedByDescending { it.timestamp }
            .take(MAX_NOTIFICATIONS)
    }

    /**
     * Verifica si la estructura de notificaciones es anidada o plana.
     */
    private fun checkIfNestedNotifications(snapshot: DataSnapshot): Boolean {
        val appSnapshot = snapshot.children.firstOrNull() ?: return true
        val firstChild = appSnapshot.children.firstOrNull() ?: return true
        return firstChild.hasChild("timestamp")
    }

    /**
     * Procesa notificaciones con estructura anidada.
     */
    private fun processNestedNotifications(
        snapshot: DataSnapshot,
        notifications: MutableList<NotificationInfo>
    ) {
        snapshot.children.forEach { appSnapshot ->
            val appPackage = appSnapshot.key
            
            appSnapshot.children.forEach { notificationSnapshot ->
                try {
                    if (!notificationSnapshot.hasChild("timestamp")) {
                        return@forEach
                    }
                    
                    val timestampLong = parseTimestamp(notificationSnapshot.child("timestamp").getValue())
                    val actualTimestamp = validateTimestamp(timestampLong)
                    
                    val syncStatus = parseSyncStatus(
                        notificationSnapshot.child("syncStatus").getValue(String::class.java)
                    )
                    
                    val notification = NotificationInfo(
                        packageName = notificationSnapshot.child("packageName")
                            .getValue(String::class.java) ?: appPackage ?: "",
                        appName = notificationSnapshot.child("appName")
                            .getValue(String::class.java) ?: "App Desconocida",
                        title = notificationSnapshot.child("title")
                            .getValue(String::class.java) ?: "Sin título",
                        content = notificationSnapshot.child("content")
                            .getValue(String::class.java) ?: "Sin contenido",
                        timestamp = Date(actualTimestamp),
                        syncStatus = syncStatus,
                        syncTimestamp = notificationSnapshot.child("syncTimestamp")
                            .getValue(Long::class.java) ?: System.currentTimeMillis()
                    )
                    notifications.add(notification)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing notification: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Procesa notificaciones con estructura plana.
     */
    private fun processFlatNotifications(
        snapshot: DataSnapshot,
        notifications: MutableList<NotificationInfo>
    ) {
        snapshot.children.forEach { appSnapshot ->
            val appId = appSnapshot.key
            val hasRequiredFields = appSnapshot.hasChild("title") || appSnapshot.hasChild("content")
            
            if (hasRequiredFields) {
                try {
                    val timestampLong = parseTimestamp(appSnapshot.child("timestamp").getValue())
                    val actualTimestamp = validateTimestamp(timestampLong)
                    
                    val syncStatus = parseSyncStatus(
                        safeGetString(appSnapshot, "syncStatus", "PENDING")
                    )
                    
                    val syncTimestamp = parseTimestamp(
                        appSnapshot.child("syncTimestamp").getValue()
                    ).takeIf { it > 0 } ?: actualTimestamp
                    
                    val notification = NotificationInfo(
                        packageName = safeGetString(appSnapshot, "packageName", appId ?: ""),
                        appName = safeGetString(appSnapshot, "appName", "App $appId"),
                        title = safeGetString(appSnapshot, "title", "Sin título"),
                        content = safeGetString(appSnapshot, "content", "Sin contenido"),
                        timestamp = Date(actualTimestamp),
                        syncStatus = syncStatus,
                        syncTimestamp = syncTimestamp
                    )
                    
                    notifications.add(notification)
                } catch (e: Exception) {
                    Log.e(TAG, "Error procesando notificación plana para $appId: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Parsea un valor a timestamp Long.
     */
    private fun parseTimestamp(value: Any?): Long {
        return when (value) {
            is Long -> value
            is Double -> value.toLong()
            is String -> try { value.toLong() } catch (e: Exception) { 0L }
            else -> 0L
        }
    }

    /**
     * Valida y corrige un timestamp.
     */
    private fun validateTimestamp(timestamp: Long): Long {
        return if (timestamp <= MIN_VALID_TIMESTAMP) {
            System.currentTimeMillis()
        } else {
            timestamp
        }
    }

    /**
     * Parsea el estado de sincronización.
     */
    private fun parseSyncStatus(value: String?): SyncStatus {
        return try {
            SyncStatus.valueOf(value ?: "PENDING")
        } catch (e: IllegalArgumentException) {
            SyncStatus.PENDING
        }
    }

    /**
     * Lee un valor String de forma segura de un DataSnapshot.
     */
    private fun safeGetString(snapshot: DataSnapshot, childName: String, defaultValue: String): String {
        val childNode = snapshot.child(childName)
        if (!childNode.exists()) return defaultValue
        
        val rawValue = childNode.getValue()
        
        return when (rawValue) {
            is String -> rawValue
            is Long -> rawValue.toString()
            is Double -> rawValue.toString()
            is Boolean -> rawValue.toString()
            is Map<*, *> -> rawValue.toString()
            null -> defaultValue
            else -> rawValue.toString()
        }
    }
}
