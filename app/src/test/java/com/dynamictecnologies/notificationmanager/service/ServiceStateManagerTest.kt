package com.dynamictecnologies.notificationmanager.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests unitarios para ServiceStateManager.
 * 
 * Verifica:
 * - FSM de 3 estados (RUNNING/STOPPED/DISABLED)
 * - Persistencia de estados
 * - Lógica de mostrar notificaciones
 * - Reset de contador
 * - Reset on app open
 */
@RunWith(AndroidJUnit4::class)
class ServiceStateManagerTest {
    
    private lateinit var context: Context
    
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Limpiar SharedPreferences antes de cada test
        context.getSharedPreferences("service_state_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
    
    @After
    fun tearDown() {
        // Limpiar después de cada test
        context.getSharedPreferences("service_state_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
    
    @Test
    fun test_01_defaultStateIsRunning() {
        // Cuando no hay estado guardado, debe retornar RUNNING
        val state = ServiceStateManager.getCurrentState(context)
        
        assertEquals(
            "Estado por defecto debe ser RUNNING",
            ServiceStateManager.ServiceState.RUNNING,
            state
        )
    }
    
    @Test
    fun test_02_setStateRunning() {
        // Establecer estado RUNNING
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.RUNNING)
        
        val state = ServiceStateManager.getCurrentState(context)
        assertEquals(ServiceStateManager.ServiceState.RUNNING, state)
    }
    
    @Test
    fun test_03_setStateStopped() {
        // Establecer estado STOPPED
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.STOPPED)
        
        val state = ServiceStateManager.getCurrentState(context)
        assertEquals(ServiceStateManager.ServiceState.STOPPED, state)
    }
    
    @Test
    fun test_04_setStateDisabled() {
        // Establecer estado DISABLED
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.DISABLED)
        
        val state = ServiceStateManager.getCurrentState(context)
        assertEquals(ServiceStateManager.ServiceState.DISABLED, state)
    }
    
