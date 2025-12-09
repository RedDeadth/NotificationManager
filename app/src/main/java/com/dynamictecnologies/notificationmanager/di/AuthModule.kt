package com.dynamictecnologies.notificationmanager.di

import android.content.Context
import com.dynamictecnologies.notificationmanager.data.datasource.LocalAuthDataSource
import com.dynamictecnologies.notificationmanager.data.datasource.RemoteAuthDataSource
import com.dynamictecnologies.notificationmanager.data.mapper.AuthErrorMapper
import com.dynamictecnologies.notificationmanager.data.repository.AuthRepositoryImpl
import com.dynamictecnologies.notificationmanager.data.storage.SessionStorage
import com.dynamictecnologies.notificationmanager.data.storage.SecureSessionStorage
import com.dynamictecnologies.notificationmanager.data.network.RetryPolicy
import com.dynamictecnologies.notificationmanager.util.logging.Logger
import com.dynamictecnologies.notificationmanager.util.logging.AndroidLogger
import com.dynamictecnologies.notificationmanager.util.logging.AuthLogger
import com.dynamictecnologies.notificationmanager.data.validator.AuthValidator
import com.dynamictecnologies.notificationmanager.domain.repositories.AuthRepository
import com.dynamictecnologies.notificationmanager.domain.usecases.GetCurrentUserUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.RegisterWithEmailUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.RegisterUserWithUsernameUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.SignInWithEmailUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.SignInWithGoogleUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.SignOutUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.ValidateSessionUseCase

import com.dynamictecnologies.notificationmanager.presentation.auth.GoogleSignInHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.dynamictecnologies.notificationmanager.data.datasource.RemoteUserDataSource
import com.dynamictecnologies.notificationmanager.data.datasource.LocalUserDataSource
import com.dynamictecnologies.notificationmanager.data.repository.UserProfileRepositoryImpl
import com.dynamictecnologies.notificationmanager.data.validator.UsernameValidator
import com.dynamictecnologies.notificationmanager.domain.repositories.UserProfileRepository
import com.dynamictecnologies.notificationmanager.domain.usecases.user.GetUserProfileUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.user.RefreshUserProfileUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.user.RegisterUsernameUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.user.ValidateUsernameUseCase
import com.dynamictecnologies.notificationmanager.viewmodel.AuthViewModel
import com.dynamictecnologies.notificationmanager.viewmodel.UserViewModelFactory

/**
 * Módulo de inyección de dependencias para autenticación y perfiles de usuario.
 * 
 * Implementa patrón Dependency Injection Container de forma manual.
 * En una aplicación real, se recomienda usar Dagger/Hilt o Koin.
 * 
 * Principios aplicados:
 * - DIP: Todas las dependencias se crean e inyectan aquí
 * - SRP: Solo se encarga de crear y proveer dependencias
 * - Single Responsibility: Centraliza la creación de objetos
 * - ISP: Proveedores segregados por funcionalidad (Auth, UserProfile)
 */
object AuthModule {
    
