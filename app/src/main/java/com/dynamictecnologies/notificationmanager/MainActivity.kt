package com.dynamictecnologies.notificationmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.navigation.compose.rememberNavController
import com.dynamictecnologies.notificationmanager.data.db.NotificationDatabase
import com.dynamictecnologies.notificationmanager.data.repository.AuthRepository
import com.dynamictecnologies.notificationmanager.data.repository.NotificationRepository
import com.dynamictecnologies.notificationmanager.navigation.AppNavigation
import com.dynamictecnologies.notificationmanager.service.FirebaseService
import com.dynamictecnologies.notificationmanager.ui.theme.NotificationManagerTheme
import com.dynamictecnologies.notificationmanager.util.PermissionManager
import com.dynamictecnologies.notificationmanager.viewmodel.*
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"

    private val permissionManager by lazy {
        PermissionManager(this)
    }

    // ViewModels
    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModelFactory(AuthRepository(this))
    }

    private val permissionViewModel: PermissionViewModel by viewModels {
        PermissionViewModelFactory(permissionManager)
    }

    private val appListViewModel: AppListViewModel by viewModels {
        AppListViewModelFactory(applicationContext, createRepository())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeFirebase()

        setContent {
            NotificationManagerTheme {
                AppNavigation(
                    authViewModel = authViewModel,
                    permissionViewModel = permissionViewModel,
                    appListViewModel = appListViewModel
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionViewModel.checkPermissions()
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
}

