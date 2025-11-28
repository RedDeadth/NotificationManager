package com.dynamictecnologies.notificationmanager.util.logging

/**
 * Interfaz de logging para abstraer la implementación del sistema de logs.
 * 
 * Principios aplicados:
 * - DIP: Abstracción que permite diferentes implementaciones
 * - ISP: Interfaz simple y específica para logging
 */
interface Logger {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String, throwable: Throwable? = null)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}
