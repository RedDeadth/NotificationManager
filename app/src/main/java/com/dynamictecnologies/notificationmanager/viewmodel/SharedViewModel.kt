package com.dynamictecnologies.notificationmanager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.data.model.UserInfo
import com.dynamictecnologies.notificationmanager.service.FirebaseService
import com.dynamictecnologies.notificationmanager.service.UserService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SharedViewModel(
    private val userService: UserService,
    private val notificationService: FirebaseService
) : ViewModel() {
    private val _uiState = MutableStateFlow<SharedScreenState>(SharedScreenState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _sharedUsers = MutableStateFlow<List<UserInfo>>(emptyList())
    val sharedUsers = _sharedUsers.asStateFlow()

    private val _sharedWithMeNotifications = MutableStateFlow<Map<String, List<NotificationInfo>>>(emptyMap())
    val sharedWithMeNotifications = _sharedWithMeNotifications.asStateFlow()

    init {
        checkUserProfile()
    }

    private fun checkUserProfile() {
        viewModelScope.launch {
            userService.checkCurrentUserRegistration()
                .onSuccess { userInfo ->
                    if (userInfo == null) {
                        _uiState.value = SharedScreenState.NoProfile
                    } else {
                        _uiState.value = SharedScreenState.Success
                        loadSharedUsers()
                        loadSharedWithMeNotifications()
                    }
                }
                .onFailure {
                    _uiState.value = SharedScreenState.Error("Error al verificar perfil")
                }
        }
    }

    private fun loadSharedUsers() {
        viewModelScope.launch {
            userService.getSharedUsers()
                .onSuccess { users ->
                    _sharedUsers.value = users
                }
        }
    }

    private fun loadSharedWithMeNotifications() {
        viewModelScope.launch {
            notificationService.getSharedWithMeNotifications()
                .onSuccess { notifications ->
                    _sharedWithMeNotifications.value = notifications
                }
        }
    }

    fun refreshData() {
        checkUserProfile()
    }
}

sealed class SharedScreenState {
    object Loading : SharedScreenState()
    object NoProfile : SharedScreenState()
    object Success : SharedScreenState()
    data class Error(val message: String) : SharedScreenState()
}

class SharedViewModelFactory(
    private val userService: UserService,
    private val notificationService: FirebaseService
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SharedViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SharedViewModel(userService, notificationService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}