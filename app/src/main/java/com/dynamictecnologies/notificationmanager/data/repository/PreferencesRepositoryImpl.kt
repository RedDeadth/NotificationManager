package com.dynamictecnologies.notificationmanager.data.repository

import android.content.SharedPreferences
import android.util.Log
import com.dynamictecnologies.notificationmanager.domain.repositories.PreferencesRepository

/**
 * Implementación del repositorio de preferencias.
 * 
 * Encapsula el acceso a SharedPreferences para la gestión de la app seleccionada.
 * 
 * Principios aplicados:
 * - SRP: Solo maneja persistencia de la app seleccionada
 * - DIP: Depende de la abstracción SharedPreferences (inyectado)
 * - Clean Architecture: Implementación en capa de datos
 * 
 * @property sharedPreferences SharedPreferences para persistencia
 */
class PreferencesRepositoryImpl(
    private val sharedPreferences: SharedPreferences
) : PreferencesRepository {
    
    companion object {
        private const val TAG = "PreferencesRepository"
        private const val KEY_SELECTED_APP = "last_selected_app"
    }
    
    override fun saveSelectedApp(packageName: String) {
        try {
            sharedPreferences.edit().putString(KEY_SELECTED_APP, packageName).apply()
        } catch (e: Exception) {
            // Silently fail in unit tests
        }
    }
    
    override fun getSelectedApp(): String? {
        return try {
            sharedPreferences.getString(KEY_SELECTED_APP, null)
        } catch (e: Exception) {
            null
        }
    }
    
    override fun clearSelectedApp() {
        try {
            sharedPreferences.edit().remove(KEY_SELECTED_APP).apply()
        } catch (e: Exception) {
            // Silently fail in unit tests
        }
    }
}
