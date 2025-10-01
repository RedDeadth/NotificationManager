package com.dynamictecnologies.notificationmanager.ui.screen.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamictecnologies.notificationmanager.viewmodel.AuthViewModel

@Composable
fun RegisterScreen(
    authViewModel: AuthViewModel,
    onNavigateToLogin: () -> Unit,
    onRegisterSuccess: () -> Unit = {}
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    val authState by authViewModel.authState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Observar el estado de autenticación exitosa
    LaunchedEffect(authState.isAuthenticated) {
        if (authState.isAuthenticated) {
            onRegisterSuccess()
        }
    }
    
    // Mostrar errores en Snackbar
    LaunchedEffect(authState.error, localError) {
        authState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Short
            )
            authViewModel.clearError()
        }
        localError?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Short
            )
            localError = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
        // Mensaje de bienvenida
        Text(
            text = "Hola, empecemos por crear una cuenta",
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        // Campo de email
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !authState.isLoading,
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Campo de contraseña
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            enabled = !authState.isLoading,
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Campo de confirmar contraseña
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirmar Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            enabled = !authState.isLoading,
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                when {
                    email.isBlank() -> {
                        localError = "El email es requerido"
                    }
                    password.isBlank() -> {
                        localError = "La contraseña es requerida"
                    }
                    password != confirmPassword -> {
                        localError = "Las contraseñas no coinciden"
                    }
                    password.length < 6 -> {
                        localError = "La contraseña debe tener al menos 6 caracteres"
                    }
                    else -> {
                        authViewModel.registerWithEmail(email, password)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !authState.isLoading
        ) {
            if (authState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("Registrarse")
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onNavigateToLogin,
            enabled = !authState.isLoading
        ) {
            Text("¿Ya tienes cuenta? Inicia sesión")
        }
        }
    }
}