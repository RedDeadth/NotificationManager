package com.dynamictecnologies.notificationmanager.ui.screen.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }

    val authState by authViewModel.authState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Configurar el launcher para el inicio de sesión con Google
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        authViewModel.handleGoogleSignInResult(result.data)
    }
    
    // Observar el estado de autenticación exitosa
    LaunchedEffect(authState.isAuthenticated) {
        if (authState.isAuthenticated) {
            onLoginSuccess()
        }
    }
    
    // Mostrar errores en Snackbar
    LaunchedEffect(authState.error) {
        authState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Short
            )
            authViewModel.clearError()
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
            text = "Bienvenido usuario, ¿cómo deberíamos llamarte?",
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        // Campo para el nombre de usuario
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Nombre") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !authState.isLoading,
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            enabled = !authState.isLoading,
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { authViewModel.signInWithEmail(email, password) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !authState.isLoading && email.isNotBlank() && password.isNotBlank()
        ) {
            if (authState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                kotlinx.coroutines.MainScope().launch {
                    authViewModel.getGoogleSignInIntent().collect { intent ->
                        googleSignInLauncher.launch(intent)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !authState.isLoading
        ) {
            Text("Iniciar Sesión con Google")
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onNavigateToRegister,
            enabled = !authState.isLoading
        ) {
            Text("¿No tienes cuenta? Regístrate")
        }
        }
    }
}