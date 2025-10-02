# Ejemplo de Uso - Nueva Arquitectura de Autenticación

## 🚀 Inicio Rápido

### 1. Configuración Inicial en tu Activity

```kotlin
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.dynamictecnologies.notificationmanager.di.AuthModule
import com.dynamictecnologies.notificationmanager.viewmodel.AuthViewModelNew
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    
    private lateinit var authViewModel: AuthViewModelNew
    
    // Launcher para Google Sign In
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        authViewModel.handleGoogleSignInResult(result.data)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        
        // Inicializar ViewModel con el factory del módulo DI
        initViewModel()
        
        // Configurar listeners
        setupListeners()
        
        // Observar estado de autenticación
        observeAuthState()
    }
    
    private fun initViewModel() {
        // Obtener tu UserService existente
        val userService = getUserServiceInstance() // Tu implementación
        
        // Crear el ViewModel usando el módulo DI
        val factory = AuthModule.provideAuthViewModelFactory(
            context = applicationContext,
            userService = userService
        )
        
        authViewModel = ViewModelProvider(this, factory)[AuthViewModelNew::class.java]
    }
    
    private fun setupListeners() {
        // Botón de Sign In
        binding.btnSignIn.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()
            authViewModel.signInWithEmail(email, password)
        }
        
        // Botón de Register
        binding.btnRegister.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()
            authViewModel.registerWithEmail(email, password)
        }
        
        // Botón de Google Sign In
        binding.btnGoogleSignIn.setOnClickListener {
            val intent = authViewModel.getGoogleSignInIntent()
            googleSignInLauncher.launch(intent)
        }
    }
    
    private fun observeAuthState() {
        lifecycleScope.launch {
            authViewModel.authState.collect { state ->
                handleAuthState(state)
            }
        }
    }
    
    private fun handleAuthState(state: AuthViewModelNew.AuthState) {
        // Manejar loading
        if (state.isLoading) {
            showLoading()
        } else {
            hideLoading()
        }
        
        // Manejar errores
        state.error?.let { error ->
            showError(error)
            authViewModel.clearError()
        }
        
        // Manejar autenticación exitosa
        if (state.isAuthenticated && state.currentUser != null) {
            val user = state.currentUser
            Toast.makeText(
                this,
                "Bienvenido ${user.displayName ?: user.email}",
                Toast.LENGTH_SHORT
            ).show()
            navigateToHome()
        }
        
        // Verificar sesión válida
        if (!state.isSessionValid && state.currentUser != null) {
            showSessionExpiredDialog()
        }
    }
    
    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSignIn.isEnabled = false
        binding.btnRegister.isEnabled = false
        binding.btnGoogleSignIn.isEnabled = false
    }
    
    private fun hideLoading() {
        binding.progressBar.visibility = View.GONE
        binding.btnSignIn.isEnabled = true
        binding.btnRegister.isEnabled = true
        binding.btnGoogleSignIn.isEnabled = true
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun navigateToHome() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
    
    private fun showSessionExpiredDialog() {
        AlertDialog.Builder(this)
            .setTitle("Sesión Expirada")
            .setMessage("Tu sesión ha expirado. Por favor inicia sesión nuevamente.")
            .setPositiveButton("OK") { _, _ ->
                authViewModel.signOut()
            }
            .show()
    }
}
```

### 2. MainActivity con Usuario Autenticado

