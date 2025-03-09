package com.dynamictecnologies.notificationmanager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
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
    private val usersRef = database.getReference("users")

    private val _uiState = MutableStateFlow<SharedScreenState>(SharedScreenState.Loading)
    val uiState: StateFlow<SharedScreenState> = _uiState.asStateFlow()

    private val _sharedUsers = MutableStateFlow<List<UserInfo>>(emptyList())
    val sharedUsers: StateFlow<List<UserInfo>> = _sharedUsers.asStateFlow()

    private val _availableUsers = MutableStateFlow<List<UserInfo>>(emptyList())
    val availableUsers: StateFlow<List<UserInfo>> = _availableUsers.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hostNotifications = MutableStateFlow<List<NotificationInfo>>(emptyList())
    val hostNotifications: StateFlow<List<NotificationInfo>> = _hostNotifications.asStateFlow()

    private val _currentUsername = MutableStateFlow<String?>(null)
    val currentUsername: StateFlow<String?> = _currentUsername.asStateFlow()

    init {
        loadCurrentUsername()
    }

    private fun loadCurrentUsername() {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser ?: return@launch
                val query = usersRef.orderByChild("uid").equalTo(currentUser.uid)
                val snapshot = query.get().await()
                val username = snapshot.children.firstOrNull()?.key

                if (username != null) {
                    _currentUsername.value = username
                    observeSharedUsers(username)
                } else {
                    _uiState.value = SharedScreenState.NoProfile
                }
            } catch (e: Exception) {
                println("Error al cargar username actual: ${e.message}")
                _uiState.value = SharedScreenState.Error(
                    e.message ?: "Error al cargar perfil"
                )
            }
        }
    }
    fun shareWithUser(targetUsername: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val currentUser = auth.currentUser ?: throw Exception("No hay usuario autenticado")

                // 1. Obtener el username actual
                val query = usersRef.orderByChild("uid").equalTo(currentUser.uid)
                val snapshot = query.get().await()
                val currentUsername = snapshot.children.firstOrNull()?.key
                    ?: throw Exception("No se encontró tu perfil")

                println("Compartiendo desde $currentUsername con $targetUsername")

                // 2. Verificar que el usuario objetivo existe y obtener su UID
                val targetUserSnapshot = usersRef.child(targetUsername).get().await()
                if (!targetUserSnapshot.exists()) {
                    throw Exception("Usuario objetivo no encontrado")
                }

                val targetUid = targetUserSnapshot.child("uid").getValue(String::class.java)
                    ?: throw Exception("Error al obtener datos del usuario objetivo")

                println("Usuario objetivo encontrado con UID: $targetUid")

                // 3. Actualizar sharedWith usando el username como clave y el uid como valor
                val updates = hashMapOf<String, Any>(
                    "users/$currentUsername/sharedWith/$targetUsername" to targetUid
                )

                println("Aplicando actualizaciones: $updates")

                // 4. Realizar la actualización
                database.reference.updateChildren(updates).await()

                println("Compartido exitosamente con $targetUsername")

                // 5. Actualizar la lista de usuarios compartidos
                loadSharedUsers()

            } catch (e: Exception) {
                println("Error al compartir: ${e.message}")
                e.printStackTrace()
                _uiState.value = SharedScreenState.Error(e.message ?: "Error al compartir")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadSharedUsers() {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser ?: return@launch

                // Obtener el username actual
                val query = usersRef.orderByChild("uid").equalTo(currentUser.uid)
                val snapshot = query.get().await()
                val currentUsername = snapshot.children.firstOrNull()?.key ?: return@launch

                println("Cargando usuarios compartidos para $currentUsername")

                // Obtener los usuarios compartidos
                val sharedUsersList = mutableListOf<UserInfo>()
                val userSnapshot = usersRef.child(currentUsername).get().await()

                val sharedWithMap = userSnapshot.child("sharedWith")
                    .children
                    .associate { it.key!! to it.getValue(String::class.java)!! }

                println("SharedWith map: $sharedWithMap")

                sharedWithMap.forEach { (username, uid) ->
                    val sharedUserSnapshot = usersRef.child(username).get().await()
                    sharedUserSnapshot.getValue(UserInfo::class.java)?.let { userInfo ->
                        sharedUsersList.add(userInfo.copy(username = username))
                    }
                }

                _sharedUsers.value = sharedUsersList
                println("Usuarios compartidos cargados: ${sharedUsersList.size}")

            } catch (e: Exception) {
                println("Error al cargar usuarios compartidos: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    fun removeSharedUser(targetUsername: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val currentUser = auth.currentUser ?: return@launch

                // Obtener el username actual
                val currentUserQuery = usersRef.orderByChild("uid").equalTo(currentUser.uid)
                val currentUserSnapshot = currentUserQuery.get().await()
                val currentUsername = currentUserSnapshot.children.firstOrNull()?.key
                    ?: throw Exception("No se encontró tu perfil")

                // Eliminar el usuario compartido
                val updates = hashMapOf<String, Any?>(
                    "users/$currentUsername/sharedWith/$targetUsername" to null
                )
                database.reference.updateChildren(updates).await()

            } catch (e: Exception) {
                _uiState.value = SharedScreenState.Error(e.message ?: "Error al remover usuario")
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
    private fun observeSharedUsers(username: String) {
        usersRef.child(username).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                viewModelScope.launch {
                    try {
                        val sharedUsersList = mutableListOf<UserInfo>()

                        val sharedWithMap = snapshot.child("sharedWith")
                            .children
                            .associate { it.key!! to it.getValue(String::class.java)!! }

                        println("SharedWith map para $username: $sharedWithMap")

                        sharedWithMap.forEach { (sharedUsername, uid) ->
                            println("Buscando usuario compartido: $sharedUsername")
                            val userSnapshot = usersRef.child(sharedUsername).get().await()
                            userSnapshot.getValue(UserInfo::class.java)?.let { userInfo ->
                                val completeUserInfo = userInfo.copy(
                                    username = sharedUsername,
                                    uid = uid
                                )
                                println("Usuario compartido encontrado: ${completeUserInfo.username}")
                                sharedUsersList.add(completeUserInfo)
                            }
                        }

                        println("Total usuarios compartidos para $username: ${sharedUsersList.size}")
                        _sharedUsers.value = sharedUsersList
                    } catch (e: Exception) {
                        println("Error al procesar usuarios compartidos: ${e.message}")
                        _uiState.value = SharedScreenState.Error(
                            e.message ?: "Error al obtener usuarios compartidos"
                        )
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
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