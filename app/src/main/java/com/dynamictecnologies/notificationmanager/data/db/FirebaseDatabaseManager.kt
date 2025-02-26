package com.dynamictecnologies.notificationmanager.data.db

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

class FirebaseDatabaseManager {
    private val TAG = "FirebaseDatabaseManager"
    private val database: FirebaseDatabase = Firebase.database

    suspend fun initializeDatabase() {
        try {
            Log.d(TAG, "Inicializando estructura de base de datos Firebase...")

            // Crear estructura base
            val initialStructure = mapOf(
                "notifications" to mapOf(
                    "metadata" to mapOf(
                        "version" to 1,
                        "lastUpdate" to System.currentTimeMillis()
                    ),
                    "config" to mapOf(
                        "retentionDays" to 7,
                        "maxNotificationsPerApp" to 1000
                    )
                )
            )

            // Verificar si la estructura ya existe
            val rootRef = database.reference
            val snapshot = rootRef.get().await()

            if (!snapshot.exists()) {
                Log.d(TAG, "Base de datos no encontrada, creando estructura inicial...")
                rootRef.setValue(initialStructure).await()
                Log.d(TAG, "✓ Estructura de base de datos creada exitosamente")
            } else {
                Log.d(TAG, "Base de datos ya existe, verificando estructura...")

                // Verificar y actualizar campos faltantes
                val updates = mutableMapOf<String, Any>()

                if (!snapshot.hasChild("notifications/metadata")) {
                    updates["notifications/metadata"] = initialStructure["notifications"]?.get("metadata") ?: mapOf<String, Any>()
                }

                if (!snapshot.hasChild("notifications/config")) {
                    updates["notifications/config"] = initialStructure["notifications"]?.get("config") ?: mapOf<String, Any>()
                }

                if (updates.isNotEmpty()) {
                    rootRef.updateChildren(updates).await()
                    Log.d(TAG, "✓ Estructura de base de datos actualizada")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando base de datos: ${e.message}")
            throw e
        }
    }
}