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
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.dynamictecnologies.notificationmanager.data.db.NotificationDatabase
import com.dynamictecnologies.notificationmanager.data.repository.NotificationRepository
import com.dynamictecnologies.notificationmanager.navigation.AppNavigation
import com.dynamictecnologies.notificationmanager.service.FirebaseService
import com.dynamictecnologies.notificationmanager.service.NotificationForegroundService
import com.dynamictecnologies.notificationmanager.service.NotificationListenerService
import com.dynamictecnologies.notificationmanager.service.UserService
import com.dynamictecnologies.notificationmanager.ui.theme.NotificationManagerTheme
import com.dynamictecnologies.notificationmanager.util.PermissionHelper
import com.dynamictecnologies.notificationmanager.viewmodel.*
import com.dynamictecnologies.notificationmanager.di.AuthModule
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class MainActivity : ComponentActivity() {

    // UserService para inyección de dependencias
    private val userService: UserService by lazy {
        UserService(
            auth = FirebaseAuth.getInstance(),
            database = FirebaseDatabase.getInstance(),
            scope = CoroutineScope(SupervisorJob())
        )
    }

    // ViewModels
    private val authViewModel: AuthViewModel by viewModels {
        AuthModule.provideAuthViewModelFactory(
            context = applicationContext,
            userService = userService
        )
    }

    private val userViewModel: UserViewModel by viewModels {
        UserViewModelFactory(
            auth = FirebaseAuth.getInstance(),
            database = FirebaseDatabase.getInstance()
        )
    }

    private val appListViewModel: AppListViewModel by viewModels {
        AppListViewModelFactory(applicationContext, createRepository())
    }

    private val deviceViewModel: DeviceViewModel by viewModels {
        DeviceViewModelFactory(applicationContext)
    }

    private val shareViewModel: ShareViewModel by viewModels {
        ShareViewModelFactory()
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

        // Registrar el receiver para permisos
        registerPermissionReceiver()

        initializeFirebase()
        startNotificationService()

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
            registerReceiver(permissionBroadcastReceiver, filter)
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
        val firebaseService = FirebaseService(applicationContext)
        return NotificationRepository(
            notificationDao = database.notificationDao(),
            firebaseService = firebaseService,
            context = applicationContext
        )
    }
}