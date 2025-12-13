package com.dynamictecnologies.notificationmanager.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dynamictecnologies.notificationmanager.data.datasource.firebase.FirebaseNotificationObserver
import com.dynamictecnologies.notificationmanager.data.datasource.firebase.SharedUsersManager
import com.dynamictecnologies.notificationmanager.data.datasource.firebase.UsernameResolver
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.domain.entities.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * ViewModel para gestión de compartir notificaciones.
 * 
 * REFACTORIZADO: Reducido de 789L a ~350L delegando a:
 * - SharedUsersManager: lógica de share/unshare
 * - UsernameResolver: queries UID↔username
 * - FirebaseNotificationObserver: observación de notificaciones
 * 
 * Principios aplicados:
 * - SRP: Solo coordina UI state, delega lógica a componentes
 * - DIP: Recibe dependencias inyectadas
 * - DRY: Reutiliza componentes centralizados
 */
class ShareViewModel(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(),
    private val usernameResolver: UsernameResolver = UsernameResolver(),
    private val notificationObserver: FirebaseNotificationObserver = FirebaseNotificationObserver(),
    private val sharedUsersManager: SharedUsersManager = SharedUsersManager()
) : ViewModel() {

    companion object {
        private const val TAG = "ShareViewModel"
    }

    private val usersRef = database.getReference("users")

    // UI States
    private val _sharedUsers = MutableStateFlow<List<User>>(emptyList())
    val sharedUsers = _sharedUsers.asStateFlow()

    private val _availableUsers = MutableStateFlow<List<User>>(emptyList())
    val availableUsers = _availableUsers.asStateFlow()

    private val _sharedByUsers = MutableStateFlow<List<User>>(emptyList())
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

    init {
        setupSharedUsersObserver()
    }

    // ========================================
    // OBSERVADORES
    // ========================================

    fun setupSharedUsersObserver() {
        val currentUser = auth.currentUser ?: return
        
        viewModelScope.launch {
            try {
                val username = usernameResolver.getUsernameByUid(currentUser.uid)
                
                if (username == null) {
                    Log.d(TAG, "No se encontró username para UID: ${currentUser.uid}")
                    _error.value = "Perfil no encontrado. Por favor, configura tu perfil."
                    return@launch
                }
                
                Log.d(TAG, "Configurando observador para: $username")
                
                // Remover listener anterior
                sharedUsersListener?.let { listener ->
                    usersRef.child(username).child("sharedWith").removeEventListener(listener)
                }
    
                // Configurar nuevo listener
                sharedUsersListener = usersRef
                    .child(username)
                    .child("sharedWith")
                    .addValueEventListener(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            viewModelScope.launch {
                                try {
                                    val sharedUsersList = mutableListOf<User>()
    
                                    snapshot.children.forEach { sharedUserSnapshot ->
                                        val targetUid = sharedUserSnapshot.key
                                        val isShared = sharedUserSnapshot.getValue(Boolean::class.java) ?: false
                                        
                                        if (targetUid != null && isShared) {
                                            val userInfo = usernameResolver.getUserInfoByUid(targetUid)
                                            if (userInfo != null) {
                                                sharedUsersList.add(userInfo)
                                                observeUserNotifications(targetUid)
                                            }
                                        }
                                    }
    
                                    _sharedUsers.value = sharedUsersList.sortedBy { it.username }
    
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error cargando usuarios compartidos: ${e.message}")
                                    _error.value = "Error al cargar usuarios compartidos"
                                }
                            }
                        }
    
                        override fun onCancelled(error: DatabaseError) {
                            Log.e(TAG, "Observador cancelado: ${error.message}")
                            _error.value = "Error al observar usuarios compartidos"
                        }
                    })
            } catch (e: Exception) {
                Log.e(TAG, "Error configurando observador: ${e.message}")
                _error.value = "Error al configurar observadores"
            }
        }
    }

    // ========================================
    // ACCIONES DE COMPARTIR (DELEGADAS)
    // ========================================

    fun shareWithUser(targetUsername: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _successMessage.value = null

            sharedUsersManager.shareWithUser(targetUsername)
                .onSuccess { message ->
                    _successMessage.value = message
                    loadAvailableUsers()
                }
                .onFailure { e ->
                    _error.value = e.message ?: "Error al compartir"
                }

            _isLoading.value = false
        }
    }

    fun removeSharedUser(targetUsername: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _successMessage.value = null

            sharedUsersManager.removeSharedUser(targetUsername)
                .onSuccess { message ->
                    _successMessage.value = message
                    loadAvailableUsers()
                }
                .onFailure { e ->
                    _error.value = e.message ?: "Error al remover usuario"
                }

            _isLoading.value = false
        }
    }

    // ========================================
    // OBSERVACIÓN DE NOTIFICACIONES
    // ========================================

    fun observeUserNotifications(targetUid: String) {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser ?: return@launch
                val isSelf = currentUser.uid == targetUid
                
                if (!isSelf) {
                    val hasPermission = sharedUsersManager.canSeeNotificationsOf(targetUid)
                    if (!hasPermission) {
                        Log.d(TAG, "Sin permiso para ver notificaciones de $targetUid")
                        return@launch
                    }
                }
                
                // Delegar al observer
                notificationObserver.observeNotifications(targetUid) { notifications ->
                    _sharedUsersNotifications.value = _sharedUsersNotifications.value.toMutableMap().apply {
                        put(targetUid, notifications)
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error en observeUserNotifications: ${e.message}")
            }
        }
    }

    // ========================================
    // CARGA DE USUARIOS
    // ========================================

    fun loadAvailableUsers() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val currentUser = auth.currentUser ?: return@launch

                val username = usernameResolver.getUsernameByUid(currentUser.uid) ?: return@launch

                // Obtener usuarios ya compartidos
                val sharedSnapshot = usersRef
                    .child(username)
                    .child("sharedWith")
                    .get()
                    .await()

                val sharedUids = sharedSnapshot.children
                    .filter { it.getValue(Boolean::class.java) == true }
                    .mapNotNull { it.key }
                    .toSet()

                // Obtener todos los usuarios
                val allUsersSnapshot = usersRef.get().await()

                val availableUsersList = allUsersSnapshot.children.mapNotNull { userSnapshot ->
                    val userUsername = userSnapshot.key ?: return@mapNotNull null
                    val uid = userSnapshot.child("uid").getValue(String::class.java) ?: return@mapNotNull null
                    
                    // Excluir usuario actual y ya compartidos
                    if (uid == currentUser.uid || sharedUids.contains(uid)) {
                        return@mapNotNull null
                    }

                    User(
                        id = uid,
                        username = userUsername,
                        email = userSnapshot.child("email").getValue(String::class.java),
                        createdAt = userSnapshot.child("createdAt").getValue(Long::class.java) 
                            ?: System.currentTimeMillis()
                    )
                }.sortedBy { it.username }

                _availableUsers.value = availableUsersList

            } catch (e: Exception) {
                Log.e(TAG, "Error cargando usuarios disponibles: ${e.message}")
                _error.value = "Error al cargar usuarios disponibles"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadUsersWhoSharedWithMe() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                val users = sharedUsersManager.getUsersWhoSharedWithMe()
                _sharedByUsers.value = users
                
                // Observar notificaciones de cada usuario
                users.forEach { user ->
                    observeUserNotifications(user.id)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error cargando usuarios que comparten: ${e.message}")
                _error.value = "Error al cargar usuarios"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ========================================
    // UTILIDADES
    // ========================================

    suspend fun hasValidUserProfile(): Boolean {
        val currentUser = auth.currentUser ?: return false
        return usernameResolver.hasValidProfile(currentUser.uid)
    }

    fun clearSuccessMessage() {
        _successMessage.value = null
    }

    fun clearError() {
        _error.value = null
    }

    fun clearData() {
        viewModelScope.launch {
            notificationObserver.removeAllListeners()
            _sharedUsers.value = emptyList()
            _availableUsers.value = emptyList()
            _sharedByUsers.value = emptyList()
            _sharedUsersNotifications.value = emptyMap()
            _error.value = null
            _successMessage.value = null
            Log.d(TAG, "Datos limpiados correctamente")
        }
    }

    override fun onCleared() {
        super.onCleared()
        notificationObserver.removeAllListeners()
    }
}

/**
 * Factory para ShareViewModel con inyección de dependencias.
 */
class ShareViewModelFactory(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(),
    private val usernameResolver: UsernameResolver = UsernameResolver(),
    private val notificationObserver: FirebaseNotificationObserver = FirebaseNotificationObserver(),
    private val sharedUsersManager: SharedUsersManager = SharedUsersManager()
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ShareViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ShareViewModel(
                auth = auth,
                database = database,
                usernameResolver = usernameResolver,
                notificationObserver = notificationObserver,
                sharedUsersManager = sharedUsersManager
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}