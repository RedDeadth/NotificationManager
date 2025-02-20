package com.dynamictecnologies.notificationmanager.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.dynamictecnologies.notificationmanager.data.repository.NotificationRepository

class AppListViewModelFactory(
    private val context: Context,
    private val repository: NotificationRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppListViewModel::class.java)) {
            return AppListViewModel(
                packageManager = context.packageManager,
                repository = repository,
                context = context
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}