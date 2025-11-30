package com.dynamictecnologies.notificationmanager.domain.repositories

/**
 * Repository abstraction para gestión de preferencias de la aplicación.
 * 
 * Principios aplicados:
 * - SRP: Solo maneja preferencias relacionadas con apps
 * - DIP: Abstracción para almacenamiento de preferencias
 * - ISP: Interfaz segregada, solo operaciones de app selection
 * - Clean Architecture: Contrato definido en dominio
 */
interface PreferencesRepository {
    /**
     * Guarda el package name de la aplicación seleccionada.
     * 
     * @param packageName El package name de la app a guardar
     */
    fun saveSelectedApp(packageName: String)
    
    /**
     * Obtiene el package name de la última aplicación seleccionada.
     * 
     * @return El package name guardado o null si no hay ninguno
     */
    fun getSelectedApp(): String?
    
    /**
     * Limpia la selección de aplicación guardada.
     * Útil al cerrar sesión o resetear preferencias.
     */
    fun clearSelectedApp()
}
