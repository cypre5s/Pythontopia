package com.pythontopia.feature.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** NPC 对话面板；由 [DialogViewModel.state.visible] 控制显隐，隐藏时返回空 Box 避免反复挂载。 */
@Composable
fun NpcDialogPanel(
    modifier: Modifier = Modifier,
    viewModel: DialogViewModel = hiltViewModel(),
    onDismissed: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.visible) {
        Box(modifier = modifier)
        return
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            DialogHeader(
                npcName = state.npcDisplayName,
                onClose = {
                    viewModel.close()
                    onDismissed()
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when {
                    state.isAdvancing -> LoadingBlock()
                    state.currentNode != null -> DialogNodeBody(
                        node = state.currentNode!!,
                        onChoose = viewModel::choose,
                    )
                    else -> Text(
                        text = state.errorMessage ?: "对话尚未初始化",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            state.errorMessage?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun DialogHeader(
    npcName: String,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = npcName.firstOrNull()?.toString() ?: "?",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = npcName, style = MaterialTheme.typography.titleMedium)
        }
        TextButton(onClick = onClose) { Text("关闭") }
    }
}

@Composable
private fun DialogNodeBody(node: DialogNode, onChoose: (DialogChoice) -> Unit) {
    val scroll = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll)) {
        Text(
            text = node.text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            node.choices.forEach { choice ->
                Button(
                    onClick = { onChoose(choice) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(choice.label)
                }
            }
        }
    }
}

@Composable
private fun LoadingBlock() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}
