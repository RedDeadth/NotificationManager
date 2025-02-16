package com.dynamictecnologies.notificationmanager.viewmodel

import android.content.pm.PackageManager
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dynamictecnologies.notificationmanager.data.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppListViewModel(private val packageManager: PackageManager) : ViewModel() {
    private val _apps = MutableLiveData<List<AppInfo>>(emptyList())
    val apps: LiveData<List<AppInfo>> = _apps

    private val _selectedApp = MutableLiveData<AppInfo?>(null)
    val selectedApp: LiveData<AppInfo?> = _selectedApp

    private val _showAppList = MutableLiveData(false)
    val showAppList: LiveData<Boolean> = _showAppList

    init {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            _apps.value = withContext(Dispatchers.IO) {
                getInstalledApps(packageManager)
            }
        }
    }

    fun selectApp(app: AppInfo) {
        _selectedApp.value = app
        _showAppList.value = false
    }

    fun toggleAppList() {
        _showAppList.value = !(_showAppList.value ?: false)
    }

    private fun getInstalledApps(packageManager: PackageManager): List<AppInfo> {
        val apps = mutableListOf<AppInfo>()
        val packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        for (packageInfo in packages) {
            if (packageManager.getLaunchIntentForPackage(packageInfo.packageName) != null &&
                (packageInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) {
                val appName = packageInfo.loadLabel(packageManager).toString()
                val appIcon = packageInfo.loadIcon(packageManager).toBitmap()
                apps.add(AppInfo(appName, appIcon.asImageBitmap()))
            }
        }
        return apps
    }
}