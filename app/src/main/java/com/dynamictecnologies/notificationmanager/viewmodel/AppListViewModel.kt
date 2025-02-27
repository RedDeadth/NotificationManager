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

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps = _apps.asStateFlow()

    private val _selectedApp = MutableStateFlow<AppInfo?>(null)
    val selectedApp = _selectedApp.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _showAppList = MutableStateFlow(false)
    val showAppList = _showAppList.asStateFlow()

    private val _notifications = MutableStateFlow<List<NotificationInfo>>(emptyList())
    val notifications: StateFlow<List<NotificationInfo>> = _notifications.asStateFlow()

    init {
        Log.d(TAG, "ViewModel inicializado")
        loadInstalledApps()
        viewModelScope.launch {
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
                Log.d(TAG, "Seleccionando app: ${app?.name}")
                _selectedApp.value = app

                app?.let { selectedApp ->
                    // Guardar la app seleccionada
                    prefs.edit().putString("last_selected_app", selectedApp.packageName).apply()
                    Log.d(TAG, "Iniciando observación de notificaciones para: ${selectedApp.name}")

                    // Observar el flujo de notificaciones
                    repository.getNotifications(selectedApp.packageName)
                        .catch { e ->
                            Log.e(TAG, "Error observando notificaciones: ${e.message}")
                            e.printStackTrace()
                        }
                        .collect { notificationsList ->
                            Log.d(TAG, "Recibidas ${notificationsList.size} notificaciones")
                            notificationsList.forEach { notification ->
                                Log.d(TAG, "Notificación: ${notification.title} (${notification.timestamp})")
                            }
                            _notifications.value = notificationsList
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
                val appInfo = withContext(Dispatchers.IO) {
                    val applicationInfo = packageManager.getApplicationInfo(lastSelectedPackage, 0)
                    val icon = applicationInfo.loadIcon(packageManager).toBitmap(
                        width = 96,
                        height = 96,
                        config = Bitmap.Config.ARGB_8888
                    ).asImageBitmap()

                    AppInfo(
                        name = applicationInfo.loadLabel(packageManager).toString(),
                        packageName = lastSelectedPackage,
                        icon = icon
                    )
                }
                Log.d(TAG, "App restaurada: ${appInfo.name}")
                selectApp(appInfo)
            } catch (e: Exception) {
                Log.e(TAG, "Error restaurando app: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}