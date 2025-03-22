package com.dynamictecnologies.notificationmanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.dynamictecnologies.notificationmanager.data.db.NotificationDatabase
import com.dynamictecnologies.notificationmanager.data.repository.AuthRepository
import com.dynamictecnologies.notificationmanager.data.repository.NotificationRepository
import com.dynamictecnologies.notificationmanager.navigation.AppNavigation
import com.dynamictecnologies.notificationmanager.service.FirebaseService
import com.dynamictecnologies.notificationmanager.service.NotificationForegroundService
import com.dynamictecnologies.notificationmanager.service.NotificationListenerService
import com.dynamictecnologies.notificationmanager.service.UserService
import com.dynamictecnologies.notificationmanager.ui.theme.NotificationManagerTheme
import com.dynamictecnologies.notificationmanager.util.PermissionManager
import com.dynamictecnologies.notificationmanager.viewmodel.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val permissionManager by lazy {
        PermissionManager(this)
    }

    // Actualiza la creación del UserService
    private val userViewModel: UserViewModel by viewModels {
        UserViewModelFactory(
            auth = FirebaseAuth.getInstance(),
            database = FirebaseDatabase.getInstance()
        )
    }

    private val authViewModel: AuthViewModel by viewModels {
        val auth = FirebaseAuth.getInstance()
        val database = FirebaseDatabase.getInstance()
        
        // Usar el mismo scope que se usará en UserViewModel para consistencia
        val userService = UserService(
            auth = auth,
            database = database,
            scope = lifecycleScope
        )
        
        AuthViewModelFactory(
            AuthRepository(
                context = this,
                auth = auth,
                userService = userService
            )
        )
    }

    private val permissionViewModel: PermissionViewModel by viewModels {
        PermissionViewModelFactory(permissionManager)
    }

    private val appListViewModel: AppListViewModel by viewModels {
        AppListViewModelFactory(applicationContext, createRepository())
    }


    private val shareViewModel: ShareViewModel by viewModels {
        ShareViewModelFactory()
    }

    private var showPermissionDialog = mutableStateOf(false)
    
    private var permissionReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeFirebase()
        
        // Verificar si el servicio de notificaciones está habilitado
        if (!NotificationListenerService.isNotificationListenerEnabled(this)) {
            showPermissionDialog.value = true
        }
        
        // Registrar receptor para mostrar el diálogo de permisos
        permissionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.dynamictecnologies.notificationmanager.SHOW_PERMISSION_DIALOG") {
                    showPermissionDialog.value = true
                }
            }
        }
        
        registerReceiver(
            permissionReceiver,
            IntentFilter("com.dynamictecnologies.notificationmanager.SHOW_PERMISSION_DIALOG")
        )
        
        // Iniciar el servicio en primer plano
        val serviceIntent = Intent(this, NotificationForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        
        setContent {
            NotificationManagerTheme {
                var showDialog by remember { showPermissionDialog }
                
                // Diálogo de permisos de notificación
                if (showDialog) {
                    NotificationPermissionDialog(
                        onDismiss = { showDialog = false },
                        onConfirm = {
                            showDialog = false
                            NotificationListenerService.openNotificationListenerSettings(this)
                        }
                    )
                }
                
                // Contenido principal de la aplicación
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    
                    AppNavigation(
                        authViewModel = authViewModel,
                        permissionViewModel = permissionViewModel,
                        appListViewModel = appListViewModel,
                        userViewModel = userViewModel,
                        shareViewModel = shareViewModel,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionViewModel.checkPermissions()
        
        // Re-verificar permisos cuando la actividad vuelve a primer plano
        val hasPermissions = NotificationListenerService.isNotificationListenerEnabled(this)
        val prefs = getSharedPreferences("notification_listener_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("permissions_enabled", hasPermissions).apply()
        
        // Si se necesita reiniciar el servicio después de obtener permisos
        if (hasPermissions && !prefs.getBoolean("service_started_after_permission", false)) {
            NotificationListenerService.requestServiceReset(this)
            prefs.edit().putBoolean("service_started_after_permission", true).apply()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Desregistrar el receptor de transmisión
        permissionReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error al desregistrar receptor: ${e.message}")
            }
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createRepository(): NotificationRepository {
        val database = NotificationDatabase.getDatabase(applicationContext)
        val firebaseService = FirebaseService()
        return NotificationRepository(
            notificationDao = database.notificationDao(),
            firebaseService = firebaseService,
            context = applicationContext
        )
    }
    private fun createFirebaseService(): FirebaseService {
        return FirebaseService()
    }
}

@Composable
fun NotificationPermissionDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permisos necesarios") },
        text = { 
            Text(
                "Para que la aplicación pueda monitorear tus notificaciones, debes habilitarla en la configuración del sistema.\n\n" +
                "En la pantalla siguiente, encuentra 'NotificationManager' y actívalo."
            ) 
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Habilitar permisos")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Más tarde")
            }
        }
    )
}

