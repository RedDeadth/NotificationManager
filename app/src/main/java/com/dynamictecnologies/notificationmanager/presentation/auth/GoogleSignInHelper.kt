package com.dynamictecnologies.notificationmanager.presentation.auth

import android.content.Context
import android.content.Intent
import com.dynamictecnologies.notificationmanager.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

/**
 * Helper para manejar Google Sign In.
 * Encapsula la lógica específica de Android para Google Sign In.
 *
 * Principios aplicados:
 * - SRP: Solo maneja la configuración y obtención de intents de Google Sign In
 * - Clean Architecture: Mantiene detalles de Android fuera del dominio
 */
class GoogleSignInHelper(private val context: Context) {

    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    /**
     * Obtiene el intent de Google Sign In
     */
    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    /**
     * Extrae el idToken del resultado de Google Sign In
     */
    @Throws(ApiException::class)
    fun getIdTokenFromIntent(data: Intent?): String {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        val account = task.getResult(ApiException::class.java)
        return account.idToken ?: throw IllegalStateException("No se pudo obtener el token de Google")
    }

    /**
     * Cierra sesión de Google
     */
    suspend fun signOut() {
        googleSignInClient.signOut()
    }
}
