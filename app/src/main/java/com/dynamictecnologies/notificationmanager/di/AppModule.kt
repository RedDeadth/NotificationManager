package com.dynamictecnologies.notificationmanager.di

import android.content.Context
import com.dynamictecnologies.notificationmanager.data.datasource.AppDataSource
import com.dynamictecnologies.notificationmanager.data.repository.AppRepositoryImpl
import com.dynamictecnologies.notificationmanager.data.repository.NotificationRepository
import com.dynamictecnologies.notificationmanager.data.repository.PreferencesRepositoryImpl
import com.dynamictecnologies.notificationmanager.domain.repositories.AppRepository
import com.dynamictecnologies.notificationmanager.domain.repositories.PreferencesRepository
import com.dynamictecnologies.notificationmanager.domain.usecases.app.GetInstalledAppsUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.app.GetSelectedAppUseCase
import com.dynamictecnologies.notificationmanager.domain.usecases.app.SaveSelectedAppUseCase
import com.dynamictecnologies.notificationmanager.viewmodel.AppListViewModelFactory

/**
 * Módulo de inyección de dependencias para aplicaciones y preferencias.
 * 
 * Sigue el mismo patrón que AuthModule para mantener consistencia.
 * 
 */
object AppModule {
    
    // ========================================
    // DATA SOURCES
    // ========================================
    
    fun provideAppDataSource(context: Context): AppDataSource {
        return AppDataSource(context.packageManager)
    }
    
    // ========================================
    // REPOSITORIES (SINGLETONS)
    // ========================================
    
    @Volatile
    private var notificationRepositoryInstance: NotificationRepository? = null
    
    /**
     * Provee singleton de NotificationRepository.
     * Usa double-checked locking para thread-safety.
     */
    fun provideNotificationRepository(context: Context): NotificationRepository {
        return notificationRepositoryInstance ?: synchronized(this) {
            notificationRepositoryInstance ?: run {
                val database = com.dynamictecnologies.notificationmanager.data.db.NotificationDatabase
                    .getDatabase(context.applicationContext)
                NotificationRepository(
                    notificationDao = database.notificationDao(),
                    context = context.applicationContext
                ).also { notificationRepositoryInstance = it }
            }
        }
    }
    
    fun provideAppRepository(context: Context): AppRepository {
        return AppRepositoryImpl(
            appDataSource = provideAppDataSource(context)
        )
    }
    
    fun providePreferencesRepository(context: Context): PreferencesRepository {
        return PreferencesRepositoryImpl(
            sharedPreferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        )
    }
    
    // ========================================
    // USE CASES
    // ========================================
    
    fun provideGetInstalledAppsUseCase(appRepository: AppRepository): GetInstalledAppsUseCase {
        return GetInstalledAppsUseCase(appRepository)
    }
    
    fun provideSaveSelectedAppUseCase(preferencesRepository: PreferencesRepository): SaveSelectedAppUseCase {
        return SaveSelectedAppUseCase(preferencesRepository)
    }
    
    fun provideGetSelectedAppUseCase(
        preferencesRepository: PreferencesRepository,
        appRepository: AppRepository
    ): GetSelectedAppUseCase {
        return GetSelectedAppUseCase(preferencesRepository, appRepository)
    }
    
    // ========================================
    // VIEW MODEL FACTORY
    // ========================================
    
    fun provideAppListViewModelFactory(
        context: Context,
        notificationRepository: NotificationRepository
    ): AppListViewModelFactory {
        val appRepository = provideAppRepository(context)
        val preferencesRepository = providePreferencesRepository(context)
        
        return AppListViewModelFactory(
            getInstalledAppsUseCase = provideGetInstalledAppsUseCase(appRepository),
            saveSelectedAppUseCase = provideSaveSelectedAppUseCase(preferencesRepository),
            getSelectedAppUseCase = provideGetSelectedAppUseCase(preferencesRepository, appRepository),
            notificationRepository = notificationRepository
        )
    }
}
