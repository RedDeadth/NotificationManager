package com.dynamictecnologies.notificationmanager.test

import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dynamictecnologies.notificationmanager.service.strategy.*
import com.dynamictecnologies.notificationmanager.util.device.DeviceManufacturerDetector
import org.junit.Assert.*
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Tests instrumentados para verificar comportamiento específico de OEMs.
 * 
 * Verifica:
 * - Detección correcta del fabricante
 * - Estrategia apropiada seleccionada
 * - Configuraciones de batería por OEM
 * - Intervalos de verificación/reintento
 * - Instrucciones claras para el usuario
 * 
 * Ejecutar con: ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class OEMBehaviorTest {

    private lateinit var context: Context
    private lateinit var manufacturerDetector: DeviceManufacturerDetector

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        manufacturerDetector = DeviceManufacturerDetector()
    }

    @Test
    fun test_01_detectCurrentManufacturer() {
        println("\n📱 ==== TEST: Detectar Fabricante Actual ====")
        
        // Given: Información del dispositivo
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val brand = Build.BRAND
        
        // When: Detectar con DeviceManufacturerDetector
        val detected = manufacturerDetector.detectManufacturer()
        
        // Then: Verificar información
        println("  📊 Build.MANUFACTURER: $manufacturer")
        println("  📊 Build.MODEL: $model")
        println("  📊 Build.BRAND: $brand")
        println("  📊 Fabricante detectado: $detected")
        
        assertNotNull("Fabricante debe ser detectado", detected)
        
        println("  ✅ Fabricante detectado correctamente")
        println("  ✅ TEST PASADO\n")
    }

    @Test
    fun test_02_allStrategiesExist() {
        println("\n🔧 ==== TEST: Todas las Estrategias Existen ====")
        
        // Given: Lista de estrategias esperadas
        val strategies = listOf(
            XiaomiServiceStrategy(),
            SamsungServiceStrategy(),
            HuaweiServiceStrategy(),
            OnePlusServiceStrategy(),
            GenericServiceStrategy()
        )
        
        // Then: Verificar que todas existen
        println("  📝 Estrategias disponibles:")
        strategies.forEach { strategy ->
            assertNotNull("Estrategia debe existir", strategy)
            println("    ✓ ${strategy.getStrategyName()}")
        }
        
        assertEquals("Debe haber 5 estrategias", 5, strategies.size)
        
        println("  ✅ Todas las estrategias implementadas")
        println("  ✅ TEST PASADO\n")
    }

    @Test
    fun test_03_batteryWhitelistRequiredByOEM() {
        println("\n🔋 ==== TEST: Battery Whitelist Requerido ====")
        
        // Given: Estrategias con diferentes requisitos
        val strategiesWithWhitelist = mapOf(
            "Xiaomi" to XiaomiServiceStrategy(),
            "Samsung" to SamsungServiceStrategy(),
            "Huawei" to HuaweiServiceStrategy(),
            "OnePlus" to OnePlusServiceStrategy(),
            "Generic" to GenericServiceStrategy()
        )
        
        // Then: Verificar shouldRequestBatteryWhitelist
        println("  📝 Requisito de Battery Whitelist:")
        strategiesWithWhitelist.forEach { (name, strategy) ->
            val required = strategy.shouldRequestBatteryWhitelist()
            println("    $name: ${if (required) "✓ Requerido" else "✗ Opcional"}")
        }
        
        // Xiaomi, Samsung y Huawei siempre requieren whitelist
        assertTrue(
            "Xiaomi debe requerir battery whitelist",
            XiaomiServiceStrategy().shouldRequestBatteryWhitelist()
        )
        assertTrue(
            "Huawei debe requerir battery whitelist",
            HuaweiServiceStrategy().shouldRequestBatteryWhitelist()
        )
        
        println("  ✅ Configuraciones de batería correctas")
        println("  ✅ TEST PASADO\n")
    }

    @Test
    fun test_04_checkIntervalVariesByOEM() {
        println("\n⏱️ ==== TEST: Intervalo de Verificación por OEM ====")
        
        // Given: Diferentes estrategias
        val intervals = mapOf(
            "Xiaomi" to XiaomiServiceStrategy().getOptimalCheckInterval(),
            "Samsung" to SamsungServiceStrategy().getOptimalCheckInterval(),
            "Huawei" to HuaweiServiceStrategy().getOptimalCheckInterval(),
            "OnePlus" to OnePlusServiceStrategy().getOptimalCheckInterval(),
            "Generic" to GenericServiceStrategy().getOptimalCheckInterval()
        )
        
        // Then: Verificar intervalos variados
        println("  📝 Intervalos de verificación:")
        intervals.forEach { (name, interval) ->
            val minutes = interval / 1000 / 60
            println("    $name: ${minutes} minutos")
        }
        
        // OEMs agresivos deben tener intervalos más cortos
        assertTrue(
            "Xiaomi debe tener intervalo más corto que Generic",
            XiaomiServiceStrategy().getOptimalCheckInterval() < 
            GenericServiceStrategy().getOptimalCheckInterval()
        )
        
        println("  ✅ Intervalos configurados según agresividad del OEM")
        println("  ✅ TEST PASADO\n")
    }

    @Test
    fun test_05_retryIntervalsConfigured() {
        println("\n🔄 ==== TEST: Intervalos de Reintento ====")
        
        // Given: Estrategias con diferentes reintentos
        val retryConfig = mapOf(
            "Xiaomi" to Pair(
                XiaomiServiceStrategy().getRetryInterval(),
                XiaomiServiceStrategy().getMaxRetries()
            ),
            "Huawei" to Pair(
                HuaweiServiceStrategy().getRetryInterval(),
                HuaweiServiceStrategy().getMaxRetries()
            ),
            "Generic" to Pair(
                GenericServiceStrategy().getRetryInterval(),
                GenericServiceStrategy().getMaxRetries()
            )
        )
        
        // Then: Verificar configuración
        println("  📝 Configuración de reintentos:")
        retryConfig.forEach { (name, config) ->
            val intervalMinutes = config.first / 1000 / 60
            println("    $name: cada $intervalMinutes min, máx ${config.second} reintentos")
        }
        
        // OEMs agresivos deben tener más reintentos
        assertTrue(
            "Xiaomi debe tener más reintentos que Generic",
            XiaomiServiceStrategy().getMaxRetries() >= GenericServiceStrategy().getMaxRetries()
        )
        
        println("  ✅ Reintentos configurados correctamente")
        println("  ✅ TEST PASADO\n")
    }

    @Test
    fun test_06_recommendedSettingsNotEmpty() {
        println("\n📋 ==== TEST: Instrucciones de Configuración ====")
        
        // Given: Todas las estrategias
        val strategies = listOf(
            XiaomiServiceStrategy(),
            SamsungServiceStrategy(),
            HuaweiServiceStrategy(),
            OnePlusServiceStrategy(),
            GenericServiceStrategy()
        )
        
        // Then: Verificar que cada estrategia tiene instrucciones
        strategies.forEach { strategy ->
            val settings = strategy.getRecommendedSettings()
            
            assertNotNull("${strategy.getStrategyName()} debe tener instrucciones", settings)
            assertTrue(
                "${strategy.getStrategyName()} debe tener al menos 1 instrucción",
                settings.isNotEmpty()
            )
            
            println("  📝 ${strategy.getStrategyName()}: ${settings.size} pasos")
        }
        
        // Verificar que Xiaomi tiene instrucciones detalladas
        val xiaomiSettings = XiaomiServiceStrategy().getRecommendedSettings()
        assertTrue(
            "Xiaomi debe tener varias instrucciones detalladas",
            xiaomiSettings.size >= 5
        )
        
        println("  ✅ Todas las estrategias tienen instrucciones claras")
        println("  ✅ TEST PASADO\n")
    }

    @Test
    fun test_07_foregroundServicePrioritySet() {
        println("\n🔔 ==== TEST: Prioridad de Notificación del Servicio ====")
        
        // Given: Estrategias con diferentes prioridades
        val priorities = listOf(
            "Xiaomi" to XiaomiServiceStrategy().getForegroundServiceNotificationPriority(),
            "Samsung" to SamsungServiceStrategy().getForegroundServiceNotificationPriority(),
            "Huawei" to HuaweiServiceStrategy().getForegroundServiceNotificationPriority(),
            "Generic" to GenericServiceStrategy().getForegroundServiceNotificationPriority()
        )
        
        // Then: Verificar prioridades
        println("  📝 Prioridades de notificación:")
        priorities.forEach { (name, priority) ->
            println("    $name: $priority")
        }
        
        // OEMs agresivos deben usar prioridad alta
        assertEquals(
            "Xiaomi debe usar HIGH priority",
            NotificationPriority.HIGH,
            XiaomiServiceStrategy().getForegroundServiceNotificationPriority()
        )
        
        assertEquals(
            "Huawei debe usar HIGH priority",
            NotificationPriority.HIGH,
            HuaweiServiceStrategy().getForegroundServiceNotificationPriority()
        )
        
        println("  ✅ Prioridades configuradas según OEM")
        println("  ✅ TEST PASADO\n")
    }
}
