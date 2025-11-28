package com.dynamictecnologies.notificationmanager.util.logging

import android.util.Log

/**
 * Implementación de Logger usando android.util.Log.
 * 
 * Principios aplicados:
 * - SRP: Solo implementa logging usando Android Log
 * - DIP: Implementa la abstracción Logger
 */
class AndroidLogger : Logger {
    override fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun i(tag: String, message: String) {
        Log.i(tag, message)
    }

    override fun w(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.w(tag, message, throwable)
        } else {
            Log.w(tag, message)
        }
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
}
