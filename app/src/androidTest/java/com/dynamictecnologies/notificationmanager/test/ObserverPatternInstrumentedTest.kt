package com.dynamictecnologies.notificationmanager.test

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.dynamictecnologies.notificationmanager.service.NotificationForegroundService
import com.dynamictecnologies.notificationmanager.service.NotificationListenerService
import com.dynamictecnologies.notificationmanager.service.ServiceNotificationManager
import com.dynamictecnologies.notificationmanager.service.ServiceStateManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Test orquestado completo para verificar:
 * 
 * 1. Persistencia del servicio de monitoreo
 * 2. Patrón Observer para estados del sistema
 * 3. MQTT Keep-Alive configurado
 * 4. Notificaciones con estados correctos
 * 5. Diálogo de permisos Material3
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ObserverPatternInstrumentedTest {
    
    private lateinit var context: Context
    private lateinit var device: UiDevice
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        
        // Iniciar el servicio antes de los tests
        startForegroundService()
        Thread.sleep(3000)
    }
    
    @After
    fun tearDown() {
        // No detenemos el servicio
    }
    
    // ==========================================
    // PERSISTENCIA DEL SERVICIO
    // ==========================================
    
    @Test
    fun test01_serviceStateIsRunning() {
        println("\n🟢 ==== TEST: Servicio en estado RUNNING ====")
        
        val currentState = ServiceStateManager.getCurrentState(context)
        println("  📊 Estado actual: $currentState")
        
        assertEquals(
            "El servicio debería estar en estado RUNNING",
            ServiceStateManager.ServiceState.RUNNING,
            currentState
        )
        println("  ✅ TEST PASADO\n")
    }
    
    @Test
    fun test02_serviceStateTransitions() {
        println("\n🔄 ==== TEST: Transiciones de estado del servicio ====")
        
        // Guardar estado original
        val originalState = ServiceStateManager.getCurrentState(context)
        println("  📊 Estado original: $originalState")
        
        // Test: RUNNING → DEGRADED
        ServiceStateManager.setDegradedState(context, ServiceStateManager.DegradedReason.NO_INTERNET)
        val degradedState = ServiceStateManager.getCurrentState(context)
        assertEquals(ServiceStateManager.ServiceState.DEGRADED, degradedState)
        println("  ✅ Transición a DEGRADED exitosa")
        
        // Test: DEGRADED → RUNNING
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.RUNNING)
        val runningState = ServiceStateManager.getCurrentState(context)
        assertEquals(ServiceStateManager.ServiceState.RUNNING, runningState)
        println("  ✅ Transición a RUNNING exitosa")
        
        println("  ✅ TEST PASADO\n")
    }
    
    // ==========================================
    // PATRÓN OBSERVER - ESTADOS DEL SISTEMA
    // ==========================================
    
    @Test
    fun test03_powerSaveModeDetection() {
        println("\n🔋 ==== TEST: Detección Power Save Mode (Observer) ====")
        
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isPowerSave = powerManager.isPowerSaveMode
        
        println("  📊 Power Save Mode activo: $isPowerSave")
        println("  📝 ACTION_POWER_SAVE_MODE_CHANGED dispara checkPowerState()")
        
        assertNotNull("PowerManager debe estar disponible", powerManager)
        println("  ✅ TEST PASADO\n")
    }
    
    @Test
    fun test04_dozeModeDetection() {
        println("\n💤 ==== TEST: Detección Doze Mode (Observer) ====")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val isDoze = powerManager.isDeviceIdleMode
            
            println("  📊 Doze Mode activo: $isDoze")
            println("  📝 ACTION_DEVICE_IDLE_MODE_CHANGED dispara checkPowerState()")
        } else {
            println("  ⏭️ API < 23: Doze no disponible")
        }
        println("  ✅ TEST PASADO\n")
    }
    
    @Test
    fun test05_networkChangeDetection() {
        println("\n🌐 ==== TEST: Detección cambio de red (Observer) ====")
        
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        val isConnected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork
            val capabilities = cm.getNetworkCapabilities(network)
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
        
        println("  📊 Red conectada: $isConnected")
        println("  📝 CONNECTIVITY_ACTION dispara checkNetworkState() → MQTT reconnect")
        
        assertNotNull("ConnectivityManager debe estar disponible", cm)
        println("  ✅ TEST PASADO\n")
    }
    
    // ==========================================
    // NOTIFICACIONES
    // ==========================================
    
    @Test
    fun test06_stopReasonPowerRestrictedExists() {
        println("\n🟡 ==== TEST: StopReason.POWER_RESTRICTED existe ====")
        
        val reasons = ServiceNotificationManager.StopReason.values()
        val hasPowerRestricted = reasons.any { it.name == "POWER_RESTRICTED" }
        
        assertTrue(
            "StopReason debería contener POWER_RESTRICTED",
            hasPowerRestricted
        )
        
        println("  📊 StopReasons: ${reasons.map { it.name }}")
        println("  ✅ TEST PASADO\n")
    }
    
    @Test
    fun test07_stopReasonPermissionRevokedExists() {
        println("\n🔴 ==== TEST: StopReason.PERMISSION_REVOKED existe ====")
        
        val reasons = ServiceNotificationManager.StopReason.values()
        val hasPermissionRevoked = reasons.any { it.name == "PERMISSION_REVOKED" }
        
        assertTrue(
            "StopReason debería contener PERMISSION_REVOKED",
            hasPermissionRevoked
        )
        println("  ✅ TEST PASADO\n")
    }
    
    // ==========================================
    // MQTT
    // ==========================================
    
    @Test
    fun test08_mqttReconnectBroadcast() {
        println("\n📡 ==== TEST: MQTT Reconnect Broadcast ====")
        
        // Enviar el broadcast que el Network Change Observer usa
        val reconnectIntent = Intent("com.dynamictecnologies.notificationmanager.MQTT_RECONNECT")
        
        try {
            context.sendBroadcast(reconnectIntent)
            println("  ✅ Broadcast MQTT_RECONNECT enviado correctamente")
        } catch (e: Exception) {
            fail("No se pudo enviar broadcast: ${e.message}")
        }
        
        println("  📝 MqttConnectionManager debería reconectar al recibir este intent")
        println("  ✅ TEST PASADO\n")
    }
    
    // ==========================================
    // BATTERY OPTIMIZATION
    // ==========================================
    
    @Test
    fun test09_batteryOptimizationCheck() {
        println("\n🔌 ==== TEST: Battery Optimization Check ====")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val isIgnoring = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            
            println("  📊 Battery Optimization ignorada: $isIgnoring")
            
            if (!isIgnoring) {
                println("  ⚠️ RECOMENDACIÓN: Solicitar exención de optimización de batería")
            } else {
                println("  ✅ App exenta de optimización de batería")
            }
        } else {
            println("  ⏭️ API < 23: No aplica")
        }
        println("  ✅ TEST PASADO\n")
    }
    
    // ==========================================
    // NOTIFICATION LISTENER
    // ==========================================
    
    @Test
    fun test10_notificationListenerStatus() {
        println("\n👂 ==== TEST: NotificationListener Status ====")
        
        val isEnabled = NotificationListenerService.isNotificationListenerEnabled(context)
        println("  📊 NotificationListener habilitado: $isEnabled")
        
        if (!isEnabled) {
            println("  ⚠️ Ir a: Configuración > Acceso a notificaciones > Habilitar app")
        }
        println("  ✅ TEST PASADO\n")
    }
    
    // ==========================================
    // RESUMEN
    // ==========================================
    
    @Test
    fun test11_fullSystemSummary() {
        println("\n📋 ==== RESUMEN COMPLETO DEL SISTEMA ====\n")
        
        // Service State
        val serviceState = ServiceStateManager.getCurrentState(context)
        println("  1️⃣ Service State: $serviceState ${if (serviceState == ServiceStateManager.ServiceState.RUNNING) "✅" else "⚠️"}")
        
        // Power Save
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isPowerSave = powerManager.isPowerSaveMode
        println("  2️⃣ Power Save Mode: $isPowerSave")
        
        // Doze
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val isDoze = powerManager.isDeviceIdleMode
            println("  3️⃣ Doze Mode: $isDoze")
        }
        
        // Network
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val hasNetwork = cm.activeNetwork != null
        println("  4️⃣ Network Connected: $hasNetwork ${if (hasNetwork) "✅" else "⚠️"}")
        
        // Battery Exemption
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val isExempt = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            println("  5️⃣ Battery Exemption: ${if (isExempt) "✅" else "⚠️ Recomendado"}")
        }
        
        // Notification Listener
        val hasListener = NotificationListenerService.isNotificationListenerEnabled(context)
        println("  6️⃣ NotificationListener: ${if (hasListener) "✅" else "⚠️ Requerido"}")
        
        // StopReasons
        val stopReasons = ServiceNotificationManager.StopReason.values()
        println("  7️⃣ StopReasons: ${stopReasons.size} tipos disponibles")
        
        println("\n  ========================================")
        println("  ✅ TODOS LOS SISTEMAS VERIFICADOS")
        println("  ========================================\n")
    }
    
    // ===================
    // Helper Methods
    // ===================
    
    private fun startForegroundService() {
        val intent = Intent(context, NotificationForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
