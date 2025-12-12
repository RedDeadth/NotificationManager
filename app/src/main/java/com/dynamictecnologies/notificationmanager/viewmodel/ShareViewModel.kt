package com.dynamictecnologies.notificationmanager.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dynamictecnologies.notificationmanager.data.datasource.firebase.FirebaseNotificationObserver
import com.dynamictecnologies.notificationmanager.data.datasource.firebase.UsernameResolver
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.data.model.SyncStatus
import com.dynamictecnologies.notificationmanager.domain.entities.User
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
import java.util.*

class ShareViewModel(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(),
    private val usernameResolver: UsernameResolver = UsernameResolver(),
    private val notificationObserver: FirebaseNotificationObserver = FirebaseNotificationObserver()
) : ViewModel() {
    private val usersRef = database.getReference("users")

    private val _sharedUsers = MutableStateFlow<List<User>>(emptyList())
    val sharedUsers = _sharedUsers.asStateFlow()

    private val _availableUsers = MutableStateFlow<List<User>>(emptyList())
    val availableUsers = _availableUsers.asStateFlow()

    private val _searchResults = MutableStateFlow<List<User>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

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
        
        // Iniciar observación cuando se crea el ViewModel
        setupSharedUsersObserver()
    }

    fun setupSharedUsersObserver() {
        val currentUser = auth.currentUser ?: return
        
        viewModelScope.launch {
            try {
                // Usar UsernameResolver para obtener username
                val username = usernameResolver.getUsernameByUid(currentUser.uid)
                
                if (username == null) {
                    Log.d("ShareViewModel", "No se encontró username para UID: ${currentUser.uid}")
                    _error.value = "Perfil no encontrado. Por favor, configura tu perfil."
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
                                    val sharedUsersList = mutableListOf<User>()
    
                                    // Iterar sobre cada entrada en el nodo sharedWith
                                    snapshot.children.forEach { sharedUserSnapshot ->
                                        val targetUid = sharedUserSnapshot.key
                                        val isShared = sharedUserSnapshot.getValue(Boolean::class.java) ?: false
                                        
                                        if (targetUid != null && isShared) {
                                            // Usar UsernameResolver para obtener info del usuario
                                            val userInfo = usernameResolver.getUserInfoByUid(targetUid)
                                            
                                            if (userInfo != null) {
                                                sharedUsersList.add(userInfo)
                                                // Configurar observador de notificaciones usando el nuevo observer
                                                observeUserNotifications(targetUid)
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

                // Registrar timestamp actual para depuración
                val currentTimestamp = System.currentTimeMillis()
                Log.d("ShareViewModel", "Añadiendo oyente $targetUsername en timestamp: $currentTimestamp")

                // Usar valor booleano simple para mantener compatibilidad con la estructura existente
                usersRef
                    .child(username)
                    .child("sharedWith")
                    .child(targetUid)
                    .setValue(true)
                    .await()

                Log.d("ShareViewModel", "Usuario $targetUsername añadido exitosamente a oyentes en $currentTimestamp")
                
                // Verificar nuestras notificaciones para este oyente
                val notificationsSnapshot = database.reference
                    .child("notifications")
                    .child(currentUser.uid)
                    .get()
                    .await()
                
                Log.d("ShareViewModel", "Tenemos ${notificationsSnapshot.childrenCount} apps con notificaciones para compartir con $targetUsername")
                
                // Analizar la estructura para diagnóstico
                notificationsSnapshot.children.forEach { appSnapshot ->
                    val appPackage = appSnapshot.key
                    val notificationsCount = appSnapshot.childrenCount
                    Log.d("ShareViewModel", "- App $appPackage tiene $notificationsCount entradas")
                    
                    if (notificationsCount > 0) {
                        // Mostrar la primera notificación como ejemplo
                        val firstNotification = appSnapshot.children.firstOrNull()
                        firstNotification?.let { notification ->
                            Log.d("ShareViewModel", "  - Primera notificación ID: ${notification.key}")
                            Log.d("ShareViewModel", "  - Campos: ${notification.children.map { it.key }.joinToString()}")
                            
                            // Verificar timestamp
                            val timestamp = notification.child("timestamp").getValue()
                            Log.d("ShareViewModel", "  - Timestamp: $timestamp (${if (timestamp != null) "válido" else "inválido"})")
                        }
                    }
                }
                
                loadAvailableUsers()
                
                _successMessage.value = "¡$targetUsername añadido exitosamente a tu lista de oyentes!"

            } catch (e: Exception) {
                Log.e("ShareViewModel", "Error compartiendo con usuario: ${e.message}", e)
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
            
            if (!targetUserSharedWithSnapshot.exists()) {
                Log.d("ShareViewModel", "Usuario $username no ha compartido con usuario actual (${currentUser.uid})")
                return false
            }
                
            // Verificar si es el nuevo formato (con addedAt) o el antiguo (solo booleano)
            val isShared = if (targetUserSharedWithSnapshot.hasChild("shared")) {
                // Formato nuevo
                targetUserSharedWithSnapshot.child("shared").getValue(Boolean::class.java) == true
            } else {
                // Formato antiguo
                targetUserSharedWithSnapshot.getValue(Boolean::class.java) == true
            }
            
            // Obtener timestamp si existe
            val addedAtTimestamp = if (targetUserSharedWithSnapshot.hasChild("addedAt")) {
                val timestamp = targetUserSharedWithSnapshot.child("addedAt").getValue(Long::class.java) ?: 0L
                Log.d("ShareViewModel", "Usuario $username compartió con ${currentUser.uid} en timestamp: ${Date(timestamp)}")
                timestamp
            } else {
                Log.d("ShareViewModel", "Usuario $username compartió con ${currentUser.uid} sin timestamp registrado")
                0L // Sin timestamp registrado
            }
            
            Log.d("ShareViewModel", "Usuario $username (UID: $targetUid) compartiendo con usuario actual (${currentUser.uid}): $isShared")
            
            // Verificar la estructura completa de sharedWith
            val allSharedWithSnapshot = usersRef.child(username).child("sharedWith").get().await()
            if (allSharedWithSnapshot.exists()) {
                val sharedWithUsers = mutableListOf<String>()
                allSharedWithSnapshot.children.forEach { child ->
                    val childUid = child.key
                    val isChildShared = if (child.hasChild("shared")) {
                        child.child("shared").getValue(Boolean::class.java) == true
                    } else {
                        child.getValue(Boolean::class.java) == true
                    }
                    
                    if (childUid != null && isChildShared) {
                        sharedWithUsers.add(childUid)
                    }
                }
                Log.d("ShareViewModel", "Usuario $username comparte con: ${sharedWithUsers.joinToString()}")
            } else {
                Log.d("ShareViewModel", "Usuario $username no comparte con ningún usuario")
            }
            
            return isShared
            
        } catch (e: Exception) {
            Log.e("ShareViewModel", "Error verificando permisos: ${e.message}", e)
            return false
        }
    }
    
    fun observeUserNotifications(targetUid: String) {
        // Añadir verificación asíncrona de permisos
        viewModelScope.launch {
            try {
                // Solo continuar si tenemos permiso o es nuestro propio UID
                val currentUser = auth.currentUser ?: return@launch
                val isSelf = currentUser.uid == targetUid
                
                Log.d("ShareViewModel", "Observando notificaciones de UID: $targetUid, isSelf=$isSelf")
                
                if (!isSelf) {
                    val hasPermission = canSeeNotificationsOf(targetUid)
                    Log.d("ShareViewModel", "Resultado de verificación de permisos para $targetUid: $hasPermission")
                    
                    if (!hasPermission) {
                        Log.d("ShareViewModel", "Sin permiso para ver notificaciones de $targetUid, saltando configuración")
                        return@launch
                    }
                }
                
                // Si llegamos aquí, tenemos permiso para configurar el listener
                Log.d("ShareViewModel", "Configurando listener de notificaciones para $targetUid")
                setupNotificationListener(targetUid)
                
            } catch (e: Exception) {
                Log.e("ShareViewModel", "Error en observeUserNotifications para $targetUid: ${e.message}", e)
            }
        }
    }
    
    /**
     * Configura el listener de notificaciones usando el observer delegado.
     * Toda la lógica de procesamiento está en FirebaseNotificationObserver.
     */
    private fun setupNotificationListener(targetUid: String) {
        Log.d("ShareViewModel", "Configurando listener para $targetUid vía notificationObserver")
        
        notificationObserver.observeNotifications(targetUid) { notifications ->
            Log.d("ShareViewModel", "Recibidas ${notifications.size} notificaciones para $targetUid")
            
            _sharedUsersNotifications.update { current ->
                current.toMutableMap().apply {
                    put(targetUid, notifications)
                }
            }
        }
    }
    
    /**
     * Actualiza el timestamp de añadido para un oyente
     * Esto se usa para saber desde cuándo un oyente está escuchando
     */
    fun updateListenerAddedTimestamp(targetUsername: String) {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser ?: return@launch
                
                // Obtener el username del usuario actual
                val usernamesRef = database.getReference("usernames")
                val usernameSnapshot = usernamesRef.orderByValue().equalTo(currentUser.uid).get().await()
                
                if (!usernameSnapshot.exists()) {
                    return@launch
                }
                
                val username = usernameSnapshot.children.firstOrNull()?.key ?: return@launch
                
                // Obtener el UID del oyente
                val targetUserSnapshot = usersRef.child(targetUsername).get().await()
                val targetUid = targetUserSnapshot.child("uid").getValue(String::class.java) ?: return@launch
                
                // Guardar el timestamp actual en sharedWith
                usersRef
                    .child(username)
                    .child("sharedWith")
                    .child(targetUid)
                    .child("addedAt")
                    .setValue(System.currentTimeMillis())
                    .await()
                
                Log.d("ShareViewModel", "Se actualizó el timestamp de añadido para $targetUsername")
                
            } catch (e: Exception) {
                Log.e("ShareViewModel", "Error al actualizar timestamp de oyente: ${e.message}")
            }
        }
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
                val availableUsersList = mutableListOf<User>()
                
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
                        
                        val userInfo = User(
                            id = uid,
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
        notificationObserver.removeAllListeners()
    }
    
    /**
     * Limpia todos los datos del ViewModel cuando el usuario cierra sesión
     */
    fun clearData() {
        viewModelScope.launch {
            try {
                // Eliminar todos los listeners usando el observer
                notificationObserver.removeAllListeners()
                
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
     * Verifica si el usuario actual tiene un perfil completo en la base de datos.
     * Delegado a UsernameResolver.
     */
    suspend fun hasValidUserProfile(): Boolean {
        val currentUser = auth.currentUser ?: return false
        Log.d("ShareViewModel", "Verificando perfil para UID: ${currentUser.uid}")
        return usernameResolver.hasValidProfile(currentUser.uid)
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
                Log.d("ShareViewModel", "Buscando usuarios que comparten con UID: ${currentUser.uid}")
                
                // Lista de usuarios que han compartido con el usuario actual
                val sharedByUsersList = mutableListOf<User>()
                
                // Buscar todos los usuarios que tienen en su sharedWith el UID del usuario actual
                val allUsersSnapshot = usersRef.get().await()
                
                Log.d("ShareViewModel", "Total de usuarios encontrados: ${allUsersSnapshot.childrenCount}")
                
                allUsersSnapshot.children.forEach { userSnapshot ->
                    val username = userSnapshot.key ?: ""
                    
                    // Verificar si este usuario tiene un nodo sharedWith
                    val sharedWithNode = userSnapshot.child("sharedWith")
                    
                    if (sharedWithNode.exists()) {
                        Log.d("ShareViewModel", "Usuario $username tiene nodo sharedWith con ${sharedWithNode.childrenCount} elementos")
                        
                        // Verificar directamente si el nodo sharedWith contiene al usuario actual
                        if (sharedWithNode.hasChild(currentUser.uid)) {
                            // Verificar si es el nuevo formato (con addedAt) o el antiguo (solo booleano)
                            val sharedWithEntry = sharedWithNode.child(currentUser.uid)
                            
                            val isShared = if (sharedWithEntry.hasChild("shared")) {
                                // Formato nuevo
                                sharedWithEntry.child("shared").getValue(Boolean::class.java) == true
                            } else {
                                // Formato antiguo
                                sharedWithEntry.getValue(Boolean::class.java) == true
                            }
                            
                            Log.d("ShareViewModel", "Usuario $username tiene al usuario actual en sharedWith: $isShared")
                            
                            // Obtener timestamp si existe
                            val addedAtTimestamp = if (sharedWithEntry.hasChild("addedAt")) {
                                val timestamp = sharedWithEntry.child("addedAt").getValue(Long::class.java) ?: 0L
                                Log.d("ShareViewModel", "Usuario $username añadió a ${currentUser.uid} en: ${Date(timestamp)}")
                                timestamp
                            } else {
                                Log.d("ShareViewModel", "No hay timestamp de añadido para usuario $username")
                                0L
                            }
                            
                            if (isShared) {
                                val sharerUid = userSnapshot.child("uid").getValue(String::class.java)
                                val sharerUsername = username 
                                val sharerEmail = userSnapshot.child("email").getValue(String::class.java)
                                val sharerCreatedAt = userSnapshot.child("createdAt").getValue(Long::class.java) ?: System.currentTimeMillis()
                                
                                if (sharerUid != null && sharerUid != currentUser.uid) {
                                    Log.d("ShareViewModel", "Añadiendo usuario compartido: $sharerUsername (UID: $sharerUid)")
                                    
                                    val userInfo = User(
                                        id = sharerUid,
                                        username = sharerUsername,
                                        email = sharerEmail,
                                        createdAt = sharerCreatedAt,
                                        isShared = true,
                                        addedAt = addedAtTimestamp
                                    )
                                    sharedByUsersList.add(userInfo)
                                    
                                    // Verificar si hay notificaciones para este usuario
                                    viewModelScope.launch {
                                        val notificationsSnapshot = database.reference
                                            .child("notifications")
                                            .child(sharerUid)
                                            .get()
                                            .await()
                                            
                                        val notificationsCount = notificationsSnapshot.childrenCount
                                        Log.d("ShareViewModel", "Usuario $sharerUsername tiene $notificationsCount apps con notificaciones")
                                        
                                        // Imprimir un ejemplo de notificación si existe
                                        if (notificationsCount > 0) {
                                            val appSnapshot = notificationsSnapshot.children.firstOrNull()
                                            appSnapshot?.let { app ->
                                                val appPackage = app.key
                                                Log.d("ShareViewModel", "Ejemplo de app: $appPackage con ${app.childrenCount} notificaciones")
                                                
                                                val notificationSnapshot = app.children.firstOrNull()
                                                notificationSnapshot?.let { notification ->
                                                    Log.d("ShareViewModel", "Notificación ID: ${notification.key}")
                                                    notification.children.forEach { field ->
                                                        Log.d("ShareViewModel", "Campo: ${field.key} = ${field.getValue()}")
                                                    }
                                                }
                                            }
                                        }
                                        
                                        // Configurar observador de notificaciones para ver las notificaciones compartidas
                                        observeUserNotifications(sharerUid)
                                    }
                                }
                            }
                        } else {
                            Log.d("ShareViewModel", "Usuario $username no tiene al usuario actual (${currentUser.uid}) en sharedWith")
                        }
                    } else {
                        Log.d("ShareViewModel", "Usuario $username no tiene nodo sharedWith")
                    }
                }
                
                _sharedByUsers.value = sharedByUsersList.sortedBy { it.username }
                
                if (sharedByUsersList.isEmpty()) {
                    Log.d("ShareViewModel", "No hay usuarios que hayan compartido contigo")
                } else {
                    Log.d("ShareViewModel", "${sharedByUsersList.size} usuarios han compartido contigo: ${sharedByUsersList.map { it.username }.joinToString()}")
                }
                
            } catch (e: Exception) {
                Log.e("ShareViewModel", "Error cargando usuarios que comparten contigo: ${e.message}", e)
                _error.value = "Error al cargar usuarios que comparten contigo: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Actualiza el formato de estructura de datos para las notificaciones del usuario
     * De formato plano a formato anidado
     */
    fun migrateNotificationsFormat() {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser ?: return@launch
                Log.d("ShareViewModel", "Iniciando migración de formato de notificaciones para ${currentUser.uid}")
                
                // Obtener las notificaciones actuales
                val notificationsSnapshot = database.reference
                    .child("notifications")
                    .child(currentUser.uid)
                    .get()
                    .await()
                
                if (!notificationsSnapshot.exists()) {
                    Log.d("ShareViewModel", "No hay notificaciones para migrar")
                    return@launch
                }
                
                // Por cada app/grupo en formato plano
                notificationsSnapshot.children.forEach { appSnapshot ->
                    val appId = appSnapshot.key ?: return@forEach
                    
                    // Verificar si tiene formato plano (campos como title, content directamente en el nodo)
                    val hasTitle = appSnapshot.hasChild("title")
                    val hasContent = appSnapshot.hasChild("content")
                    
                    if (hasTitle || hasContent) {
                        Log.d("ShareViewModel", "Migrando app $appId a formato anidado")
                        
                        // Crear un ID único para esta notificación
                        val notificationId = database.reference.push().key ?: return@forEach
                        
                        // Mapear todos los campos de la notificación
                        val notificationData = mutableMapOf<String, Any>()
                        
                        appSnapshot.children.forEach { field ->
                            val fieldName = field.key ?: return@forEach
                            val fieldValue = field.getValue() ?: return@forEach
                            notificationData[fieldName] = fieldValue
                        }
                        
                        // Asegurar que tenga un timestamp
                        if (!notificationData.containsKey("timestamp")) {
                            notificationData["timestamp"] = System.currentTimeMillis()
                        }
                        
                        // Guardar como formato anidado
                        database.reference
                            .child("notifications")
                            .child(currentUser.uid)
                            .child(appId)
                            .child(notificationId)
                            .setValue(notificationData)
                            .await()
                        
                        // Eliminar la versión plana antigua
                        database.reference
                            .child("notifications")
                            .child(currentUser.uid)
                            .child(appId)
                            .removeValue()
                            .await()
                        
                        Log.d("ShareViewModel", "Migración de app $appId completada")
                    }
                }
                
                Log.d("ShareViewModel", "Migración de formato de notificaciones completada")
                
            } catch (e: Exception) {
                Log.e("ShareViewModel", "Error en migración de formato: ${e.message}", e)
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