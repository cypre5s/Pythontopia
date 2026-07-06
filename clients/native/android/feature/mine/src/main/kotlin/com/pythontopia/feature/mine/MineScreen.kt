package com.pythontopia.feature.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pythontopia.core.design.PytColors
import com.pythontopia.core.design.PytSpace

/**
 * Bug 矿洞副本主屏；连续失败 [MineUiState.ESCAPE_THRESHOLD] 次弹 [MineEscapeDialog]。
 *
 * @param sourceSubmissionId 失败提交 ID，[MineRepository.startSession] 必填。
 * @param onExit 退出或通关确认回调。
 */
@Composable
fun MineScreen(
    sourceSubmissionId: Long? = null,
    onExit: () -> Unit = {},
    viewModel: MineViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(sourceSubmissionId) {
        if (state.session == null && !state.isLoading) {
            viewModel.startSession(sourceSubmissionId)
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
            ScreenHeader(state = state, onExit = onExit)
            MineLayerProgress(currentLayer = state.currentLayer)

            when {
                state.isCompleted -> CompletedView(onExit = onExit)
                state.session == null && state.isLoading -> LoadingView()
                state.session == null -> EmptyEntryView(
                    onStart = { viewModel.startSession(sourceSubmissionId) },
                    errorMessage = state.errorMessage,
                )
                state.currentCard == null -> EmptyEntryView(
                    onStart = { viewModel.startSession(sourceSubmissionId) },
                    errorMessage = state.errorMessage ?: "本次会话没有可用卡片",
                )
                else -> ActiveSolveView(
                    state = state,
                    onCodeChange = viewModel::updateCode,
                    onSubmit = viewModel::submitAttempt,
                    onNext = viewModel::nextCard,
                )
            }

            state.errorMessage?.let { msg ->
                ErrorBanner(msg, onClear = viewModel::clearError)
            }
        }

        if (state.showEscapeDialog) {
            MineEscapeDialog(
                failureCount = state.failureCount,
                onAbandonCard = viewModel::escapeCurrentCard,
                onSummonNpc = viewModel::summonNpcHint,
                onDismiss = viewModel::dismissEscapeDialog,
            )
        }
    }
}

@Composable
private fun ScreenHeader(state: MineUiState, onExit: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "Bug 矿洞",
                style = MaterialTheme.typography.headlineMedium,
                color = PytColors.textPrimary,
            )
            val card = state.currentCard
            Text(
                text = if (card != null) {
                    "卡 ${state.cardIdx + 1} / ${state.cards.size} · ${card.deckCardId} · ${card.title}"
                } else {
                    "等待进入..."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = PytColors.textMuted,
            )
        }
        TextButton(onClick = onExit) {
            Text("退出副本", color = PytColors.textMuted)
        }
    }
}

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LOADING_MIN_HEIGHT.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = PytColors.primary)
    }
}

