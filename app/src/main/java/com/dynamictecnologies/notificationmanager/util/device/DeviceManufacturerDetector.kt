package com.dynamictecnologies.notificationmanager.util.device

import android.os.Build

/**
 * Sealed class que representa los diferentes fabricantes de dispositivos Android.
 * Cada fabricante puede tener comportamientos específicos de gestión de servicios en background.
 */
sealed class DeviceManufacturer {
    /**
     * Dispositivos Xiaomi con MIUI
     * Conocidos por políticas agresivas de gestión de batería y cierre de servicios
     */
    object Xiaomi : DeviceManufacturer() {
        override fun toString() = "Xiaomi (MIUI)"
    }
    
    /**
     * Dispositivos Samsung con OneUI
     * Tienen optimización de batería agresiva en versiones recientes
     */
    object Samsung : DeviceManufacturer() {
        override fun toString() = "Samsung (OneUI)"
    }
    
    /**
     * Dispositivos Huawei/Honor con EMUI
     * Requieren configuración de "Protected Apps"
     */
    object Huawei : DeviceManufacturer() {
        override fun toString() = "Huawei (EMUI)"
    }
    
    /**
     * Dispositivos OnePlus con OxygenOS
     * Tienen optimización de batería moderada
     */
    object OnePlus : DeviceManufacturer() {
        override fun toString() = "OnePlus (OxygenOS)"
    }
    
    /**
     * Dispositivos Oppo/Realme con ColorOS
     * Requieren permisos adicionales para background
     */
    object Oppo : DeviceManufacturer() {
        override fun toString() = "Oppo/Realme (ColorOS)"
    }
    
    /**
     * Dispositivos Vivo con FuntouchOS
     * Similar a Oppo en restricciones
     */
    object Vivo : DeviceManufacturer() {
        override fun toString() = "Vivo (FuntouchOS)"
    }
    
    /**
     * Otros fabricantes con Android stock o personalizaciones mínimas
     */
    object Generic : DeviceManufacturer() {
        override fun toString() = "Generic Android"
    }
}

/**
 * Información sobre las restricciones de background de un dispositivo
 */
data class RestrictionInfo(
    val manufacturer: DeviceManufacturer,
    val manufacturerName: String,
    val model: String,
    val androidVersion: Int,
    val hasAggressiveKilling: Boolean,
    val requiresBatteryWhitelist: Boolean,
    val recommendedActions: List<String>,
    val estimatedSurvivalRate: Int // 0-100%
)

/**
 * Detector de fabricante de dispositivo con recomendaciones específicas.
 * 
 */
class DeviceManufacturerDetector {
    
    /**
     * Detecta el fabricante del dispositivo actual
     */
    fun detectManufacturer(): DeviceManufacturer {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        
        return when {
            manufacturer.contains("xiaomi") || brand.contains("xiaomi") || 
            manufacturer.contains("redmi") || brand.contains("redmi") ->
                DeviceManufacturer.Xiaomi
                
            manufacturer.contains("samsung") || brand.contains("samsung") ->
                DeviceManufacturer.Samsung
                
            manufacturer.contains("huawei") || brand.contains("huawei") ||
            manufacturer.contains("honor") || brand.contains("honor") ->
                DeviceManufacturer.Huawei
                
            manufacturer.contains("oneplus") || brand.contains("oneplus") ->
                DeviceManufacturer.OnePlus
                
            manufacturer.contains("oppo") || brand.contains("oppo") ||
            manufacturer.contains("realme") || brand.contains("realme") ->
                DeviceManufacturer.Oppo
                
            manufacturer.contains("vivo") || brand.contains("vivo") ->
                DeviceManufacturer.Vivo
                
            else -> DeviceManufacturer.Generic
        }
    }
    
