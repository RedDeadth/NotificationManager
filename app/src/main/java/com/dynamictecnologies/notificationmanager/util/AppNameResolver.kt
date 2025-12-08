package com.dynamictecnologies.notificationmanager.util

import android.content.Context
import android.util.Log

/**
 * Utilidad para obtener nombres de aplicaciones desde packageName.
 * 
 * Responsabilidad única: Traducir packageName a nombre legible.
 * 
 * Principios aplicados:
 * - SRP: Solo obtención de nombres de app
 * - Stateless: Sin estado interno
 * - Cached (futuro): Podría agregar cache para eficiencia
 */
class AppNameResolver(private val context: Context) {
    
    /**
     * Obtiene el nombre legible de una app desde su packageName
     */
    fun getAppName(packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo nombre para $packageName: ${e.message}")
            packageName  // Fallback al packageName
        }
    }
    
    /**
     * Verifica si un appName corresponde a un packageName
     */
    fun matchesPackage(appName: String, packageName: String): Boolean {
        // Comparación directa
        if (appName == packageName) return true
        
        // Comparación con nombre resuelto
        val resolvedName = getAppName(packageName)
        return appName == resolvedName
    }
    
    companion object {
        private const val TAG = "AppNameResolver"
    }
}