@Composable
private fun EmptyEntryView(onStart: () -> Unit, errorMessage: String?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PytSpace.s3),
        color = PytColors.bgElevated,
    ) {
        Column(
            modifier = Modifier.padding(PytSpace.s5),
            verticalArrangement = Arrangement.spacedBy(PytSpace.s3),
        ) {
            Text(
                text = "矿头 Bug 在等你下井",
                style = MaterialTheme.typography.titleMedium,
                color = PytColors.textPrimary,
            )
            Text(
                text = "Bug 矿洞 5 层副本会按你最近的失败提交挑出 3-5 张谜题卡，" +
                    "你修对一张就升一格，连续失败 3 次可以撤退。",
                style = MaterialTheme.typography.bodyMedium,
                color = PytColors.textMuted,
            )
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Text("进入副本")
            }
            errorMessage?.let {
                Text(it, color = PytColors.danger, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ActiveSolveView(
    state: MineUiState,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onNext: () -> Unit,
) {
    val card = state.currentCard ?: return
    Column(verticalArrangement = Arrangement.spacedBy(PytSpace.s3)) {
        MineCardView(card = card)

        Text(
            text = "你的修复代码",
            style = MaterialTheme.typography.labelMedium,
            color = PytColors.textMuted,
        )
        OutlinedTextField(
            value = state.userCode,
            onValueChange = onCodeChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = EDITOR_MIN_HEIGHT.dp, max = EDITOR_MAX_HEIGHT.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = PytColors.textPrimary,
            ),
            placeholder = { Text("在这里改写错误代码...", color = PytColors.textHint) },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(PytSpace.s3)) {
            Button(
                onClick = onSubmit,
                enabled = !state.isLoading && state.userCode.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Text(if (state.isLoading) "判题中..." else "提交修复")
            }
            if (state.lastAttemptPassed) {
                OutlinedButton(
                    onClick = onNext,
                    enabled = !state.isLoading,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("下一卡 →")
                }
            }
        }

        state.lastResult?.let { ResultBanner(result = it) }
        if (state.npcHintRevealed) {
            HintBanner()
        }
        if (state.failureCount > 0 && !state.showEscapeDialog) {
            Text(
                text = "已失败 ${state.failureCount} / ${MineUiState.ESCAPE_THRESHOLD} 次",
                style = MaterialTheme.typography.labelSmall,
                color = PytColors.warning,
            )
        }
    }
}

@Composable
private fun ResultBanner(result: MineAttempt) {
    val accent = when (result.status) {
        MineAttempt.STATUS_PASS -> PytColors.success
        MineAttempt.STATUS_RUNTIME_ERROR -> PytColors.warning
        MineAttempt.STATUS_TIMEOUT -> PytColors.warning
        else -> PytColors.danger
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PytSpace.s2),
        color = accent.copy(alpha = BANNER_BG_ALPHA),
    ) {
        Column(
            modifier = Modifier.padding(PytSpace.s3),
            verticalArrangement = Arrangement.spacedBy(PytSpace.s1),
        ) {
            Text(
                text = "判题：${result.status}",
                style = MaterialTheme.typography.labelMedium,
                color = accent,
            )
            result.message?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = PytColors.textPrimary, style = MaterialTheme.typography.bodyMedium)
            }
            result.actualOutput?.takeIf { it.isNotBlank() }?.let { actual ->
                Text(
                    text = "实际输出：\n$actual",
                    style = MaterialTheme.typography.bodySmall,
                    color = PytColors.textMuted,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun HintBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PytSpace.s2),
        color = PytColors.info.copy(alpha = BANNER_BG_ALPHA),
    ) {
        Column(modifier = Modifier.padding(PytSpace.s3)) {
            Text(
                text = "勤勉小柒：",
                style = MaterialTheme.typography.labelMedium,
                color = PytColors.info,
            )
            Text(
                text = "先看 traceback 的最后一行，再看错误关键词；改之前先在心里跑一遍变量取值。",
                style = MaterialTheme.typography.bodyMedium,
                color = PytColors.textPrimary,
            )
        }
    }
}

@Composable
private fun CompletedView(onExit: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PytSpace.s3),
        color = PytColors.bgElevated,
    ) {
        Column(
            modifier = Modifier.padding(PytSpace.s5),
            verticalArrangement = Arrangement.spacedBy(PytSpace.s3),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "副本通关！",
                style = MaterialTheme.typography.headlineSmall,
                color = PytColors.success,
            )
            Text(
                text = "矿头 Bug 已记录你这次的调试足迹；奖励将在后端 quest_evidence 入账。",
                style = MaterialTheme.typography.bodyMedium,
                color = PytColors.textMuted,
            )
            Button(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                Text("回到 hub")
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onClear: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PytSpace.s2),
        color = PytColors.danger.copy(alpha = BANNER_BG_ALPHA),
    ) {
        Row(
            modifier = Modifier.padding(PytSpace.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = PytColors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClear) {
                Text("知道了")
            }
        }
    }
}

private const val LOADING_MIN_HEIGHT = 220
private const val EDITOR_MIN_HEIGHT = 160
private const val EDITOR_MAX_HEIGHT = 280
private const val BANNER_BG_ALPHA = 0.18f
