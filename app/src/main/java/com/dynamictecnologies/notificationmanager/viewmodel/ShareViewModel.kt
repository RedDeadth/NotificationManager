package com.dynamictecnologies.notificationmanager.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.data.model.SyncStatus
import com.dynamictecnologies.notificationmanager.domain.entities.User
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
                                    val sharedUsersList = mutableListOf<User>()
    
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
                                                    val userInfo = User(
                                                        id = targetUid,
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
     * Configura el listener real de notificaciones una vez verificados los permisos
     */
    private fun setupNotificationListener(targetUid: String) {
        notificationListeners[targetUid]?.let { oldListener ->
            database.reference
                .child("notifications")
                .child(targetUid)
                .removeEventListener(oldListener)
            Log.d("ShareViewModel", "Eliminado listener antiguo para $targetUid")
        }

        Log.d("ShareViewModel", "Añadiendo nuevo listener para $targetUid en path: notifications/$targetUid")
        
        val listener = database.reference
            .child("notifications")
            .child(targetUid)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val notifications = mutableListOf<NotificationInfo>()
                    
                    Log.d("ShareViewModel", "Recibiendo datos de notificaciones para $targetUid, cantidad de apps: ${snapshot.childrenCount}")

                    // Analizamos la estructura para detectar si tenemos formato plano o anidado
                    val isNestedStructure = checkIfNestedNotifications(snapshot)
                    
                    Log.d("ShareViewModel", "Estructura de notificaciones detectada: ${if (isNestedStructure) "anidada" else "plana"}")
                    
                    if (isNestedStructure) {
                        // Estructura anidada: /notifications/{user_uid}/{app_package_or_ID}/{notification_id}/fields
                        processNestedNotifications(snapshot, notifications)
                    } else {
                        // Estructura plana: /notifications/{user_uid}/{app_package_or_ID}/{field}
                        processFlatNotifications(snapshot, notifications)
                    }

                    // Limitar a 20 notificaciones para mejor rendimiento
                    val sortedNotifications = notifications
                        .sortedByDescending { it.timestamp }
                        .take(20)
                    
                    Log.d("ShareViewModel", "Total notificaciones procesadas para $targetUid: ${notifications.size}, después de filtrar y ordenar: ${sortedNotifications.size}")

                    _sharedUsersNotifications.update { current ->
                        current.toMutableMap().apply {
                            put(targetUid, sortedNotifications)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ShareViewModel", "Error observing notifications for $targetUid: ${error.message}")
                }
            })

        notificationListeners[targetUid] = listener
        Log.d("ShareViewModel", "Listener configurado para $targetUid")
    }
    
    /**
     * Verifica si la estructura de notificaciones es anidada o plana
     */
    private fun checkIfNestedNotifications(snapshot: DataSnapshot): Boolean {
        // Si hay al menos una app con notificaciones
        val appSnapshot = snapshot.children.firstOrNull() ?: return true
        
        // Verificar la estructura del primer hijo
        val firstChild = appSnapshot.children.firstOrNull() ?: return true
        
        // Si el primer hijo tiene un campo 'timestamp', es probablemente un nodo de notificación anidado
        return firstChild.hasChild("timestamp")
    }
    
    /**
     * Procesa notificaciones con estructura anidada normal
     */
    private fun processNestedNotifications(snapshot: DataSnapshot, notifications: MutableList<NotificationInfo>) {
        snapshot.children.forEach { appSnapshot ->
            val appPackage = appSnapshot.key
            val notificationsCount = appSnapshot.childrenCount
            Log.d("ShareViewModel", "Procesando app: $appPackage con $notificationsCount notificaciones (formato anidado)")
            
            appSnapshot.children.forEach { notificationSnapshot ->
                try {
                    // Obtener el ID de la notificación
                    val notificationId = notificationSnapshot.key
                    Log.d("ShareViewModel", "Analizando notificación $notificationId para app $appPackage")
                    
                    // Verificar si esta notificación tiene la estructura esperada
                    if (!notificationSnapshot.hasChild("timestamp")) {
                        Log.d("ShareViewModel", "Notificación $notificationId no tiene campo timestamp, saltando")
                        return@forEach // Saltar esta notificación
                    }
                    
                    // Obtener el timestamp como Long y validarlo
                    val timestampValue = notificationSnapshot.child("timestamp").getValue()
                    
                    // Intentar diferentes tipos de conversión
                    val timestampLong = when (timestampValue) {
                        is Long -> timestampValue
                        is Double -> timestampValue.toLong()
                        is String -> try { timestampValue.toLong() } catch (e: Exception) { 0L }
                        else -> 0L
                    }
                    
                    // Usar timestamp actual si el valor es 0 o muy antiguo
                    var actualTimestamp = timestampLong
                    if (actualTimestamp <= 631152000000L) { // 01/01/1990
                        Log.d("ShareViewModel", "Corrigiendo timestamp inválido para notificación $notificationId: $timestampLong -> ${System.currentTimeMillis()}")
                        actualTimestamp = System.currentTimeMillis()
                    }
                    
                    val timestamp = Date(actualTimestamp)
                    
                    // Convertir String a enum SyncStatus
                    val syncStatusStr = notificationSnapshot.child("syncStatus").getValue(String::class.java) ?: "PENDING"
                    val syncStatus = try {
                        SyncStatus.valueOf(syncStatusStr)
                    } catch (e: IllegalArgumentException) {
                        SyncStatus.PENDING // Valor por defecto si hay error
                    }
                    
                    // Validar título y contenido - usar valores predeterminados si faltan
                    val title = notificationSnapshot.child("title").getValue(String::class.java) ?: "Sin título"
                    val content = notificationSnapshot.child("content").getValue(String::class.java) ?: "Sin contenido"
                    
                    // Crear objeto de notificación con los datos disponibles
                    val notification = NotificationInfo(
                        packageName = notificationSnapshot.child("packageName").getValue(String::class.java) ?: appPackage ?: "",
                        appName = notificationSnapshot.child("appName").getValue(String::class.java) ?: "App Desconocida",
                        title = title,
                        content = content,
                        timestamp = timestamp,
                        syncStatus = syncStatus,
                        syncTimestamp = notificationSnapshot.child("syncTimestamp").getValue(Long::class.java) ?: System.currentTimeMillis()
                    )
                    notifications.add(notification)
                    Log.d("ShareViewModel", "Notificación $notificationId añadida: $title (${timestamp.time})")
                } catch (e: Exception) {
                    Log.e("ShareViewModel", "Error parsing notification: ${e.message}", e)
                }
            }
        }
    }
    
    /**
     * Procesa notificaciones con estructura plana (todos los campos como nodos independientes)
     */
    private fun processFlatNotifications(snapshot: DataSnapshot, notifications: MutableList<NotificationInfo>) {
        snapshot.children.forEach { appSnapshot ->
            val appId = appSnapshot.key
            val fieldsCount = appSnapshot.childrenCount
            Log.d("ShareViewModel", "Procesando app/grupo: $appId con $fieldsCount campos (formato plano)")
            
            // Verificar si este nodo tiene los campos básicos que necesitamos
            val hasRequiredFields = appSnapshot.hasChild("title") || appSnapshot.hasChild("content")
            
            if (hasRequiredFields) {
                try {
                    // Extraer campos directamente del nodo app
                    val timestampNode = appSnapshot.child("timestamp")
                    
                    // Leer timestamp con manejo flexible de tipos
                    var timestampLong = 0L
                    if (timestampNode.exists()) {
                        // Primero intentamos obtener el valor directamente sin especificar tipo
                        val rawValue = timestampNode.getValue()
                        timestampLong = when (rawValue) {
                            is Long -> rawValue
                            is Double -> rawValue.toLong()
                            is String -> try { rawValue.toLong() } catch (e: Exception) { 0L }
                            else -> {
                                Log.d("ShareViewModel", "Timestamp con tipo desconocido: ${rawValue?.javaClass?.simpleName}")
                                0L
                            }
                        }
                    }
                    
                    // Si el timestamp es inválido, usamos el tiempo actual
                    if (timestampLong <= 631152000000L) { // 01/01/1990
                        Log.d("ShareViewModel", "Usando timestamp actual en lugar de valor inválido: $timestampLong")
                        timestampLong = System.currentTimeMillis()
                    }
                    
                    val timestamp = Date(timestampLong)
                    
                    // Obtener resto de campos con manejo flexible de tipos
                    val title = safeGetString(appSnapshot, "title", "Sin título")
                    val content = safeGetString(appSnapshot, "content", "Sin contenido")
                    val appName = safeGetString(appSnapshot, "appName", "App $appId")
                    val packageName = safeGetString(appSnapshot, "packageName", appId ?: "")
                    
                    // Estado de sincronización
                    val syncStatusStr = safeGetString(appSnapshot, "syncStatus", "PENDING")
                    val syncStatus = try {
                        SyncStatus.valueOf(syncStatusStr)
                    } catch (e: Exception) {
                        SyncStatus.PENDING
                    }
                    
                    // Timestamp de sincronización con manejo flexible de tipos
                    var syncTimestamp = 0L
                    val syncTimestampNode = appSnapshot.child("syncTimestamp")
                    if (syncTimestampNode.exists()) {
                        val rawValue = syncTimestampNode.getValue()
                        syncTimestamp = when (rawValue) {
                            is Long -> rawValue
                            is Double -> rawValue.toLong()
                            is String -> try { rawValue.toLong() } catch (e: Exception) { 0L }
                            else -> timestampLong
                        }
                    } else {
                        syncTimestamp = timestampLong
                    }
                    
                    // Crear el objeto notificación
                    val notification = NotificationInfo(
                        packageName = packageName,
                        appName = appName,
                        title = title,
                        content = content,
                        timestamp = timestamp,
                        syncStatus = syncStatus,
                        syncTimestamp = syncTimestamp
                    )
                    
                    notifications.add(notification)
                    Log.d("ShareViewModel", "Notificación añadida desde app $appId: $title (${timestamp.time})")
                    
                } catch (e: Exception) {
                    Log.e("ShareViewModel", "Error procesando notificación plana para $appId: ${e.message}", e)
                }
            } else {
                Log.d("ShareViewModel", "App/grupo $appId no contiene los campos necesarios")
            }
        }
    }
    
    /**
     * Lee un valor String de forma segura de un DataSnapshot, manejando diferentes tipos
     */
    private fun safeGetString(snapshot: DataSnapshot, childName: String, defaultValue: String): String {
        val childNode = snapshot.child(childName)
        if (!childNode.exists()) return defaultValue
        
        // Intentar obtener el valor sin especificar tipo primero
        val rawValue = childNode.getValue()
        
        return when (rawValue) {
            is String -> rawValue
            is Long -> rawValue.toString()
            is Double -> rawValue.toString()
            is Boolean -> rawValue.toString()
            is Map<*, *> -> rawValue.toString()
            null -> defaultValue
            else -> {
                Log.d("ShareViewModel", "Valor para $childName con tipo desconocido: ${rawValue.javaClass.simpleName}")
                rawValue.toString()
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