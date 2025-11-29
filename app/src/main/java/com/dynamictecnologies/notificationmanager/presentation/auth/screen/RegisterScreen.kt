package com.dynamictecnologies.notificationmanager.presentation.auth.screen

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
    onNavigateToLogin: () -> Unit
) {
    val authState by authViewModel.authState.collectAsState()
    val registerFormState by authViewModel.registerFormState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(authState.isAuthenticated) {
        if (authState.isAuthenticated) {
            authViewModel.clearRegisterForm()
            onNavigateToLogin()
        }
    }
    
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
            Text(
                text = "Crear nueva cuenta",
                fontSize = 24.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            OutlinedTextField(
                value = registerFormState.username,
                onValueChange = { authViewModel.updateRegisterUsername(it) },
                label = { Text("Nombre de usuario") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !authState.isLoading,
                isError = registerFormState.usernameError != null,
                supportingText = registerFormState.usernameError?.let { { Text(it) } },
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = registerFormState.email,
                onValueChange = { authViewModel.updateRegisterEmail(it) },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !authState.isLoading,
                isError = registerFormState.emailError != null,
                supportingText = registerFormState.emailError?.let { { Text(it) } },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = registerFormState.password,
                onValueChange = { authViewModel.updateRegisterPassword(it) },
                label = { Text("Contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                enabled = !authState.isLoading,
                isError = registerFormState.passwordError != null,
                supportingText = registerFormState.passwordError?.let { { Text(it) } },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = registerFormState.confirmPassword,
                onValueChange = { authViewModel.updateRegisterConfirmPassword(it) },
                label = { Text("Confirmar contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                enabled = !authState.isLoading,
                isError = registerFormState.confirmPasswordError != null,
                supportingText = registerFormState.confirmPasswordError?.let { { Text(it) } },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { 
                    authViewModel.registerWithEmail(
                        registerFormState.email,
                        registerFormState.password,
                        registerFormState.confirmPassword,
                        registerFormState.username
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !authState.isLoading && registerFormState.isFormValid
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
