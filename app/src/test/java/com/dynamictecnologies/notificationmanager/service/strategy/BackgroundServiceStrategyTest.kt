package com.dynamictecnologies.notificationmanager.service.strategy

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests para XiaomiServiceStrategy
 */
class XiaomiServiceStrategyTest {
    
    private val strategy = XiaomiServiceStrategy()
    
    @Test
    fun `intervalo de verificacion es 8 minutos`() {
        val interval = strategy.getOptimalCheckInterval()
        
        assertThat(interval).isEqualTo(8 * 60 * 1000L)
    }
    
    @Test
    fun `debe usar AlarmManager`() {
        val shouldUse = strategy.shouldUseAlarmManager()
        
        assertThat(shouldUse).isTrue()
    }
    
    @Test
    fun `requiere battery whitelist`() {
        val requires = strategy.shouldRequestBatteryWhitelist()
        
        assertThat(requires).isTrue()
    }
    
    @Test
    fun `retorna settings recomendados`() {
        val settings = strategy.getRecommendedSettings()
        
        assertThat(settings).isNotEmpty()
        assertThat(settings.any { it.contains("Inicio automático") }).isTrue()
    }
    
    @Test
    fun `intervalo de reintentos es 2 minutos`() {
        val interval = strategy.getRetryInterval()
        
        assertThat(interval).isEqualTo(2 * 60 * 1000L)
    }
    
    @Test
    fun `maximo 5 reintentos`() {
        val maxRetries = strategy.getMaxRetries()
        
        assertThat(maxRetries).isEqualTo(5)
    }
    
    @Test
    fun `requiere foreground service persistente`() {
        val requires = strategy.requiresPersistentForegroundService()
        
        assertThat(requires).isTrue()
    }
    
    @Test
    fun `prioridad de notificacion es HIGH`() {
        val priority = strategy.getForegroundServiceNotificationPriority()
        
        assertThat(priority).isEqualTo(NotificationPriority.HIGH)
    }
    
    @Test
    fun `nombre de estrategia correcto`() {
        val name = strategy.getStrategyName()
        
        assertThat(name).contains("Xiaomi")
        assertThat(name).contains("MIUI")
    }
}

/**
 * Tests para SamsungServiceStrategy
 */
class SamsungServiceStrategyTest {
    
    private val strategy = SamsungServiceStrategy()
    
    @Test
    fun `intervalo de verificacion es 15 minutos`() {
        val interval = strategy.getOptimalCheckInterval()
        
        assertThat(interval).isEqualTo(15 * 60 * 1000L)
    }
    
    @Test
    fun `debe usar AlarmManager`() {
        val shouldUse = strategy.shouldUseAlarmManager()
        
        assertThat(shouldUse).isTrue()
    }
    
    @Test
    fun `requiere battery whitelist`() {
        val requires = strategy.shouldRequestBatteryWhitelist()
        
        assertThat(requires).isTrue()
    }
    
    @Test
    fun `retorna settings recomendados`() {
        val settings = strategy.getRecommendedSettings()
        
        assertThat(settings).isNotEmpty()
        assertThat(settings.any { it.contains("suspensión") }).isTrue()
    }
    
    @Test
    fun `intervalo de reintentos es 3 minutos`() {
        val interval = strategy.getRetryInterval()
        
        assertThat(interval).isEqualTo(3 * 60 * 1000L)
    }
    
    @Test
    fun `maximo 4 reintentos`() {
        val maxRetries = strategy.getMaxRetries()
        
        assertThat(maxRetries).isEqualTo(4)
    }
    
    @Test
    fun `requiere foreground service persistente`() {
        val requires = strategy.requiresPersistentForegroundService()
        
        assertThat(requires).isTrue()
    }
    
    @Test
    fun `prioridad de notificacion es DEFAULT`() {
        val priority = strategy.getForegroundServiceNotificationPriority()
        
        assertThat(priority).isEqualTo(NotificationPriority.DEFAULT)
    }
    
    @Test
    fun `nombre de estrategia correcto`() {
        val name = strategy.getStrategyName()
        
        assertThat(name).contains("Samsung")
        assertThat(name).contains("OneUI")
    }
}

/**
 * Tests para HuaweiServiceStrategy
 */
class HuaweiServiceStrategyTest {
    
    private val strategy = HuaweiServiceStrategy()
    
    @Test
    fun `intervalo de verificacion es 8 minutos`() {
        val interval = strategy.getOptimalCheckInterval()
        
        assertThat(interval).isEqualTo(8 * 60 * 1000L)
    }
    
    @Test
    fun `debe usar AlarmManager`() {
        val shouldUse = strategy.shouldUseAlarmManager()
        
        assertThat(shouldUse).isTrue()
    }
    
    @Test
    fun `maximo 5 reintentos como Xiaomi`() {
        val maxRetries = strategy.getMaxRetries()
        
        assertThat(maxRetries).isEqualTo(5)
    }
    
    @Test
    fun `prioridad de notificacion es HIGH`() {
        val priority = strategy.getForegroundServiceNotificationPriority()
        
        assertThat(priority).isEqualTo(NotificationPriority.HIGH)
    }
}

/**
 * Tests para OnePlusServiceStrategy
 */
class OnePlusServiceStrategyTest {
    
    private val strategy = OnePlusServiceStrategy()
    
    @Test
    fun `intervalo de verificacion es 15 minutos`() {
        val interval = strategy.getOptimalCheckInterval()
        
        assertThat(interval).isEqualTo(15 * 60 * 1000L)
    }
    
