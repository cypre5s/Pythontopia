package com.pythontopia.feature.mine

import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pythontopia.core.design.PytColors
import com.pythontopia.core.design.PytSpace

/**
 * Bug 矿洞富会话详情：题干 + 5 层深度进度 + reveal 门控深度卡 + 原题重试 + L5 同 KC 变式 + 关闭。
 *
 * @param sessionId 目标会话 ID（由遗忘回声入矿或错题本进入）。
 * @param onExit 退出/通关回调。
 */
@Composable
fun MineSessionScreen(
    sessionId: Long,
    onExit: () -> Unit = {},
    viewModel: MineSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(sessionId) {
        viewModel.load(sessionId)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = PytColors.bgPrimary) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(PytSpace.s4)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(PytSpace.s4),
        ) {
            SessionHeader(state = state, onExit = onExit)

            val session = state.session
            when {
                state.isLoading && session == null -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = LOADING_HEIGHT.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = PytColors.primary)
                }
                session == null -> Text(
                    text = state.errorMessage ?: "未能加载矿洞会话",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PytColors.danger,
                )
                else -> SessionBody(
                    state = state,
                    session = session,
                    viewModel = viewModel,
                    onExit = onExit,
                )
            }

            state.errorMessage?.let { msg ->
                if (session != null) ErrorBanner(message = msg, onClear = viewModel::clearError)
            }
        }
    }
}

@Composable
private fun SessionHeader(state: MineSessionUiState, onExit: () -> Unit) {
    val status = when (state.session?.status) {
        MineSessionDetail.STATUS_OPEN -> "进行中"
        MineSessionDetail.STATUS_RESOLVED -> "已解决"
        MineSessionDetail.STATUS_ABANDONED -> "已放弃"
        else -> state.session?.status ?: "—"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.session?.problem?.title ?: "Bug 矿洞会话",
                style = MaterialTheme.typography.headlineSmall,
                color = PytColors.textPrimary,
            )
            Text(
                text = "深度 L${state.depthLayer} / 目标 L${state.session?.profileDepthTarget ?: 1} · 状态 $status",
                style = MaterialTheme.typography.bodyMedium,
                color = PytColors.textMuted,
            )
        }
        TextButton(onClick = onExit) {
            Text("返回", color = PytColors.textMuted)
        }
    }
}

@Composable
private fun SessionBody(
    state: MineSessionUiState,
    session: MineSessionDetail,
    viewModel: MineSessionViewModel,
    onExit: () -> Unit,
) {
    MineLayerProgress(currentLayer = session.depthLayer)

    session.problem?.let { ProblemCard(it, session.profileGuidance) }

    if (session.depthSteps.isNotEmpty()) {
        DepthStepsCard(session.depthSteps)
    }

    if (session.cards.isNotEmpty()) {
        Text(
            text = "深度卡（按下探深度逐层解锁）",
            style = MaterialTheme.typography.titleSmall,
            color = PytColors.textPrimary,
            fontWeight = FontWeight.Bold,
        )
        session.cards.forEach { card ->
            DepthCard(
                card = card,
                depthLayer = session.depthLayer,
                isRevealing = state.revealingCardId == card.id,
                canAct = session.isOpen && !state.isBusy,
                onReveal = { viewModel.revealCard(card.id) },
            )
        }
    }

    if (session.isOpen) {
        RetrySection(
            code = state.retryCode,
            isSubmitting = state.isSubmittingRetry,
            enabled = !state.isBusy,
            onCodeChange = viewModel::updateRetryCode,
            onSubmit = viewModel::submitRetry,
        )
        state.lastResult?.let { ResultBanner(it) }

        if (session.canSubmitVariant) {
            VariantSection(
                variant = session.variantProblem,
                code = state.variantCode,
                isSubmitting = state.isSubmittingVariant,
                enabled = !state.isBusy,
                onCodeChange = viewModel::updateVariantCode,
                onSubmit = viewModel::submitVariant,
            )
        }

        CloseRow(isClosing = state.isClosing, enabled = !state.isBusy, onClose = viewModel::close)
    } else {
        ClosedBanner(session = session, onExit = onExit)
    }
}

