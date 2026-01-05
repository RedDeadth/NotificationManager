package com.dynamictecnologies.notificationmanager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.dynamictecnologies.notificationmanager.di.BluetoothMqttModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receiver para detectar cambios en el estado de la red.
 * 
 * Cuando la red vuelve a estar disponible (por ejemplo, al salir del 
 * modo Doze o al cambiar de WiFi a datos móviles), intenta reconectar MQTT.
 * 
 * Esto es crucial para mantener la conexión MQTT activa porque:
 * - Doze mode suspende la red periódicamente
 * - Cambios de red (WiFi↔Mobile) pueden desconectar MQTT
 * - El usuario puede apagar/encender WiFi manualmente
 */
class NetworkStateReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "NetworkStateReceiver"
        private var lastKnownConnected = false
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ConnectivityManager.CONNECTIVITY_ACTION) {
            return
        }
        
        val isConnected = isNetworkAvailable(context)
        Log.d(TAG, "Cambio de conectividad detectado: connected=$isConnected")
        
        // Solo actuar si pasamos de desconectado a conectado
        if (isConnected && !lastKnownConnected) {
            Log.d(TAG, "Red recuperada - intentando reconectar MQTT")
            tryReconnectMqtt(context)
        }
        
        lastKnownConnected = isConnected
    }
    
    /**
     * Verifica si hay conectividad de red disponible.
     */
    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
            as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.isConnected == true
        }
    }
    
    /**
     * Intenta reconectar MQTT de forma asíncrona.
     */
    private fun tryReconnectMqtt(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val mqttManager = BluetoothMqttModule.provideMqttConnectionManager(context)
                
                if (!mqttManager.isConnected()) {
                    Log.d(TAG, "MQTT desconectado, intentando reconectar...")
                    val result = mqttManager.connect()
                    
                    if (result.isSuccess) {
                        Log.d(TAG, "MQTT reconectado exitosamente después de cambio de red")
                    } else {
                        Log.w(TAG, "Fallo reconexión MQTT: ${result.exceptionOrNull()?.message}")
                    }
                } else {
                    Log.d(TAG, "MQTT ya está conectado")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en reconexión MQTT: ${e.message}", e)
            }
        }
    }
}
