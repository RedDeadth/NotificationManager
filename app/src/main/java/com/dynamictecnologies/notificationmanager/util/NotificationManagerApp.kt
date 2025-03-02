package com.dynamictecnologies.notificationmanager.util

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase

class NotificationManagerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            // Inicializar Firebase
            FirebaseApp.initializeApp(this)

            // Configurar Firebase Database
            FirebaseDatabase.getInstance().apply {
                setPersistenceEnabled(true)
            }

            Log.d("NotificationManagerApp", "Firebase inicializado correctamente")
        } catch (e: Exception) {
            Log.e("NotificationManagerApp", "Error inicializando Firebase: ${e.message}")
        }
    }
}