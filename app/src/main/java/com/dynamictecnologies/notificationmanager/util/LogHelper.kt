package com.dynamictecnologies.notificationmanager.util

import android.content.Context
import android.util.Log

/**
 * Helper para logging consistente en toda la app.
 * 
 * Principios aplicados:
 * - DRY: Centraliza logging logic
 * - SRP: Solo responsable de logging
 * - Strategy Pattern: Puede implementarse diferentes loggers (Crashlytics, etc.)
 */
object LogHelper {
    
    private const val TAG_PREFIX = "NM_"
    
    /**
     * Log de debug.
     */
    fun d(tag: String, message: String) {
        Log.d("$TAG_PREFIX$tag", message)
    }
    
    /**
     * Log de información.
     */
    fun i(tag: String, message: String) {
        Log.i("$TAG_PREFIX$tag", message)
    }
    
    /**
     * Log de warning.
     */
    fun w(tag: String, message: String) {
        Log.w("$TAG_PREFIX$tag", message)
    }
    
    /**
     * Log de error.
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e("$TAG_PREFIX$tag", message, throwable)
        } else {
            Log.e("$TAG_PREFIX$tag", message)
        }
    }
    
    /**
     * Log de error con solo excepción.
     */
    fun e(tag: String, throwable: Throwable) {
        Log.e("$TAG_PREFIX$tag", "Error: ${throwable.message}", throwable)
    }
}
