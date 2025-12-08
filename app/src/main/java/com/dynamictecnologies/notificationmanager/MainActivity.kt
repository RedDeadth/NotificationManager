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

import com.dynamictecnologies.notificationmanager.presentation.core.theme.NotificationManagerTheme
import com.dynamictecnologies.notificationmanager.util.PermissionHelper
import com.dynamictecnologies.notificationmanager.viewmodel.*
import com.dynamictecnologies.notificationmanager.di.AuthModule
import com.dynamictecnologies.notificationmanager.di.AppModule
import com.dynamictecnologies.notificationmanager.di.DeviceModule
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.app.AlertDialog
import com.dynamictecnologies.notificationmanager.domain.repositories.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class MainActivity : ComponentActivity() {

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

    private val deviceViewModel: DeviceViewModel by viewModels {
        DeviceViewModelFactory(
            connectToMqttUseCase = DeviceModule.provideConnectToMqttUseCase(applicationContext),
            disconnectFromMqttUseCase = DeviceModule.provideDisconnectFromMqttUseCase(applicationContext),
            searchDevicesUseCase = DeviceModule.provideSearchDevicesUseCase(applicationContext),
            sendNotificationUseCase = DeviceModule.provideSendNotificationViaMqttUseCase(applicationContext),
            connectToDeviceUseCase = DeviceModule.provideConnectToDeviceUseCase(),
            unlinkDeviceUseCase = DeviceModule.provideUnlinkDeviceUseCase(),
            observeDeviceUseCase = DeviceModule.provideObserveDeviceConnectionUseCase(),
            getUsernameUseCase = DeviceModule.provideGetUsernameByUidUseCase()
        )
    }

    private val shareViewModel: ShareViewModel by viewModels {
        ShareViewModelFactory()
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
                    Log.d("MainActivity", "✅ Permisos otorgados - notificando al repositorio")
                    notifyPermissionGranted()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // IMPORTANTE: Resetear estado del servicio cuando usuario abre la app
        ServiceStateManager.resetOnAppOpen(this)
        
        // Registrar el receiver para permisos
        registerPermissionReceiver()

        initializeFirebase()
        
        // Pedir permiso POST_NOTIFICATIONS antes de iniciar servicio (Android 13+)
        requestNotificationPermissionAndStartService()

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
                        shareViewModel = shareViewModel,
                        deviceViewModel = deviceViewModel
                    )
                }
            }
        }

        // Verificar permisos al iniciar (con retraso para que la UI se estabilice)
        checkPermissionsOnStartup()
    }

    override fun onResume() {
        super.onResume()
        // Verificar permisos cada vez que la app vuelve al foco
        checkPermissionsOnResume()
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
                    Log.d("MainActivity", "✅ POST_NOTIFICATIONS ya otorgado")
                    startNotificationService()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Mostrar explicación antes de pedir permiso
                    showNotificationPermissionRationale()
                }
                else -> {
                    // Pedir permiso directamente
                    Log.d("MainActivity", "📱 Pidiendo permiso POST_NOTIFICATIONS")
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
        // ✅ CORRECCIÓN PARA ANDROID 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                permissionBroadcastReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED  // ← REQUERIDO EN ANDROID 13+
            )
        } else {
            registerReceiver(permissionBroadcastReceiver, filter)
        }
        Log.d("MainActivity", "✅ BroadcastReceiver de permisos registrado")
    } catch (e: Exception) {
        Log.e("MainActivity", "❌ Error registrando BroadcastReceiver: ${e.message}")
    }
}

    /**
     * Verificación de permisos al iniciar la app
     */
    private fun checkPermissionsOnStartup() {
        if (!PermissionHelper.hasNotificationListenerPermission(this)) {
            Log.w("MainActivity", "⚠️ App iniciada sin permisos de NotificationListener")

            // Mostrar diálogo después de un breve retraso para que la UI se estabilice
            Handler(Looper.getMainLooper()).postDelayed({
                showPermissionDialog()
            }, 2000) // 2 segundos de retraso
        } else {
            Log.d("MainActivity", "✅ Permisos de notificación activos al iniciar")
        }
    }

    /**
     * Verificación al volver a la app (el usuario pudo haber otorgado permisos)
     */
    private fun checkPermissionsOnResume() {
        if (PermissionHelper.hasNotificationListenerPermission(this)) {
            Log.d("MainActivity", "✅ Permisos confirmados en onResume")
            notifyPermissionGranted()
        } else {
            Log.w("MainActivity", "⚠️ Sin permisos en onResume")
        }
    }

    /**
     * Muestra el diálogo de permisos
     */
    private fun showPermissionDialog() {
        if (!isFinishing && !isDestroyed) {
            Log.d("MainActivity", "📱 Mostrando diálogo de permisos")
            PermissionHelper.showNotificationPermissionDialog(this)
        } else {
            Log.w("MainActivity", "⚠️ Activity terminando - no se muestra diálogo")
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

            Log.d("MainActivity", "✅ Repositorio notificado sobre permisos otorgados")
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Error notificando permisos: ${e.message}")
        }
    }

    private fun initializeFirebase() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
            FirebaseDatabase.getInstance().apply {
                setPersistenceEnabled(true)
            }
            Log.d("MainActivity", "✅ Firebase inicializado correctamente")
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Error inicializando Firebase: ${e.message}", e)
        }
    }

    private fun startNotificationService() {
        try {
            val serviceIntent = Intent(this, NotificationForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Log.d("MainActivity", "✅ Servicio de notificaciones iniciado")
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Error iniciando servicio: ${e.message}", e)
        }
    }

    private fun createRepository(): NotificationRepository {
        val database = NotificationDatabase.getDatabase(applicationContext)
        return NotificationRepository(
            notificationDao = database.notificationDao(),
            context = applicationContext
        )
    }
}