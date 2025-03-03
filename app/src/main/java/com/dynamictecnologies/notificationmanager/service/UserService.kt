package com.dynamictecnologies.notificationmanager.service

import com.dynamictecnologies.notificationmanager.data.model.UserInfo
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

class UserService(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
) {
    private val usersRef = database.getReference("users")
    private val usernameMappingRef = database.getReference("usernames")

    suspend fun registerUsername(username: String): Result<UserInfo> {
        return try {
            val currentUser = auth.currentUser ?: return Result.failure(Exception("No hay usuario autenticado"))

            // Verificar si el username ya existe
            val usernameSnapshot = usernameMappingRef.child(username).get().await()
            if (usernameSnapshot.exists()) {
                return Result.failure(Exception("El nombre de usuario ya existe"))
            }

            val userInfo = UserInfo(
                uid = currentUser.uid,
                username = username
            )

            // Crear transacción para guardar username y user info
            database.reference.updateChildren(mapOf(
                "/usernames/$username" to currentUser.uid,
                "/users/${currentUser.uid}" to userInfo.toMap()
            )).await()

            Result.success(userInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun shareNotificationsAccess(targetUsername: String): Result<Unit> {
        return try {
            val currentUser = auth.currentUser ?: return Result.failure(Exception("No hay usuario autenticado"))

            // Obtener UID del usuario objetivo
            val targetUidSnapshot = usernameMappingRef.child(targetUsername).get().await()
            val targetUid = targetUidSnapshot.getValue(String::class.java)
                ?: return Result.failure(Exception("Usuario no encontrado"))

            // Actualizar lista de usuarios compartidos
            usersRef.child(currentUser.uid)
                .child("sharedWith")
                .updateChildren(mapOf(targetUid to true))
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSharedUsers(): Result<List<UserInfo>> {
        return try {
            val currentUser = auth.currentUser ?: return Result.failure(Exception("No hay usuario autenticado"))

            val snapshot = usersRef.child(currentUser.uid)
                .child("sharedWith")
                .get()
                .await()

            val sharedUsers = mutableListOf<UserInfo>()
            snapshot.children.forEach { child ->
                val uid = child.key ?: return@forEach
                val userSnapshot = usersRef.child(uid).get().await()
                userSnapshot.getValue(UserInfo::class.java)?.let {
                    sharedUsers.add(it)
                }
            }

            Result.success(sharedUsers)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}