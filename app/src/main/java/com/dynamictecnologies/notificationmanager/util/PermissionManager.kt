package com.dynamictecnologies.notificationmanager.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log

class PermissionManager(private val context: Context) {
    private val TAG = "PermissionManager"

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isNotificationListenerEnabled(): Boolean {
        try {
            val packageName = context.packageName
            val listeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            return listeners?.contains(packageName) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando permisos: ${e.message}")
            return false
        }
    }

    fun openNotificationSettings() {
        try {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error abriendo configuración: ${e.message}")
        }
    }

    fun closeApp() {
        if (context is Activity) {
            context.finish()
        } else {
            // Si el contexto no es una Activity, forzar el cierre
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
}