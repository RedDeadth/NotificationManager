package com.dynamictecnologies.notificationmanager.data.datasource.firebase

import android.util.Log
import com.dynamictecnologies.notificationmanager.domain.entities.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

/**
 * Gestor de usuarios compartidos.
 * 
 * Extrae la lógica de gestión de sharing de ShareViewModel para:
 * - Reducir ShareViewModel de 789L a ~300L
 * - Reutilizar UsernameResolver (DRY)
 * - SRP: Solo maneja lógica de compartir
 * 
 * Principios aplicados:
 * - SRP: Solo gestiona compartir/dejar de compartir
 * - DRY: Reutiliza UsernameResolver para queries UID↔username
 * - Clean Code: Métodos pequeños y enfocados
 */
class SharedUsersManager(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(),
    private val usernameResolver: UsernameResolver = UsernameResolver()
) {
    companion object {
        private const val TAG = "SharedUsersManager"
    }

    private val usersRef = database.getReference("users")

    /**
     * Comparte con un usuario objetivo.
     * 
     * @param targetUsername Username del usuario a añadir
     * @return Result.success con mensaje o Result.failure con error
     */
    suspend fun shareWithUser(targetUsername: String): Result<String> {
        return try {
            val currentUser = auth.currentUser 
                ?: return Result.failure(Exception("No hay usuario autenticado"))
            
            // Obtener username del usuario actual usando UsernameResolver
            val username = usernameResolver.getUsernameByUid(currentUser.uid)
                ?: return Result.failure(Exception("Tu perfil no está configurado correctamente"))

            // Verificar el usuario objetivo
            val targetUid = usernameResolver.getUidByUsername(targetUsername)
                ?: return Result.failure(Exception("El usuario '$targetUsername' no existe"))

            // Verificar si ya está compartido
            val isAlreadyShared = usersRef
                .child(username)
                .child("sharedWith")
                .child(targetUid)
                .get()
                .await()
                .exists()

            if (isAlreadyShared) {
                return Result.failure(Exception("Este usuario ya está en tu lista de oyentes"))
            }

            // Añadir usuario a sharedWith
            usersRef
                .child(username)
                .child("sharedWith")
                .child(targetUid)
                .setValue(true)
                .await()

            Log.d(TAG, "Usuario $targetUsername añadido exitosamente a oyentes")
            Result.success("¡$targetUsername añadido exitosamente a tu lista de oyentes!")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error compartiendo con usuario: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Elimina un usuario de la lista de compartidos.
     * 
     * @param targetUsername Username del usuario a eliminar
     * @return Result.success con mensaje o Result.failure con error
     */
    suspend fun removeSharedUser(targetUsername: String): Result<String> {
        return try {
            val currentUser = auth.currentUser 
                ?: return Result.failure(Exception("No hay usuario autenticado"))
            
            // Obtener username del usuario actual
            val username = usernameResolver.getUsernameByUid(currentUser.uid)
                ?: return Result.failure(Exception("Tu perfil no está configurado correctamente"))

            // Obtener UID del usuario objetivo
            val targetUid = usernameResolver.getUidByUsername(targetUsername)
                ?: return Result.failure(Exception("Usuario no encontrado"))

            // Eliminar la referencia
            usersRef
                .child(username)
                .child("sharedWith")
                .child(targetUid)
                .removeValue()
                .await()

            Log.d(TAG, "Oyente removido: $targetUsername")
            Result.success("Usuario $targetUsername eliminado de tu lista de oyentes")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando usuario compartido: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Verifica si el usuario actual puede ver notificaciones de otro usuario.
     * 
     * @param targetUid UID del usuario objetivo
     * @return true si tiene permiso
     */
    suspend fun canSeeNotificationsOf(targetUid: String): Boolean {
        return try {
            val currentUser = auth.currentUser ?: return false
            
            // Obtener username del usuario objetivo
            val username = usernameResolver.getUsernameByUid(targetUid) ?: return false
            
            // Verificar si el usuario objetivo ha compartido con el usuario actual
            val sharedWithSnapshot = usersRef
                .child(username)
                .child("sharedWith")
                .child(currentUser.uid)
                .get()
                .await()
            
            if (!sharedWithSnapshot.exists()) return false
                
            // Verificar formato nuevo o antiguo
            val isShared = if (sharedWithSnapshot.hasChild("shared")) {
                sharedWithSnapshot.child("shared").getValue(Boolean::class.java) == true
            } else {
                sharedWithSnapshot.getValue(Boolean::class.java) == true
            }
            
            Log.d(TAG, "Usuario $username compartiendo con actual: $isShared")
            isShared
            
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando permisos: ${e.message}", e)
            false
        }
    }

    /**
     * Obtiene la lista de usuarios con los que el usuario actual comparte.
     * 
     * @return Lista de User compartidos
     */
    suspend fun getSharedUsers(): List<User> {
        return try {
            val currentUser = auth.currentUser ?: return emptyList()
            
            val username = usernameResolver.getUsernameByUid(currentUser.uid) 
                ?: return emptyList()
            
            val sharedWithSnapshot = usersRef
                .child(username)
                .child("sharedWith")
                .get()
                .await()
            
            if (!sharedWithSnapshot.exists()) return emptyList()
            
            sharedWithSnapshot.children.mapNotNull { child ->
                val targetUid = child.key ?: return@mapNotNull null
                val isShared = child.getValue(Boolean::class.java) == true
                
                if (isShared) {
                    usernameResolver.getUserInfoByUid(targetUid)
                } else {
                    null
                }
            }.sortedBy { it.username }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo usuarios compartidos: ${e.message}")
            emptyList()
        }
    }

    /**
     * Obtiene la lista de usuarios que han compartido con el usuario actual.
     * 
     * @return Lista de User que comparten con nosotros
     */
    suspend fun getUsersWhoSharedWithMe(): List<User> {
        return try {
            val currentUser = auth.currentUser ?: return emptyList()
            
            val usersSnapshot = usersRef.get().await()
            
            usersSnapshot.children.mapNotNull { userSnapshot ->
                val username = userSnapshot.key ?: return@mapNotNull null
                val sharedWithMe = userSnapshot
                    .child("sharedWith")
                    .child(currentUser.uid)
                    .getValue(Boolean::class.java) == true
                
                if (sharedWithMe) {
                    val uid = userSnapshot.child("uid").getValue(String::class.java)
                    val email = userSnapshot.child("email").getValue(String::class.java)
                    val createdAt = userSnapshot.child("createdAt").getValue(Long::class.java) 
                        ?: System.currentTimeMillis()
                    
                    if (uid != null) {
                        User(
                            id = uid,
                            username = username,
                            email = email,
                            createdAt = createdAt,
                            isShared = true
                        )
                    } else null
                } else {
                    null
                }
            }.sortedBy { it.username }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando usuarios que comparten: ${e.message}")
            emptyList()
        }
    }
}