    @Test
    fun test_05_stateTransition_runningToStopped() {
        // Transición RUNNING → STOPPED
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.RUNNING)
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.STOPPED)
        
        val state = ServiceStateManager.getCurrentState(context)
        assertEquals(ServiceStateManager.ServiceState.STOPPED, state)
    }
    
    @Test
    fun test_06_stateTransition_stoppedToDisabled() {
        // Transición STOPPED → DISABLED
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.STOPPED)
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.DISABLED)
        
        val state = ServiceStateManager.getCurrentState(context)
        assertEquals(ServiceStateManager.ServiceState.DISABLED, state)
    }
    
    @Test
    fun test_07_stateTransition_stoppedToRunning() {
        // Transición STOPPED → RUNNING (reinicio)
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.STOPPED)
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.RUNNING)
        
        val state = ServiceStateManager.getCurrentState(context)
        assertEquals(ServiceStateManager.ServiceState.RUNNING, state)
    }
    
    @Test
    fun test_08_canShowStoppedNotification_whenRunningAndNotShown() {
        // Estado RUNNING + no mostrada = true
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.RUNNING)
        
        val canShow = ServiceStateManager.canShowStoppedNotification(context)
        
        assertTrue(
            "Debe poder mostrar notificación STOPPED cuando estado es RUNNING y no se ha mostrado",
            canShow
        )
    }
    
    @Test
    fun test_09_canShowStoppedNotification_whenRunningButAlreadyShown() {
        // Estado RUNNING + ya mostrada = false
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.RUNNING)
        ServiceStateManager.markStoppedNotificationShown(context)
        
        val canShow = ServiceStateManager.canShowStoppedNotification(context)
        
        assertFalse(
            "NO debe mostrar notificación STOPPED si ya fue mostrada en esta sesión",
            canShow
        )
    }
    
    @Test
    fun test_10_canShowStoppedNotification_whenStopped() {
        // Estado STOPPED = false
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.STOPPED)
        
        val canShow = ServiceStateManager.canShowStoppedNotification(context)
        
        assertFalse(
            "NO debe mostrar notificación STOPPED cuando estado ya es STOPPED",
            canShow
        )
    }
    
    @Test
    fun test_11_canShowStoppedNotification_whenDisabled() {
        // Estado DISABLED = false
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.DISABLED)
        
        val canShow = ServiceStateManager.canShowStoppedNotification(context)
        
        assertFalse(
            "NO debe mostrar notificación STOPPED cuando estado es DISABLED",
            canShow
        )
    }
    
    @Test
    fun test_12_markStoppedNotificationShown() {
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.RUNNING)
        
        // Antes de marcar
        assertTrue(ServiceStateManager.canShowStoppedNotification(context))
        
        // Marcar como mostrada
        ServiceStateManager.markStoppedNotificationShown(context)
        
        // Después de marcar
        assertFalse(ServiceStateManager.canShowStoppedNotification(context))
    }
    
    @Test
    fun test_13_stoppedCounterIncrement() {
        // Contador debe incrementar
        assertEquals(0, ServiceStateManager.getStoppedCount(context))
        
        ServiceStateManager.markStoppedNotificationShown(context)
        assertEquals(1, ServiceStateManager.getStoppedCount(context))
        
        ServiceStateManager.resetStoppedCounter(context)
        ServiceStateManager.markStoppedNotificationShown(context)
        assertEquals(2, ServiceStateManager.getStoppedCount(context))
    }
    
    @Test
    fun test_14_resetStoppedCounter() {
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.RUNNING)
        ServiceStateManager.markStoppedNotificationShown(context)
        
        // Después de marcar, no puede mostrar
        assertFalse(ServiceStateManager.canShowStoppedNotification(context))
        
        // Resetear contador
        ServiceStateManager.resetStoppedCounter(context)
        
        // Ahora puede mostrar otra vez
        assertTrue(ServiceStateManager.canShowStoppedNotification(context))
    }
    
    @Test
    fun test_15_resetOnAppOpen_fromDisabledToRunning() {
        // Simular que usuario eligió "Entendido" (DISABLED)
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.DISABLED)
        assertEquals(ServiceStateManager.ServiceState.DISABLED, ServiceStateManager.getCurrentState(context))
        
        // Usuario abre app de nuevo
        ServiceStateManager.resetOnAppOpen(context)
        
        // Estado debe cambiar a RUNNING
        assertEquals(
            "resetOnAppOpen() debe cambiar DISABLED → RUNNING",
            ServiceStateManager.ServiceState.RUNNING,
            ServiceStateManager.getCurrentState(context)
        )
    }
    
    @Test
    fun test_16_resetOnAppOpen_fromStoppedStaysStopped() {
        // Si estado es STOPPED, debe mantenerse
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.STOPPED)
        ServiceStateManager.resetOnAppOpen(context)
        
        // STOPPED no cambia automáticamente
        assertEquals(
            "STOPPED debe mantenerse (no es DISABLED)",
            ServiceStateManager.ServiceState.STOPPED,
            ServiceStateManager.getCurrentState(context)
        )
    }
    
    @Test
    fun test_17_resetOnAppOpen_resetsCounter() {
        // Marcar notificación como mostrada
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.RUNNING)
        ServiceStateManager.markStoppedNotificationShown(context)
        assertFalse(ServiceStateManager.canShowStoppedNotification(context))
        
        // resetOnAppOpen debe resetear contador
        ServiceStateManager.resetOnAppOpen(context)
        
        // Ahora puede mostrar otra vez
        assertTrue(
            "resetOnAppOpen() debe resetear contador de notificaciones",
            ServiceStateManager.canShowStoppedNotification(context)
        )
    }
    
    @Test
    fun test_18_getTimeSinceLastStateChange() {
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.RUNNING)
        
        // Wait a bit
        Thread.sleep(100)
        
        val timeSince = ServiceStateManager.getTimeSinceLastStateChange(context)
        
        assertTrue(
            "Debe haber pasado al menos 100ms desde último cambio",
            timeSince >= 100
        )
    }
    
    @Test
    fun test_19_statePersistence() {
        // Establecer estado
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.STOPPED)
        
        // Obtener nuevo contexto (simula reinicio de app)
        val newContext = ApplicationProvider.getApplicationContext<Context>()
        
        // Estado debe persistir
        assertEquals(
            "Estado debe persistir en SharedPreferences",
            ServiceStateManager.ServiceState.STOPPED,
            ServiceStateManager.getCurrentState(newContext)
        )
    }
    
    @Test
    fun test_20_completeLifecycle() {
        // Test de ciclo completo
        
        // 1. App inicia (RUNNING)
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.RUNNING)
        assertTrue(ServiceStateManager.canShowStoppedNotification(context))
        
        // 2. Servicio muere, mostrar STOPPED
        ServiceStateManager.markStoppedNotificationShown(context)
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.STOPPED)
        assertFalse(ServiceStateManager.canShowStoppedNotification(context))
        assertEquals(1, ServiceStateManager.getStoppedCount(context))
        
        // 3. Usuario presiona "Reiniciar"
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.RUNNING)
        ServiceStateManager.resetStoppedCounter(context)
        assertTrue(ServiceStateManager.canShowStoppedNotification(context))
        
        // 4. Servicio muere otra vez
        ServiceStateManager.markStoppedNotificationShown(context)
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.STOPPED)
        assertEquals(2, ServiceStateManager.getStoppedCount(context))
        
        // 5. Usuario presiona "Entendido"
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.DISABLED)
        assertFalse(ServiceStateManager.canShowStoppedNotification(context))
        
        // 6. Usuario abre app de nuevo
        ServiceStateManager.resetOnAppOpen(context)
        assertEquals(ServiceStateManager.ServiceState.RUNNING, ServiceStateManager.getCurrentState(context))
        assertTrue(ServiceStateManager.canShowStoppedNotification(context))
    }
}
