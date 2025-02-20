package com.dynamictecnologies.notificationmanager

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.dynamictecnologies.notificationmanager.data.db.NotificationDatabase
import com.dynamictecnologies.notificationmanager.data.repository.NotificationRepository
import com.dynamictecnologies.notificationmanager.service.NotificationListenerService
import com.dynamictecnologies.notificationmanager.ui.AppListScreen
import com.dynamictecnologies.notificationmanager.ui.theme.NotificationManagerTheme
import com.dynamictecnologies.notificationmanager.viewmodel.AppListViewModel
import com.dynamictecnologies.notificationmanager.viewmodel.AppListViewModelFactory

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    private val NOTIFICATION_PERMISSION_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verificar permisos primero
        checkAndRequestPermissions()

        val database = NotificationDatabase.getDatabase(applicationContext)
        val repository = NotificationRepository(database.notificationDao())

        val viewModel: AppListViewModel by viewModels {
            AppListViewModelFactory(applicationContext, repository)
        }

        setContent {
            NotificationManagerTheme {
                AppListScreen(viewModel = viewModel)
            }
        }
    }

    private fun checkAndRequestPermissions() {
        // Verificar permiso de notificaciones para Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_CODE
                )
            }
        }

        // Verificar permiso de NotificationListener
        if (!isNotificationListenerEnabled()) {
            showNotificationListenerDialog()
        } else {
            // Si ya tenemos el permiso, asegurémonos de que el servicio esté activo
            toggleNotificationListenerService()
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val packageName = packageName
        val flat = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )
        val enabled = flat?.contains(packageName) ?: false
        Log.d(TAG, "NotificationListener habilitado: $enabled")
        Log.d(TAG, "Listeners activos: $flat")
        return enabled
    }

    private fun showNotificationListenerDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permiso requerido")
            .setMessage("Para recibir notificaciones, necesitamos acceso a ellas. " +
                    "Por favor, habilita el acceso para Notification Manager en la siguiente pantalla.")
            .setPositiveButton("Configurar") { _, _ ->
                startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun toggleNotificationListenerService() {
        Log.d(TAG, "Reconectando servicio de notificaciones...")

        val componentName = ComponentName(this, NotificationListenerService::class.java)

        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )

        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )

        Log.d(TAG, "Servicio de notificaciones reconectado")
    }

    override fun onResume() {
        super.onResume()
        // Verificar permisos cada vez que la app vuelve a primer plano
        if (!isNotificationListenerEnabled()) {
            showNotificationListenerDialog()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            NOTIFICATION_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Permiso de notificaciones concedido")
                } else {
                    Log.w(TAG, "Permiso de notificaciones denegado")
                    // Mostrar diálogo explicando por qué necesitamos el permiso
                    AlertDialog.Builder(this)
                        .setTitle("Permiso necesario")
                        .setMessage("Sin este permiso, la app no podrá mostrar notificaciones importantes.")
                        .setPositiveButton("Reintentar") { _, _ ->
                            checkAndRequestPermissions()
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                }
            }
        }
    }
}