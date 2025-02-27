package com.dynamictecnologies.notificationmanager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dynamictecnologies.notificationmanager.util.PermissionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PermissionViewModel(
    private val permissionManager: PermissionManager
) : ViewModel() {
    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted = _permissionsGranted.asStateFlow()

    init {
        checkPermissions()
    }

    fun checkPermissions() {
        viewModelScope.launch {
            val hasNotificationPermission = permissionManager.hasNotificationPermission()
            val hasListenerEnabled = permissionManager.isNotificationListenerEnabled()

            _permissionsGranted.value = hasNotificationPermission && hasListenerEnabled
        }
    }

    fun openNotificationSettings() {
        permissionManager.openNotificationSettings()
    }

    fun closeApp() {
        permissionManager.closeApp()
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