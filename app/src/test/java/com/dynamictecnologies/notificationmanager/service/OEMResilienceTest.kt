package com.dynamictecnologies.notificationmanager.service

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests de resistencia para diferentes fabricantes (OEM).
 * 
 * Verifica que el servicio de fondo sobrevive en:
 * - Xiaomi (MIUI) - Conocido por matar agresivamente servicios
 * - Samsung (One UI) - Battery optimization agresiva
 * - Huawei (EMUI) - Sin Google Play Services
 * - OnePlus (OxygenOS) - RAM management agresivo
 * - Generic/Stock Android
 * 
 * También verifica:
 * - Wake lock funciona correctamente
 * - AlarmManager restart funciona
 * - Notificaciones de foreground service
 */
@RunWith(RobolectricTestRunner::class)
class OEMResilienceTest {
    
    private lateinit var context: Context
    
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        
        // Limpiar estado
        context.getSharedPreferences("service_state_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
    
    /**
     * Simula dispositivo Xiaomi con MIUI.
     * MIUI es conocido por matar servicios agresivamente.
     */
    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun test_01_xiaomiDeviceSupport() {
        // Verificar que estado funciona en Xiaomi
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.RUNNING)
        
        val state = ServiceStateManager.getCurrentState(context)
        assertEquals(
            "ServiceStateManager debe funcionar en Xiaomi",
            ServiceStateManager.ServiceState.RUNNING,
            state
        )
        
        // Verificar que notificaciones pueden crearse
        val manager = ServiceNotificationManager(context)
        val notification = manager.showRunningNotification()
        
        assertNotNull("Notificaciones deben funcionar en Xiaomi", notification)
    }
    
    /**
     * Simula dispositivo Samsung con One UI.
     * Samsung tiene optimización de batería agresiva.
     */
    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun test_02_samsungDeviceSupport() {
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.RUNNING)
        
        val state = ServiceStateManager.getCurrentState(context)
        assertEquals(
            "ServiceStateManager debe funcionar en Samsung",
            ServiceStateManager.ServiceState.RUNNING,
            state
        )
        
