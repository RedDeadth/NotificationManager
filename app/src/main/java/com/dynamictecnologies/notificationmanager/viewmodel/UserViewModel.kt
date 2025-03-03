package com.dynamictecnologies.notificationmanager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dynamictecnologies.notificationmanager.data.model.UserInfo
import com.dynamictecnologies.notificationmanager.service.UserService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UserViewModel(
    private val userService: UserService
) : ViewModel() {
    private val _usernameState = MutableStateFlow<UsernameState>(UsernameState.Initial)
    val usernameState = _usernameState.asStateFlow()

    private val _sharedUsers = MutableStateFlow<List<UserInfo>>(emptyList())
    val sharedUsers = _sharedUsers.asStateFlow()

    init {
        loadSharedUsers()
    }

    fun registerUsername(username: String) {
        viewModelScope.launch {
            _usernameState.value = UsernameState.Loading

            userService.registerUsername(username)
                .onSuccess {
                    _usernameState.value = UsernameState.Success(it)
                }
                .onFailure {
                    _usernameState.value = UsernameState.Error(it.message ?: "Error desconocido")
                }
        }
    }

    fun shareWithUser(username: String) {
        viewModelScope.launch {
            userService.shareNotificationsAccess(username)
                .onSuccess {
                    loadSharedUsers()
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
}

sealed class UsernameState {
    object Initial : UsernameState()
    object Loading : UsernameState()
    data class Success(val userInfo: UserInfo) : UsernameState()
    data class Error(val message: String) : UsernameState()
}