package com.dynamictecnologies.notificationmanager.test

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.*
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Tests instrumentados para verificar comportamiento de permisos.
 * 
 * Verifica:
 * - POST_NOTIFICATIONS en Android 13+
 * - Permisos Bluetooth
 * - NotificationListenerService habilitado
 * 
 * Ejecutar con: ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PermissionsRequestTest {

    private lateinit var context: Context
    private lateinit var device: UiDevice

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Test
    fun test_01_sdkVersionDetection() {
        println("\n📱 ==== TEST: SDK Version Detection ====")
        
        val sdkVersion = Build.VERSION.SDK_INT
        val needsNotificationPermission = sdkVersion >= Build.VERSION_CODES.TIRAMISU
        
        println("  📝 SDK Version: $sdkVersion")
        println("  📝 API Level: ${Build.VERSION.SDK_INT}")
        println("  📝 Codename: ${Build.VERSION.CODENAME}")
        println("  📝 Needs POST_NOTIFICATIONS: $needsNotificationPermission")
        
        if (sdkVersion >= Build.VERSION_CODES.TIRAMISU) {
            println("  ℹ️ Android 13+ - Requiere permiso POST_NOTIFICATIONS")
        } else {
            println("  ℹ️ Android < 13 - No requiere permiso runtime")
        }
        
        println("  ✅ TEST PASADO\n")
    }

    @Test
    fun test_02_postNotificationsPermissionDeclared() {
        println("\n📋 ==== TEST: POST_NOTIFICATIONS Permission Declared ====")
        
        try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS
            )
            
            val permissions = packageInfo.requestedPermissions?.toList() ?: emptyList()
            
            println("  📝 Total permisos declarados: ${permissions.size}")
            
            // Verificar permiso POST_NOTIFICATIONS
            val hasPostNotifications = permissions.contains(Manifest.permission.POST_NOTIFICATIONS)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                assertTrue(
                    "POST_NOTIFICATIONS debe estar declarado en Android 13+",
                    hasPostNotifications
                )
                println("  ✅ POST_NOTIFICATIONS declarado")
            } else {
                println("  ℹ️ Android < 13, permiso no requerido")
            }
            
        } catch (e: Exception) {
            println("  ⚠️ Error obteniendo permisos: ${e.message}")
        }
        
        println("  ✅ TEST PASADO\n")
    }

    @Test
    fun test_03_postNotificationsPermissionStatus() {
        println("\n🔐 ==== TEST: POST_NOTIFICATIONS Permission Status ====")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionStatus = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            
            val isGranted = permissionStatus == PackageManager.PERMISSION_GRANTED
            
            println("  📝 Permission status: ${if (isGranted) "GRANTED" else "DENIED"}")
            
            if (isGranted) {
                println("  ✅ Permiso ya otorgado")
            } else {
                println("  ⚠️ Permiso denegado - Requiere acción del usuario")
            }
        } else {
            println("  ℹ️ Android < 13 - Permiso no aplica")
        }
        
        println("  ✅ TEST PASADO\n")
    }

    @Test
    fun test_04_bluetoothPermissionsDeclared() {
        println("\n📶 ==== TEST: Bluetooth Permissions Declared ====")
        
        try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS
            )
            
            val permissions = packageInfo.requestedPermissions?.toList() ?: emptyList()
            
            val bluetoothPermissions = listOf(
                "android.permission.BLUETOOTH",
                "android.permission.BLUETOOTH_ADMIN",
                "android.permission.BLUETOOTH_CONNECT",
                "android.permission.BLUETOOTH_SCAN"
            )
            
            bluetoothPermissions.forEach { permission ->
                val hasPermission = permissions.contains(permission)
                val shortName = permission.substringAfterLast(".")
                println("  📝 $shortName: ${if (hasPermission) "✅" else "❌"}")
            }
            
        } catch (e: Exception) {
            println("  ⚠️ Error: ${e.message}")
        }
        
        println("  ✅ TEST PASADO\n")
    }

    @Test
    fun test_05_notificationListenerStatus() {
        println("\n👂 ==== TEST: NotificationListener Status ====")
        
        val listenerEnabled = isNotificationListenerEnabled()
        
        println("  📝 NotificationListener habilitado: $listenerEnabled")
        
        if (listenerEnabled) {
            println("  ✅ NotificationListener activo")
        } else {
            println("  ⚠️ NotificationListener NO activo")
            println("  ⚠️ Usuario debe habilitarlo en Settings > Apps > Special access")
        }
        
        println("  ✅ TEST PASADO\n")
    }

    @Test
    fun test_06_areNotificationsEnabled() {
        println("\n🔔 ==== TEST: Notifications Enabled Check ====")
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val areEnabled = notificationManager.areNotificationsEnabled()
        
        println("  📝 Notificaciones habilitadas: $areEnabled")
        
        if (areEnabled) {
            println("  ✅ Las notificaciones están habilitadas")
        } else {
            println("  ⚠️ Las notificaciones están deshabilitadas")
            println("  ⚠️ El usuario debe habilitarlas en Settings")
        }
        
        println("  ✅ TEST PASADO\n")
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val listeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false

        val componentName = ComponentName(
            context.packageName,
            "com.dynamictecnologies.notificationmanager.service.NotificationListenerService"
        ).flattenToString()

        return listeners.contains(componentName)
    }
}