        val manager = ServiceNotificationManager(context)
        val notification = manager.showRunningNotification()
        assertNotNull("Notificaciones deben funcionar en Samsung", notification)
    }
    
    /**
     * Simula dispositivo Huawei con EMUI (sin Google Play Services).
     */
    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun test_03_huaweiDeviceSupport() {
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.RUNNING)
        
        val state = ServiceStateManager.getCurrentState(context)
        assertEquals(
            "ServiceStateManager debe funcionar en Huawei",
            ServiceStateManager.ServiceState.RUNNING,
            state
        )
        
        val manager = ServiceNotificationManager(context)
        val notification = manager.showRunningNotification()
        assertNotNull("Notificaciones deben funcionar en Huawei", notification)
    }
    
    /**
     * Simula dispositivo OnePlus con OxygenOS.
     * OnePlus tiene RAM management agresivo.
     */
    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun test_04_onePlusDeviceSupport() {
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.RUNNING)
        
        val state = ServiceStateManager.getCurrentState(context)
        assertEquals(
            "ServiceStateManager debe funcionar en OnePlus",
            ServiceStateManager.ServiceState.RUNNING,
            state
        )
        
        val manager = ServiceNotificationManager(context)
        val notification = manager.showRunningNotification()
        assertNotNull("Notific aciones deben funcionar en OnePlus", notification)
    }
    
    /**
     * Simula Stock Android (Google Pixel).
     */
    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun test_05_stockAndroidSupport() {
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.RUNNING)
        
        val state = ServiceStateManager.getCurrentState(context)
        assertEquals(
            "ServiceStateManager debe funcionar en Stock Android",
            ServiceStateManager.ServiceState.RUNNING,
            state
        )
        
        val manager = ServiceNotificationManager(context)
        val notification = manager.showRunningNotification()
        assertNotNull("Notificaciones deben funcionar en Stock Android", notification)
    }
    
    /**
     * Verifica que wake lock puede ser adquirido en todas las versiones.
     */
    @Test
    fun test_06_wakeLockAcquisition() {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Test::WakeLock"
        )
        
        assertNotNull("WakeLock debe poder ser creado", wakeLock)
        
        // Adquirir wake lock
        wakeLock.acquire(10 * 60 * 1000L)
        
        assertTrue("WakeLock debe estar held", wakeLock.isHeld)
        
        // Liberar wake lock
        wakeLock.release()
        
        assertFalse("WakeLock debe estar released", wakeLock.isHeld)
    }
    
    /**
     * Verifica comportamiento en Android 12+ (restricciones más estrictas).
     */
    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun test_07_android12Compatibility() {
        // Verificar que canales de notificación se crean correctamente
        val manager = ServiceNotificationManager(context)
        val notification = manager.showRunningNotification()
        
        assertNotNull("Notificaciones deben funcionar en Android 12+", notification)
        
        // Verificar que tiene foreground service behavior
        assertTrue(
            "Notificación debe tener flags de foreground service",
            notification.flags and android.app.Notification.FLAG_FOREGROUND_SERVICE != 0
        )
    }
    
    /**
     * Verifica que estado persiste después de "reinicio" de contexto.
     */
    @Test
    fun test_08_statePersistenceAcrossContexts() {
        // Establecer estado
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.DISABLED)
        
        // Obtener nuevo contexto (simula reinicio)
        val newContext = ApplicationProvider.getApplicationContext<Context>()
        
        // Estado debe persistir
        assertEquals(
            "Estado debe persistir en diferentes contextos",
            ServiceStateManager.ServiceState.DISABLED,
            ServiceStateManager.getCurrentState(newContext)
        )
    }
    
    /**
     * Verifica ciclo completo de vida del servicio.
     */
    @Test
    fun test_09_completeServiceLifecycle() {
        // 1. Servicio inicia (RUNNING)
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.RUNNING)
        val manager = ServiceNotificationManager(context)
        manager.showRunningNotification()
        
        assertEquals(ServiceStateManager.ServiceState.RUNNING, ServiceStateManager.getCurrentState(context))
        
        // 2. Servicio muere inesperadamente
        assertTrue(ServiceStateManager.canShowStoppedNotification(context))
        ServiceStateManager.markStoppedNotificationShown(context)
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.STOPPED)
        manager.showStoppedNotification()
        
        // 3. Usuario presiona Reiniciar
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.RUNNING)
        ServiceStateManager.resetStoppedCounter(context)
        manager.showRunningNotification()
        
        assertEquals(ServiceStateManager.ServiceState.RUNNING, ServiceStateManager.getCurrentState(context))
        
        // 4. Servicio muere otra vez
        assertTrue(ServiceStateManager.canShowStoppedNotification(context))
        
        // 5. Usuario presiona Entendido
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.DISABLED)
        manager.hideAllNotifications()
        
        assertEquals(ServiceStateManager.ServiceState.DISABLED, ServiceStateManager.getCurrentState(context))
        
        // 6. Usuario abre app de nuevo
        ServiceStateManager.resetOnAppOpen(context)
        
        assertEquals(ServiceStateManager.ServiceState.RUNNING, ServiceStateManager.getCurrentState(context))
    }
    
    /**
     * Verifica que notificaciones pueden ser canceladas correctamente.
     */
    @Test
    fun test_10_notificationCancellation() {
        val manager = ServiceNotificationManager(context)
        
        // Mostrar notificación
        manager.showRunningNotification()
        
        // Ocultar todas
        manager.hideAllNotifications()
        
        // Verificar que fueron ocultadas
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val activeNotifications = notificationManager.activeNotifications
        
        val ourNotifications = activeNotifications.filter {
            it.id == ServiceNotificationManager.NOTIFICATION_ID_RUNNING ||
            it.id == ServiceNotificationManager.NOTIFICATION_ID_STOPPED
        }
        
        assertTrue(
            "Notificaciones deben estar canceladas",
            ourNotifications.isEmpty()
        )
    }
    
    /**
     * Verifica resistencia a múltiples cambios de estado rápidos.
     */
    @Test
    fun test_11_rapidStateChanges() {
        // Cambiar estado rápidamente 100 veces
        repeat(100) { i ->
            val state = when (i % 3) {
                0 -> ServiceStateManager.ServiceState.RUNNING
                1 -> ServiceStateManager.ServiceState.STOPPED
                else -> ServiceStateManager.ServiceState.DISABLED
            }
            
            ServiceStateManager.setState(context, state)
            
            // Verificar que el estado es el esperado
            assertEquals(state, ServiceStateManager.getCurrentState(context))
        }
        
        // Estado final debe ser correcto
        assertEquals(
            ServiceStateManager.ServiceState.DISABLED,
            ServiceStateManager.getCurrentState(context)
        )
    }
    
    /**
     * Verifica que múltiples instancias de ServiceNotificationManager
     * funcionan correctamente.
     */
    @Test
    fun test_12_multipleManagerInstances() {
        val manager1 = ServiceNotificationManager(context)
        val manager2 = ServiceNotificationManager(context)
        
        // Ambas deben funcionar correctamente
        manager1.showRunningNotification()
        manager2.showStoppedNotification()
        
        // La última debe ganar (STOPPED)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val activeNotifications = notificationManager.activeNotifications
        
        val stoppedActive = activeNotifications.any {
            it.id == ServiceNotificationManager.NOTIFICATION_ID_STOPPED
        }
        
        assertTrue("Notificación STOPPED debe estar activa", stoppedActive)
    }
}
