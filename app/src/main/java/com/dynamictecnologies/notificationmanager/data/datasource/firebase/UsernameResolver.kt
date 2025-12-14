package com.dynamictecnologies.notificationmanager.data.datasource.firebase

import com.dynamictecnologies.notificationmanager.domain.entities.User
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

/**
 * Resolver centralizado para consultas de username ↔ UID en Firebase.
 * 
 */
class UsernameResolver(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
) {
    private val usernamesRef = database.getReference("usernames")
    private val usersRef = database.getReference("users")

    /**
     * Obtiene el username asociado a un UID.
     * 
     * @param uid UID del usuario de Firebase Auth
     * @return Username o null si no existe
     */
    suspend fun getUsernameByUid(uid: String): String? {
        return try {
            val snapshot = usernamesRef.orderByValue().equalTo(uid).get().await()
            snapshot.children.firstOrNull()?.key
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Obtiene el UID asociado a un username.
     * 
     * @param username Username del usuario
     * @return UID o null si no existe
     */
    suspend fun getUidByUsername(username: String): String? {
        return try {
            val userSnapshot = usersRef.child(username).get().await()
            userSnapshot.child("uid").getValue(String::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Obtiene información completa de un usuario por su username.
     * 
     * @param username Username del usuario
     * @return User object o null si no existe
     */
    suspend fun getUserInfoByUsername(username: String): User? {
        return try {
            val userSnapshot = usersRef.child(username).get().await()
            if (!userSnapshot.exists()) return null

            val uid = userSnapshot.child("uid").getValue(String::class.java) ?: return null
            val email = userSnapshot.child("email").getValue(String::class.java)
            val createdAt = userSnapshot.child("createdAt").getValue(Long::class.java)
                ?: System.currentTimeMillis()

            User(
                id = uid,
                username = username,
                email = email,
                createdAt = createdAt
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Obtiene información completa de un usuario por su UID.
     * 
     * @param uid UID del usuario
     * @return User object o null si no existe
     */
    suspend fun getUserInfoByUid(uid: String): User? {
        val username = getUsernameByUid(uid) ?: return null
        return getUserInfoByUsername(username)
    }

    /**
     * Verifica si un usuario tiene un perfil válido.
     * 
     * @param uid UID del usuario
     * @return true si el perfil existe y tiene email y uid
     */
    suspend fun hasValidProfile(uid: String): Boolean {
        return try {
            val username = getUsernameByUid(uid) ?: return false
            val userSnapshot = usersRef.child(username).get().await()
            
            val email = userSnapshot.child("email").getValue(String::class.java)
            val storedUid = userSnapshot.child("uid").getValue(String::class.java)
            
            userSnapshot.exists() && !email.isNullOrEmpty() && !storedUid.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }
    }
}
