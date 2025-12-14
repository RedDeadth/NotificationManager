package com.dynamictecnologies.notificationmanager.domain.usecases.app

import com.dynamictecnologies.notificationmanager.data.model.AppInfo
import com.dynamictecnologies.notificationmanager.domain.repositories.AppRepository
import com.dynamictecnologies.notificationmanager.domain.repositories.PreferencesRepository

/**
 * Caso de uso para obtener la última aplicación seleccionada.
 * 
 * Este Use Case coordina dos repositories:
 * 1. PreferencesRepository: Para obtener el package name guardado
 * 2. AppRepository: Para obtener la información completa de la app
 * 
 * - Clean Architecture: Orquesta operaciones en la capa de dominio
 * 
 * @param preferencesRepository Repository para obtener preferencias
 * @param appRepository Repository para obtener información de apps
 */
class GetSelectedAppUseCase(
    private val preferencesRepository: PreferencesRepository,
    private val appRepository: AppRepository
) {
    /**
     * Obtiene la aplicación seleccionada previamente.
     * 
     * @return La información de la app seleccionada o null si no hay ninguna guardada
     *         o si la app ya no está instalada
     */
    suspend operator fun invoke(): AppInfo? {
        // Paso 1: Obtener el package name guardado
        val packageName = preferencesRepository.getSelectedApp() ?: return null
        
        // Paso 2: Obtener la información completa de la app
        return appRepository.getAppByPackageName(packageName).getOrNull()
    }
}
