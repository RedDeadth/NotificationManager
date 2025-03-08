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
import com.dynamictecnologies.notificationmanager.service.UserService
import com.dynamictecnologies.notificationmanager.ui.theme.NotificationManagerTheme
import com.dynamictecnologies.notificationmanager.util.PermissionManager
import com.dynamictecnologies.notificationmanager.viewmodel.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.dynamictecnologies.notificationmanager.viewmodel.UserViewModel
import com.dynamictecnologies.notificationmanager.viewmodel.ShareViewModel
import com.dynamictecnologies.notificationmanager.viewmodel.UserViewModelFactory

class MainActivity : ComponentActivity() {
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

    private val userViewModel: UserViewModel by viewModels {
        UserViewModelFactory(createUserService())
    }
    private val sharedViewModel: ShareViewModel by viewModels {
        ShareViewModelFactory(
            userService = createUserService(),
            notificationService = createFirebaseService()  // Añadir el servicio de notificaciones
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeFirebase()

        setContent {
            NotificationManagerTheme {
                AppNavigation(
                    authViewModel = authViewModel,
                    permissionViewModel = permissionViewModel,
                    appListViewModel = appListViewModel,
                    userViewModel = userViewModel,
                    sharedViewModel = sharedViewModel,
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
    private fun createUserService(): UserService {
        return UserService(
            FirebaseAuth.getInstance(),
            FirebaseDatabase.getInstance()
        )
    }
    private fun createFirebaseService(): FirebaseService {
        return FirebaseService()
    }
}

