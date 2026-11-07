package com.example.splitwise.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Green = Color(0xFF2E9E6B)
val Red = Color(0xFFD9534F)

@Composable
fun SplitWiseTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme())
        darkColorScheme(primary = Color(0xFF5BC5A7), secondary = Color(0xFF8FD6C1))
    else
        lightColorScheme(primary = Color(0xFF0E9F7E), secondary = Color(0xFF3E8E7E))
    MaterialTheme(colorScheme = scheme, content = content)
}
