package com.dynamictecnologies.notificationmanager.domain.repositories

import com.dynamictecnologies.notificationmanager.data.model.AppInfo

/**
 * Repository abstraction para gestión de aplicaciones instaladas.
 * 
 * Principios aplicados:
 * - DIP: Inversión de dependencias, el ViewModel dependerá de esta abstracción
 * - ISP: Interfaz específica para gestión de apps
 * - Clean Architecture: Define el contrato en la capa de dominio
 */
interface AppRepository {
    /**
     * Obtiene la lista de aplicaciones instaladas en el dispositivo.
     * Solo incluye apps de usuario (no del sistema) que tengan launcher activity.
     * 
     * @return Result con la lista de apps o error
     */
    suspend fun getInstalledApps(): Result<List<AppInfo>>
    
    /**
     * Obtiene información de una aplicación específica por su package name.
     * 
     * @param packageName El nombre del paquete de la aplicación
     * @return Result con la información de la app o error si no existe
     */
    suspend fun getAppByPackageName(packageName: String): Result<AppInfo>
}
