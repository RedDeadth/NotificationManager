package com.dynamictecnologies.notificationmanager.data.mapper

import androidx.compose.ui.graphics.asImageBitmap
import com.dynamictecnologies.notificationmanager.data.model.AppInfo
import com.dynamictecnologies.notificationmanager.data.model.AppInfoData

/**
 * Mapper para transformar entre la capa de datos y la capa de dominio/presentación.
 * 
 * - Clean Architecture: Separa modelos de datos y dominio
 */
object AppInfoMapper {
    /**
     * Transforma AppInfoData (capa de datos) a AppInfo (dominio/presentación).
     * 
     * @param data Modelo de la capa de datos
     * @return Modelo para la capa de dominio/presentación
     */
    fun toDomain(data: AppInfoData): AppInfo {
        return AppInfo(
            name = data.name,
            packageName = data.packageName,
            icon = data.iconBitmap?.asImageBitmap()
        )
    }
}
