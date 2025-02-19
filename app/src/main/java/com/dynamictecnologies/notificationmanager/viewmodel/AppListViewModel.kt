package com.dynamictecnologies.notificationmanager.viewmodel

import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dynamictecnologies.notificationmanager.data.model.AppInfo
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.data.repository.NotificationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppListViewModel(
    private val packageManager: PackageManager,
    private val repository: NotificationRepository // Agregar el repositorio
) : ViewModel() {
    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    private val _selectedApp = mutableStateOf<AppInfo?>(null)
    val selectedApp = _selectedApp

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _showAppList = MutableStateFlow(false)
    val showAppList: StateFlow<Boolean> = _showAppList

    private val _notifications = MutableStateFlow<List<NotificationInfo>>(emptyList())
    val notifications: StateFlow<List<NotificationInfo>> = _notifications

    fun toggleAppList() {
        _showAppList.value = !_showAppList.value
    }
    init {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                withContext(Dispatchers.IO) {
                    val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                        .asSequence()
                        .filter { packageInfo ->
                            packageManager.getLaunchIntentForPackage(packageInfo.packageName) != null &&
                                    (packageInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0
                        }
                        .map { packageInfo ->
                            val icon = try {
                                packageInfo.loadIcon(packageManager).toBitmap(
                                    width = 96,
                                    height = 96,
                                    config = Bitmap.Config.ARGB_8888
                                ).asImageBitmap()
                            } catch (e: Exception) {
                                null
                            }

                            AppInfo(
                                name = packageInfo.loadLabel(packageManager).toString(),
                                icon = icon
                            )
                        }
                        .sortedBy { it.name }
                        .toList()

                    _apps.emit(installedApps)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectApp(app: AppInfo?) {
        _selectedApp.value = app
    }
}