    // Singleton de FirebaseAuth
    private val firebaseAuth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }
    
    fun provideSessionStorage(context: Context): SessionStorage {
        return SecureSessionStorage(context)
    }
    
    fun provideLogger(): Logger {
        return AndroidLogger()
    }
    
    fun provideAuthValidator(): AuthValidator {
        return AuthValidator()
    }
    
    fun provideAuthErrorMapper(): AuthErrorMapper {
        return AuthErrorMapper()
    }
    
    fun provideRemoteAuthDataSource(): RemoteAuthDataSource {
        return RemoteAuthDataSource(firebaseAuth)
    }
    
    fun provideLocalAuthDataSource(context: Context): LocalAuthDataSource {
        val sessionStorage = provideSessionStorage(context)
        return LocalAuthDataSource(sessionStorage)
    }
    
    fun provideAuthRepository(context: Context): AuthRepository {
        return AuthRepositoryImpl(
            remoteDataSource = provideRemoteAuthDataSource(),
            localDataSource = provideLocalAuthDataSource(context),
            validator = provideAuthValidator(),
            errorMapper = provideAuthErrorMapper()
        )
    }
    
    fun provideGoogleSignInHelper(context: Context): GoogleSignInHelper {
        return GoogleSignInHelper(context)
    }
    
    // Use Cases
    
    fun provideSignInWithEmailUseCase(authRepository: AuthRepository) = SignInWithEmailUseCase(authRepository)

    fun provideRegisterWithEmailUseCase(authRepository: AuthRepository) = RegisterWithEmailUseCase(authRepository)
    
    fun provideRegisterUserWithUsernameUseCase(
        authRepository: AuthRepository,
        userProfileRepository: UserProfileRepository
    ) = RegisterUserWithUsernameUseCase(authRepository, userProfileRepository)

    fun provideSignInWithGoogleUseCase(authRepository: AuthRepository) = SignInWithGoogleUseCase(authRepository)

    fun provideSignOutUseCase(authRepository: AuthRepository, userProfileRepository: UserProfileRepository) = SignOutUseCase(authRepository, userProfileRepository)

    fun provideGetCurrentUserUseCase(authRepository: AuthRepository) = GetCurrentUserUseCase(authRepository)

    fun provideValidateSessionUseCase(authRepository: AuthRepository) = ValidateSessionUseCase(authRepository)
    
    // ========================================
    // USER PROFILE PROVIDERS
    // ========================================
    
    /**
     * Provee FirebaseDatabase
     */
    private fun provideFirebaseDatabase(): FirebaseDatabase {
        return FirebaseDatabase.getInstance()
    }
    
    fun provideUsernameValidator(): UsernameValidator {
        return UsernameValidator()
    }
    
    fun provideRemoteUserDataSource(): RemoteUserDataSource {
        return RemoteUserDataSource(provideFirebaseDatabase())
    }
    
    fun provideLocalUserDataSource(): LocalUserDataSource {
        return LocalUserDataSource()
    }
    
    fun provideUserProfileRepository(authRepository: AuthRepository):UserProfileRepository {
        return UserProfileRepositoryImpl(
            remoteDataSource = provideRemoteUserDataSource(),
            localDataSource = provideLocalUserDataSource(),
            usernameValidator = provideUsernameValidator(),
            firebaseAuth = firebaseAuth,
            authRepository = authRepository
        )
    }

    
    fun provideRegisterUsernameUseCase(
        userProfileRepository: UserProfileRepository
    ): RegisterUsernameUseCase {
        return RegisterUsernameUseCase(userProfileRepository)
    }
    
    fun provideGetUserProfileUseCase(
        userProfileRepository: UserProfileRepository
    ): GetUserProfileUseCase {
        return GetUserProfileUseCase(userProfileRepository)
    }
    
    fun provideRefreshUserProfileUseCase(
        userProfileRepository: UserProfileRepository
    ): RefreshUserProfileUseCase {
        return RefreshUserProfileUseCase(userProfileRepository)
    }
    

    
    /**
     * Provee el UserViewModelFactory con todas las dependencias
     */
    fun provideUserViewModelFactory(authRepository: AuthRepository): com.dynamictecnologies.notificationmanager.viewmodel.UserViewModelFactory {
        val userProfileRepository = provideUserProfileRepository(authRepository)
        
        return com.dynamictecnologies.notificationmanager.viewmodel.UserViewModelFactory(
            getUserProfileUseCase = provideGetUserProfileUseCase(userProfileRepository),
            registerUsernameUseCase = provideRegisterUsernameUseCase(userProfileRepository),
            refreshUserProfileUseCase = provideRefreshUserProfileUseCase(userProfileRepository)
        )
    }
    
    fun provideAuthViewModelFactory(
        context: Context
    ): com.dynamictecnologies.notificationmanager.viewmodel.AuthViewModel.Factory {
        val authRepository = provideAuthRepository(context)
        val userProfileRepository = provideUserProfileRepository(authRepository)
        
        return com.dynamictecnologies.notificationmanager.viewmodel.AuthViewModel.Factory(
            authRepository = authRepository,
            signInWithEmailUseCase = provideSignInWithEmailUseCase(authRepository),
            registerUserWithUsernameUseCase = provideRegisterUserWithUsernameUseCase(authRepository, userProfileRepository),
            signInWithGoogleUseCase = provideSignInWithGoogleUseCase(authRepository),
            signOutUseCase = provideSignOutUseCase(authRepository, userProfileRepository),
            getCurrentUserUseCase = provideGetCurrentUserUseCase(authRepository),
            validateSessionUseCase = provideValidateSessionUseCase(authRepository),
            googleSignInHelper = provideGoogleSignInHelper(context),
            errorMapper = provideAuthErrorMapper(),
            authValidator = provideAuthValidator(),
            usernameValidator = provideUsernameValidator()
        )
    }
}