    @Test
    fun `intervalo de reintentos es 5 minutos`() {
        val interval = strategy.getRetryInterval()
        
        assertThat(interval).isEqualTo(5 * 60 * 1000L)
    }
    
    @Test
    fun `maximo 3 reintentos`() {
        val maxRetries = strategy.getMaxRetries()
        
        assertThat(maxRetries).isEqualTo(3)
    }
    
    @Test
    fun `prioridad de notificacion es DEFAULT`() {
        val priority = strategy.getForegroundServiceNotificationPriority()
        
        assertThat(priority).isEqualTo(NotificationPriority.DEFAULT)
    }
}

/**
 * Tests para GenericServiceStrategy
 */
class GenericServiceStrategyTest {
    
    private val strategy = GenericServiceStrategy()
    
    @Test
    fun `intervalo de verificacion es 20 minutos`() {
        val interval = strategy.getOptimalCheckInterval()
        
        assertThat(interval).isEqualTo(20 * 60 * 1000L)
    }
    
    @Test
    fun `NO debe usar AlarmManager`() {
        val shouldUse = strategy.shouldUseAlarmManager()
        
        assertThat(shouldUse).isFalse()
    }
    
    @Test
    fun `requiere battery whitelist aunque no critico`() {
        val requires = strategy.shouldRequestBatteryWhitelist()
        
        assertThat(requires).isTrue()
    }
    
    @Test
    fun `retorna settings minimos`() {
        val settings = strategy.getRecommendedSettings()
        
        assertThat(settings).isNotEmpty()
        assertThat(settings.size).isLessThan(5) // Menos configuraciones que OEMs específicos
    }
    
    @Test
    fun `intervalo de reintentos es 10 minutos`() {
        val interval = strategy.getRetryInterval()
        
        assertThat(interval).isEqualTo(10 * 60 * 1000L)
    }
    
    @Test
    fun `maximo 3 reintentos`() {
        val maxRetries = strategy.getMaxRetries()
        
        assertThat(maxRetries).isEqualTo(3)
    }
    
    @Test
    fun `prioridad de notificacion es LOW`() {
        val priority = strategy.getForegroundServiceNotificationPriority()
        
        assertThat(priority).isEqualTo(NotificationPriority.LOW)
    }
    
    @Test
    fun `nombre de estrategia correcto`() {
        val name = strategy.getStrategyName()
        
        assertThat(name).contains("Generic")
        assertThat(name).contains("Android")
    }
}

/**
 * Tests comparativos entre estrategias
 */
class StrategyComparisonTest {
    
    private val xiaomi = XiaomiServiceStrategy()
    private val samsung = SamsungServiceStrategy()
    private val huawei = HuaweiServiceStrategy()
    private val oneplus = OnePlusServiceStrategy()
    private val generic = GenericServiceStrategy()
    
    @Test
    fun `Xiaomi y Huawei tienen mismos intervalos agresivos`() {
        assertThat(xiaomi.getOptimalCheckInterval())
            .isEqualTo(huawei.getOptimalCheckInterval())
        assertThat(xiaomi.getOptimalCheckInterval())
            .isEqualTo(8 * 60 * 1000L)
    }
    
    @Test
    fun `Samsung y OnePlus tienen intervalos moderados similares`() {
        assertThat(samsung.getOptimalCheckInterval())
            .isEqualTo(oneplus.getOptimalCheckInterval())
        assertThat(samsung.getOptimalCheckInterval())
            .isEqualTo(15 * 60 * 1000L)
    }
    
    @Test
    fun `Generic tiene intervalo mas largo`() {
        val allStrategies = listOf(xiaomi, samsung, huawei, oneplus, generic)
        val intervals = allStrategies.map { it.getOptimalCheckInterval() }
        
        assertThat(generic.getOptimalCheckInterval()).isEqualTo(intervals.maxOrNull())
    }
    
    @Test
    fun `Solo fabricantes agresivos usan HIGH priority`() {
        assertThat(xiaomi.getForegroundServiceNotificationPriority())
            .isEqualTo(NotificationPriority.HIGH)
        assertThat(huawei.getForegroundServiceNotificationPriority())
            .isEqualTo(NotificationPriority.HIGH)
        
        assertThat(samsung.getForegroundServiceNotificationPriority())
            .isNotEqualTo(NotificationPriority.HIGH)
        assertThat(generic.getForegroundServiceNotificationPriority())
            .isNotEqualTo(NotificationPriority.HIGH)
    }
    
    @Test
    fun `Solo Generic NO usa AlarmManager`() {
        assertThat(xiaomi.shouldUseAlarmManager()).isTrue()
        assertThat(samsung.shouldUseAlarmManager()).isTrue()
        assertThat(huawei.shouldUseAlarmManager()).isTrue()
        assertThat(oneplus.shouldUseAlarmManager()).isTrue()
        
        assertThat(generic.shouldUseAlarmManager()).isFalse()
    }
    
    @Test
    fun `Fabricantes agresivos tienen mas reintentos`() {
        assertThat(xiaomi.getMaxRetries()).isEqualTo(5)
        assertThat(huawei.getMaxRetries()).isEqualTo(5)
        
        assertThat(samsung.getMaxRetries()).isLessThan(5)
        assertThat(oneplus.getMaxRetries()).isLessThan(5)
        assertThat(generic.getMaxRetries()).isLessThan(5)
    }
}