@Composable
private fun ProblemCard(problem: MineSessionProblem, guidance: String?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PytSpace.s3),
        color = PytColors.bgElevated,
    ) {
        Column(
            modifier = Modifier.padding(PytSpace.s4),
            verticalArrangement = Arrangement.spacedBy(PytSpace.s2),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(PytSpace.s2)) {
                if (problem.kcCode.isNotBlank()) {
                    TagChipSession(problem.kcCode, PytColors.info.copy(alpha = TAG_ALPHA))
                }
                problem.difficulty?.takeIf { it.isNotBlank() }?.let {
                    TagChipSession("难度 $it", PytColors.warning.copy(alpha = TAG_ALPHA))
                }
            }
            if (problem.statementMarkdown.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = STATEMENT_MAX_HEIGHT.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = problem.statementMarkdown,
                        style = MaterialTheme.typography.bodySmall,
                        color = PytColors.textPrimary,
                    )
                }
            }
            problem.samples.take(MAX_SAMPLES).forEachIndexed { index, sample ->
                Text(
                    text = "样例${index + 1}  in: ${sample.input.take(SAMPLE_PREVIEW)}  out: ${sample.output.take(SAMPLE_PREVIEW)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = PytColors.textMuted,
                    fontFamily = FontFamily.Monospace,
                )
            }
            guidance?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = "画像建议：$it",
                    style = MaterialTheme.typography.bodySmall,
                    color = PytColors.textHint,
                )
            }
        }
    }
}

@Composable
private fun DepthStepsCard(steps: List<MineDepthStep>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PytSpace.s3),
        color = PytColors.bgSoft,
    ) {
        Column(
            modifier = Modifier.padding(PytSpace.s4),
            verticalArrangement = Arrangement.spacedBy(PytSpace.s2),
        ) {
            steps.forEach { step ->
                Row(horizontalArrangement = Arrangement.spacedBy(PytSpace.s2)) {
                    Text(
                        text = if (step.unlocked) "✓" else "•",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (step.unlocked) PytColors.success else PytColors.textHint,
                    )
                    Column {
                        Text(
                            text = step.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (step.unlocked) PytColors.textPrimary else PytColors.textMuted,
                            fontWeight = if (step.unlocked) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        Text(
                            text = step.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = PytColors.textHint,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DepthCard(
    card: MineSessionCard,
    depthLayer: Int,
    isRevealing: Boolean,
    canAct: Boolean,
    onReveal: () -> Unit,
) {
    val accent = layerColor(card.requiredDepth)
    val unlockable = depthLayer >= card.requiredDepth
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accent.copy(alpha = CARD_BORDER_ALPHA), RoundedCornerShape(PytSpace.s2)),
        shape = RoundedCornerShape(PytSpace.s2),
        color = PytColors.bgElevated,
    ) {
        Column(
            modifier = Modifier.padding(PytSpace.s3),
            verticalArrangement = Arrangement.spacedBy(PytSpace.s2),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = PytColors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                TagChipSession("需 L${card.requiredDepth}", accent.copy(alpha = TAG_ALPHA))
            }
            when {
                card.revealed -> {
                    Text(
                        text = card.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = PytColors.textPrimary,
                        fontFamily = FontFamily.Monospace,
                    )
                    card.expectedOutput?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = "期望 stdout：$it",
                            style = MaterialTheme.typography.labelSmall,
                            color = PytColors.success,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                unlockable -> {
                    Text(
                        text = "已下探到可解锁深度，打开后请只取思路、独立修复。",
                        style = MaterialTheme.typography.bodySmall,
                        color = PytColors.textMuted,
                    )
                    OutlinedButton(
                        onClick = onReveal,
                        enabled = canAct,
                    ) {
                        Text(if (isRevealing) "解锁中..." else "解锁卡片")
                    }
                }
                else -> Text(
                    text = "锁定中：需下探到 L${card.requiredDepth}（当前 L$depthLayer）——先在 L1 裸重试，失败会自动加深。",
                    style = MaterialTheme.typography.bodySmall,
                    color = PytColors.textHint,
                )
            }
        }
    }
}

@Composable
private fun RetrySection(
    code: String,
    isSubmitting: Boolean,
    enabled: Boolean,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PytSpace.s2)) {
        Text(
            text = "原题重试",
            style = MaterialTheme.typography.titleSmall,
            color = PytColors.textPrimary,
            fontWeight = FontWeight.Bold,
        )
        CodeField(value = code, enabled = enabled, onValueChange = onCodeChange)
        Button(
            onClick = onSubmit,
            enabled = enabled && code.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isSubmitting) "判题中..." else "提交修复（retry-submit）")
        }
    }
}

@Composable
private fun VariantSection(
    variant: MineVariantProblem?,
    code: String,
    isSubmitting: Boolean,
    enabled: Boolean,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PytSpace.s3),
        color = PytColors.bgSoft,
    ) {
        Column(
            modifier = Modifier.padding(PytSpace.s3),
            verticalArrangement = Arrangement.spacedBy(PytSpace.s2),
        ) {
            Text(
                text = "L5 同 KC 变式 · ${variant?.title ?: "变式题"}",
                style = MaterialTheme.typography.titleSmall,
                color = PytColors.textPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "已下探到 L5，换一道同知识点题独立完成以确认迁移掌握。",
                style = MaterialTheme.typography.bodySmall,
                color = PytColors.textMuted,
            )
            CodeField(value = code, enabled = enabled, onValueChange = onCodeChange)
            Button(
                onClick = onSubmit,
                enabled = enabled && code.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isSubmitting) "判题中..." else "提交变式（variant-submit）")
            }
        }
    }
}

