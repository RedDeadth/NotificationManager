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
import com.dynamictecnologies.notificationmanager.viewmodel.*
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
import com.dynamictecnologies.notificationmanager.viewmodel.AuthViewModel.AuthViewModelFactory

class MainActivity : ComponentActivity() {

    // ViewModels
    private val userViewModel: UserViewModel by viewModels {
        UserViewModelFactory(
            auth = FirebaseAuth.getInstance(),
            database = FirebaseDatabase.getInstance()
        )
    }

    private val authViewModel: AuthViewModel by viewModels {
        val auth = FirebaseAuth.getInstance()
        val database = FirebaseDatabase.getInstance()

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

    private val appListViewModel: AppListViewModel by viewModels {
        AppListViewModelFactory(applicationContext, createRepository())
    }

    private val deviceViewModel: DeviceViewModel by viewModels {
        DeviceViewModelFactory(applicationContext)
    }

    private val shareViewModel: ShareViewModel by viewModels {
        ShareViewModelFactory()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    }

    override fun onDestroy() {
        super.onDestroy()
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
            Log.e("MainActivity", "Error inicializando Firebase: ${e.message}", e)
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
        } catch (e: Exception) {
            Log.e("MainActivity", "Error iniciando servicio: ${e.message}", e)
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