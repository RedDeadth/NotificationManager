package com.dynamictecnologies.notificationmanager.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.data.model.SyncStatus
import com.dynamictecnologies.notificationmanager.data.model.UserInfo
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
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
import java.util.*

class ShareViewModel(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
) : ViewModel() {
    private val usersRef = database.getReference("users")
    private val notificationsRef = database.getReference("notifications")
    private val sharedAccessRef = database.getReference("shared_access")

    private val _sharedUsers = MutableStateFlow<List<UserInfo>>(emptyList())
    val sharedUsers = _sharedUsers.asStateFlow()

    private val _availableUsers = MutableStateFlow<List<UserInfo>>(emptyList())
    val availableUsers = _availableUsers.asStateFlow()

    private val _searchResults = MutableStateFlow<List<UserInfo>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _sharedByUsers = MutableStateFlow<List<UserInfo>>(emptyList())
    val sharedByUsers = _sharedByUsers.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage = _successMessage.asStateFlow()

    private val _sharedUsersNotifications = MutableStateFlow<Map<String, List<NotificationInfo>>>(emptyMap())
    val sharedUsersNotifications = _sharedUsersNotifications.asStateFlow()

    private var sharedUsersListener: ValueEventListener? = null
    private val notificationListeners = mutableMapOf<String, ValueEventListener>()

    init {
        
        // Iniciar observación cuando se crea el ViewModel
        setupSharedUsersObserver()
    }

    fun setupSharedUsersObserver() {
        val currentUser = auth.currentUser ?: return
        
        viewModelScope.launch {
            try {
                // Obtener el username del usuario actual a partir de su UID
                val usernamesRef = database.getReference("usernames")
                val usernameSnapshot = usernamesRef.orderByValue().equalTo(currentUser.uid).get().await()
                
                if (!usernameSnapshot.exists()) {
                    Log.d("ShareViewModel", "No se encontró username para UID: ${currentUser.uid}")
                    _error.value = "Perfil no encontrado. Por favor, configura tu perfil."
                    return@launch
                }
                
                val username = usernameSnapshot.children.firstOrNull()?.key
                if (username == null) {
                    Log.d("ShareViewModel", "Username es nulo para UID: ${currentUser.uid}")
                    _error.value = "Error al recuperar información de perfil"
                    return@launch
                }
                
                Log.d("ShareViewModel", "Configurando observador para: $username")
                
                // Remover listener anterior si existe
                sharedUsersListener?.let { listener ->
                    usersRef.child(username).child("sharedWith").removeEventListener(listener)
                }
    
                // Configurar nuevo listener para la estructura
                sharedUsersListener = usersRef
                    .child(username)
                    .child("sharedWith")
                    .addValueEventListener(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            viewModelScope.launch {
                                try {
                                    val sharedUsersList = mutableListOf<UserInfo>()
    
                                    // Iterar sobre cada entrada en el nodo sharedWith
                                    snapshot.children.forEach { sharedUserSnapshot ->
                                        val targetUid = sharedUserSnapshot.key
                                        val isShared = sharedUserSnapshot.getValue(Boolean::class.java) ?: false
                                        
                                        if (targetUid != null && isShared) {
                                            // Buscar el username basado en el UID
                                            val targetUsernameSnapshot = usernamesRef.orderByValue().equalTo(targetUid).get().await()
                                            val targetUsername = targetUsernameSnapshot.children.firstOrNull()?.key
                                            
                                            if (targetUsername != null) {
                                                // Obtener información del usuario
                                                val userDataSnapshot = usersRef.child(targetUsername).get().await()
                                                
                                                if (userDataSnapshot.exists()) {
                                                    val userInfo = UserInfo(
                                                        uid = targetUid,
                                                        username = targetUsername,
                                                        email = userDataSnapshot.child("email").getValue(String::class.java),
                                                        createdAt = userDataSnapshot.child("createdAt").getValue(Long::class.java)
                                                            ?: System.currentTimeMillis()
                                                    )
                                                    sharedUsersList.add(userInfo)
                                                    
                                                    // Configurar observador de notificaciones
                                                    observeUserNotifications(targetUid)
                                                }
                                            }
                                        }
                                    }
    
                                    _sharedUsers.value = sharedUsersList.sortedBy { it.username }
    
                                } catch (e: Exception) {
                                    Log.e("ShareViewModel", "Error cargando usuarios compartidos: ${e.message}")
                                    _error.value = "Error al cargar usuarios compartidos: ${e.message}"
                                }
                            }
                        }
    
                        override fun onCancelled(error: DatabaseError) {
                            Log.e("ShareViewModel", "Observador cancelado: ${error.message}")
                            _error.value = "Error al observar usuarios compartidos"
                        }
                    })
            } catch (e: Exception) {
                Log.e("ShareViewModel", "Error configurando observador: ${e.message}")
                _error.value = "Error al configurar observadores: ${e.message}"
            }
        }
    }

    fun shareWithUser(targetUsername: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                _successMessage.value = null

                val currentUser = auth.currentUser ?: throw Exception("No hay usuario autenticado")
                
                // Obtener el username del usuario actual
                val usernamesRef = database.getReference("usernames")
                val usernameSnapshot = usernamesRef.orderByValue().equalTo(currentUser.uid).get().await()
                
                if (!usernameSnapshot.exists()) {
                    throw Exception("Tu perfil no está configurado correctamente")
                }
                
                val username = usernameSnapshot.children.firstOrNull()?.key
                    ?: throw Exception("No se pudo obtener tu información de usuario")

                // Verificar el usuario objetivo
                val targetUserSnapshot = usersRef.child(targetUsername).get().await()
                
                if (!targetUserSnapshot.exists()) {
                    throw Exception("El usuario '$targetUsername' no existe")
                }
                
                val targetUid = targetUserSnapshot.child("uid").getValue(String::class.java)
                    ?: throw Exception("No se pudo obtener información del usuario '$targetUsername'")

                // Verificar si ya está compartido
                val isAlreadyShared = usersRef
                    .child(username)
                    .child("sharedWith")
                    .child(targetUid)
                    .get()
                    .await()
                    .exists()

                if (isAlreadyShared) {
                    throw Exception("Este usuario ya está en tu lista de oyentes")
                }

                // Añadir el UID al subnodo sharedWith del usuario actual
                usersRef
                    .child(username)
                    .child("sharedWith")
                    .child(targetUid)
                    .setValue(true)
                    .await()

                Log.d("ShareViewModel", "Usuario $targetUsername añadido exitosamente a oyentes")
                loadAvailableUsers()
                
                _successMessage.value = "¡$targetUsername añadido exitosamente a tu lista de oyentes!"

            } catch (e: Exception) {
                Log.e("ShareViewModel", "Error compartiendo con usuario: ${e.message}")
                _error.value = e.message ?: "Error al compartir"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun removeSharedUser(targetUsername: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                _successMessage.value = null

                val currentUser = auth.currentUser ?: throw Exception("No hay usuario autenticado")
                
                // Obtener el username del usuario actual
                val usernamesRef = database.getReference("usernames")
                val usernameSnapshot = usernamesRef.orderByValue().equalTo(currentUser.uid).get().await()
                
                if (!usernameSnapshot.exists()) {
                    throw Exception("Tu perfil no está configurado correctamente")
                }
                
                val username = usernameSnapshot.children.firstOrNull()?.key
                    ?: throw Exception("No se pudo obtener tu información de usuario")

                // Obtener el UID del usuario objetivo
                val targetUserSnapshot = usersRef.child(targetUsername).get().await()
                val targetUid = targetUserSnapshot.child("uid").getValue(String::class.java)
                    ?: throw Exception("Usuario no encontrado")

                // Eliminar la referencia del subnodo sharedWith
                usersRef
                    .child(username)
                    .child("sharedWith")
                    .child(targetUid)
                    .removeValue()
                    .await()
                
                _successMessage.value = "Usuario $targetUsername eliminado de tu lista de oyentes"

                Log.d("ShareViewModel", "Oyente removido: $targetUsername")
                loadAvailableUsers()

            } catch (e: Exception) {
                Log.e("ShareViewModel", "Error eliminando usuario compartido: ${e.message}")
                _error.value = e.message ?: "Error al remover usuario"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Verifica si el usuario actual puede ver las notificaciones de un usuario objetivo
     */
    private suspend fun canSeeNotificationsOf(targetUid: String): Boolean {
        try {
            val currentUser = auth.currentUser ?: return false
            
            // Obtener el username del usuario objetivo basado en el UID
            val usernamesRef = database.getReference("usernames")
            val usernameSnapshot = usernamesRef.orderByValue().equalTo(targetUid).get().await()
            
            if (!usernameSnapshot.exists()) {
                Log.d("ShareViewModel", "No se encontró username para UID: $targetUid")
                return false
            }
            
            val username = usernameSnapshot.children.firstOrNull()?.key
            if (username == null) {
                Log.d("ShareViewModel", "Username es nulo para UID: $targetUid")
                return false
            }
            
            // Verificar si el usuario objetivo ha compartido con el usuario actual
            val targetUserSharedWithSnapshot = usersRef
                .child(username)
                .child("sharedWith")
                .child(currentUser.uid)
                .get()
                .await()
            
            val isShared = targetUserSharedWithSnapshot.exists() && 
                          targetUserSharedWithSnapshot.getValue(Boolean::class.java) == true
            
            Log.d("ShareViewModel", "Usuario $username compartiendo con actual: $isShared")
            return isShared
            
        } catch (e: Exception) {
            Log.e("ShareViewModel", "Error verificando permisos: ${e.message}")
            return false
        }
    }
    
    fun observeUserNotifications(targetUid: String) {
        // Añadir verificación asíncrona de permisos
        viewModelScope.launch {
            // Solo continuar si tenemos permiso o es nuestro propio UID
            val currentUser = auth.currentUser ?: return@launch
            val isSelf = currentUser.uid == targetUid
            
            if (!isSelf && !canSeeNotificationsOf(targetUid)) {
                Log.d("ShareViewModel", "Sin permiso para ver notificaciones de $targetUid")
                return@launch
            }
            
            setupNotificationListener(targetUid)
        }
    }
    
    /**
     * Configura el listener real de notificaciones una vez verificados los permisos
     */
    private fun setupNotificationListener(targetUid: String) {
        notificationListeners[targetUid]?.let { oldListener ->
            database.reference
                .child("notifications")
                .child(targetUid)
                .removeEventListener(oldListener)
        }

        val listener = database.reference
            .child("notifications")
            .child(targetUid)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val notifications = mutableListOf<NotificationInfo>()

                    snapshot.children.forEach { appSnapshot ->
                        appSnapshot.children.forEach { notificationSnapshot ->
                            try {
                                // Obtener el timestamp como Long y validarlo
                                val timestampLong = notificationSnapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                                
                                // Validar que el timestamp sea razonable (posterior a 1990)
                                if (timestampLong <= 631152000000L) { // 01/01/1990
                                    Log.d("ShareViewModel", "Ignorando notificación con timestamp inválido: $timestampLong")
                                    return@forEach // Saltar esta notificación
                                }
                                
                                val timestamp = Date(timestampLong)
                                
                                // Convertir String a enum SyncStatus
                                val syncStatusStr = notificationSnapshot.child("syncStatus").getValue(String::class.java) ?: "PENDING"
                                val syncStatus = try {
                                    SyncStatus.valueOf(syncStatusStr)
                                } catch (e: IllegalArgumentException) {
                                    SyncStatus.PENDING // Valor por defecto si hay error
                                }
                                
                                // Validar título y contenido
                                val title = notificationSnapshot.child("title").getValue(String::class.java) ?: ""
                                val content = notificationSnapshot.child("content").getValue(String::class.java) ?: ""
                                
                                // Solo agregar notificaciones con título o contenido no vacío
                                if (title.isBlank() && content.isBlank()) {
                                    Log.d("ShareViewModel", "Ignorando notificación sin título ni contenido")
                                    return@forEach // Saltar esta notificación
                                }
                                
                                val notification = NotificationInfo(
                                    packageName = notificationSnapshot.child("packageName").getValue(String::class.java) ?: "",
                                    appName = notificationSnapshot.child("appName").getValue(String::class.java) ?: "",
                                    title = title,
                                    content = content,
                                    timestamp = timestamp,
                                    syncStatus = syncStatus,
                                    syncTimestamp = notificationSnapshot.child("syncTimestamp").getValue(Long::class.java) ?: 0L
                                )
                                notifications.add(notification)
                            } catch (e: Exception) {
                                Log.e("ShareViewModel", "Error parsing notification: ${e.message}")
                            }
                        }
                    }

                    // Limitar a 20 notificaciones para mejor rendimiento
                    val sortedNotifications = notifications
                        .sortedByDescending { it.timestamp }
                        .take(20)

                    _sharedUsersNotifications.update { current ->
                        current.toMutableMap().apply {
                            put(targetUid, sortedNotifications)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ShareViewModel", "Error observing notifications: ${error.message}")
                }
            })

        notificationListeners[targetUid] = listener
    }
    fun loadAvailableUsers() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null

                val currentUser = auth.currentUser ?: throw Exception("No hay usuario autenticado")
                
                // Obtener el username del usuario actual
                val usernamesRef = database.getReference("usernames")
                val usernameSnapshot = usernamesRef.orderByValue().equalTo(currentUser.uid).get().await()
                
                if (!usernameSnapshot.exists()) {
                    throw Exception("Tu perfil no está configurado correctamente")
                }
                
                val username = usernameSnapshot.children.firstOrNull()?.key
                    ?: throw Exception("No se pudo obtener tu información de usuario")

                // Obtener usuarios ya compartidos
                val sharedWithSnapshot = usersRef
                    .child(username)
                    .child("sharedWith")
                    .get()
                    .await()

                val sharedWithUids = sharedWithSnapshot.children
                    .mapNotNull { it.key }
                    .toSet()

                // Obtener todos los usuarios excepto el actual y los ya compartidos
                val availableUsersList = mutableListOf<UserInfo>()
                
                // Obtener todos los usernames
                val allUsersSnapshot = usersRef.get().await()
                
                allUsersSnapshot.children.forEach { userSnapshot ->
                    val uid = userSnapshot.child("uid").getValue(String::class.java)
                    
                    // Verificar que no sea el usuario actual y que no esté ya compartido
                    if (uid != null && 
                        uid != currentUser.uid && 
                        !sharedWithUids.contains(uid)) {
                        
                        val userUsername = userSnapshot.key ?: ""
                        val userEmail = userSnapshot.child("email").getValue(String::class.java)
                        val userCreatedAt = userSnapshot.child("createdAt").getValue(Long::class.java) ?: System.currentTimeMillis()
                        
                        val userInfo = UserInfo(
                            uid = uid,
                            username = userUsername,
                            email = userEmail,
                            createdAt = userCreatedAt
                        )
                        availableUsersList.add(userInfo)
                    }
                }

                _availableUsers.value = availableUsersList.sortedBy { it.username }

            } catch (e: Exception) {
                Log.e("ShareViewModel", "Error cargando usuarios disponibles: ${e.message}")
                _error.value = e.message ?: "Error al cargar usuarios disponibles"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearSuccessMessage() {
        _successMessage.value = null
    }

    fun clearError() {
        _error.value = null
    }

    override fun onCleared() {
        super.onCleared()
        notificationListeners.forEach { (_, listener) ->
            database.reference.removeEventListener(listener)
        }
        notificationListeners.clear()
    }
    
    /**
     * Limpia todos los datos del ViewModel cuando el usuario cierra sesión
     */
    fun clearData() {
        viewModelScope.launch {
            try {
                // Eliminar todos los listeners
                notificationListeners.forEach { (_, listener) ->
                    database.reference.removeEventListener(listener)
                }
                notificationListeners.clear()
                
                // Limpiar todos los StateFlows con datos del usuario
                _sharedUsers.value = emptyList()
                _availableUsers.value = emptyList()
                _searchResults.value = emptyList()
                _sharedByUsers.value = emptyList()
                _sharedUsersNotifications.value = emptyMap()
                _error.value = null
                _successMessage.value = null
                
                Log.d("ShareViewModel", "Datos limpiados correctamente al cerrar sesión")
            } catch (e: Exception) {
                Log.e("ShareViewModel", "Error al limpiar datos: ${e.message}")
            }
        }
    }
    
    /**
     * Verifica si el usuario actual tiene un perfil completo en la base de datos
     */
    suspend fun hasValidUserProfile(): Boolean {
        return try {
            val currentUser = auth.currentUser ?: return false
            Log.d("ShareViewModel", "Verificando perfil para UID: ${currentUser.uid}")
            
            // Primero verificar si el UID existe en usernames
            val usernamesRef = database.getReference("usernames")
            val usernameSnapshot = usernamesRef.orderByValue().equalTo(currentUser.uid).get().await()
            
            if (!usernameSnapshot.exists()) {
                Log.d("ShareViewModel", "No se encontró username para UID: ${currentUser.uid}")
                return false
            }
            
            // Obtener el username basado en el UID
            val username = usernameSnapshot.children.firstOrNull()?.key
            if (username == null) {
                Log.d("ShareViewModel", "Username es nulo para UID: ${currentUser.uid}")
                return false
            }
            
            Log.d("ShareViewModel", "Username encontrado: $username")
            
            // Verificar si existe el perfil en users con ese username
            val userSnapshot = usersRef.child(username).get().await()
            
            // Verificar los campos
            val email = userSnapshot.child("email").getValue(String::class.java)
            val uid = userSnapshot.child("uid").getValue(String::class.java)
            
            Log.d("ShareViewModel", "Datos del perfil - email: $email, uid: $uid")
            
            val isValid = userSnapshot.exists() && 
                          !email.isNullOrEmpty() &&
                          !uid.isNullOrEmpty()
            
            Log.d("ShareViewModel", "Perfil válido para $username: $isValid")
            return isValid
            
        } catch (e: Exception) {
            Log.e("ShareViewModel", "Error verificando perfil de usuario: ${e.message}", e)
            false
        }
    }

    /**
     * Carga los usuarios que han compartido sus notificaciones con el usuario actual
     */
    fun loadUsersWhoSharedWithMe() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                val currentUser = auth.currentUser ?: throw Exception("No hay usuario autenticado")
                
                // Lista de usuarios que han compartido con el usuario actual
                val sharedByUsersList = mutableListOf<UserInfo>()
                
                // Buscar todos los usuarios que tienen en su sharedWith el UID del usuario actual
                val allUsersSnapshot = usersRef.get().await()
                
                allUsersSnapshot.children.forEach { userSnapshot ->
                    // Verificar si este usuario tiene un nodo sharedWith
                    val sharedWithNode = userSnapshot.child("sharedWith")
                    
                    if (sharedWithNode.exists() && sharedWithNode.hasChild(currentUser.uid)) {
                        // Verificar si el valor es true (está compartido)
                        val isShared = sharedWithNode.child(currentUser.uid).getValue(Boolean::class.java) ?: false
                        
                        if (isShared) {
                            val sharerUid = userSnapshot.child("uid").getValue(String::class.java)
                            val sharerUsername = userSnapshot.key ?: ""
                            val sharerEmail = userSnapshot.child("email").getValue(String::class.java)
                            val sharerCreatedAt = userSnapshot.child("createdAt").getValue(Long::class.java) ?: System.currentTimeMillis()
                            
                            if (sharerUid != null && sharerUid != currentUser.uid) {
                                val userInfo = UserInfo(
                                    uid = sharerUid,
                                    username = sharerUsername,
                                    email = sharerEmail,
                                    createdAt = sharerCreatedAt,
                                    isShared = true
                                )
                                sharedByUsersList.add(userInfo)
                                
                                // Configurar observador de notificaciones para ver las notificaciones compartidas
                                observeUserNotifications(sharerUid)
                            }
                        }
                    }
                }
                
                _sharedByUsers.value = sharedByUsersList.sortedBy { it.username }
                
                if (sharedByUsersList.isEmpty()) {
                    Log.d("ShareViewModel", "No hay usuarios que hayan compartido contigo")
                } else {
                    Log.d("ShareViewModel", "${sharedByUsersList.size} usuarios han compartido contigo")
                }
                
            } catch (e: Exception) {
                Log.e("ShareViewModel", "Error cargando usuarios que comparten contigo: ${e.message}")
                _error.value = "Error al cargar usuarios que comparten contigo: ${e.message}"
            } finally {
                _isLoading.value = false
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