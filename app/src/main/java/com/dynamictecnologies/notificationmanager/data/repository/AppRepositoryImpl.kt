package com.dynamictecnologies.notificationmanager.data.repository

import android.util.Log
import com.dynamictecnologies.notificationmanager.data.datasource.AppDataSource
import com.dynamictecnologies.notificationmanager.data.mapper.AppInfoMapper
import com.dynamictecnologies.notificationmanager.data.model.AppInfo
import com.dynamictecnologies.notificationmanager.domain.repositories.AppRepository

/**
 * Implementación del repositorio de aplicaciones.
 * 
 * Coordina entre el data source (PackageManager) y la capa de dominio,
 * transformando los datos según sea necesario.
 * 
 * - Clean Architecture: Implementación en capa de datos, expone entidades de dominio
 * 
 * @property appDataSource Data source para acceder a aplicaciones del sistema
 */
class AppRepositoryImpl(
    private val appDataSource: AppDataSource
) : AppRepository {
    
    companion object {
        private const val TAG = "AppRepositoryImpl"
    }
    
    override suspend fun getInstalledApps(): Result<List<AppInfo>> {
        return try {
            val appsData = appDataSource.getInstalledApplications()
            val apps = appsData.map { AppInfoMapper.toDomain(it) }
            Result.success(apps)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getAppByPackageName(packageName: String): Result<AppInfo> {
        return try {
            val appData = appDataSource.getApplicationInfo(packageName)
                ?: return Result.failure(Exception("Aplicación no encontrada: $packageName"))
            
            val app = AppInfoMapper.toDomain(appData)
            Result.success(app)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
