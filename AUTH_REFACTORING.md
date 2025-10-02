# Refactorización del Módulo de Autenticación

## 📋 Resumen

Se ha refactorizado completamente el módulo de autenticación aplicando **Clean Architecture**, **SOLID**, **Repository Pattern** y **DRY (Don't Repeat Yourself)**.

## 🏗️ Nueva Arquitectura

### Estructura de Capas

```
domain/                          # Capa de Dominio (sin dependencias externas)
├── entities/
│   └── User.kt                 # Entidad de usuario (sin Firebase)
├── repositories/
│   └── AuthRepository.kt       # Interfaz del repositorio (abstracción)
└── usecases/                   # Casos de uso (lógica de negocio)
    ├── SignInWithEmailUseCase.kt
    ├── RegisterWithEmailUseCase.kt
    ├── SignInWithGoogleUseCase.kt
    ├── SignOutUseCase.kt
    ├── GetCurrentUserUseCase.kt
    └── ValidateSessionUseCase.kt

data/                            # Capa de Datos (implementación)
├── datasource/
│   ├── RemoteAuthDataSource.kt # Comunicación con Firebase
│   └── LocalAuthDataSource.kt  # Almacenamiento local
├── storage/
│   ├── SessionStorage.kt       # Interfaz de almacenamiento
│   └── SharedPreferencesSessionStorage.kt
├── repository/
│   └── AuthRepositoryImpl.kt   # Implementación del repositorio
├── mapper/
│   ├── UserMapper.kt           # Mapea FirebaseUser -> User
│   └── AuthErrorMapper.kt      # Mapea errores
└── validator/
    └── AuthValidator.kt        # Validación de datos

ui/                              # Capa de Presentación
├── auth/
│   └── GoogleSignInHelper.kt   # Helper para Google Sign-In
└── viewmodel/
    └── AuthViewModelNew.kt     # ViewModel refactorizado

di/                              # Inyección de Dependencias
└── AuthModule.kt               # Contenedor DI manual
```

## ✅ Principios Aplicados

### SOLID

1. **Single Responsibility Principle (SRP)**
   - `RemoteAuthDataSource`: Solo comunicación con Firebase
   - `LocalAuthDataSource`: Solo almacenamiento local
   - `AuthValidator`: Solo validación
   - `AuthErrorMapper`: Solo mapeo de errores
   - Cada Use Case: Una operación específica

2. **Open/Closed Principle (OCP)**
   - Fácil agregar nuevos métodos de autenticación
   - Extensible sin modificar código existente

3. **Liskov Substitution Principle (LSP)**
   - Las implementaciones pueden sustituirse por sus abstracciones

4. **Interface Segregation Principle (ISP)**
   - Interfaces específicas y segregadas (AuthRepository, SessionStorage)

5. **Dependency Inversion Principle (DIP)**
   - Todas las dependencias son abstracciones inyectadas
   - No hay instanciación con valores por defecto

### Clean Architecture

- **Capa de Dominio**: Independiente de frameworks (Firebase, Android)
- **Capa de Datos**: Implementa interfaces del dominio
- **Capa de UI**: Depende solo del dominio

### DRY (Don't Repeat Yourself)

- Función `executeAuthOperation()` elimina código duplicado
- Función `mapValidationError()` centraliza mapeo de errores
- Helper `GoogleSignInHelper` encapsula lógica de Google

## 🔄 Cambios Principales

### Antes vs Después

| Aspecto | Antes | Después |
|---------|-------|---------|
| **Entidad Usuario** | `FirebaseUser` en toda la app | `User` del dominio |
| **Repository** | Clase con defaults | Interfaz + Implementación |
| **ViewModel** | Llama directamente al Repository | Usa Use Cases |
| **Validación** | Duplicada en `signIn` y `register` | Función común `executeAuthOperation` |
| **Dependencias** | Valores por defecto en constructor | Todas inyectadas |
| **SessionManager** | Clase concreta | Interfaz `SessionStorage` |
| **Google Sign-In** | Mezclado en Repository | Helper separado |

## 📝 Guía de Migración

### 1. Crear las instancias usando AuthModule

```kotlin
// En tu Activity o Fragment
class LoginActivity : AppCompatActivity() {
    
    private lateinit var authViewModel: AuthViewModelNew
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Obtener UserService (o crearlo si no existe)
        val userService = // ... tu instancia de UserService
        
        // Crear el ViewModel usando el factory del módulo DI
        val factory = AuthModule.provideAuthViewModelFactory(
            context = applicationContext,
            userService = userService
        )
        
        authViewModel = ViewModelProvider(this, factory)[AuthViewModelNew::class.java]
        
        // Observar el estado
        observeAuthState()
    }
    
    private fun observeAuthState() {
        lifecycleScope.launch {
            authViewModel.authState.collect { state ->
                when {
                    state.isLoading -> showLoading()
                    state.error != null -> showError(state.error)
                    state.isAuthenticated -> navigateToHome()
                }
            }
        }
    }
}
```

### 2. Usar el nuevo ViewModel

```kotlin
// Sign In
authViewModel.signInWithEmail(email, password)

// Register
authViewModel.registerWithEmail(email, password)

// Google Sign In
val intent = authViewModel.getGoogleSignInIntent()
googleSignInLauncher.launch(intent)

// Manejar resultado de Google
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == RC_GOOGLE_SIGN_IN) {
        authViewModel.handleGoogleSignInResult(data)
    }
}

// Sign Out
authViewModel.signOut()

// Observar usuario actual
authViewModel.authState.collect { state ->
    state.currentUser?.let { user ->
        // user es de tipo User (dominio), no FirebaseUser
        println("User ID: ${user.id}")
        println("Email: ${user.email}")
        println("Name: ${user.displayName}")
    }
}
```

### 3. Migrar código que usaba FirebaseUser

**Antes:**
```kotlin
val firebaseUser: FirebaseUser? = auth.currentUser
val userId = firebaseUser?.uid
val email = firebaseUser?.email
```

**Después:**
```kotlin
val user: User? = authState.value.currentUser
val userId = user?.id
val email = user?.email
```

## 🧪 Testing

La nueva arquitectura facilita el testing:

### Test de Use Cases
```kotlin
class SignInWithEmailUseCaseTest {
    @Test
    fun `signIn should return user when credentials are valid`() = runTest {
        // Arrange
        val mockRepository = mock<AuthRepository>()
        val useCase = SignInWithEmailUseCase(mockRepository)
        
        // Act
        val result = useCase("test@test.com", "password123")
        
        // Assert
        assertTrue(result.isSuccess)
    }
}
```

### Test de Repository
```kotlin
class AuthRepositoryImplTest {
    @Test
    fun `signIn should save session when successful`() = runTest {
        // Arrange
        val mockRemoteDataSource = mock<RemoteAuthDataSource>()
        val mockLocalDataSource = mock<LocalAuthDataSource>()
        val repository = AuthRepositoryImpl(...)
        
        // Act & Assert
        verify(mockLocalDataSource).saveSession(any())
    }
}
```

## 📦 Archivos Obsoletos

Los siguientes archivos ya no son necesarios pero se mantienen por compatibilidad:

- `data/repository/AuthRepository.kt` → Reemplazado por `AuthRepositoryImpl.kt`
- `data/repository/IAuthRepository.kt` → Reemplazado por `domain/repositories/AuthRepository.kt`
- `data/session/SessionManager.kt` → Reemplazado por `SessionStorage` interface
- `viewmodel/AuthViewModel.kt` → Reemplazado por `AuthViewModelNew.kt`

**Recomendación**: Una vez migrado todo el código, eliminar los archivos antiguos.

## 🚀 Ventajas de la Nueva Arquitectura

1. **Testeable**: Todas las dependencias son inyectables y mockeable
2. **Mantenible**: Código organizado en capas claras
3. **Escalable**: Fácil agregar nuevas funcionalidades
4. **Independiente**: El dominio no depende de frameworks
5. **Reutilizable**: Los use cases pueden usarse en diferentes contextos
6. **Sin código duplicado**: Principio DRY aplicado consistentemente

## 📚 Próximos Pasos Recomendados

1. **Migrar a Dagger/Hilt o Koin**: Para inyección de dependencias automática
2. **Agregar tests unitarios**: Para use cases y repository
3. **Implementar Room**: Para persistencia local robusta
4. **Agregar logging**: Para debugging y monitoreo
5. **Implementar refresh token**: Para mejorar la gestión de sesiones

## ❓ FAQ

**P: ¿Debo migrar todo mi código de una vez?**
R: No, puedes mantener ambas implementaciones y migrar gradualmente.

**P: ¿Qué pasa con el código existente que usa FirebaseUser?**
R: Debe migrarse a usar `User` del dominio. El mapper `UserMapper` facilita la conversión.

**P: ¿Puedo usar esta arquitectura con otros servicios de autenticación?**
R: Sí, solo necesitas crear un nuevo `RemoteAuthDataSource` sin cambiar el dominio.

**P: ¿Es necesario AuthModule si uso Dagger/Hilt?**
R: No, puedes reemplazarlo con módulos de Hilt y anotaciones `@Inject`.

## 📞 Soporte

Si tienes dudas sobre la implementación, revisa los comentarios en el código o consulta la documentación de Clean Architecture.
