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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class SharedScreenState {
    object Loading : SharedScreenState()
    object NoProfile : SharedScreenState()
    object Success : SharedScreenState()
    data class Error(val message: String) : SharedScreenState()
}

class ShareViewModel(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
) : ViewModel() {
    private val usersRef = database.getReference("users")
    private var sharedUsersListener: ValueEventListener? = null

    private val _uiState = MutableStateFlow<SharedScreenState>(SharedScreenState.Loading)
    val uiState: StateFlow<SharedScreenState> = _uiState.asStateFlow()

    private val _sharedUsers = MutableStateFlow<List<UserInfo>>(emptyList())
    val sharedUsers: StateFlow<List<UserInfo>> = _sharedUsers.asStateFlow()

    private val _availableUsers = MutableStateFlow<List<UserInfo>>(emptyList())
    val availableUsers: StateFlow<List<UserInfo>> = _availableUsers.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _sharedUsersNotifications = MutableStateFlow<Map<String, List<NotificationInfo>>>(emptyMap())
    val sharedUsersNotifications: StateFlow<Map<String, List<NotificationInfo>>> = _sharedUsersNotifications.asStateFlow()

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
                val currentUser = auth.currentUser ?: throw Exception("No hay usuario autenticado")

                // 1. Obtener el username actual
                val currentUserQuery = usersRef.orderByChild("uid").equalTo(currentUser.uid)
                val currentUserSnapshot = currentUserQuery.get().await()
                val currentUsername = currentUserSnapshot.children.firstOrNull()?.key
                    ?: throw Exception("No se encontró tu perfil")

                println("Removiendo usuario compartido: $targetUsername de $currentUsername")

                // 2. Eliminar el usuario compartido
                usersRef
                    .child(currentUsername)
                    .child("sharedWith")
                    .child(targetUsername)
                    .removeValue()
                    .await()

                // 3. Actualizar la lista local inmediatamente
                _sharedUsers.update { currentList ->
                    currentList.filterNot { it.username == targetUsername }
                }

                // 4. Limpiar las notificaciones del usuario eliminado
                _sharedUsersNotifications.update { currentMap ->
                    currentMap.filterKeys { it != targetUsername }
                }

                println("Usuario removido exitosamente")

            } catch (e: Exception) {
                println("Error al remover usuario: ${e.message}")
                e.printStackTrace()
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
                val currentUser = auth.currentUser ?: throw Exception("No hay usuario autenticado")

                // 1. Obtener el username actual
                val currentUserQuery = usersRef.orderByChild("uid").equalTo(currentUser.uid)
                val currentUserSnapshot = currentUserQuery.get().await()
                val currentUsername = currentUserSnapshot.children.firstOrNull()?.key
                    ?: throw Exception("No se encontró tu perfil")

                println("Cargando usuarios disponibles...")
                println("Usuario actual: $currentUsername (${currentUser.uid})")

                // 2. Obtener la lista actual de usuarios compartidos
                val sharedWithSnapshot = usersRef
                    .child(currentUsername)
                    .child("sharedWith")
                    .get()
                    .await()

                // Crear un conjunto de usernames ya compartidos
                val sharedUsernames = sharedWithSnapshot.children.mapNotNull { it.key }.toSet()
                println("Usuarios ya compartidos: $sharedUsernames")

                // 3. Obtener todos los usuarios
                val allUsersSnapshot = usersRef.get().await()
                val availableUsersList = mutableListOf<UserInfo>()

                allUsersSnapshot.children.forEach { userSnapshot ->
                    val username = userSnapshot.key
                    if (username != null &&
                        username != currentUsername && // No incluir al usuario actual
                        !sharedUsernames.contains(username)) { // No incluir usuarios ya compartidos

                        userSnapshot.getValue(UserInfo::class.java)?.let { userInfo ->
                            val completeUserInfo = userInfo.copy(
                                username = username,
                                uid = userSnapshot.child("uid").getValue(String::class.java) ?: ""
                            )
                            println("Añadiendo usuario disponible: ${completeUserInfo.username}")
                            availableUsersList.add(completeUserInfo)
                        }
                    } else {
                        println("Usuario filtrado: $username (actual: $currentUsername, compartido: ${sharedUsernames.contains(username)})")
                    }
                }

                println("Total usuarios disponibles encontrados: ${availableUsersList.size}")
                _availableUsers.value = availableUsersList

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
        // Remover listener anterior si existe
        sharedUsersListener?.let { listener ->
            usersRef.child(username).child("sharedWith").removeEventListener(listener)
        }

        // Crear nuevo listener
        sharedUsersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                viewModelScope.launch {
                    try {
                        val sharedUsersList = mutableListOf<UserInfo>()

                        if (snapshot.exists() && snapshot.hasChildren()) {
                            snapshot.children.forEach { child ->
                                val sharedUsername = child.key
                                val sharedUid = child.getValue(String::class.java)

                                if (sharedUsername != null && sharedUid != null) {
                                    val userSnapshot = usersRef.child(sharedUsername).get().await()
                                    userSnapshot.getValue(UserInfo::class.java)?.let { userInfo ->
                                        sharedUsersList.add(userInfo.copy(
                                            username = sharedUsername,
                                            uid = sharedUid
                                        ))
                                    }
                                }
                            }
                        }

                        _sharedUsers.value = sharedUsersList
                    } catch (e: Exception) {
                        println("Error procesando usuarios compartidos: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                println("Error en observador de sharedWith: ${error.message}")
                _uiState.value = SharedScreenState.Error(error.message)
            }
        }

        // Añadir nuevo listener
        usersRef.child(username).child("sharedWith").addValueEventListener(sharedUsersListener!!)
    }
    private fun observeUserNotifications(uid: String, username: String) {
        val notificationsRef = database.getReference("notifications/$uid")

        notificationsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                viewModelScope.launch {
                    try {
                        val notifications = mutableListOf<NotificationInfo>()

                        snapshot.children.forEach { appSnapshot ->
                            appSnapshot.children.forEach { notificationSnapshot ->
                                notificationSnapshot.getValue(NotificationInfo::class.java)?.let { notification ->
                                    notifications.add(notification)
                                }
                            }
                        }

                        // Actualizar el mapa de notificaciones
                        val currentNotifications = _sharedUsersNotifications.value.toMutableMap()
                        currentNotifications[username] = notifications.sortedByDescending { it.timestamp }
                        _sharedUsersNotifications.value = currentNotifications

                        println("Notificaciones actualizadas para $username: ${notifications.size}")
                    } catch (e: Exception) {
                        println("Error al procesar notificaciones de $username: ${e.message}")
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                println("Error al observar notificaciones de $username: ${error.message}")
            }
        })
    }
    override fun onCleared() {
        super.onCleared()
        // Limpiar listener al destruir ViewModel
        sharedUsersListener?.let { listener ->
            _currentUsername.value?.let { username ->
                usersRef.child(username).child("sharedWith").removeEventListener(listener)
            }
        }
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