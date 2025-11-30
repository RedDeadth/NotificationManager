package com.dynamictecnologies.notificationmanager.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dynamictecnologies.notificationmanager.data.model.AppInfo
import com.dynamictecnologies.notificationmanager.data.model.NotificationInfo
import com.dynamictecnologies.notificationmanager.data.repository.NotificationRepository
import com.dynamictecnologies.notificationmanager.domain.usecases.app.GetInstalledAppsUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.app.GetSelectedAppUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.app.SaveSelectedAppUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ViewModel para gestión de aplicaciones instaladas y selección de app.
 * Refactorizado para seguir Clean Architecture y SOLID principles.
 * 
 * Principios aplicados:
 * - SRP: Solo maneja UI state para apps y notificaciones
 * - DIP: Depende de abstracciones (Use Cases) no de implementaciones
 * - Clean Architecture: No tiene lógica de negocio, solo coordina Use Cases
 * - DRY: Reutiliza Use Cases compartidos
 * 
 * @param getInstalledAppsUseCase Use case para obtener apps instaladas
 * @param saveSelectedAppUseCase Use case para guardar app seleccionada
 * @param getSelectedAppUseCase Use case para recuperar app seleccionada
 * @param notificationRepository Repository para gestión de notificaciones
 */
class AppListViewModel(
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    private val saveSelectedAppUseCase: SaveSelectedAppUseCase,
    private val getSelectedAppUseCase: GetSelectedAppUseCase,
    private val notificationRepository: NotificationRepository
) : ViewModel() {
    
    private val TAG = "AppListViewModel"
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
        Log.d(TAG, "ViewModel inicializado con Clean Architecture")
        viewModelScope.launch {
            loadInstalledApps()
            restoreLastSelectedApp()
        }
    }

    /**
     * Carga las aplicaciones instaladas usando el Use Case.
     */
    private fun loadInstalledApps() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                Log.d(TAG, "Cargando apps instaladas...")

                getInstalledAppsUseCase()
                    .onSuccess { installedApps ->
                        Log.d(TAG, "Cargadas ${installedApps.size} apps")
                        _apps.value = installedApps
                    }
                    .onFailure { error ->
                        Log.e(TAG, "Error cargando apps: ${error.message}")
                    }
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Alterna la visibilidad del diálogo de selección de apps.
     */
    fun toggleAppList() {
        _showAppList.value = !_showAppList.value
        Log.d(TAG, "Toggle dialog: ${_showAppList.value}")
    }

    /**
     * Selecciona una aplicación y comienza a observar sus notificaciones.
     * 
     * @param app La aplicación a seleccionar o null para deseleccionar
     */
    fun selectApp(app: AppInfo?) {
        viewModelScope.launch {
            try {
                // Cancelar job anterior de notificaciones
                notificationJob?.cancel()

                Log.d(TAG, "Seleccionando app: ${app?.name}")
                _selectedApp.value = app

                app?.let { selectedApp ->
                    // Guardar selección usando Use Case
                    saveSelectedAppUseCase(selectedApp.packageName)
                    
                    Log.d(TAG, "Iniciando observación de notificaciones para: ${selectedApp.name}")
                    
                    // Limpiar notificaciones antiguas
                    launch {
                        try {
                            Log.d(TAG, "Verificando límite de notificaciones para ${selectedApp.name}")
                            notificationRepository.cleanupOldNotifications(selectedApp.packageName)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error limpiando notificaciones antiguas: ${e.message}")
                        }
                    }

                    // Observar notificaciones de la app seleccionada
                    notificationJob = launch {
                        notificationRepository.getNotifications(selectedApp.packageName)
                            .catch { e ->
                                Log.e(TAG, "Error observando notificaciones: ${e.message}")
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
            }
        }
    }

    /**
     * Restaura la última aplicación seleccionada usando el Use Case.
     */
    private suspend fun restoreLastSelectedApp() {
        try {
            Log.d(TAG, "Restaurando última app seleccionada...")
            
            val app = getSelectedAppUseCase()
            
            if (app != null) {
                Log.d(TAG, "App restaurada: ${app.name}")
                selectApp(app)
            } else {
                Log.d(TAG, "No hay app seleccionada previamente")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restaurando app: ${e.message}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        notificationJob?.cancel()
        Log.d(TAG, "ViewModel cleared")
    }
    
    /**
     * Limpia todos los datos del ViewModel cuando el usuario cierra sesión.
     * Cancela jobs, limpia state y borra preferencias.
     */
    fun clearData() {
        viewModelScope.launch {
            try {
                // Cancelar job de notificaciones
                notificationJob?.cancel()
                
                // Limpiar todos los StateFlows
                _selectedApp.value = null
                _notifications.value = emptyList()
                
                // Limpiar la preferencia usando Use Case
                // Pasamos string vacío para limpiar
                saveSelectedAppUseCase("")
                
                Log.d(TAG, "Datos de apps y notificaciones limpiados correctamente al cerrar sesión")
            } catch (e: Exception) {
                Log.e(TAG, "Error al limpiar datos de apps: ${e.message}")
            }
        }
    }
}

/**
 * Factory para crear instancias de AppListViewModel con inyección de dependencias.
 * Refactorizado para usar Use Cases en lugar de dependencias concretas.
 * 
 * Principios aplicados:
 * - Factory Pattern: Centraliza creación del ViewModel
 * - DIP: Inyecta abstracciones (Use Cases)
 */
class AppListViewModelFactory(
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    private val saveSelectedAppUseCase: SaveSelectedAppUseCase,
    private val getSelectedAppUseCase: GetSelectedAppUseCase,
    private val notificationRepository: NotificationRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppListViewModel::class.java)) {
            return AppListViewModel(
                getInstalledAppsUseCase = getInstalledAppsUseCase,
                saveSelectedAppUseCase = saveSelectedAppUseCase,
                getSelectedAppUseCase = getSelectedAppUseCase,
                notificationRepository = notificationRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}