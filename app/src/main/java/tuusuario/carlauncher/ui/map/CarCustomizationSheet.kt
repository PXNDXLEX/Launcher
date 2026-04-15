package com.tuusuario.carlauncher.ui.map

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun CarCustomizationSheet(onIconSelected: (Int) -> Unit) {
    LazyRow {
        items(3) { 
            Icon(
                Icons.Default.DirectionsCar,
                contentDescription = "Coche Genérico",
                tint = Color.White,
                modifier = Modifier.size(80.dp).padding(8.dp)
            )
        }
    }
}