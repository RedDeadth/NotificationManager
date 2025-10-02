# Diagrama de Arquitectura - Módulo de Autenticación

## 📊 Flujo de Datos

```
┌─────────────────────────────────────────────────────────────────┐
│                         CAPA DE UI                              │
│                                                                 │
│  ┌────────────────┐          ┌──────────────────┐             │
│  │   Activity/    │          │ AuthViewModelNew │             │
│  │   Fragment     │◄─────────┤  (StateFlow)     │             │
│  │                │          │                  │             │
│  └────────┬───────┘          └────────┬─────────┘             │
│           │                           │                        │
│           │                  ┌────────▼─────────┐             │
│           └─────────────────►│GoogleSignInHelper│             │
│                              └──────────────────┘             │
└─────────────────────────────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────┐
│                      CAPA DE DOMINIO                            │
│                    (Sin dependencias)                           │
│                                                                 │
│  ┌──────────────────────────────────────────────────┐          │
│  │              USE CASES (Casos de Uso)            │          │
│  │                                                  │          │
│  │  • SignInWithEmailUseCase                        │          │
│  │  • RegisterWithEmailUseCase                      │          │
│  │  • SignInWithGoogleUseCase                       │          │
│  │  • SignOutUseCase                                │          │
│  │  • GetCurrentUserUseCase                         │          │
│  │  • ValidateSessionUseCase                        │          │
│  └────────────────────┬─────────────────────────────┘          │
│                       │                                         │
│                       ▼                                         │
│  ┌──────────────────────────────────────────────────┐          │
│  │     AuthRepository (Interface)                   │          │
│  │                                                  │          │
│  │  + signInWithEmail()                             │          │
│  │  + registerWithEmail()                           │          │
│  │  + signInWithGoogle()                            │          │
│  │  + signOut()                                     │          │
│  │  + getCurrentUser()                              │          │
│  │  + isSessionValid()                              │          │
│  └──────────────────────────────────────────────────┘          │
│                                                                 │
│  ┌──────────────────────────────────────────────────┐          │
│  │           User (Entidad de Dominio)              │          │
│  │                                                  │          │
│  │  - id: String                                    │          │
│  │  - email: String?                                │          │
│  │  - displayName: String?                          │          │
│  │  - photoUrl: String?                             │          │
│  │  - isEmailVerified: Boolean                      │          │
│  └──────────────────────────────────────────────────┘          │
└─────────────────────────────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────┐
│                       CAPA DE DATOS                             │
│                   (Implementación)                              │
│                                                                 │
│  ┌──────────────────────────────────────────────────┐          │
│  │        AuthRepositoryImpl                        │          │
│  │      (Implementa AuthRepository)                 │          │
│  │                                                  │          │
│  │  - remoteDataSource                              │          │
│  │  - localDataSource                               │          │
│  │  - validator                                     │          │
│  │  - errorMapper                                   │          │
│  │  - userService                                   │          │
│  └─────────┬────────────────────┬───────────────────┘          │
│            │                    │                               │
│            ▼                    ▼                               │
│  ┌──────────────────┐  ┌──────────────────┐                   │
│  │RemoteAuthDataSrc │  │LocalAuthDataSrc  │                   │
│  │                  │  │                  │                   │
│  │ - FirebaseAuth   │  │ - SessionStorage │                   │
│  │                  │  │                  │                   │
│  └────────┬─────────┘  └────────┬─────────┘                   │
│           │                     │                              │
│           ▼                     ▼                              │
│  ┌──────────────────┐  ┌──────────────────┐                   │
│  │  Firebase Auth   │  │  SessionStorage  │                   │
│  │  (External)      │  │   (Interface)    │                   │
│  └──────────────────┘  └────────┬─────────┘                   │
│                                 │                              │
│                                 ▼                              │
│                    ┌──────────────────────────┐               │
│                    │SharedPreferencesSession  │               │
│                    │       Storage            │               │
│                    └──────────────────────────┘               │
│                                                                 │
│  ┌──────────────────────────────────────────────────┐          │
│  │              COMPONENTES AUXILIARES              │          │
│  │                                                  │          │
│  │  • UserMapper: FirebaseUser → User               │          │
│  │  • AuthValidator: Validación de entrada          │          │
│  │  • AuthErrorMapper: Mapeo de errores             │          │
│  └──────────────────────────────────────────────────┘          │
└─────────────────────────────────────────────────────────────────┘
```

## 🔄 Flujo de una Operación de Sign In

