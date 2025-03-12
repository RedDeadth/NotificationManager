package com.dynamictecnologies.notificationmanager.data.repository

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.dynamictecnologies.notificationmanager.R
import com.dynamictecnologies.notificationmanager.service.UserService
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val context: Context,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val userService: UserService

) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "auth_prefs",
        Context.MODE_PRIVATE
    )

    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    fun getCurrentUser(): FirebaseUser? {
        val user = auth.currentUser
        if (user != null) {
            saveUserSession(user)
        }
        return user
    }

    suspend fun signInWithEmail(email: String, password: String): FirebaseUser {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        saveUserSession(result.user!!)
        return result.user!!
    }

    suspend fun registerWithEmail(email: String, password: String): FirebaseUser {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        saveUserSession(result.user!!)
        return result.user!!
    }

    fun getGoogleSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    suspend fun handleGoogleSignIn(idToken: String): FirebaseUser {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        saveUserSession(result.user!!)
        return result.user!!
    }

    fun signOut() {
        auth.signOut()
        googleSignInClient.signOut()
        clearUserSession()
        userService.cleanup()
    }

    private fun saveUserSession(user: FirebaseUser) {
        prefs.edit().apply {
            putString("user_id", user.uid)
            putString("user_email", user.email)
            apply()
        }
    }

    private fun clearUserSession() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val TAG = "AuthRepository"
    }
}