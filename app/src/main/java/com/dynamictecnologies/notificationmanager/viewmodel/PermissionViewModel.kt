package com.dynamictecnologies.notificationmanager.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dynamictecnologies.notificationmanager.util.PermissionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class PermissionViewModel(
    private val permissionManager: PermissionManager
) : ViewModel() {

    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted = _permissionsGranted.asStateFlow()

    private val _isCheckingPermissions = MutableStateFlow(false)
    val isCheckingPermissions = _isCheckingPermissions.asStateFlow()

    private val _shouldShowPermissionDialog = MutableStateFlow(false)
    val shouldShowPermissionDialog = _shouldShowPermissionDialog.asStateFlow()

    init {
        checkPermissions()
    }

    fun checkPermissions() {
        viewModelScope.launch {
            try {
                _isCheckingPermissions.value = true

                // Dar un pequeño delay para evitar checks muy rápidos
                delay(500)

                val hasNotificationPermission = permissionManager.hasNotificationPermission()
                val hasListenerEnabled = permissionManager.isNotificationListenerEnabled()

                Log.d("PermissionViewModel", "Notification permission: $hasNotificationPermission")
                Log.d("PermissionViewModel", "Listener enabled: $hasListenerEnabled")

                // Para que la app funcione, solo necesitamos el NotificationListener habilitado
                // El permiso de notificaciones regulares no es crítico para esta funcionalidad
                val allPermissionsGranted = hasListenerEnabled
                _permissionsGranted.value = allPermissionsGranted

                // Mostrar diálogo solo si falta el permiso crítico (NotificationListener)
                if (!hasListenerEnabled) {
                    _shouldShowPermissionDialog.value = true
                }

            } catch (e: Exception) {
                Log.e("PermissionViewModel", "Error checking permissions: ${e.message}", e)
                _permissionsGranted.value = false
            } finally {
                _isCheckingPermissions.value = false
            }
        }
    }

    fun recheckPermissions() {
        viewModelScope.launch {
            // Esperar un poco antes de recheck para dar tiempo al sistema
            delay(1000)
            checkPermissions()
        }
    }

    fun openNotificationSettings() {
        try {
            // Si no tienes el permiso de notificaciones regular, pide ese primero
            if (!permissionManager.hasNotificationPermission()) {

            } else if (!permissionManager.isNotificationListenerEnabled()) {
                // Si ya tienes el permiso regular, ve al NotificationListener
                permissionManager.openNotificationSettings()
            }
            _shouldShowPermissionDialog.value = false
        } catch (e: Exception) {
            Log.e("PermissionViewModel", "Error opening notification settings: ${e.message}", e)
        }
    }

    fun dismissPermissionDialog() {
        _shouldShowPermissionDialog.value = false
    }

    fun onPermissionDialogConfirm() {
        openNotificationSettings()
    }

    fun closeApp() {
        try {
            permissionManager.closeApp()
        } catch (e: Exception) {
            Log.e("PermissionViewModel", "Error closing app: ${e.message}", e)
        }
    }

    // Método para forzar recarga después de otorgar permisos
    fun onResumeFromSettings() {
        recheckPermissions()
    }
}

class PermissionViewModelFactory(
    private val permissionManager: PermissionManager
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PermissionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PermissionViewModel(permissionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}