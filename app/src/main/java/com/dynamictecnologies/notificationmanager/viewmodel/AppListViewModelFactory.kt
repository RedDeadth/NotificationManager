package com.dynamictecnologies.notificationmanager.viewmodel

import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class AppListViewModelFactory(private val packageManager: PackageManager) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppListViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppListViewModel(packageManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}