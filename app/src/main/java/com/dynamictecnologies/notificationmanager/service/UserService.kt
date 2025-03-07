package com.dynamictecnologies.notificationmanager.service

import android.util.Log
import com.dynamictecnologies.notificationmanager.data.model.UserInfo
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.getValue
import kotlinx.coroutines.tasks.await
import com.google.firebase.database.Transaction
import com.google.firebase.database.MutableData

class UserService(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
) {
    private val usersRef = database.getReference("users")
    private val usernameMappingRef = database.getReference("usernames")


    suspend fun shareNotificationsAccess(targetUsername: String): Result<Unit> {
        return try {
            val currentUser = auth.currentUser ?: return Result.failure(Exception("No hay usuario autenticado"))

            Log.d("UserService", "Buscando usuario para compartir: $targetUsername")

            // 1. Primero verificar si el username existe
            val targetUidSnapshot = usernameMappingRef.child(targetUsername).get().await()
            if (!targetUidSnapshot.exists()) {
                Log.d("UserService", "Username $targetUsername no encontrado")
                return Result.failure(Exception("El usuario $targetUsername no existe"))
            }

            val targetUid = targetUidSnapshot.getValue(String::class.java)
                ?: return Result.failure(Exception("Error al obtener UID del usuario $targetUsername"))

            Log.d("UserService", "Usuario encontrado. UID: $targetUid")

            // 2. Verificar que no estemos compartiendo con nosotros mismos
            if (targetUid == currentUser.uid) {
                return Result.failure(Exception("No puedes compartir contigo mismo"))
            }

            // 3. Obtener la lista actual de usuarios compartidos
            val currentUserRef = usersRef.child(currentUser.uid)
            val sharedWithSnapshot = currentUserRef.child("sharedWith").get().await()
            val currentSharedWith = sharedWithSnapshot.getValue<List<String>>() ?: emptyList()

            // 4. Verificar si ya está compartido
            if (currentSharedWith.contains(targetUid)) {
                return Result.failure(Exception("Ya estás compartiendo con este usuario"))
            }

            // 5. Actualizar la lista de usuarios compartidos
            val updatedSharedWith = currentSharedWith.toMutableList().apply {
                add(targetUid)
            }

            Log.d("UserService", "Actualizando lista de usuarios compartidos: $updatedSharedWith")

            // 6. Guardar la lista actualizada
            currentUserRef.child("sharedWith").setValue(updatedSharedWith).await()

            Log.d("UserService", "Compartido exitosamente con $targetUsername")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("UserService", "Error al compartir: ${e.message}", e)
            Result.failure(e)
        }
    }

    // Método para listar usuarios disponibles para compartir
    suspend fun getAvailableUsers(): Result<List<String>> {
        return try {
            val currentUser = auth.currentUser ?: return Result.failure(Exception("No hay usuario autenticado"))

            val snapshot = usernameMappingRef.get().await()
            val usernames = mutableListOf<String>()

            snapshot.children.forEach { child ->
                val username = child.key
                val uid = child.getValue(String::class.java)
                if (username != null && uid != currentUser.uid) {
                    usernames.add(username)
                }
            }

            Result.success(usernames)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun getSharedUsers(): Result<List<UserInfo>> {
        return try {
            val currentUser = auth.currentUser ?: return Result.failure(Exception("No hay usuario autenticado"))

            val userSnapshot = usersRef.child(currentUser.uid).get().await()
            val sharedWithIds = userSnapshot.child("sharedWith").getValue<List<String>>() ?: emptyList()

            val sharedUsers = mutableListOf<UserInfo>()
            for (uid in sharedWithIds) {
                val sharedUserSnapshot = usersRef.child(uid).get().await()
                sharedUserSnapshot.getValue(UserInfo::class.java)?.let {
                    sharedUsers.add(it)
                }
            }

            Result.success(sharedUsers)
        } catch (e: Exception) {
            Log.e("UserService", "Error al obtener usuarios compartidos: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun checkCurrentUserRegistration(): Result<UserInfo?> {
        return try {
            val currentUser = auth.currentUser ?:
            return Result.failure(Exception("No hay usuario autenticado"))

            Log.d("UserService", "Verificando registro para UID: ${currentUser.uid}")

            // Primero limpiar cualquier caché local
            database.getReference("users").keepSynced(false)
            database.getReference("usernames").keepSynced(false)

            val userSnapshot = usersRef.child(currentUser.uid).get().await()

            if (!userSnapshot.exists()) {
                Log.d("UserService", "No se encontró perfil para UID: ${currentUser.uid}")
                return Result.success(null)
            }

            val userInfo = userSnapshot.getValue(UserInfo::class.java)
            if (userInfo == null) {
                Log.e("UserService", "Error al convertir datos para UID: ${currentUser.uid}")
                return Result.failure(Exception("Error al obtener datos del perfil"))
            }

            // Verificar que el perfil corresponde al usuario actual
            if (userInfo.uid != currentUser.uid) {
                Log.e("UserService", "Inconsistencia de UID detectada. Esperado: ${currentUser.uid}, Encontrado: ${userInfo.uid}")
                // Limpiar el estado inconsistente
                usersRef.child(currentUser.uid).removeValue().await()
                return Result.success(null)
            }

            // Verificar que el email coincide
            if (userInfo.email != currentUser.email) {
                Log.e("UserService", "Inconsistencia de email detectada")
                return Result.failure(Exception("Error de integridad en los datos"))
            }

            Log.d("UserService", "Perfil recuperado exitosamente para UID: ${currentUser.uid}")
            Result.success(userInfo)
        } catch (e: Exception) {
            Log.e("UserService", "Error en verificación: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun registerUsername(username: String): Result<UserInfo> {
        return try {
            val currentUser = auth.currentUser ?:
            return Result.failure(Exception("No hay usuario autenticado"))

            Log.d("UserService", "Iniciando registro para UID: ${currentUser.uid}")

            // Verificar si el usuario ya tiene un perfil
            val existingUserSnapshot = usersRef.child(currentUser.uid).get().await()
            if (existingUserSnapshot.exists()) {
                val existingUser = existingUserSnapshot.getValue(UserInfo::class.java)
                if (existingUser?.email == currentUser.email) {
                    Log.d("UserService", "Usuario ya tiene perfil")
                    return Result.failure(Exception("Ya tienes un perfil registrado"))
                } else {
                    // Si el email no coincide, eliminar el perfil existente
                    usersRef.child(currentUser.uid).removeValue().await()
                }
            }

            // Verificar si el username ya existe
            val usernameSnapshot = usernameMappingRef.child(username).get().await()
            if (usernameSnapshot.exists()) {
                return Result.failure(Exception("El nombre de usuario ya existe"))
            }

            val userInfo = UserInfo(
                uid = currentUser.uid,
                username = username,
                email = currentUser.email,
                createdAt = System.currentTimeMillis()
            )

            // Crear el mapa de actualizaciones
            val updates = hashMapOf<String, Any>(
                "/users/${currentUser.uid}" to userInfo.toMap(),
                "/usernames/$username" to currentUser.uid
            )

            // Realizar las actualizaciones de manera atómica
            database.reference.updateChildren(updates).await()

            // Verificar que se guardó correctamente
            val savedUserSnapshot = usersRef.child(currentUser.uid).get().await()
            val savedUserInfo = savedUserSnapshot.getValue(UserInfo::class.java)
                ?: return Result.failure(Exception("Error al guardar el perfil"))

            Log.d("UserService", "Registro exitoso para UID: ${currentUser.uid}")
            Result.success(savedUserInfo)
        } catch (e: Exception) {
            Log.e("UserService", "Error en registro: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun observeAuthChanges(onAuthChanged: (FirebaseUser?) -> Unit) {
        auth.addAuthStateListener { firebaseAuth ->
            onAuthChanged(firebaseAuth.currentUser)
        }
    }
}