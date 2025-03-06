// Shape.kt (Nuevo archivo para añadir formas redondeadas estilo Samsung)
package com.dynamictecnologies.notificationmanager.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Samsung usa esquinas bastante redondeadas en su interfaz OneUI
val RoundedShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)