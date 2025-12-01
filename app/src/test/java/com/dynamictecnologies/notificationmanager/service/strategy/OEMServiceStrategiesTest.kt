package com.dynamictecnologies.notificationmanager.service.strategy

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests unitarios para BaseServiceStrategy y sus implementaciones.
 * 
 * Verifica que:
 * - Todas las strategies heredan correctamente de BaseServiceStrategy
 * - El método getAppName() es accesible y retorna el valor correcto
 * - Cada strategy mantiene su funcionalidad específica
 */
class OEMServiceStrategiesTest {

    // ========== BaseServiceStrategy Tests ==========
    
    /**
     * Strategy concreta para testing de BaseServiceStrategy
     */
    private class TestStrategy : BaseServiceStrategy() {
        override fun getOptimalCheckInterval(): Long = 10000L
        override fun shouldUseAlarmManager(): Boolean = false
        override fun shouldRequestBatteryWhitelist(): Boolean = false
        override fun getRecommendedSettings(): List<String> = emptyList()
        override fun getRetryInterval(): Long = 5000L
        override fun getMaxRetries(): Int = 3
        override fun requiresPersistentForegroundService(): Boolean = false
        override fun getForegroundServiceNotificationPriority(): NotificationPriority = NotificationPriority.DEFAULT
        override fun getStrategyName(): String = "Test Strategy"
        
        // Exponer método protected para testing
        fun testGetAppName() = getAppName()
    }
    
    @Test
    fun `BaseServiceStrategy provides correct app name`() {
        // Given
        val strategy = TestStrategy()
        
        // When
        val appName = strategy.testGetAppName()
        
        // Then
        assertEquals("Gestor de Notificaciones", appName)
    }
    
    // ========== XiaomiServiceStrategy Tests ==========
    
    @Test
    fun `XiaomiServiceStrategy extends BaseServiceStrategy`() {
        // Given
        val strategy = XiaomiServiceStrategy()
        
        // Then
        assertTrue("Should extend BaseServiceStrategy", strategy is BaseServiceStrategy)
    }
    
    @Test
    fun `XiaomiServiceStrategy has aggressive check interval`() {
        // Given
        val strategy = XiaomiServiceStrategy()
        
        // When
        val interval = strategy.getOptimalCheckInterval()
        
        // Then
        assertEquals("Xiaomi should check every 8 minutes", 8 * 60 * 1000L, interval)
    }
    
    @Test
    fun `XiaomiServiceStrategy should use AlarmManager`() {
        // Given
        val strategy = XiaomiServiceStrategy()
        
        // Then
        assertTrue("Xiaomi should use AlarmManager", strategy.shouldUseAlarmManager())
    }
    
    @Test
    fun `XiaomiServiceStrategy requires battery whitelist`() {
        // Given
        val strategy = XiaomiServiceStrategy()
        
        // Then
        assertTrue("Xiaomi requires battery whitelist", strategy.shouldRequestBatteryWhitelist())
    }
    
    @Test
    fun `XiaomiServiceStrategy has high priority notification`() {
        // Given
        val strategy = XiaomiServiceStrategy()
        
        // When
        val priority = strategy.getForegroundServiceNotificationPriority()
        
        // Then
        assertEquals("Should have HIGH priority", NotificationPriority.HIGH, priority)
    }
    
    @Test
    fun `XiaomiServiceStrategy has correct name`() {
        // Given
        val strategy = XiaomiServiceStrategy()
        
        // When
        val name = strategy.getStrategyName()
        
        // Then
        assertEquals("Xiaomi (MIUI) Strategy", name)
    }
    
    // ========== SamsungServiceStrategy Tests ==========
    
    @Test
    fun `SamsungServiceStrategy extends BaseServiceStrategy`() {
        // Given
        val strategy = SamsungServiceStrategy()
        
        // Then
        assertTrue("Should extend BaseServiceStrategy", strategy is BaseServiceStrategy)
    }
    
    @Test
    fun `SamsungServiceStrategy has moderate check interval`() {
        // Given
        val strategy = SamsungServiceStrategy()
        
        // When
        val interval = strategy.getOptimalCheckInterval()
        
        // Then
        assertEquals("Samsung should check every 15 minutes", 15 * 60 * 1000L, interval)
    }
    
    @Test
    fun `SamsungServiceStrategy has default priority notification`() {
        // Given
        val strategy = SamsungServiceStrategy()
        
        // When
        val priority = strategy.getForegroundServiceNotificationPriority()
        
        // Then
        assertEquals("Should have DEFAULT priority", NotificationPriority.DEFAULT, priority)
    }
    
    // ========== HuaweiServiceStrategy Tests ==========
    
    @Test
    fun `HuaweiServiceStrategy extends BaseServiceStrategy`() {
        // Given
        val strategy = HuaweiServiceStrategy()
        
        // Then
        assertTrue("Should extend BaseServiceStrategy", strategy is BaseServiceStrategy)
    }
    
    @Test
    fun `HuaweiServiceStrategy has aggressive check interval`() {
        // Given
        val strategy = HuaweiServiceStrategy()
        
        // When
        val interval = strategy.getOptimalCheckInterval()
        
        // Then
        assertEquals("Huawei should check every 8 minutes", 8 * 60 * 1000L, interval)
    }
    
