package com.dynamictecnologies.notificationmanager.di

import android.content.Context
import com.dynamictecnologies.notificationmanager.data.datasource.LocalAuthDataSource
import com.dynamictecnologies.notificationmanager.data.datasource.RemoteAuthDataSource
import com.dynamictecnologies.notificationmanager.data.mapper.AuthErrorMapper
import com.dynamictecnologies.notificationmanager.data.repository.AuthRepositoryImpl
import com.dynamictecnologies.notificationmanager.data.storage.SessionStorage
import com.dynamictecnologies.notificationmanager.data.storage.SharedPreferencesSessionStorage
import com.dynamictecnologies.notificationmanager.data.validator.AuthValidator
import com.dynamictecnologies.notificationmanager.domain.repositories.AuthRepository
import com.dynamictecnologies.notificationmanager.domain.usecases.GetCurrentUserUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.RegisterWithEmailUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.SignInWithEmailUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.SignInWithGoogleUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.SignOutUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.ValidateSessionUseCase
import com.dynamictecnologies.notificationmanager.service.UserService
import com.dynamictecnologies.notificationmanager.ui.auth.GoogleSignInHelper
import com.dynamictecnologies.notificationmanager.viewmodel.AuthViewModelNew
import com.google.firebase.auth.FirebaseAuth

/**
 * Módulo de inyección de dependencias para el módulo de autenticación.
 * 
 * Implementa patrón Dependency Injection Container de forma manual.
 * En una aplicación real, se recomienda usar Dagger/Hilt o Koin.
 * 
 * Principios aplicados:
 * - DIP: Todas las dependencias se crean e inyectan aquí
 * - SRP: Solo se encarga de crear y proveer dependencias
 * - Single Responsibility: Centraliza la creación de objetos
 */
object AuthModule {
    
    // Singleton de FirebaseAuth
    private val firebaseAuth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }
    
    /**
     * Provee el SessionStorage
     */
    fun provideSessionStorage(context: Context): SessionStorage {
        return SharedPreferencesSessionStorage(context)
    }
    
    /**
     * Provee el AuthValidator
     */
    fun provideAuthValidator(): AuthValidator {
        return AuthValidator()
    }
    
    /**
     * Provee el AuthErrorMapper
     */
    fun provideAuthErrorMapper(): AuthErrorMapper {
        return AuthErrorMapper()
    }
    
    /**
     * Provee el RemoteAuthDataSource
     */
    fun provideRemoteAuthDataSource(): RemoteAuthDataSource {
        return RemoteAuthDataSource(firebaseAuth)
    }
    
    /**
     * Provee el LocalAuthDataSource
     */
    fun provideLocalAuthDataSource(context: Context): LocalAuthDataSource {
        val sessionStorage = provideSessionStorage(context)
        return LocalAuthDataSource(sessionStorage)
    }
    
    /**
     * Provee el AuthRepository
     */
    fun provideAuthRepository(
        context: Context,
        userService: UserService
    ): AuthRepository {
        return AuthRepositoryImpl(
            remoteDataSource = provideRemoteAuthDataSource(),
            localDataSource = provideLocalAuthDataSource(context),
            validator = provideAuthValidator(),
            errorMapper = provideAuthErrorMapper(),
            userService = userService
        )
    }
    
    /**
     * Provee el GoogleSignInHelper
     */
    fun provideGoogleSignInHelper(context: Context): GoogleSignInHelper {
        return GoogleSignInHelper(context)
    }
    
    // Use Cases
    
    fun provideSignInWithEmailUseCase(authRepository: AuthRepository): SignInWithEmailUseCase {
        return SignInWithEmailUseCase(authRepository)
    }
    
    fun provideRegisterWithEmailUseCase(authRepository: AuthRepository): RegisterWithEmailUseCase {
        return RegisterWithEmailUseCase(authRepository)
    }
    
    fun provideSignInWithGoogleUseCase(authRepository: AuthRepository): SignInWithGoogleUseCase {
        return SignInWithGoogleUseCase(authRepository)
    }
    
    fun provideSignOutUseCase(authRepository: AuthRepository): SignOutUseCase {
        return SignOutUseCase(authRepository)
    }
    
    fun provideGetCurrentUserUseCase(authRepository: AuthRepository): GetCurrentUserUseCase {
        return GetCurrentUserUseCase(authRepository)
    }
    
    fun provideValidateSessionUseCase(authRepository: AuthRepository): ValidateSessionUseCase {
        return ValidateSessionUseCase(authRepository)
    }
    
    /**
     * Provee el ViewModelFactory con todas las dependencias
     */
    fun provideAuthViewModelFactory(
        context: Context,
        userService: UserService
    ): AuthViewModelNew.Factory {
        val authRepository = provideAuthRepository(context, userService)
        
        return AuthViewModelNew.Factory(
            signInWithEmailUseCase = provideSignInWithEmailUseCase(authRepository),
            registerWithEmailUseCase = provideRegisterWithEmailUseCase(authRepository),
            signInWithGoogleUseCase = provideSignInWithGoogleUseCase(authRepository),
            signOutUseCase = provideSignOutUseCase(authRepository),
            getCurrentUserUseCase = provideGetCurrentUserUseCase(authRepository),
            validateSessionUseCase = provideValidateSessionUseCase(authRepository),
            googleSignInHelper = provideGoogleSignInHelper(context),
            errorMapper = provideAuthErrorMapper()
        )
    }
}