```kotlin
class MainActivity : AppCompatActivity() {
    
    private lateinit var authViewModel: AuthViewModelNew
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Inicializar ViewModel
        initViewModel()
        
        // Verificar sesión al iniciar
        checkSession()
        
        // Mostrar información del usuario
        observeCurrentUser()
        
        // Configurar botón de Sign Out
        binding.btnSignOut.setOnClickListener {
            authViewModel.signOut()
        }
    }
    
    private fun initViewModel() {
        val userService = getUserServiceInstance()
        val factory = AuthModule.provideAuthViewModelFactory(
            context = applicationContext,
            userService = userService
        )
        authViewModel = ViewModelProvider(this, factory)[AuthViewModelNew::class.java]
    }
    
    private fun checkSession() {
        lifecycleScope.launch {
            authViewModel.authState.collect { state ->
                if (!state.isAuthenticated || !state.isSessionValid) {
                    // Redirigir al login
                    navigateToLogin()
                }
            }
        }
    }
    
    private fun observeCurrentUser() {
        lifecycleScope.launch {
            authViewModel.authState.collect { state ->
                state.currentUser?.let { user ->
                    binding.tvUserName.text = user.displayName ?: "Usuario"
                    binding.tvUserEmail.text = user.email ?: "Sin email"
                    
                    // Cargar foto de perfil si existe
                    user.photoUrl?.let { photoUrl ->
                        Glide.with(this@MainActivity)
                            .load(photoUrl)
                            .circleCrop()
                            .into(binding.ivProfilePhoto)
                    }
                    
                    // Mostrar badge de verificación
                    if (user.isEmailVerified) {
                        binding.ivVerified.visibility = View.VISIBLE
                    }
                }
            }
        }
    }
    
    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
```

### 3. Fragmento con Registro

```kotlin
class RegisterFragment : Fragment() {
    
    private lateinit var authViewModel: AuthViewModelNew
    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Obtener ViewModel de la Activity
        authViewModel = ViewModelProvider(requireActivity())[AuthViewModelNew::class.java]
        
        setupListeners()
        observeAuthState()
    }
    
    private fun setupListeners() {
        binding.btnRegister.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()
            val confirmPassword = binding.etConfirmPassword.text.toString()
            
            // Validación local básica
            when {
                email.isEmpty() -> {
                    binding.etEmail.error = "Email requerido"
                }
                password.isEmpty() -> {
                    binding.etPassword.error = "Contraseña requerida"
                }
                password != confirmPassword -> {
                    binding.etConfirmPassword.error = "Las contraseñas no coinciden"
                }
                else -> {
                    authViewModel.registerWithEmail(email, password)
                }
            }
        }
    }
    
    private fun observeAuthState() {
        viewLifecycleOwner.lifecycleScope.launch {
            authViewModel.authState.collect { state ->
                binding.progressBar.visibility = if (state.isLoading) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
                
                state.error?.let { error ->
                    Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
                    authViewModel.clearError()
                }
                
                if (state.isAuthenticated) {
                    findNavController().navigate(R.id.action_register_to_home)
                }
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

### 4. Verificación de Sesión en Application

```kotlin
class MyApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Verificar sesión al iniciar la app
        checkSession()
    }
    
    private fun checkSession() {
        // Crear el validador de sesión
        val sessionStorage = AuthModule.provideSessionStorage(this)
        val localDataSource = LocalAuthDataSource(sessionStorage)
        
        // Verificar si la sesión sigue válida
        if (!localDataSource.isSessionValid() && localDataSource.hasSession()) {
            // La sesión expiró, limpiar datos
            localDataSource.clearSession()
            
            // Opcional: Notificar al usuario
            showSessionExpiredNotification()
        }
    }
    
    private fun showSessionExpiredNotification() {
        // Implementar notificación de sesión expirada
    }
}
```

### 5. Uso Avanzado - Extender Sesión

```kotlin
class ProfileActivity : AppCompatActivity() {
    
    private lateinit var sessionStorage: SessionStorage
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        
        sessionStorage = AuthModule.provideSessionStorage(applicationContext)
        
        // Mostrar tiempo restante de sesión
        showSessionInfo()
        
