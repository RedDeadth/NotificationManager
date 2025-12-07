package com.dynamictecnologies.notificationmanager.data.datasource.firebase

import com.dynamictecnologies.notificationmanager.data.model.DeviceInfo
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

/**
 * Data Source para operaciones de dispositivos en Firebase.
 * 
 * Responsabilidad única: Gestionar acceso a datos de dispositivos en Firebase.
 * 
 * Principios aplicados:
 * - SRP: Solo maneja operaciones Firebase de dispositivos
 * - Clean Architecture: Data layer, no conoce domain
 */
class DeviceDataSource(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
) {
    private val dispositivosRef = database.getReference("dispositivos")
    private val usernamesRef = database.getReference("usernames")
    
    /**
     * Vincula un dispositivo con un usuario en Firebase.
     */
    suspend fun linkDeviceToUser(
        deviceId: String,
        userId: String,
        username: String?,
        email: String?
    ): Result<Unit> {
        return try {
            val updates = hashMapOf<String, Any>(
                "vinculado" to true,
                "ultima_conexion" to "Conectado: ${System.currentTimeMillis()}",
                "usuario" to mapOf(
                    "uid" to userId,
                    "nombre" to (username ?: "Usuario"),
                    "email" to (email ?: "")
                )
            )
            
            dispositivosRef.child(deviceId).updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Desvincula un dispositivo en Firebase.
     */
    suspend fun unlinkDevice(deviceId: String): Result<Unit> {
        return try {
            val updates = hashMapOf<String, Any>(
                "vinculado" to false,
                "ultima_conexion" to "Desconectado: ${System.currentTimeMillis()}"
            )
            
            dispositivosRef.child(deviceId).updateChildren(updates).await()
            dispositivosRef.child(deviceId).child("usuario").removeValue().await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Busca el username asociado a un UID de Firebase.
     */
    suspend fun findUsernameByUid(uid: String): Result<String> {
        return try {
            val snapshot = usernamesRef
                .orderByValue()
                .equalTo(uid)
                .get()
                .await()
            
            if (snapshot.exists() && snapshot.childrenCount > 0) {
                val username = snapshot.children.first().key
                if (username != null) {
                    Result.success(username)
                } else {
                    Result.failure(Exception("Username not found"))
                }
            } else {
                Result.failure(Exception("No username found for UID: $uid"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Obtiene información de un usuario por username.
     */
    suspend fun getUserInfo(username: String): Result<Pair<String, String>> {
        return try {
            val snapshot = database.getReference("users")
                .child(username)
                .get()
                .await()
            
            if (snapshot.exists()) {
                val email = snapshot.child("email").getValue(String::class.java) ?: ""
                Result.success(Pair(username, email))
            } else {
                Result.failure(Exception("User not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Verifica si un dispositivo existe en Firebase.
     */
    suspend fun deviceExists(deviceId: String): Result<Boolean> {
        return try {
            val snapshot = dispositivosRef.child(deviceId).get().await()
            Result.success(snapshot.exists())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Registra un nuevo dispositivo en Firebase si no existe.
     */
    suspend fun registerDeviceIfNeeded(deviceId: String): Result<Unit> {
        return try {
            val snapshot = dispositivosRef.child(deviceId).get().await()
            
            if (!snapshot.exists()) {
                val deviceData = hashMapOf(
                    "disponible" to true,
                    "vinculado" to false,
                    "ultima_conexion" to "Primer registro: ${System.currentTimeMillis()}",
                    "version_firmware" to "1.2.0_MQTT"
                )
                dispositivosRef.child(deviceId).setValue(deviceData).await()
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
