package com.pythontopia.feature.workshop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pythontopia.core.design.PytColors
import com.pythontopia.core.design.PytSpace

/**
 * 百晓工坊「意图路由」屏：一句话 → 后端分类 → 自动分派。
 * REVIEW→记忆矿脉回声（[onOpenReview]）、ASK/UNKNOWN→导师（[onOpenTutor]）、其余→工坊生成（[onOpenGenerate]）。
 * 历史列表保留近几次分类，导航离开后回看意图与理由。
 */
@Composable
fun IntentRouterScreen(
    onOpenReview: (List<String>) -> Unit = {},
    onOpenTutor: (List<String>) -> Unit = {},
    onOpenGenerate: (IntentKind, List<String>) -> Unit = { _, _ -> },
    viewModel: IntentViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.pendingDispatch) {
        when (val dispatch = state.pendingDispatch) {
            is IntentDispatch.OpenReview -> onOpenReview(dispatch.kcCodes)
            is IntentDispatch.OpenTutor -> onOpenTutor(dispatch.kcCodes)
            is IntentDispatch.Generate -> onOpenGenerate(dispatch.kind, dispatch.kcCodes)
            null -> Unit
        }
        if (state.pendingDispatch != null) {
            viewModel.consumeDispatch()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = PytColors.bgPrimary) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(PytSpace.s4)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(PytSpace.s4),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(PytSpace.s1)) {
                Text(
                    text = "百晓工坊",
                    style = MaterialTheme.typography.headlineMedium,
                    color = PytColors.textPrimary,
                )
                Text(
                    text = "说一句你想做什么，我来判断该问导师、去记忆矿脉复盘，还是生成学习资源。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PytColors.textMuted,
                )
            }

            InputRow(
                input = state.input,
                isRouting = state.isRouting,
                onInputChange = viewModel::onInputChange,
                onSend = viewModel::route,
            )

            state.errorMessage?.let { msg ->
                ErrorBanner(message = msg, onClear = viewModel::clearError)
            }

            if (state.history.isNotEmpty()) {
                Text(
                    text = "最近分派",
                    style = MaterialTheme.typography.titleSmall,
                    color = PytColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
                state.history.forEach { response ->
                    ClassificationCard(response)
                }
            }
        }
    }
}

@Composable
private fun InputRow(
    input: String,
    isRouting: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PytSpace.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("例如：我想复习一下 for 循环", color = PytColors.textHint) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = PytColors.textPrimary),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
        )
        Button(onClick = onSend, enabled = input.isNotBlank() && !isRouting) {
            Text(if (isRouting) "识别中..." else "分派")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClassificationCard(response: IntentRouteResponse) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PytSpace.s3),
        color = PytColors.bgElevated,
        tonalElevation = PytSpace.s1,
    ) {
        Column(
            modifier = Modifier.padding(PytSpace.s4),
            verticalArrangement = Arrangement.spacedBy(PytSpace.s2),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Chip(text = response.kind.label, bg = accentFor(response.kind))
                Text(
                    text = "置信度 ${(response.confidence * PERCENT).toInt().coerceIn(0, PERCENT.toInt())}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = PytColors.textHint,
                )
            }
            response.focusHint?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleSmall,
                    color = PytColors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            response.reason?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = PytColors.textMuted)
            }
            if (response.kcCodes.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(PytSpace.s2)) {
                    response.kcCodes.forEach { kc ->
                        Chip(text = kc, bg = PytColors.info.copy(alpha = TAG_ALPHA))
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, bg: Color) {
    Surface(shape = RoundedCornerShape(PytSpace.s1), color = bg) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = PytSpace.s2, vertical = PytSpace.s1),
            style = MaterialTheme.typography.labelSmall,
            color = PytColors.textPrimary,
        )
    }
}

@Composable
private fun ErrorBanner(message: String, onClear: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PytSpace.s2),
        color = PytColors.danger.copy(alpha = BANNER_ALPHA),
    ) {
        Row(modifier = Modifier.padding(PytSpace.s3), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = PytColors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClear) { Text("知道了") }
        }
    }
}

private fun accentFor(kind: IntentKind): Color = when (kind) {
    IntentKind.REVIEW -> PytColors.danger.copy(alpha = TAG_ALPHA)
    IntentKind.ASK, IntentKind.UNKNOWN -> PytColors.info.copy(alpha = TAG_ALPHA)
    else -> PytColors.warning.copy(alpha = TAG_ALPHA)
}

private const val TAG_ALPHA = 0.25f
private const val BANNER_ALPHA = 0.18f
private const val PERCENT = 100.0
