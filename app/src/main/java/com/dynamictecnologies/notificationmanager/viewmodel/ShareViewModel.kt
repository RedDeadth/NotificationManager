package com.dynamictecnologies.notificationmanager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dynamictecnologies.notificationmanager.data.model.UserInfo
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class SharedScreenState {
    object Loading : SharedScreenState()
    object NoProfile : SharedScreenState()
    object Success : SharedScreenState()
    data class Error(val message: String) : SharedScreenState()
}

class ShareViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

    private val _uiState = MutableStateFlow<SharedScreenState>(SharedScreenState.Loading)
    val uiState: StateFlow<SharedScreenState> = _uiState.asStateFlow()

    private val _sharedUsers = MutableStateFlow<List<UserInfo>>(emptyList())
    val sharedUsers: StateFlow<List<UserInfo>> = _sharedUsers.asStateFlow()

    private val _availableUsers = MutableStateFlow<List<UserInfo>>(emptyList())
    val availableUsers: StateFlow<List<UserInfo>> = _availableUsers.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadCurrentUserProfile()
        observeSharedUsers()
    }

    private fun loadCurrentUserProfile() {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    _uiState.value = SharedScreenState.NoProfile
                    return@launch
                }

                val userSnapshot = database.getReference("users")
                    .child(currentUser.uid)
                    .get()
                    .await()

                if (!userSnapshot.exists()) {
                    _uiState.value = SharedScreenState.NoProfile
                } else {
                    _uiState.value = SharedScreenState.Success
                }
            } catch (e: Exception) {
                _uiState.value = SharedScreenState.Error(e.message ?: "Error desconocido")
            }
        }
    }

    fun shareWithUser(userId: String) {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser ?: return@launch
                val updates = hashMapOf<String, Any>(
                    "users/${currentUser.uid}/sharedWith/$userId" to true
                )
                database.reference.updateChildren(updates).await()
            } catch (e: Exception) {
                _uiState.value = SharedScreenState.Error(e.message ?: "Error al compartir")
            }
        }
    }

    fun removeSharedUser(userId: String) {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser ?: return@launch
                val updates = hashMapOf<String, Any?>().apply {
                    put("users/${currentUser.uid}/sharedWith/$userId", null)
                }
                database.reference.updateChildren(updates).await()
            } catch (e: Exception) {
                _uiState.value = SharedScreenState.Error(e.message ?: "Error al remover usuario")
            }
        }
    }
    fun addFriend(userId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val currentUser = auth.currentUser ?: return@launch
                val updates = hashMapOf<String, Any>(
                    "users/${currentUser.uid}/sharedWith/$userId" to true
                )
                database.reference.updateChildren(updates).await()
                loadAvailableUsers() // Recargar la lista de usuarios disponibles
            } catch (e: Exception) {
                _uiState.value = SharedScreenState.Error(e.message ?: "Error al añadir usuario")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun removeFriend(userId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val currentUser = auth.currentUser ?: return@launch
                val updates = hashMapOf<String, Any?>(
                    "users/${currentUser.uid}/sharedWith/$userId" to null
                )
                database.reference.updateChildren(updates).await()
            } catch (e: Exception) {
                _uiState.value = SharedScreenState.Error(e.message ?: "Error al eliminar usuario")
            } finally {
                _isLoading.value = false
            }
        }
    }
    fun loadAvailableUsers() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val currentUser = auth.currentUser ?: return@launch

                println("Cargando usuarios disponibles...")
                println("Usuario actual: ${currentUser.uid}")

                // Primero obtener la lista de usuarios compartidos actual
                val currentSharedUsers = _sharedUsers.value.map { it.uid }.toSet()

                // Obtener todos los usuarios
                val usersRef = database.getReference("users")
                val usersSnapshot = usersRef.get().await()

                val usersList = mutableListOf<UserInfo>()

                usersSnapshot.children.forEach { userSnapshot ->
                    val userId = userSnapshot.key
                    println("Procesando usuario: $userId")

                    if (userId != null && userId != currentUser.uid && !currentSharedUsers.contains(userId)) {
                        val userData = userSnapshot.getValue(UserInfo::class.java)
                        if (userData != null) {
                            val user = userData.copy(
                                uid = userId,
                                username = userData.username,
                                email = userData.email,
                                createdAt = userData.createdAt
                            )
                            println("Añadiendo usuario disponible: ${user.username} (${user.uid})")
                            usersList.add(user)
                        }
                    } else {
                        println("Usuario saltado: $userId (actual: ${currentUser.uid}, compartido: ${currentSharedUsers.contains(userId)})")
                    }
                }

                println("Total usuarios disponibles encontrados: ${usersList.size}")
                _availableUsers.value = usersList

            } catch (e: Exception) {
                println("Error al cargar usuarios: ${e.message}")
                e.printStackTrace()
                _uiState.value = SharedScreenState.Error(e.message ?: "Error al cargar usuarios")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun getUserInfo(userId: String): UserInfo? {
        return try {
            val userSnapshot = database.getReference("users").child(userId).get().await()
            userSnapshot.getValue(UserInfo::class.java)?.let { userData ->
                userData.copy(
                    uid = userId,
                    username = userData.username,
                    email = userData.email,
                    createdAt = userData.createdAt
                )
            }
        } catch (e: Exception) {
            println("Error al obtener información del usuario $userId: ${e.message}")
            e.printStackTrace()
            null
        }
    }


    // También vamos a mejorar observeSharedUsers para asegurarnos de que funcione correctamente
    private fun observeSharedUsers() {
        val currentUser = auth.currentUser ?: return
        val userRef = database.getReference("users").child(currentUser.uid)

        userRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                viewModelScope.launch {
                    try {
                        val userInfo = snapshot.getValue(UserInfo::class.java)
                        println("Observando usuarios compartidos para: ${userInfo?.username}")

                        val sharedUsersList = mutableListOf<UserInfo>()

                        // Obtener los usuarios compartidos
                        userInfo?.sharedWith?.forEach { (userId, isShared) ->
                            if (isShared) {
                                println("Buscando usuario compartido: $userId")
                                getUserInfo(userId)?.let {
                                    println("Usuario compartido encontrado: ${it.username}")
                                    sharedUsersList.add(it)
                                }
                            }
                        }

                        println("Total usuarios compartidos: ${sharedUsersList.size}")
                        _sharedUsers.value = sharedUsersList
                    } catch (e: Exception) {
                        println("Error al observar usuarios compartidos: ${e.message}")
                        _uiState.value = SharedScreenState.Error(e.message ?: "Error al obtener usuarios compartidos")
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                println("Error en la base de datos: ${error.message}")
                _uiState.value = SharedScreenState.Error(error.message)
            }
        })
    }
}

class ShareViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ShareViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ShareViewModel() as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}