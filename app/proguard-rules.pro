# ProGuard/R8 Rules para Producción
# Mantiene nombres de clases críticas pero elimina logs

# ========== LOG REMOVAL ==========
# Elimina TODOS los logs de producción (excepto errores críticos)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}

# Mantener solo logs de error críticos
# Los Log.e se mantienen para debugging en producción si es necesario

# ========== CRASH REPORTING ==========
# Mantener stack traces legibles para Firebase Crashlytics
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ========== FIREBASE ==========
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# ========== MQTT ==========
-keep class org.eclipse.paho.** { *; }
-dontwarn org.eclipse.paho.**

# ========== ROOM ==========
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ========== KOTLIN ==========
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# ========== KOTLINX COROUTINES ==========
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ========== COMPOSE ==========
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ========== DATA CLASSES ==========
# Mantener data classes para serialización
-keep class com.dynamictecnologies.notificationmanager.data.model.** { *; }
-keep class com.dynamictecnologies.notificationmanager.domain.entities.** { *; }

# ========== VALIDATORS & PARSERS ==========
# Mantener para reflection si es necesario
-keep class com.dynamictecnologies.notificationmanager.data.validator.** { *; }
-keep class com.dynamictecnologies.notificationmanager.data.parser.** { *; }

# ========== GENERAL ==========
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exception

# Optimizaciones agresivas
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# Remover código no usado
-dontwarn **