    /**
     * Obtiene información detallada sobre las restricciones de background
     */
    fun getRestrictionInfo(): RestrictionInfo {
        val manufacturer = detectManufacturer()
        val androidVersion = Build.VERSION.SDK_INT
        
        return when (manufacturer) {
            is DeviceManufacturer.Xiaomi -> RestrictionInfo(
                manufacturer = manufacturer,
                manufacturerName = Build.MANUFACTURER,
                model = Build.MODEL,
                androidVersion = androidVersion,
                hasAggressiveKilling = true,
                requiresBatteryWhitelist = true,
                recommendedActions = listOf(
                    "Desactivar 'Ahorro de batería' para esta app",
                    "Activar 'Inicio automático'",
                    "Bloquear app en 'Aplicaciones recientes'",
                    "En Seguridad > Permisos > Inicio automático (Activar)",
                    "En Configuración de batería > Ahorro de batería (Sin restricciones)"
                ),
                estimatedSurvivalRate = 40 // Muy agresivo
            )
            
            is DeviceManufacturer.Samsung -> RestrictionInfo(
                manufacturer = manufacturer,
                manufacturerName = Build.MANUFACTURER,
                model = Build.MODEL,
                androidVersion = androidVersion,
                hasAggressiveKilling = true,
                requiresBatteryWhitelist = true,
                recommendedActions = listOf(
                    "Quitar de 'Aplicaciones en suspensión'",
                    "Quitar de 'Aplicaciones en suspensión profunda'",
                    "Agregar a 'Aplicaciones sin optimizar'",
                    "En Batería > Uso de batería en segundo plano (No restringir)"
                ),
                estimatedSurvivalRate = 55 // Moderadamente agresivo
            )
            
            is DeviceManufacturer.Huawei -> RestrictionInfo(
                manufacturer = manufacturer,
                manufacturerName = Build.MANUFACTURER,
                model = Build.MODEL,
                androidVersion = androidVersion,
                hasAggressiveKilling = true,
                requiresBatteryWhitelist = true,
                recommendedActions = listOf(
                    "Activar en 'Aplicaciones protegidas'",
                    "Desactivar 'Gestión de inicio'",
                    "En Batería > Iniciar aplicaciones (Gestionar manualmente)",
                    "Permitir: Inicio automático, Inicio secundario, Ejecutar en segundo plano"
                ),
                estimatedSurvivalRate = 35 // Muy agresivo
            )
            
            is DeviceManufacturer.OnePlus -> RestrictionInfo(
                manufacturer = manufacturer,
                manufacturerName = Build.MANUFACTURER,
                model = Build.MODEL,
                androidVersion = androidVersion,
                hasAggressiveKilling = androidVersion >= Build.VERSION_CODES.P,
                requiresBatteryWhitelist = true,
                recommendedActions = listOf(
                    "Desactivar 'Optimización de batería'",
                    "En Batería > Optimización de batería (No optimizar)",
                    "Activar 'Bloquear app' en aplicaciones recientes"
                ),
                estimatedSurvivalRate = 65 // Moderado
            )
            
            is DeviceManufacturer.Oppo -> RestrictionInfo(
                manufacturer = manufacturer,
                manufacturerName = Build.MANUFACTURER,
                model = Build.MODEL,
                androidVersion = androidVersion,
                hasAggressiveKilling = true,
                requiresBatteryWhitelist = true,
                recommendedActions = listOf(
                    "Permitir 'Inicio en segundo plano'",
                    "En Privacidad > Inicio automático (Activar)",
                    "En Batería > Ahorro de batería (Desactivar)",
                    "Bloquear app en aplicaciones recientes"
                ),
                estimatedSurvivalRate = 45 // Agresivo
            )
            
            is DeviceManufacturer.Vivo -> RestrictionInfo(
                manufacturer = manufacturer,
                manufacturerName = Build.MANUFACTURER,
                model = Build.MODEL,
                androidVersion = androidVersion,
                hasAggressiveKilling = true,
                requiresBatteryWhitelist = true,
                recommendedActions = listOf(
                    "Activar 'Inicio automático'",
                    "Permitir ejecución en segundo plano",
                    "En Batería > Alto consumo en segundo plano (Permitir)"
                ),
                estimatedSurvivalRate = 45 // Agresivo
            )
            
            is DeviceManufacturer.Generic -> RestrictionInfo(
                manufacturer = manufacturer,
                manufacturerName = Build.MANUFACTURER,
                model = Build.MODEL,
                androidVersion = androidVersion,
                hasAggressiveKilling = false,
                requiresBatteryWhitelist = androidVersion >= Build.VERSION_CODES.M,
                recommendedActions = listOf(
                    "Desactivar 'Optimización de batería' (opcional)",
                    "Permitir permisos de notificaciones"
                ),
                estimatedSurvivalRate = 85 // Bueno
            )
        }
    }
    
    /**
     * Verifica si el dispositivo requiere manejo especial para servicios background
     */
    fun requiresSpecialHandling(): Boolean {
        return when (detectManufacturer()) {
            is DeviceManufacturer.Generic -> false
            else -> true
        }
    }
    
    /**
     * Obtiene el intervalo recomendado de verificación para el dispositivo (en ms)
     */
    fun getRecommendedCheckInterval(): Long {
        return when (detectManufacturer()) {
            is DeviceManufacturer.Xiaomi -> 8 * 60 * 1000L      // 8 minutos
            is DeviceManufacturer.Huawei -> 8 * 60 * 1000L      // 8 minutos
            is DeviceManufacturer.Samsung -> 15 * 60 * 1000L    // 15 minutos
            is DeviceManufacturer.Oppo -> 10 * 60 * 1000L       // 10 minutos
            is DeviceManufacturer.Vivo -> 10 * 60 * 1000L       // 10 minutos
            is DeviceManufacturer.OnePlus -> 15 * 60 * 1000L    // 15 minutos
            is DeviceManufacturer.Generic -> 20 * 60 * 1000L    // 20 minutos
        }
    }
}
