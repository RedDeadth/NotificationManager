package com.dynamictecnologies.notificationmanager.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dynamictecnologies.notificationmanager.data.model.UserInfo
import com.dynamictecnologies.notificationmanager.service.UserService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UserViewModel(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase
) : ViewModel() {

    private val userService = UserService(
        auth = auth,
        database = database,
        scope = viewModelScope
    )

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    val userProfile = userService.userProfileFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState = _errorState.asStateFlow()

    init {
        refreshProfile()
    }

    fun refreshProfile() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                userService.checkCurrentUserRegistration()
            } catch (e: Exception) {
                _errorState.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun registerUsername(username: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorState.value = null
                userService.registerUsername(username)
            } catch (e: Exception) {
                _errorState.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorState.value = null
    }

    internal fun getUserService(): UserService = userService

    override fun onCleared() {
        super.onCleared()
        userService.cleanup()
    }
}

class UserViewModelFactory(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UserViewModel::class.java)) {
            return UserViewModel(auth, database) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}