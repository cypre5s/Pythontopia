package com.pythontopia.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MainScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "Pythontopia",
            style = MaterialTheme.typography.headlineLarge
        )
        Text(
            text = "教学小镇 · Android-R1 骨架",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
