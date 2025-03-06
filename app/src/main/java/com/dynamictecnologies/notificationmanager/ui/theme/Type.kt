package com.dynamictecnologies.notificationmanager.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.unit.sp
import com.dynamictecnologies.notificationmanager.R
import androidx.compose.ui.text.googlefonts.GoogleFont.Provider

// Implementación mediante dependencias Google Fonts
private val provider = Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

// Opciones de fuentes elegantes, delgadas con terminaciones redondeadas:
private val poppinsFont = GoogleFont("Poppins") // Moderna, elegante con terminaciones suaves
private val quicksandFont = GoogleFont("Quicksand") // Muy redondeada y elegante
private val montserratFont = GoogleFont("Montserrat") // Elegante, ligeramente redondeada

// Escoge una como fuente principal (Poppins, elegante y moderna)
val ElegantFontFamily = FontFamily(
    Font(googleFont = poppinsFont, fontProvider = provider, weight = FontWeight.Light),
    Font(googleFont = poppinsFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = poppinsFont, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = poppinsFont, fontProvider = provider, weight = FontWeight.SemiBold)
)

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = ElegantFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp // Ligero espaciado para mayor elegancia
    ),
    titleLarge = TextStyle(
        fontFamily = ElegantFontFamily,
        fontWeight = FontWeight.SemiBold, // Semibold para títulos
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = ElegantFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = ElegantFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = -0.25.sp // Ligero espaciado negativo para titulares
    ),
    bodyMedium = TextStyle(
        fontFamily = ElegantFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    )
)