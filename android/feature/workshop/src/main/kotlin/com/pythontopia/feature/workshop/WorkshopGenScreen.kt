package com.pythontopia.feature.workshop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** 百晓工坊「资源生成」屏：选类型 + KC + 重点 → 流式多智能体生成（plan/trace 实时 + result）。 */
@Composable
fun WorkshopGenScreen(
    prefillType: WorkshopResourceType? = null,
    prefillKcCodes: List<String> = emptyList(),
    viewModel: WorkshopGenViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(prefillType, prefillKcCodes) {
        if (prefillType != null || prefillKcCodes.isNotEmpty()) {
            viewModel.prefill(prefillType, prefillKcCodes)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "百晓工坊 · 资源生成",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            TypeChips(selected = state.type, onSelect = viewModel::setType)
            OutlinedTextField(
                value = state.kcInput,
                onValueChange = viewModel::setKcInput,
                label = { Text("知识点 KC（逗号分隔，如 PY43-KC-001）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.focusHint,
                onValueChange = viewModel::setFocusHint,
                label = { Text("学习重点（可选）") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = viewModel::generate,
                enabled = state.canGenerate,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.streaming) "生成中…" else "生成 ${state.type.label}")
            }
            state.error?.let { ErrorText(it) }
            StreamView(state = state)
            state.result?.let { ResultCard(it) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeChips(selected: WorkshopResourceType, onSelect: (WorkshopResourceType) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WorkshopResourceType.entries.forEach { type ->
            val isSelected = type == selected
            Surface(
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.clickable { onSelect(type) },
            ) {
                Text(
                    text = type.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun StreamView(state: WorkshopGenUiState) {
    if (state.plan.isEmpty() && state.traces.isEmpty()) return
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.plan.isNotEmpty()) {
                Text("计划", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                state.plan.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall) }
            }
            if (state.traces.isNotEmpty()) {
                Text("进度", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                state.traces.forEach {
                    Text(
                        "▸ $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultCard(result: WorkshopGenEvent.Result) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(result.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (result.summary.isNotBlank()) {
                Text(result.summary, style = MaterialTheme.typography.bodyMedium)
            }
            val grounding = groundingText(result.groundingStatus)
            if (grounding.isNotBlank()) {
                Text(
                    grounding,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

private fun groundingText(status: String?): String = when (status?.uppercase()) {
    "PASSED" -> "✓ 课件佐证"
    "NO_EVIDENCE" -> "基于通识知识"
    "FAILED" -> "证据校验未通过"
    else -> ""
}
