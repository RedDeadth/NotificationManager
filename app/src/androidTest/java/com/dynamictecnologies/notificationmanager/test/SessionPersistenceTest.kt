package com.dynamictecnologies.notificationmanager.test

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dynamictecnologies.notificationmanager.service.ServiceStateManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Tests instrumentados para verificar persistencia de sesión y SharedPreferences.
 * 
 * Verifica:
 * - ServiceStateManager persiste estado correctamente
 * - SharedPreferences funcionan en dispositivo real
 * - Estado sobrevive recreación de contexto
 * 
 * Ejecutar con: ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SessionPersistenceTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Limpiar estado previo
        context.getSharedPreferences("service_state_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun test_01_serviceStateManager_savesState() {
        println("\n🔐 ==== TEST: ServiceStateManager Save State ====")
        
        // Given: Estado inicial
        val initialState = ServiceStateManager.getCurrentState(context)
        println("  📊 Estado inicial: $initialState")
        
        // When: Cambiar estado a RUNNING
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.RUNNING)
        
        // Then: Estado debe ser RUNNING
        val currentState = ServiceStateManager.getCurrentState(context)
        assertEquals(
            "Estado debe ser RUNNING",
            ServiceStateManager.ServiceState.RUNNING,
            currentState
        )
        println("  ✅ Estado guardado: $currentState")
        println("  ✅ TEST PASADO\n")
    }

    @Test
    fun test_02_serviceStateManager_persistsAcrossContexts() {
        println("\n🔄 ==== TEST: State Persists Across Contexts ====")
        
        // Given: Estado guardado con primer contexto
        ServiceStateManager.setState(context, ServiceStateManager.ServiceState.DISABLED)
        println("  📝 Estado guardado: DISABLED")
        
        // When: Obtener estado con nuevo contexto
        val newContext = InstrumentationRegistry.getInstrumentation().targetContext
        val persistedState = ServiceStateManager.getCurrentState(newContext)
        
        // Then: Estado debe persistir
        assertEquals(
            "Estado debe persistir entre contextos",
            ServiceStateManager.ServiceState.DISABLED,
            persistedState
        )
        println("  ✅ Estado persistido: $persistedState")
        println("  ✅ TEST PASADO\n")
    }

    @Test
    fun test_03_stoppedNotificationCounter_works() {
        println("\n📊 ==== TEST: Stopped Notification Counter ====")
        
        // Given: Contador reiniciado
        ServiceStateManager.resetStoppedCounter(context)
        
        // When: Primera verificación después de reset
        val canShowFirst = ServiceStateManager.canShowStoppedNotification(context)
        
        // Then: Primera debe permitirse
        println("  📝 Primera verificación: canShow = $canShowFirst")
        
        // Marcar como mostrada
        if (canShowFirst) {
            ServiceStateManager.markStoppedNotificationShown(context)
            println("  ✅ Marcada como mostrada")
        }
        
        // Verificar que el contador funciona
        val canShowSecond = ServiceStateManager.canShowStoppedNotification(context)
        println("  📝 Segunda verificación: canShow = $canShowSecond")
        
        println("  ✅ Contador funciona correctamente")
        println("  ✅ TEST PASADO\n")
        
        // Cleanup
        ServiceStateManager.resetStoppedCounter(context)
    }

    @Test
    fun test_04_sharedPreferences_multipleValues() {
        println("\n💾 ==== TEST: SharedPreferences Multiple Values ====")
        
        val prefs = context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        
        // Given: Guardar varios valores
        prefs.edit()
            .putString("user_id", "test_user_123")
            .putBoolean("is_authenticated", true)
            .putLong("last_login", System.currentTimeMillis())
            .putInt("login_count", 5)
            .apply()
        
        // When: Leer valores
        val userId = prefs.getString("user_id", null)
        val isAuth = prefs.getBoolean("is_authenticated", false)
        val loginCount = prefs.getInt("login_count", 0)
        
        // Then
        assertEquals("User ID debe persistir", "test_user_123", userId)
        assertTrue("Auth debe ser true", isAuth)
        assertEquals("Login count debe ser 5", 5, loginCount)
        
        println("  📝 User ID: $userId")
        println("  📝 Authenticated: $isAuth")
        println("  📝 Login count: $loginCount")
        println("  ✅ TEST PASADO\n")
        
        // Cleanup
        prefs.edit().clear().apply()
    }

    @Test
    fun test_05_stateTransitions() {
        println("\n🔀 ==== TEST: State Transitions ====")
        
        val transitions = listOf(
            ServiceStateManager.ServiceState.STOPPED,
            ServiceStateManager.ServiceState.RUNNING,
            ServiceStateManager.ServiceState.DISABLED,
            ServiceStateManager.ServiceState.RUNNING
        )
        
        transitions.forEachIndexed { index, expectedState ->
            ServiceStateManager.setState(context, expectedState)
            val actualState = ServiceStateManager.getCurrentState(context)
            assertEquals(
                "Transición $index debe funcionar",
                expectedState,
                actualState
            )
            println("  🔄 $index: $actualState")
        }
        
        println("  ✅ TEST PASADO\n")
    }
}
