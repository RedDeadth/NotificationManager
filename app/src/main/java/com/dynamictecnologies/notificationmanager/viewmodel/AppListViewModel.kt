package com.dynamictecnologies.notificationmanager.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class AppListViewModel(
    private val packageManager: PackageManager,
    private val repository: NotificationRepository,
    private val context: Context
) : ViewModel() {
    private val TAG = "AppListViewModel"
    private val prefs = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
    private var notificationJob: Job? = null

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps = _apps.asStateFlow()

    private val _selectedApp = MutableStateFlow<AppInfo?>(null)
    val selectedApp = _selectedApp.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _showAppList = MutableStateFlow(false)
    val showAppList = _showAppList.asStateFlow()

    private val _notifications = MutableStateFlow<List<NotificationInfo>>(emptyList())
    val notifications = _notifications.asStateFlow()

    init {
        Log.d(TAG, "ViewModel inicializado")
        viewModelScope.launch {
            loadInstalledApps()
            restoreLastSelectedApp()
        }
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isLoading.value = true
                Log.d(TAG, "Cargando apps instaladas...")

                val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                    .asSequence()
                    .filter { packageInfo ->
                        packageManager.getLaunchIntentForPackage(packageInfo.packageName) != null &&
                                (packageInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0
                    }
                    .map { packageInfo ->
                        val icon = packageInfo.loadIcon(packageManager).toBitmap(
                            width = 96,
                            height = 96,
                            config = Bitmap.Config.ARGB_8888
                        ).asImageBitmap()

                        AppInfo(
                            name = packageInfo.loadLabel(packageManager).toString(),
                            packageName = packageInfo.packageName,
                            icon = icon
                        )
                    }
                    .sortedBy { it.name }
                    .toList()

                Log.d(TAG, "Cargadas ${installedApps.size} apps")
                _apps.value = installedApps
            } catch (e: Exception) {
                Log.e(TAG, "Error cargando apps: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleAppList() {
        _showAppList.value = !_showAppList.value
        Log.d(TAG, "Toggle dialog: ${_showAppList.value}")
    }

    fun selectApp(app: AppInfo?) {
        viewModelScope.launch {
            try {
                notificationJob?.cancel()

                Log.d(TAG, "Seleccionando app: ${app?.name}")
                _selectedApp.value = app

                app?.let { selectedApp ->
                    prefs.edit().putString("last_selected_app", selectedApp.packageName).apply()
                    Log.d(TAG, "Iniciando observación de notificaciones para: ${selectedApp.name}")
                    
                    // Ejecutar limpieza de notificaciones antiguas al seleccionar la app
                    launch(Dispatchers.IO) {
                        try {
                            Log.d(TAG, "Verificando límite de notificaciones para ${selectedApp.name}")
                            repository.cleanupOldNotifications(selectedApp.packageName)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error limpiando notificaciones antiguas: ${e.message}")
                        }
                    }

                    notificationJob = launch {
                        repository.getNotifications(selectedApp.packageName)
                            .catch { e ->
                                Log.e(TAG, "Error observando notificaciones: ${e.message}")
                                e.printStackTrace()
                            }
                            .collectLatest { notificationsList ->
                                Log.d(TAG, "Actualizando lista de notificaciones: ${notificationsList.size}")
                                _notifications.value = notificationsList.sortedByDescending { it.timestamp }
                            }
                    }
                } ?: run {
                    Log.d(TAG, "No hay app seleccionada, limpiando notificaciones")
                    _notifications.value = emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en selectApp: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private suspend fun restoreLastSelectedApp() {
        val lastSelectedPackage = prefs.getString("last_selected_app", null)
        Log.d(TAG, "Restaurando última app seleccionada: $lastSelectedPackage")

        if (lastSelectedPackage != null) {
            try {
                withContext(Dispatchers.IO) {
                    val applicationInfo = packageManager.getApplicationInfo(lastSelectedPackage, 0)
                    val icon = applicationInfo.loadIcon(packageManager).toBitmap(
                        width = 96,
                        height = 96,
                        config = Bitmap.Config.ARGB_8888
                    ).asImageBitmap()

                    val appInfo = AppInfo(
                        name = applicationInfo.loadLabel(packageManager).toString(),
                        packageName = lastSelectedPackage,
                        icon = icon
                    )

                    withContext(Dispatchers.Main) {
                        selectApp(appInfo)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restaurando app: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        notificationJob?.cancel()
    }
}

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