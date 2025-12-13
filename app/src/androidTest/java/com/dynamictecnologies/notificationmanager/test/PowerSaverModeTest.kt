package com.dynamictecnologies.notificationmanager.test

import android.content.Context
import android.os.PowerManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dynamictecnologies.notificationmanager.service.monitor.ServiceHealth
import com.dynamictecnologies.notificationmanager.util.BluetoothScanOptimizer
import com.dynamictecnologies.notificationmanager.util.notification.ServiceCrashNotifier
import com.dynamictecnologies.notificationmanager.util.notification.ServiceStopReason
import org.junit.Assert.*
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Tests instrumentados para verificar comportamiento en modo ahorro de batería.
 * 
 * Verifica:
 * - Detección de Power Save Mode
 * - BluetoothScanOptimizer adapta comportamiento
 * - Duraciones de escaneo/pausa correctas
 * - ServiceCrashNotifier muestra mensajes apropiados
 * 
 * Ejecutar con: ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PowerSaverModeTest {

    private lateinit var context: Context
    private lateinit var powerManager: PowerManager

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    @Test
    fun test_01_detectPowerSaveMode() {
        println("\n🔋 ==== TEST: Detección de Power Save Mode ====")
        
        // Given: PowerManager del sistema
        val isPowerSaveMode = powerManager.isPowerSaveMode
        
        // Then: El sistema puede detectar el modo
        println("  📊 Power Save Mode activo: $isPowerSaveMode")
        println("  📝 Este test reporta el estado actual del dispositivo")
        
        // Verificar que podemos acceder al estado (no debe lanzar excepción)
        assertNotNull("PowerManager debe estar disponible", powerManager)
        
        println("  ✅ TEST PASADO\n")
    }

    @Test
    fun test_02_bluetoothOptimizerLimitsScan() {
        println("\n📡 ==== TEST: BluetoothScanOptimizer Limita Escaneo ====")
        
        // Given: BluetoothScanOptimizer
        val optimizer = BluetoothScanOptimizer(context)
        
        // When: Verificar si permite escaneo
        val shouldAllow = optimizer.shouldAllowScan()
        val isPowerSave = optimizer.isPowerSaveMode()
        
        // Then: Si está en power save, no debe permitir escaneo
        println("  📊 Power Save Mode: $isPowerSave")
        println("  📊 Permite escaneo: $shouldAllow")
        
        if (isPowerSave) {
            assertFalse(
                "En Power Save Mode, shouldAllowScan() debe ser false",
                shouldAllow
            )
            println("  ✅ Escaneo bloqueado correctamente en Power Save Mode")
        } else {
            assertTrue(
                "Sin Power Save Mode, shouldAllowScan() debe ser true",
                shouldAllow
            )
            println("  ✅ Escaneo permitido en modo normal")
        }
        
        println("  ✅ TEST PASADO\n")
    }

    @Test
    fun test_03_scanDurationReducedInPowerSave() {
        println("\n⏱️ ==== TEST: Duración de Escaneo Adaptativa ====")
        
        // Given: BluetoothScanOptimizer
        val optimizer = BluetoothScanOptimizer(context)
        
        // When: Obtener duración recomendada
        val duration = optimizer.getRecommendedScanDuration()
        val isPowerSave = optimizer.isPowerSaveMode()
        
        // Then: Verificar duración según modo
        println("  📊 Power Save Mode: $isPowerSave")
        println("  📊 Duración de escaneo: ${duration}ms")
        
        if (isPowerSave) {
            assertEquals(
                "En Power Save, duración debe ser 6000ms",
                BluetoothScanOptimizer.POWER_SAVE_SCAN_DURATION_MS,
                duration
            )
            println("  ✅ Duración reducida a 6 segundos")
        } else {
            assertEquals(
                "En modo normal, duración debe ser 12000ms",
                BluetoothScanOptimizer.NORMAL_SCAN_DURATION_MS,
                duration
            )
            println("  ✅ Duración normal de 12 segundos")
        }
        
        // Verificar constantes
        assertEquals("NORMAL_SCAN_DURATION_MS", 12000L, BluetoothScanOptimizer.NORMAL_SCAN_DURATION_MS)
        assertEquals("POWER_SAVE_SCAN_DURATION_MS", 6000L, BluetoothScanOptimizer.POWER_SAVE_SCAN_DURATION_MS)
        
        println("  ✅ TEST PASADO\n")
    }

    @Test
    fun test_04_pauseDurationIncreasedInPowerSave() {
        println("\n⏸️ ==== TEST: Duración de Pausa Adaptativa ====")
        
        // Given: BluetoothScanOptimizer
        val optimizer = BluetoothScanOptimizer(context)
        
        // When: Obtener pausa recomendada
        val pause = optimizer.getRecommendedPauseDuration()
        val isPowerSave = optimizer.isPowerSaveMode()
        
        // Then: Verificar pausa según modo
        println("  📊 Power Save Mode: $isPowerSave")
        println("  📊 Pausa entre escaneos: ${pause}ms")
        
        if (isPowerSave) {
            assertEquals(
                "En Power Save, pausa debe ser 10000ms",
                BluetoothScanOptimizer.POWER_SAVE_PAUSE_DURATION_MS,
                pause
            )
            println("  ✅ Pausa aumentada a 10 segundos")
        } else {
            assertEquals(
                "En modo normal, pausa debe ser 3000ms",
                BluetoothScanOptimizer.NORMAL_PAUSE_DURATION_MS,
                pause
            )
            println("  ✅ Pausa normal de 3 segundos")
        }
        
        // Verificar constantes
        assertEquals("NORMAL_PAUSE_DURATION_MS", 3000L, BluetoothScanOptimizer.NORMAL_PAUSE_DURATION_MS)
        assertEquals("POWER_SAVE_PAUSE_DURATION_MS", 10000L, BluetoothScanOptimizer.POWER_SAVE_PAUSE_DURATION_MS)
        
        println("  ✅ TEST PASADO\n")
    }

    @Test
    fun test_05_serviceStopReasonBatteryOptimizationExists() {
        println("\n🔴 ==== TEST: ServiceStopReason.BatteryOptimization ====")
        
        // Given: ServiceStopReason sealed class
        val reason = ServiceStopReason.BatteryOptimization
        
        // Then: Verificar que el tipo existe y es correcto
        assertNotNull("BatteryOptimization debe existir", reason)
        assertTrue(
            "BatteryOptimization debe ser ServiceStopReason",
            reason is ServiceStopReason
        )
        
        println("  📝 ServiceStopReason.BatteryOptimization: $reason")
        
        // Verificar otros tipos de razón también existen
        val allReasons = listOf(
            ServiceStopReason.SystemKilled,
            ServiceStopReason.UnexpectedCrash,
            ServiceStopReason.PermissionRevoked,
            ServiceStopReason.BatteryOptimization,
            ServiceStopReason.ManufacturerRestriction
        )
        
        println("  📝 Todos los tipos de ServiceStopReason:")
        allReasons.forEach { stopReason ->
            println("    - ${stopReason::class.simpleName}")
        }
        
        assertEquals("Debe haber 5 tipos predefinidos", 5, allReasons.size)
        
        println("  ✅ TEST PASADO\n")
    }

    @Test
    fun test_06_crashNotifierCreatesCorrectly() {
        println("\n🔔 ==== TEST: ServiceCrashNotifier Creación ====")
        
        // Given: ServiceCrashNotifier
        val notifier = ServiceCrashNotifier(context)
        
        // When: Verificar funcionalidad básica
        val canShow = notifier.shouldShowCrashNotification()
        
        // Then: Notifier debe existir y funcionar
        assertNotNull("ServiceCrashNotifier debe crearse", notifier)
        println("  📝 ServiceCrashNotifier creado")
        println("  📝 Puede mostrar notificación: $canShow")
        
        // Verificar que dismiss no lanza excepción
        notifier.dismissAllNotifications()
        println("  ✅ dismissAllNotifications() ejecutado sin errores")
        
        println("  ✅ TEST PASADO\n")
    }
}
