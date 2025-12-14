package com.dynamictecnologies.notificationmanager.domain.usecases.app

import com.dynamictecnologies.notificationmanager.domain.repositories.PreferencesRepository

/**
 * Caso de uso para guardar la aplicación seleccionada.
 * 
 * - Clean Architecture: Lógica de negocio en dominio
 * 
 * @param preferencesRepository Repository para gestionar preferencias
 */
class SaveSelectedAppUseCase(
    private val preferencesRepository: PreferencesRepository
) {
    /**
     * Guarda el package name de la aplicación seleccionada.
     * 
     * @param packageName El package name de la app a guardar
     */
    operator fun invoke(packageName: String) {
        preferencesRepository.saveSelectedApp(packageName)
    }
}
