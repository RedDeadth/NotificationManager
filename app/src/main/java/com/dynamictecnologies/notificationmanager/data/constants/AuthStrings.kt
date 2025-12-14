package com.dynamictecnologies.notificationmanager.data.constants

/**
 * Objeto centralizado para todos los strings relacionados con autenticación.
 * 
 * - Facilita internacionalización futura
 * - Mejora mantenibilidad
 */
object AuthStrings {
    
    /**
     * Mensajes de error de validación
     */
    object ValidationErrors {
        // Email
        const val EMPTY_EMAIL = "El email es requerido"
        const val INVALID_EMAIL_FORMAT = "Formato de email inválido"
        
        // Password
        const val EMPTY_PASSWORD = "La contraseña es requerida"
        const val WEAK_PASSWORD = "La contraseña no cumple con los requisitos de seguridad"
        const val WEAK_PASSWORD_WITH_DETAILS = "Contraseña débil:\n"
        const val PASSWORDS_DO_NOT_MATCH = "Las contraseñas no coinciden"
        
        // Username
        const val EMPTY_USERNAME = "El nombre de usuario no puede estar vacío"
        const val USERNAME_TOO_SHORT = "El nombre de usuario debe tener al menos %d caracteres"
        const val USERNAME_TOO_LONG = "El nombre de usuario no puede tener más de %d caracteres"
        const val USERNAME_CONTAINS_SPACES = "El nombre de usuario no puede contener espacios"
        const val USERNAME_INVALID_CHARACTERS = "El nombre de usuario solo puede contener letras y números"
    }
    
    /**
     * Mensajes de error de operaciones
     */
    object OperationErrors {
        const val NO_AUTHENTICATED_USER = "No hay usuario autenticado"
        const val PROFILE_ALREADY_EXISTS = "Ya tienes un perfil registrado"
        const val USERNAME_ALREADY_IN_USE = "El nombre de usuario ya está en uso"
        const val PROFILE_NOT_FOUND = "Perfil no encontrado"
        const val PROFILE_CREATION_FAILED = "Error al obtener perfil creado"
    }
    
    /**
     * Mensajes de éxito
     */
    object SuccessMessages {
        const val USERNAME_REGISTERED = "Username registrado exitosamente"
        const val LOGIN_SUCCESS = "Inicio de sesión exitoso"
        const val REGISTRATION_SUCCESS = "Registro completado exitosamente"
    }
    
    /**
     * Mensajes de UI
     */
    object UIMessages {
        const val WELCOME_MESSAGE = "Bienvenido usuario, ¿cómo deberíamos llamarte?"
        const val CREATE_ACCOUNT = "Crear nueva cuenta"
        const val ALREADY_HAVE_ACCOUNT = "¿Ya tienes cuenta? Inicia sesión"
        const val NO_ACCOUNT = "¿No tienes cuenta? Regístrate"
        const val SIGN_IN_WITH_GOOGLE = "Iniciar Sesión con Google"
    }
    
    /**
     * Labels de formularios
     */
    object FormLabels {
        const val EMAIL = "Email"
        const val PASSWORD = "Contraseña"
        const val CONFIRM_PASSWORD = "Confirmar contraseña"
        const val USERNAME = "Nombre de usuario"
        const val NAME = "Nombre"
        const val LOGIN = "Iniciar Sesión"
        const val REGISTER = "Registrarse"
    }
}
