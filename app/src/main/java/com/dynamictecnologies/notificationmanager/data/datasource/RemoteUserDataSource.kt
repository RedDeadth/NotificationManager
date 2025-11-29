package com.dynamictecnologies.notificationmanager.data.datasource

import android.util.Log
import com.dynamictecnologies.notificationmanager.domain.entities.User
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Data Source remoto para operaciones de perfiles de usuario con Firebase Database.
 * 
 * Principios aplicados:
 * - SRP: Solo maneja comunicación con Firebase Database para perfiles
 * - DIP: Puede ser inyectado como dependencia
 * - ISP: Interfaz específica para perfiles (separada de Auth)
 * - Clean Architecture: Pertenece a la capa de datos
 */
class RemoteUserDataSource(
    private val database: FirebaseDatabase
) {
    
    private val usersRef = database.getReference("users")
    private val usernamesRef = database.getReference("usernames")
    
    companion object {
        private const val TAG = "RemoteUserDataSource"
        private const val TIMEOUT_MS = 5000L
    }
    
    init {
        // Habilitar persistencia offline
        try {
            database.setPersistenceEnabled(true)
            usernamesRef.keepSynced(true)
        } catch (e: Exception) {
            Log.d(TAG, "Persistencia ya configurada: ${e.message}")
        }
    }
    
    /**
     * Obtiene el perfil del usuario por UID como Flow reactivo
     */
    fun getUserProfileByUid(uid: String): Flow<User?> = callbackFlow {
        try {
            // Primero buscar el username
            val usernameSnapshot = withTimeout(TIMEOUT_MS) {
                usernamesRef.orderByValue()
                    .equalTo(uid)
                    .get()
                    .await()
            }
            
            if (!usernameSnapshot.exists()) {
                trySend(null)
                close()
                return@callbackFlow
            }
            
            val username = usernameSnapshot.children.firstOrNull()?.key
            if (username == null) {
                trySend(null)
                close()
                return@callbackFlow
            }
            
            // Configurar listener para el perfil
            val userRef = usersRef.child(username)
            userRef.keepSynced(true)
            
            val valueEventListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        trySend(null)
                        return
                    }
                    
                    val email = snapshot.child("email").getValue(String::class.java)
                    val storedUid = snapshot.child("uid").getValue(String::class.java) ?: uid
                    val createdAt = snapshot.child("createdAt").getValue(Long::class.java) 
                        ?: System.currentTimeMillis()
                    
                    val userInfo = User(
                        id = storedUid,
                        username = username,
                        email = email,
                        createdAt = createdAt
                    )
                    
                    trySend(userInfo)
                }
                
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error en listener: ${error.message}")
                    close(error.toException())
                }
            }
            
            userRef.addValueEventListener(valueEventListener)
            
            awaitClose {
                userRef.removeEventListener(valueEventListener)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo perfil: ${e.message}")
            close(e)
        }
    }
    
    /**
     * Verifica si un username está disponible
     */
    suspend fun isUsernameAvailable(username: String): Boolean {
        return try {
            withTimeout(TIMEOUT_MS) {
                val snapshot = usernamesRef.child(username).get().await()
                !snapshot.exists()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando disponibilidad: ${e.message}")
            false
        }
    }
    
    /**
     * Verifica si un UID ya tiene un perfil
     */
    suspend fun hasUserProfile(uid: String): Boolean {
        return try {
            withTimeout(TIMEOUT_MS) {
                val snapshot = usernamesRef.orderByValue()
                    .equalTo(uid)
                    .get()
                    .await()
                snapshot.exists()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando perfil: ${e.message}")
            false
        }
    }
    
    /**
     * Registra un nuevo username para un UID
     */
    /**
     * Registra un nuevo username para un UID usando una transacción para garantizar unicidad.
     */
    suspend fun registerUsername(uid: String, username: String, email: String) {
        val usernameRef = usernamesRef.child(username)
        
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            usernameRef.runTransaction(object : com.google.firebase.database.Transaction.Handler {
                override fun doTransaction(currentData: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                    if (currentData.value != null) {
                        // El username ya existe
                        return com.google.firebase.database.Transaction.abort()
                    }
                    
                    // El username está libre, lo tomamos
                    currentData.value = uid
                    return com.google.firebase.database.Transaction.success(currentData)
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    currentData: DataSnapshot?
                ) {
                    if (error != null) {
                        continuation.resumeWith(Result.failure(error.toException()))
                    } else if (!committed) {
                        continuation.resumeWith(Result.failure(Exception("El nombre de usuario ya está en uso")))
                    } else {
                        // Transacción exitosa, ahora guardamos el resto del perfil
                        // Nota: Si esto falla, podríamos tener un username reservado sin perfil completo.
                        // Idealmente esto también sería parte de la transacción si estuviera en el mismo nodo,
                        // pero al estar separado, aceptamos este riesgo menor o implementamos compensación.
                        val userUpdates = mapOf(
                            "users/$username/uid" to uid,
                            "users/$username/email" to email,
                            "users/$username/createdAt" to com.google.firebase.database.ServerValue.TIMESTAMP
                        )
                        
                        database.reference.updateChildren(userUpdates)
                            .addOnSuccessListener { 
                                continuation.resumeWith(Result.success(Unit)) 
                            }
                            .addOnFailureListener { e -> 
                                // Intento de rollback (compensación)
                                usernameRef.removeValue()
                                continuation.resumeWith(Result.failure(e)) 
                            }
                    }
                }
            })
        }
    }
    
    /**
     * Obtiene el perfil de forma síncrona (sin listener)
     */
    suspend fun getUserProfileSync(uid: String): User? {
        return try {
            withTimeout(TIMEOUT_MS) {
                val usernameSnapshot = usernamesRef.orderByValue()
                    .equalTo(uid)
                    .get()
                    .await()
                
                if (!usernameSnapshot.exists()) return@withTimeout null
                
                val username = usernameSnapshot.children.firstOrNull()?.key 
                    ?: return@withTimeout null
                
                val userSnapshot = usersRef.child(username).get().await()
                
                if (!userSnapshot.exists()) return@withTimeout null
                
                val email = userSnapshot.child("email").getValue(String::class.java)
                val storedUid = userSnapshot.child("uid").getValue(String::class.java) ?: uid
                val createdAt = userSnapshot.child("createdAt").getValue(Long::class.java)
                    ?: System.currentTimeMillis()
                
                User(
                    id = storedUid,
                    username = username,
                    email = email,
                    createdAt = createdAt
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo perfil sync: ${e.message}")
            null
        }
    }
}