```
1. Usuario ingresa email y password
   │
   ▼
2. Activity/Fragment llama → authViewModel.signInWithEmail(email, password)
   │
   ▼
3. ViewModel llama → signInWithEmailUseCase(email, password)
   │
   ▼
4. Use Case llama → authRepository.signInWithEmail(email, password)
   │
   ▼
5. AuthRepositoryImpl:
   │
   ├─► AuthValidator.validateLoginCredentials() → Valida entrada
   │   │
   │   └─► Si inválido: AuthException
   │
   ├─► RemoteAuthDataSource.signInWithEmail() → Llama a Firebase
   │   │
   │   └─► FirebaseAuth.signInWithEmailAndPassword()
   │
   ├─► UserMapper.toDomain(firebaseUser) → Convierte a User
   │
   └─► LocalAuthDataSource.saveSession(user) → Guarda en local
       │
       └─► SessionStorage.saveSession(user)
           │
           └─► SharedPreferences
   │
   ▼
6. Result<User> regresa al ViewModel
   │
   ▼
7. ViewModel actualiza AuthState
   │
   ▼
8. UI observa StateFlow y actualiza la vista
```

## 🎯 Principios SOLID en la Arquitectura

### Single Responsibility Principle (SRP)
```
RemoteAuthDataSource  → Solo comunicación con Firebase
LocalAuthDataSource   → Solo almacenamiento local
AuthValidator         → Solo validación
AuthErrorMapper       → Solo mapeo de errores
UserMapper            → Solo conversión de entidades
SessionStorage        → Solo gestión de sesión
```

### Dependency Inversion Principle (DIP)
```
            ┌──────────────────┐
            │ AuthViewModel    │ (Alto nivel)
            └────────┬─────────┘
                     │ depende de
                     ▼
            ┌──────────────────┐
            │   Use Cases      │ (Alto nivel)
            └────────┬─────────┘
                     │ depende de
                     ▼
            ┌──────────────────┐
            │ AuthRepository   │ (Abstracción)
            │  (Interface)     │
            └────────┬─────────┘
                     │ implementa
                     ▼
            ┌──────────────────┐
            │AuthRepositoryImpl│ (Bajo nivel)
            └────────┬─────────┘
                     │ depende de
                     ▼
      ┌──────────────┴──────────────┐
      │                             │
      ▼                             ▼
┌──────────────┐         ┌──────────────────┐
│ RemoteData   │         │   LocalData      │
│   Source     │         │    Source        │
└──────────────┘         └──────────────────┘
```

### Interface Segregation Principle (ISP)
```
┌────────────────────┐
│  AuthRepository    │ → Operaciones de autenticación
└────────────────────┘

┌────────────────────┐
│  SessionStorage    │ → Operaciones de sesión
└────────────────────┘

(Interfaces separadas y específicas)
```

## 📦 Módulos y Dependencias

```
┌─────────────────────────────────────────────┐
│            AuthModule (DI)                  │
│                                             │
│  provideAuthRepository()                    │
│  provideRemoteAuthDataSource()              │
│  provideLocalAuthDataSource()               │
│  provideAuthValidator()                     │
│  provideAuthErrorMapper()                   │
│  provideSessionStorage()                    │
│  provideGoogleSignInHelper()                │
│  provideUse Cases...()                      │
│  provideAuthViewModelFactory()              │
└─────────────────────────────────────────────┘
```

## 🧪 Testing

### Capas Testeables Independientemente

```
┌────────────────────────────────────────┐
│ Use Cases Tests                        │
│ (Mock AuthRepository)                  │
└────────────────────────────────────────┘

┌────────────────────────────────────────┐
│ Repository Tests                       │
│ (Mock DataSources, Validator, Mapper)  │
└────────────────────────────────────────┘

┌────────────────────────────────────────┐
│ DataSource Tests                       │
│ (Mock Firebase/SharedPreferences)      │
└────────────────────────────────────────┘

┌────────────────────────────────────────┐
│ ViewModel Tests                        │
│ (Mock Use Cases)                       │
└────────────────────────────────────────┘
```

## 🔑 Ventajas Clave

1. **Separación de Capas**: Cada capa tiene su responsabilidad clara
2. **Independencia del Framework**: El dominio no conoce Firebase ni Android
3. **Testeable**: Todas las dependencias son inyectables
4. **Mantenible**: Código organizado y fácil de entender
5. **Escalable**: Fácil agregar nuevas funcionalidades
6. **Reutilizable**: Use cases pueden usarse en diferentes contextos
7. **Sin Código Duplicado**: DRY aplicado consistentemente
