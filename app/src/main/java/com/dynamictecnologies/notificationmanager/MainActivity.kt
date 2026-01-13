package com.dynamictecnologies.notificationmanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.dynamictecnologies.notificationmanager.data.db.NotificationDatabase
import com.dynamictecnologies.notificationmanager.data.repository.NotificationRepository
import com.dynamictecnologies.notificationmanager.presentation.core.navigation.AppNavigation
import com.dynamictecnologies.notificationmanager.service.NotificationForegroundService
import com.dynamictecnologies.notificationmanager.service.NotificationListenerService
import com.dynamictecnologies.notificationmanager.service.ServiceStateManager
import com.dynamictecnologies.notificationmanager.service.util.ServiceDeathDetector

import com.dynamictecnologies.notificationmanager.presentation.core.theme.NotificationManagerTheme
import com.dynamictecnologies.notificationmanager.util.PermissionHelper
import com.dynamictecnologies.notificationmanager.presentation.core.dialog.PermissionDialogContent
import com.dynamictecnologies.notificationmanager.viewmodel.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.dynamictecnologies.notificationmanager.di.AuthModule
import com.dynamictecnologies.notificationmanager.di.AppModule
import com.dynamictecnologies.notificationmanager.di.BluetoothMqttModule
import com.dynamictecnologies.notificationmanager.worker.ServiceHealthCheckWorker
import androidx.work.WorkManager
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Constraints
import java.util.concurrent.TimeUnit
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import com.dynamictecnologies.notificationmanager.domain.repositories.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    // Estado Compose para el diálogo de permisos
    private var showPermissionDialogState = mutableStateOf(false)

    // Crear authRepository compartido
    private val authRepository: AuthRepository by lazy {
        AuthModule.provideAuthRepository(
            context = applicationContext
        )
    }

    private val authViewModel: AuthViewModel by viewModels {
        AuthModule.provideAuthViewModelFactory(
            context = applicationContext
        )
    }

    private val userViewModel: UserViewModel by viewModels {
        AuthModule.provideUserViewModelFactory(authRepository)
    }

    private val appListViewModel: AppListViewModel by viewModels {
        AppModule.provideAppListViewModelFactory(
            context = applicationContext,
            notificationRepository = createRepository()
        )
    }

    // Nuevo ViewModel para pairing Bluetooth
    private val devicePairingViewModel: com.dynamictecnologies.notificationmanager.viewmodel.DevicePairingViewModel by viewModels {
        com.dynamictecnologies.notificationmanager.di.BluetoothMqttModule.provideDevicePairingViewModelFactory(applicationContext)
    }
    
    // Permission launcher para POST_NOTIFICATIONS (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d("MainActivity", "\u2705 Permiso POST_NOTIFICATIONS otorgado")
            // Iniciar servicio después de obtener permiso
            startNotificationService()
        } else {
            Log.w("MainActivity", "\u26a0\ufe0f Permiso POST_NOTIFICATIONS denegado")
            // Mostrar diálogo explicativo
            showNotificationPermissionRationale()
        }
    }
    
    // Permission launcher para permisos de Bluetooth
    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Log.d("MainActivity", "Permisos Bluetooth otorgados")
            checkAndEnableBluetooth()
        } else {
            Log.w("MainActivity", "Algunos permisos Bluetooth fueron denegados")
            showBluetoothPermissionRationale()
        }
    }

    // Launcher para habilitar Bluetooth
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.d("MainActivity", "Bluetooth habilitado por el usuario")
            devicePairingViewModel.startBluetoothScan()
        } else {
            Log.w("MainActivity", "Usuario rechazó habilitar Bluetooth")
            showBluetoothEnableRationale()
        }
    }
    
    /**
     * Solicita permisos de Bluetooth.
     * Una vez otorgados, verifica si Bluetooth está encendido.
     */
    private fun requestBluetoothPermissions() {
        if (PermissionHelper.hasBluetoothPermissions(this)) {
            Log.d("MainActivity", "Permisos Bluetooth ya otorgados")
            checkAndEnableBluetooth()
        } else {
            Log.d("MainActivity", "Solicitando permisos Bluetooth...")
            val permissions = PermissionHelper.getRequiredBluetoothPermissions()
            bluetoothPermissionLauncher.launch(permissions)
        }
    }
    
    /**
     * Verifica si Bluetooth está encendido y lo habilita si es necesario.
     */
    private fun checkAndEnableBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        
        if (bluetoothAdapter == null) {
            showBluetoothNotSupportedDialog()
            return
        }
        
        if (!bluetoothAdapter.isEnabled) {
            Log.d("MainActivity", "Bluetooth apagado, solicitando habilitarlo...")
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        } else {
            Log.d("MainActivity", "Bluetooth ya está encendido")
            devicePairingViewModel.startBluetoothScan()
        }
    }
    
    /**
     * Muestra diálogo cuando el dispositivo no soporta Bluetooth.
     */
    private fun showBluetoothNotSupportedDialog() {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Bluetooth No Disponible")
                .setMessage("Tu dispositivo no soporta Bluetooth.")
                .setPositiveButton("Entendido") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }
    
    /**
     * Muestra explicación de por qué se necesita habilitar Bluetooth.
     */
    private fun showBluetoothEnableRationale() {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Bluetooth Requerido")
                .setMessage("Para conectar con tu dispositivo ESP32, necesitas habilitar Bluetooth.\n\n¿Deseas habilitarlo ahora?")
                .setPositiveButton("Habilitar") { dialog, _ ->
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    enableBluetoothLauncher.launch(enableBtIntent)
                    dialog.dismiss()
                }
                .setNegativeButton("Ahora no") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }
    
    /**
     * Muestra explicación de por qué se necesitan permisos Bluetooth.
     */
    private fun showBluetoothPermissionRationale() {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Permisos Bluetooth")
                .setMessage("Esta app necesita permisos de Bluetooth para conectar con tu dispositivo ESP32.\\n\\n¿Deseas otorgar los permisos?")
                .setPositiveButton("Permitir") { dialog: android.content.DialogInterface, _: Int ->
                    val permissions = PermissionHelper.getRequiredBluetoothPermissions()
                    bluetoothPermissionLauncher.launch(permissions)
                    dialog.dismiss()
                }
                .setNegativeButton("Ahora no") { dialog: android.content.DialogInterface, _: Int ->
                    dialog.dismiss()
                    Log.w("MainActivity", "Usuario rechazó permisos Bluetooth")
                }
                .show()
        }
    }

    // BroadcastReceiver para manejar solicitudes de permisos
    private val permissionBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.dynamictecnologies.notificationmanager.NEED_PERMISSIONS" -> {
                    Log.d("MainActivity", "📢 Recibida solicitud de mostrar permisos")
                    showPermissionDialog()
                }
                "com.dynamictecnologies.notificationmanager.SHOW_PERMISSION_DIALOG" -> {
                    Log.d("MainActivity", "📢 Recibida solicitud de mostrar diálogo de permisos")
                    showPermissionDialog()
                }
                "com.dynamictecnologies.notificationmanager.PERMISSIONS_GRANTED" -> {
                    Log.d("MainActivity", "Permisos otorgados - notificando al repositorio")
                    notifyPermissionGranted()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // IMPORTANTE: Detectar si el servicio murió mientras la app estaba cerrada
        // Esto debe ir ANTES de resetOnAppOpen para poder verificar el estado previo
        ServiceDeathDetector.handleDeathOnAppStart(this)
        
        // IMPORTANTE: Resetear estado del servicio cuando usuario abre la app
        ServiceStateManager.resetOnAppOpen(this)
        
        // Registrar el receiver para permisos
        registerPermissionReceiver()

        // Firebase ya inicializado en NotificationManagerApp.kt
        
        // Pedir permiso POST_NOTIFICATIONS antes de iniciar servicio (Android 13+)
        requestNotificationPermissionAndStartService()
        
        // Nota: Permisos Bluetooth se solicitan SOLO cuando el usuario 
        // presiona el botón "Conectar" en la pantalla principal

        setContent {
            NotificationManagerTheme {
                // Contenido principal de la aplicación
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        authViewModel = authViewModel,
                        appListViewModel = appListViewModel,
                        userViewModel = userViewModel,
                        devicePairingViewModel = devicePairingViewModel,
                        requestBluetoothPermissions = { requestBluetoothPermissions() }
                    )
                    
                    // Diálogo de permisos Material3 (centralizado)
                    if (showPermissionDialogState.value) {
                        PermissionDialogContent(
                            onDismiss = {
                                showPermissionDialogState.value = false
                                Log.d(TAG, "Usuario pospuso configuración de permisos")
                            },
                            onGoToSettings = {
                                showPermissionDialogState.value = false
                                PermissionHelper.openNotificationListenerSettings(this@MainActivity)
                            }
                        )
                    }
                }
            }
        }
        
        // Programar watchdog de WorkManager
        scheduleServiceHealthCheckIfNeeded()

        // Verificar permisos al iniciar (con retraso para que la UI se estabilice)
        checkPermissionsOnStartup()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume - verificando permisos...")
        
        // Verificar permisos cada vez que la app vuelve al foco
        val hasNotificationListenerPermission = NotificationListenerService.isNotificationListenerEnabled(this)
        
        if (hasNotificationListenerPermission) {
            Log.d(TAG, "Permisos NotificationListener activos")
            
            // Cerrar diálogo de permisos si estaba abierto
            showPermissionDialogState.value = false
            
            notifyPermissionGranted()
            
            // Reiniciar servicio si no está corriendo
            startNotificationService()
        } else {
            // Solo mostrar diálogo si no está ya visible
            if (!showPermissionDialogState.value) {
                Log.w(TAG, "Sin permisos NotificationListener - mostrando diálogo")
                showPermissionDialog()
            }
        }
    }

    
    /**
     * Pide permiso de notificaciones POST_NOTIFICATIONS (Android 13+) e inicia servicio
     */
    private fun requestNotificationPermissionAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permiso ya otorgado
                    Log.d("MainActivity", "POST_NOTIFICATIONS ya otorgado")
                    startNotificationService()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Mostrar explicación antes de pedir permiso
                    showNotificationPermissionRationale()
                }
                else -> {
                    // Pedir permiso directamente
                    Log.d("MainActivity", "Pidiendo permiso POST_NOTIFICATIONS")
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // Android < 13, no necesita permiso POST_NOTIFICATIONS
            startNotificationService()
        }
    }
    
    /**
     * Muestra explicación de por qué necesitamos el permiso de notificaciones
     */
    private fun showNotificationPermissionRationale() {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Permiso de Notificaciones")
                .setMessage("Esta app necesita permiso para mostrar notificaciones persistentes que te informan cuando el servicio está activo.\n\n¿Deseas otorgar el permiso?")
                .setPositiveButton("Permitir") { dialog: android.content.DialogInterface, _: Int ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    dialog.dismiss()
                }
                .setNegativeButton("Ahora no") { dialog: android.content.DialogInterface, _: Int ->
                    dialog.dismiss()
                    Log.w("MainActivity", "Usuario rechazó permiso de notificaciones")
                }
                .show()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Desregistrar el receiver
        try {
            unregisterReceiver(permissionBroadcastReceiver)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error desregistrando receiver: ${e.message}")
        }
    }

    /**
     * Registra el BroadcastReceiver para permisos
     */
    private fun registerPermissionReceiver() {
    try {
        val filter = IntentFilter().apply {
            addAction("com.dynamictecnologies.notificationmanager.NEED_PERMISSIONS")
            addAction("com.dynamictecnologies.notificationmanager.SHOW_PERMISSION_DIALOG")
            addAction("com.dynamictecnologies.notificationmanager.PERMISSIONS_GRANTED")
        }
        // CORRECCIÓN PARA ANDROID 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                permissionBroadcastReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED  // ← REQUERIDO EN ANDROID 13+
            )
        } else {
            registerReceiver(permissionBroadcastReceiver, filter)
        }
        Log.d("MainActivity", "BroadcastReceiver de permisos registrado")
    } catch (e: Exception) {
        Log.e("MainActivity", "Error registrando BroadcastReceiver: ${e.message}")
    }
}

    /**
     * Verifica y muestra diálogo de permisos al iniciar con retraso.
     */
    private fun checkPermissionsOnStartup() {
        // Esperar a que la UI se estabilice antes de verificar permisos
        Handler(Looper.getMainLooper()).postDelayed({
            val hasPermissions = NotificationListenerService.isNotificationListenerEnabled(this)
            if (!hasPermissions && !showPermissionDialogState.value) {
                Log.w("MainActivity", "Permisos de notificación no otorgados al iniciar")
                showPermissionDialog()
            }
        }, 2000) // 2 segundos
    }
    
    /**
     * Programa el watchdog de WorkManager si aún no está programado.
     * Esto asegura que el monitoreo esté siempre activo.
     */
    private fun scheduleServiceHealthCheckIfNeeded() {
        try {
            val workRequest = PeriodicWorkRequestBuilder<ServiceHealthCheckWorker>(
                15, TimeUnit.MINUTES // Cada 15 minutos (mínimo de Android)
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(false) // IMPORTANTE: ejecutar siempre
                        .build()
                )
                .addTag("service_health_check")
                .build()
            
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "service_health_check",
                ExistingPeriodicWorkPolicy.KEEP, // No reemplazar si ya existe
                workRequest
            )
            
            Log.d(TAG, "Watchdog WorkManager verificado/programado desde MainActivity")
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando watchdog: ${e.message}", e)
        }
    }

    /**
     * Verificación al volver a la app (el usuario pudo haber otorgado permisos)
     */
    private fun checkPermissionsOnResume() {
        if (PermissionHelper.hasNotificationListenerPermission(this)) {
            Log.d("MainActivity", "Permisos confirmados en onResume")
            notifyPermissionGranted()
        } else {
            Log.w("MainActivity", "Sin permisos en onResume")
        }
    }

    /**
     * Muestra el diálogo de permisos
     */
    private fun showPermissionDialog() {
        // Evitar múltiples diálogos
        if (showPermissionDialogState.value) {
            Log.d("MainActivity", "Diálogo de permisos ya visible - ignorando")
            return
        }
        
        // Verificar si ya tiene permisos (usuario pudo otorgarlos)
        if (NotificationListenerService.isNotificationListenerEnabled(this)) {
            Log.d("MainActivity", "Permisos ya otorgados - no se muestra diálogo")
            return
        }
        
        if (!isFinishing && !isDestroyed) {
            Log.d("MainActivity", "Mostrando diálogo de permisos (Compose)")
            showPermissionDialogState.value = true
        } else {
            Log.w("MainActivity", "Activity terminando - no se muestra diálogo")
        }
    }

    /**
     * Notifica que los permisos fueron otorgados
     */
    private fun notifyPermissionGranted() {
        try {
            // Notificar al repositorio que rechecke permisos
            val repository = createRepository()
            repository.recheckPermissions()

            Log.d("MainActivity", "Repositorio notificado sobre permisos otorgados")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error notificando permisos: ${e.message}")
        }
    }

    // ELIMINADO: initializeFirebase() - ya está en NotificationManagerApp.kt
    // setPersistenceEnabled() solo puede llamarse una vez

    private fun startNotificationService() {
        try {
            val serviceIntent = Intent(this, NotificationForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Log.d("MainActivity", "Servicio de notificaciones iniciado")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error iniciando servicio: ${e.message}", e)
        }
    }

    /**
     * Provee NotificationRepository como singleton via AppModule.
     * ANTES: Creaba nueva instancia cada vez (memory leak potencial).
     * AHORA: Usa singleton con double-checked locking.
     */
    private fun createRepository(): NotificationRepository {
        return AppModule.provideNotificationRepository(applicationContext)
    }
}