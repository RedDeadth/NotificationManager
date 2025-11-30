package com.dynamictecnologies.notificationmanager.domain.usecases.app

import com.dynamictecnologies.notificationmanager.data.model.AppInfo
import com.dynamictecnologies.notificationmanager.domain.repositories.AppRepository

/**
 * Caso de uso para obtener las aplicaciones instaladas en el dispositivo.
 * 
 * Principios aplicados:
 * - SRP: Una sola responsabilidad - obtener apps instaladas
 * - DIP: Depende de la abstracción AppRepository
 * - Clean Architecture: Encapsula lógica de negocio en la capa de dominio
 * 
 * @param appRepository Repository para acceder a las aplicaciones
 */
class GetInstalledAppsUseCase(
    private val appRepository: AppRepository
) {
    /**
     * Obtiene la lista de aplicaciones instaladas.
     * 
     * @return Result con la lista de apps o error
     */
    suspend operator fun invoke(): Result<List<AppInfo>> {
        return appRepository.getInstalledApps()
    }
}
