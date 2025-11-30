package com.dynamictecnologies.notificationmanager.data.datasource

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import com.dynamictecnologies.notificationmanager.data.model.AppInfoData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Data Source para acceso al PackageManager del sistema Android.
 * 
 * Encapsula toda la lógica de acceso al PackageManager y transformación de datos.
 * Esto permite:
 * - Aislar dependencias del framework Android
 * - Facilitar testing (mockear este data source)
 * - Centralizar lógica de filtrado y transformación
 * 
 * Principios aplicados:
 * - SRP: Solo maneja acceso a PackageManager
 * - DIP: Puede ser inyectado como dependencia
 * - Clean Architecture: Encapsula detalles del framework
 * 
 * @property packageManager PackageManager del sistema Android
 */
class AppDataSource(
    private val packageManager: PackageManager
) {
    companion object {
        private const val TAG = "AppDataSource"
        private const val ICON_SIZE = 96 // Tamaño del icono en pixeles
    }
    
    /**
     * Obtiene todas las aplicaciones instaladas del usuario.
     * 
     * Filtra:
     * - Solo apps con launcher intent (apps que se pueden abrir)
     * - Solo apps de usuario (excluye apps del sistema)
     * 
     * @return Lista de aplicaciones ordenadas alfabéticamente por nombre
     */
    suspend fun getInstalledApplications(): List<AppInfoData> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Obteniendo aplicaciones instaladas...")
            
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                .asSequence()
                .filter { appInfo ->
                    // Solo apps que tienen launcher intent
                    packageManager.getLaunchIntentForPackage(appInfo.packageName) != null &&
                    // Solo apps de usuario (no del sistema)
                    (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                }
                .map { appInfo ->
                    createAppInfoData(appInfo)
                }
                .sortedBy { it.name }
                .toList()
                .also { apps ->
                    Log.d(TAG, "Cargadas ${apps.size} aplicaciones")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo aplicaciones: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Obtiene información de una aplicación específica por su package name.
     * 
     * @param packageName El package name de la aplicación
     * @return Información de la app o null si no existe o no está instalada
     */
    suspend fun getApplicationInfo(packageName: String): AppInfoData? = withContext(Dispatchers.IO) {
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            createAppInfoData(appInfo)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Aplicación no encontrada: $packageName")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo info de $packageName: ${e.message}", e)
            null
        }
    }
    
    /**
     * Crea un AppInfoData a partir de ApplicationInfo del sistema.
     * 
     * @param appInfo ApplicationInfo del PackageManager
     * @return AppInfoData con nombre, package name e icono
     */
    private fun createAppInfoData(appInfo: ApplicationInfo): AppInfoData {
        val name = appInfo.loadLabel(packageManager).toString()
        val icon = try {
            appInfo.loadIcon(packageManager).toBitmap(
                width = ICON_SIZE,
                height = ICON_SIZE,
                config = Bitmap.Config.ARGB_8888
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error cargando icono para ${appInfo.packageName}: ${e.message}")
            null
        }
        
        return AppInfoData(
            name = name,
            packageName = appInfo.packageName,
            iconBitmap = icon
        )
    }
}