    @Test
    fun `HuaweiServiceStrategy requires battery whitelist`() {
        // Given
        val strategy = HuaweiServiceStrategy()
        
        // Then
        assertTrue("Huawei requires battery whitelist", strategy.shouldRequestBatteryWhitelist())
    }
    
    // ========== OnePlusServiceStrategy Tests ==========
    
    @Test
    fun `OnePlusServiceStrategy extends BaseServiceStrategy`() {
        // Given
        val strategy = OnePlusServiceStrategy()
        
        // Then
        assertTrue("Should extend BaseServiceStrategy", strategy is BaseServiceStrategy)
    }
    
    @Test
    fun `OnePlusServiceStrategy has moderate check interval`() {
        // Given
        val strategy = OnePlusServiceStrategy()
        
        // When
        val interval = strategy.getOptimalCheckInterval()
        
        // Then
        assertEquals("OnePlus should check every 15 minutes", 15 * 60 * 1000L, interval)
    }
    
    @Test
    fun `OnePlusServiceStrategy has lower max retries`() {
        // Given
        val strategy = OnePlusServiceStrategy()
        
        // When
        val maxRetries = strategy.getMaxRetries()
        
        // Then
        assertEquals("OnePlus should have 3 max retries", 3, maxRetries)
    }
    
    // ========== GenericServiceStrategy Tests ==========
    
    @Test
    fun `GenericServiceStrategy extends BaseServiceStrategy`() {
        // Given
        val strategy = GenericServiceStrategy()
        
        // Then
        assertTrue("Should extend BaseServiceStrategy", strategy is BaseServiceStrategy)
    }
    
    @Test
    fun `GenericServiceStrategy has longest check interval`() {
        // Given
        val strategy = GenericServiceStrategy()
        
        // When
        val interval = strategy.getOptimalCheckInterval()
        
        // Then
        assertEquals("Generic should check every 20 minutes", 20 * 60 * 1000L, interval)
    }
    
    @Test
    fun `GenericServiceStrategy should NOT use AlarmManager`() {
        // Given
        val strategy = GenericServiceStrategy()
        
        // Then
        assertFalse("Generic should not require AlarmManager", strategy.shouldUseAlarmManager())
    }
    
    @Test
    fun `GenericServiceStrategy has low priority notification`() {
        // Given
        val strategy = GenericServiceStrategy()
        
        // When
        val priority = strategy.getForegroundServiceNotificationPriority()
        
        // Then
        assertEquals("Should have LOW priority", NotificationPriority.LOW, priority)
    }
    
    // ========== Comparative Tests ==========
    
    @Test
    fun `All strategies are instances of BackgroundServiceStrategy`() {
        // Given
        val strategies = listOf(
            XiaomiServiceStrategy(),
            SamsungServiceStrategy(),
            HuaweiServiceStrategy(),
            OnePlusServiceStrategy(),
            GenericServiceStrategy()
        )
        
        // Then
        strategies.forEach { strategy ->
            assertTrue(
                "${strategy.getStrategyName()} should implement BackgroundServiceStrategy",
                strategy is BackgroundServiceStrategy
            )
        }
    }
    
    @Test
    fun `All strategies are instances of BaseServiceStrategy`() {
        // Given
        val strategies = listOf(
            XiaomiServiceStrategy(),
            SamsungServiceStrategy(),
            HuaweiServiceStrategy(),
            OnePlusServiceStrategy(),
            GenericServiceStrategy()
        )
        
        // Then
        strategies.forEach { strategy ->
            assertTrue(
                "${strategy.getStrategyName()} should extend BaseServiceStrategy",
                strategy is BaseServiceStrategy
            )
        }
    }
    
    @Test
    fun `Xiaomi and Huawei have most aggressive intervals`() {
        // Given
        val xiaomi = XiaomiServiceStrategy()
        val huawei = HuaweiServiceStrategy()
        val samsung = SamsungServiceStrategy()
        val generic = GenericServiceStrategy()
        
        // Then
        assertEquals("Xiaomi and Huawei should have same interval", 
            xiaomi.getOptimalCheckInterval(), 
            huawei.getOptimalCheckInterval()
        )
        assertTrue("Xiaomi should be more aggressive than Samsung",
            xiaomi.getOptimalCheckInterval() < samsung.getOptimalCheckInterval()
        )
        assertTrue("Xiaomi should be more aggressive than Generic",
            xiaomi.getOptimalCheckInterval() < generic.getOptimalCheckInterval()
        )
    }
    
    @Test
    fun `All strategies have unique names`() {
        // Given
        val strategies = listOf(
            XiaomiServiceStrategy(),
            SamsungServiceStrategy(),
            HuaweiServiceStrategy(),
            OnePlusServiceStrategy(),
            GenericServiceStrategy()
        )
        
        // When
        val names = strategies.map { it.getStrategyName() }
        val uniqueNames = names.toSet()
        
        // Then
        assertEquals("All strategies should have unique names", names.size, uniqueNames.size)
    }
    
    @Test
    fun `All strategies require persistent foreground service`() {
        // Given
        val strategies = listOf(
            XiaomiServiceStrategy(),
            SamsungServiceStrategy(),
            HuaweiServiceStrategy(),
            OnePlusServiceStrategy(),
            GenericServiceStrategy()
        )
        
        // Then
        strategies.forEach { strategy ->
            assertTrue(
                "${strategy.getStrategyName()} should require persistent foreground service",
                strategy.requiresPersistentForegroundService()
            )
        }
    }
}
