package com.dynamictecnologies.notificationmanager.service

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests unitarios para ServiceActionReceiver.
 * 
 * Verifica:
 * - Acción STOP: Estado → STOPPED, servicios detenidos
 * - Acción RESTART: Estado → RUNNING, contador reset
 * - Acción ACKNOWLEDGE: Estado → DISABLED, todo detenido
 */
@RunWith(RobolectricTestRunner::class)
class ServiceActionReceiverTest {
    
    private lateinit var context: Context
    private lateinit var receiver: ServiceActionReceiver
    
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        receiver = ServiceActionReceiver()
        
        // Limpiar SharedPreferences
        context.getSharedPreferences("service_state_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
    
    @After
    fun tearDown() {
        context.getSharedPreferences("service_state_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
    
    @Test
    fun test_01_actionStop_changesStateToStopped() {
        // Establecer estado inicial RUNNING
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.RUNNING)
        
        // Crear intent con acción STOP
        val intent = Intent(context, ServiceActionReceiver::class.java).apply {
            action = ServiceActionReceiver.ACTION_STOP_SERVICE
        }
        
        // Ejecutar receiver
        receiver.onReceive(context, intent)
        
        // Verificar que estado cambió a STOPPED
        assertEquals(
            "Estado debe cambiar a STOPPED",
            ServiceStateManager.ServiceState.STOPPED,
            ServiceStateManager.getCurrentState(context)
        )
    }
    
    @Test
    fun test_02_actionRestart_changesStateToRunning() {
        // Establecer estado inicial STOPPED
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.STOPPED)
        
        // Crear intent con acción RESTART
        val intent = Intent(context, ServiceActionReceiver::class.java).apply {
            action = ServiceActionReceiver.ACTION_RESTART_SERVICE
        }
        
        // Ejecutar receiver
        receiver.onReceive(context, intent)
        
        // Verificar que estado cambió a RUNNING
        assertEquals(
            "Estado debe cambiar a RUNNING",
            ServiceStateManager.ServiceState.RUNNING,
            ServiceStateManager.getCurrentState(context)
        )
    }
    
    @Test
    fun test_03_actionRestart_resetsCounter() {
        // Marcar notificación como mostrada
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.RUNNING)
        ServiceStateManager.markStoppedNotificationShown(context)
        
        // Verificar que no puede mostrar
        assertFalse(ServiceStateManager.canShowStoppedNotification(context))
        
        // Cambiar a STOPPED y ejecutar RESTART
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.STOPPED)
        val intent = Intent(context, ServiceActionReceiver::class.java).apply {
            action = ServiceActionReceiver.ACTION_RESTART_SERVICE
        }
        receiver.onReceive(context, intent)
        
        // Verificar que contador fue reseteado
        assertTrue(
            "Contador debe ser reseteado después de RESTART",
            ServiceStateManager.canShowStoppedNotification(context)
        )
    }
    
    @Test
    fun test_04_actionAcknowledge_changesStateToDisabled() {
        // Establecer estado inicial STOPPED
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.STOPPED)
        
        // Crear intent con acción ACKNOWLEDGE
        val intent = Intent(context, ServiceActionReceiver::class.java).apply {
            action = ServiceActionReceiver.ACTION_ACKNOWLEDGE
        }
        
        // Ejecutar receiver
        receiver.onReceive(context, intent)
        
        // Verificar que estado cambió a DISABLED
        assertEquals(
            "Estado debe cambiar a DISABLED",
            ServiceStateManager.ServiceState.DISABLED,
            ServiceStateManager.getCurrentState(context)
        )
    }
    
    @Test
    fun test_05_actionAcknowledge_preventsNotifications() {
        // Establecer estado RUNNING
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.RUNNING)
        assertTrue(ServiceStateManager.canShowStoppedNotification(context))
        
        // Ejecutar ACKNOWLEDGE
        val intent = Intent(context, ServiceActionReceiver::class.java).apply {
            action = ServiceActionReceiver.ACTION_ACKNOWLEDGE
        }
        receiver.onReceive(context, intent)
        
        // Verificar que no puede mostrar notificaciones
        assertFalse(
            "No debe poder mostrar notificaciones después de ACKNOWLEDGE",
            ServiceStateManager.canShowStoppedNotification(context)
        )
    }
    
    @Test
    fun test_06_unknownAction_doesNothing() {
        // Establecer estado inicial
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.RUNNING)
        
        // Intent con acción desconocida
        val intent = Intent(context, ServiceActionReceiver::class.java).apply {
            action = "com.unknown.ACTION"
        }
        
        receiver.onReceive(context, intent)
        
        // Estado no debe cambiar
        assertEquals(
            "Estado no debe cambiar con acción desconocida",
            ServiceStateManager.ServiceState.RUNNING,
            ServiceStateManager.getCurrentState(context)
        )
    }
    
    @Test
    fun test_07_completeUserFlow_stopAndRestart() {
        // 1. Usuario presiona STOP
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.RUNNING)
        
        val stopIntent = Intent(context, ServiceActionReceiver::class.java).apply {
            action = ServiceActionReceiver.ACTION_STOP_SERVICE
        }
        receiver.onReceive(context, stopIntent)
        
        assertEquals(ServiceStateManager.ServiceState.STOPPED, ServiceStateManager.getCurrentState(context))
        
        // 2. Usuario presiona RESTART
        val restartIntent = Intent(context, ServiceActionReceiver::class.java).apply {
            action = ServiceActionReceiver.ACTION_RESTART_SERVICE
        }
        receiver.onReceive(context, restartIntent)
        
        assertEquals(ServiceStateManager.ServiceState.RUNNING, ServiceStateManager.getCurrentState(context))
    }
    
    @Test
    fun test_08_completeUserFlow_acknowledge() {
        // 1. Servicio muere (STOPPED)
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.STOPPED)
        ServiceStateManager.markStoppedNotificationShown(context)
        
        // 2. Usuario presiona ACKNOWLEDGE
        val ackIntent = Intent(context, ServiceActionReceiver::class.java).apply {
            action = ServiceActionReceiver.ACTION_ACKNOWLEDGE
        }
        receiver.onReceive(context, ackIntent)
        
        // 3. Verificar estado final
        assertEquals(ServiceStateManager.ServiceState.DISABLED, ServiceStateManager.getCurrentState(context))
        assertFalse(ServiceStateManager.canShowStoppedNotification(context))
    }
}
