package com.pythontopia.feature.mine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun MineScreen(viewModel: MineViewModel = hiltViewModel()) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Bug 矿洞", style = MaterialTheme.typography.headlineMedium)
        Text("Android-R11 实施中, 当前为占位骨架", style = MaterialTheme.typography.bodyMedium)
    }
}
