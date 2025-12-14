package com.dynamictecnologies.notificationmanager.data.model

import android.graphics.Bitmap

/**
 * Modelo de datos para aplicaciones en la capa de datos.
 * 
 * Esta clase se usa internamente en la capa de datos y no debe exponerse
 * a capas superiores. Para la capa de dominio/presentación se usa AppInfo.
 * 
 * - Clean Architecture: Modelo específico de la capa de datos
 * 
 * @property name Nombre de la aplicación
 * @property packageName Package name de la aplicación
 * @property iconBitmap Icono de la aplicación como Bitmap (antes de convertir a ImageBitmap)
 */
data class AppInfoData(
    val name: String,
    val packageName: String,
    val iconBitmap: Bitmap?
)
