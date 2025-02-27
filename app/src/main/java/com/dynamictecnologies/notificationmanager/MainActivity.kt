package com.dynamictecnologies.notificationmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.dynamictecnologies.notificationmanager.data.db.NotificationDatabase
import com.dynamictecnologies.notificationmanager.data.repository.NotificationRepository
import com.dynamictecnologies.notificationmanager.service.FirebaseService
import com.dynamictecnologies.notificationmanager.ui.screen.PermissionScreen
import com.dynamictecnologies.notificationmanager.ui.theme.NotificationManagerTheme
import com.dynamictecnologies.notificationmanager.util.PermissionManager
import com.dynamictecnologies.notificationmanager.viewmodel.AppListViewModel
import com.dynamictecnologies.notificationmanager.viewmodel.AppListViewModelFactory
import com.google.firebase.FirebaseApp
import com.dynamictecnologies.notificationmanager.viewmodel.PermissionViewModel
import com.dynamictecnologies.notificationmanager.viewmodel.PermissionViewModelFactory
import com.google.firebase.database.FirebaseDatabase

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"

    private val permissionViewModel: PermissionViewModel by viewModels {
        // Crear una instancia de PermissionManager primero
        PermissionViewModelFactory(PermissionManager(applicationContext))
    }

    private val appListViewModel: AppListViewModel by viewModels {
        AppListViewModelFactory(applicationContext, createRepository())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeFirebase()

        setContent {
            NotificationManagerTheme {
                PermissionScreen(
                    permissionViewModel = permissionViewModel,
                    appListViewModel = appListViewModel
                )
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
}