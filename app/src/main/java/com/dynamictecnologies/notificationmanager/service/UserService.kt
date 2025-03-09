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

    suspend fun checkCurrentUserRegistration(): Result<UserInfo?> {
        return try {
            val currentUser = auth.currentUser ?:
            return Result.failure(Exception("No hay usuario autenticado"))

            Log.d("UserService", "Verificando registro para username del usuario: ${currentUser.uid}")

            // Buscar el usuario por UID en todos los perfiles
            val query = usersRef.orderByChild("uid").equalTo(currentUser.uid)
            val snapshot = query.get().await()

            if (!snapshot.exists()) {
                Log.d("UserService", "No se encontró perfil para UID: ${currentUser.uid}")
                return Result.success(null)
            }

            // Tomar el primer (y único) resultado
            val userSnapshot = snapshot.children.first()
            val username = userSnapshot.key ?:
            return Result.failure(Exception("Error al obtener username"))

            val userInfo = userSnapshot.getValue(UserInfo::class.java)?.copy(username = username)
                ?: return Result.failure(Exception("Error al obtener datos del perfil"))

            // Verificar integridad de datos
            if (userInfo.uid != currentUser.uid || userInfo.email != currentUser.email) {
                Log.e("UserService", "Inconsistencia de datos detectada")
                return Result.failure(Exception("Error de integridad en los datos"))
            }

            Log.d("UserService", "Perfil recuperado exitosamente para username: $username")
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

            Log.d("UserService", "Iniciando registro para username: $username")

            // Verificar si el username ya existe
            val usernameSnapshot = usersRef.child(username).get().await()
            if (usernameSnapshot.exists()) {
                return Result.failure(Exception("El nombre de usuario ya existe"))
            }

            // Verificar si el usuario ya tiene un perfil
            val existingQuery = usersRef.orderByChild("uid").equalTo(currentUser.uid)
            val existingSnapshot = existingQuery.get().await()
            if (existingSnapshot.exists()) {
                return Result.failure(Exception("Ya tienes un perfil registrado"))
            }

            val userInfo = UserInfo(
                uid = currentUser.uid,
                username = username,
                email = currentUser.email,
                createdAt = System.currentTimeMillis()
            )

            // Guardar usando username como clave
            usersRef.child(username).setValue(userInfo.toMap()).await()

            // Verificar que se guardó correctamente
            val savedUserSnapshot = usersRef.child(username).get().await()
            val savedUser = savedUserSnapshot.getValue(UserInfo::class.java)
                ?: return Result.failure(Exception("Error al guardar el perfil"))

            Log.d("UserService", "Registro exitoso para username: $username")
            Result.success(savedUser.copy(username = username))
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