@Composable
private fun CodeField(value: String, enabled: Boolean, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = EDITOR_MIN_HEIGHT.dp, max = EDITOR_MAX_HEIGHT.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            color = PytColors.textPrimary,
        ),
        placeholder = { Text("在这里编写修复代码...", color = PytColors.textHint) },
    )
}

@Composable
private fun CloseRow(isClosing: Boolean, enabled: Boolean, onClose: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(PytSpace.s3)) {
        OutlinedButton(
            onClick = { onClose(MineSessionDetail.STATUS_RESOLVED) },
            enabled = enabled,
            modifier = Modifier.weight(1f),
        ) {
            Text(if (isClosing) "处理中..." else "标记已解决")
        }
        OutlinedButton(
            onClick = { onClose(MineSessionDetail.STATUS_ABANDONED) },
            enabled = enabled,
            modifier = Modifier.weight(1f),
        ) {
            Text("放弃退出")
        }
    }
}

@Composable
private fun ClosedBanner(session: MineSessionDetail, onExit: () -> Unit) {
    val resolved = session.status == MineSessionDetail.STATUS_RESOLVED
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
                text = if (resolved) "矿洞已解决！" else "会话已关闭",
                style = MaterialTheme.typography.headlineSmall,
                color = if (resolved) PytColors.success else PytColors.textMuted,
            )
            Text(
                text = if (session.masteryConfirmed) {
                    "已通过 L5 变式确认掌握，复习节奏会回写到 FSRS 引擎。"
                } else {
                    "你的调试足迹已记录；FSRS 引擎会据此安排下次复习。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = PytColors.textMuted,
            )
            Button(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                Text("回到矿脉入口")
            }
        }
    }
}

@Composable
private fun ResultBanner(result: MineAttempt) {
    val accent = when (result.status) {
        MineAttempt.STATUS_PASS -> PytColors.success
        MineAttempt.STATUS_RUNTIME_ERROR, MineAttempt.STATUS_TIMEOUT -> PytColors.warning
        else -> PytColors.danger
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PytSpace.s2),
        color = accent.copy(alpha = BANNER_ALPHA),
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
                Text(it, style = MaterialTheme.typography.bodyMedium, color = PytColors.textPrimary)
            }
        }
    }
}

@Composable
private fun TagChipSession(text: String, bg: Color) {
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

private const val TAG_ALPHA = 0.25f
private const val BANNER_ALPHA = 0.18f
private const val CARD_BORDER_ALPHA = 0.5f
private const val LOADING_HEIGHT = 200
private const val STATEMENT_MAX_HEIGHT = 160
private const val EDITOR_MIN_HEIGHT = 150
private const val EDITOR_MAX_HEIGHT = 280
private const val SAMPLE_PREVIEW = 24
private const val MAX_SAMPLES = 2
