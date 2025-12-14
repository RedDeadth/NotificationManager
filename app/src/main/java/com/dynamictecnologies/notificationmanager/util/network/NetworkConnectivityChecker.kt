package com.dynamictecnologies.notificationmanager.util.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log

/**
 * Verificador de conectividad de red.
 * 
 * Responsabilidad única: Verificar disponibilidad de internet.
 * 
 * - Stateless: Solo verifica estado actual
 * - Reusable: Útil para cualquier componente que necesite red
 */
class NetworkConnectivityChecker(private val context: Context) {
    
    /**
     * Verifica si hay conexión a internet disponible
     */
    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        
        val network = connectivityManager.activeNetwork
        if (network == null) {
            Log.d(TAG, "Sin red activa")
            return false
        }
        
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        if (capabilities == null) {
            Log.d(TAG, "Sin capacidades de red")
            return false
        }
        
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        Log.d(TAG, "Estado de red: ${if (hasInternet) "Disponible" else "No disponible"}")
        
        return hasInternet
    }
    
    /**
     * Verifica tipo de conexión activa
     */
    fun getNetworkType(): NetworkType {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkType.NONE
        
        val network = connectivityManager.activeNetwork ?: return NetworkType.NONE
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkType.NONE
        
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            else -> NetworkType.OTHER
        }
    }
    
    /**
     * Tipos de red
     */
    enum class NetworkType {
        NONE,
        WIFI,
        CELLULAR,
        ETHERNET,
        OTHER
    }
    
    companion object {
        private const val TAG = "NetworkConnectivity"
    }
}