        // Botón para extender sesión
        binding.btnExtendSession.setOnClickListener {
            extendSession()
        }
    }
    
    private fun showSessionInfo() {
        val remainingTime = sessionStorage.getRemainingSessionTime()
        val hours = TimeUnit.MILLISECONDS.toHours(remainingTime)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingTime) % 60
        
        binding.tvSessionInfo.text = "Sesión válida por: ${hours}h ${minutes}m"
    }
    
    private fun extendSession() {
        sessionStorage.extendSession(additionalHours = 24L)
        showSessionInfo()
        Toast.makeText(this, "Sesión extendida por 24 horas", Toast.LENGTH_SHORT).show()
    }
}
```

### 6. Testing de Example

```kotlin
class SignInUseCaseTest {
    
    private lateinit var signInUseCase: SignInWithEmailUseCase
    private lateinit var mockRepository: AuthRepository
    
    @Before
    fun setup() {
        mockRepository = mock()
        signInUseCase = SignInWithEmailUseCase(mockRepository)
    }
    
    @Test
    fun `signIn with valid credentials should return success`() = runTest {
        // Arrange
        val email = "test@test.com"
        val password = "password123"
        val expectedUser = User(
            id = "123",
            email = email,
            displayName = "Test User",
            photoUrl = null,
            isEmailVerified = true
        )
        
        whenever(mockRepository.signInWithEmail(email, password))
            .thenReturn(Result.success(expectedUser))
        
        // Act
        val result = signInUseCase(email, password)
        
        // Assert
        assertTrue(result.isSuccess)
        assertEquals(expectedUser, result.getOrNull())
        verify(mockRepository).signInWithEmail(email, password)
    }
    
    @Test
    fun `signIn with invalid credentials should return failure`() = runTest {
        // Arrange
        val email = "test@test.com"
        val password = "wrong"
        val exception = AuthException(
            code = AuthErrorCode.INVALID_CREDENTIALS,
            message = "Credenciales inválidas"
        )
        
        whenever(mockRepository.signInWithEmail(email, password))
            .thenReturn(Result.failure(exception))
        
        // Act
        val result = signInUseCase(email, password)
        
        // Assert
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }
}
```

## 📱 Ejemplo con Jetpack Compose

```kotlin
@Composable
fun LoginScreen(
    viewModel: AuthViewModelNew = viewModel()
) {
    val authState by viewModel.authState.collectAsState()
    
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    
    // Navegar si está autenticado
    LaunchedEffect(authState.isAuthenticated) {
        if (authState.isAuthenticated) {
            // Navegar a home
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Email Field
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !authState.isLoading
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Password Field
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            enabled = !authState.isLoading
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Sign In Button
        Button(
            onClick = { viewModel.signInWithEmail(email, password) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !authState.isLoading
        ) {
            if (authState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White
                )
            } else {
                Text("Iniciar Sesión")
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Google Sign In Button
        OutlinedButton(
            onClick = {
                // Lanzar Google Sign In
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_google),
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Continuar con Google")
        }
        
        // Error Message
        authState.error?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
```

## 🔧 Configuración Recomendada en build.gradle

```gradle
dependencies {
    // Firebase
    implementation platform('com.google.firebase:firebase-bom:32.7.0')
    implementation 'com.google.firebase:firebase-auth-ktx'
    
    // Google Sign In
    implementation 'com.google.android.gms:play-services-auth:20.7.0'
    
    // Coroutines
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3'
    
    // Lifecycle
    implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0'
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.7.0'
    
    // Testing
    testImplementation 'junit:junit:4.13.2'
    testImplementation 'org.mockito.kotlin:mockito-kotlin:5.1.0'
    testImplementation 'org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3'
}
```

## ✅ Checklist de Migración

- [ ] Copiar todos los nuevos archivos al proyecto
- [ ] Actualizar imports en archivos existentes
- [ ] Reemplazar `AuthViewModel` por `AuthViewModelNew`
- [ ] Cambiar `FirebaseUser` por `User` del dominio
- [ ] Actualizar el código que usa `IAuthRepository` a usar `AuthRepository` del dominio
- [ ] Configurar `AuthModule` en tu Application o Activity
- [ ] Ejecutar tests para verificar que todo funciona
- [ ] Eliminar archivos obsoletos después de la migración completa
