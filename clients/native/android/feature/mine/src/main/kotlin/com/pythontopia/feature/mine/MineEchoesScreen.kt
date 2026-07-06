package com.pythontopia.feature.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pythontopia.core.design.PytColors
import com.pythontopia.core.design.PytSpace

/**
 * 记忆矿脉入口：上半「遗忘回声」（FSRS 到期 KC，推荐% + 遗忘风险 + reason），下半「错题本」（未关闭 session）。
 * 入矿/继续均跳到 [MineSessionScreen]（[onOpenSession]）。
 *
 * @param onOpenSession 打开某会话详情的导航回调（sessionId）。
 */
@Composable
fun MineEchoesScreen(
    onOpenSession: (Long) -> Unit = {},
    viewModel: MineEchoesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (state.echoes.isEmpty() && state.errorBook.isEmpty() && !state.isLoading) {
            viewModel.refresh()
        }
    }

    LaunchedEffect(state.pendingSessionId) {
        state.pendingSessionId?.let { sessionId ->
            onOpenSession(sessionId)
            viewModel.consumePendingSession()
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
            Header(onRefresh = viewModel::refresh, isLoading = state.isLoading)

            if (state.isLoading && state.isEmpty) {
                LoadingBlock()
            }

            EchoSection(
                echoes = state.echoes,
                openingKcCode = state.openingKcCode,
                onOpenEcho = viewModel::openEcho,
            )

            ErrorBookSection(
                items = state.errorBook,
                onContinue = onOpenSession,
            )

            if (state.isEmpty) {
                EmptyBlock()
            }

            state.errorMessage?.let { msg ->
                ErrorBanner(message = msg, onClear = viewModel::clearError)
            }
        }
    }
}

@Composable
private fun Header(onRefresh: () -> Unit, isLoading: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "记忆矿脉 · 遗忘回声",
                style = MaterialTheme.typography.headlineMedium,
                color = PytColors.textPrimary,
            )
            Text(
                text = "按 FSRS 复习引擎挑出当下最该复盘的知识点（遗忘风险 = 1 − 可提取性）。",
                style = MaterialTheme.typography.bodyMedium,
                color = PytColors.textMuted,
            )
        }
        TextButton(onClick = onRefresh, enabled = !isLoading) {
            Text(if (isLoading) "刷新中..." else "刷新", color = PytColors.textMuted)
        }
    }
}

@Composable
private fun EchoSection(
    echoes: List<EchoCandidate>,
    openingKcCode: String?,
    onOpenEcho: (String) -> Unit,
) {
    SectionTitle(text = "遗忘回声候选（${echoes.size}）", accent = PytColors.info)
    if (echoes.isEmpty()) {
        HintText("当前没有到期的遗忘回声——你的复习节奏保持得不错。")
        return
    }
    echoes.forEach { echo ->
        EchoCard(
            echo = echo,
            isOpening = openingKcCode == echo.kcCode,
            onOpen = { onOpenEcho(echo.kcCode) },
        )
    }
}

@Composable
private fun EchoCard(echo: EchoCandidate, isOpening: Boolean, onOpen: () -> Unit) {
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
            Text(
                text = echo.problemTitle,
                style = MaterialTheme.typography.titleMedium,
                color = PytColors.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(PytSpace.s2)) {
                TagChipMine(text = echo.kcCode, bg = PytColors.info.copy(alpha = TAG_ALPHA))
                TagChipMine(
                    text = "推荐 ${echo.recommendationPercent}%",
                    bg = PytColors.warning.copy(alpha = TAG_ALPHA),
                )
                TagChipMine(
                    text = "遗忘 ${echo.decayPercent}%",
                    bg = PytColors.danger.copy(alpha = TAG_ALPHA),
                )
            }
            Text(
                text = echo.recommendationReason,
                style = MaterialTheme.typography.bodyMedium,
                color = PytColors.textMuted,
            )
            Text(
                text = "掌握度 ${percent(echo.masteryScore)}% · 置信度 ${percent(echo.confidence)}%",
                style = MaterialTheme.typography.labelSmall,
                color = PytColors.textHint,
            )
            Button(
                onClick = onOpen,
                enabled = !isOpening,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isOpening) "入矿中..." else "入矿复盘")
            }
        }
    }
}

@Composable
private fun ErrorBookSection(
    items: List<ErrorBookItem>,
    onContinue: (Long) -> Unit,
) {
    SectionTitle(text = "错题本 · 未关闭矿洞（${items.size}）", accent = PytColors.warning)
    if (items.isEmpty()) {
        HintText("没有进行中的矿洞会话。")
        return
    }
    items.forEach { item ->
        ErrorBookCard(item = item, onContinue = { onContinue(item.sessionId) })
    }
}

@Composable
private fun ErrorBookCard(item: ErrorBookItem, onContinue: () -> Unit) {
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
            Text(
                text = item.problemTitle,
                style = MaterialTheme.typography.titleMedium,
                color = PytColors.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(PytSpace.s2)) {
                TagChipMine(text = item.kcCode, bg = PytColors.info.copy(alpha = TAG_ALPHA))
                TagChipMine(
                    text = "L${item.depthLayer}/目标 L${item.profileDepthTarget}",
                    bg = PytColors.success.copy(alpha = TAG_ALPHA),
                )
                TagChipMine(
                    text = "推荐 ${item.recommendationPercent}%",
                    bg = PytColors.warning.copy(alpha = TAG_ALPHA),
                )
            }
            Text(
                text = item.recommendationReason,
                style = MaterialTheme.typography.bodyMedium,
                color = PytColors.textMuted,
            )
            item.profileGuidance?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = PytColors.textHint)
            }
            if (item.mistakePatterns.isNotEmpty()) {
                Text(
                    text = "易错模式：${item.mistakePatterns.joinToString("、")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = PytColors.textHint,
                )
            }
            OutlinedButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text("继续调试")
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PytSpace.s2)) {
        Surface(
            modifier = Modifier.size(SECTION_DOT),
            color = accent,
            shape = RoundedCornerShape(PytSpace.s1),
        ) {}
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = PytColors.textPrimary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TagChipMine(text: String, bg: Color) {
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
private fun HintText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = PytColors.textHint)
}

@Composable
private fun LoadingBlock() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LOADING_HEIGHT.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = PytColors.primary)
    }
}

@Composable
private fun EmptyBlock() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PytSpace.s3),
        color = PytColors.bgElevated,
    ) {
        Column(modifier = Modifier.padding(PytSpace.s5), verticalArrangement = Arrangement.spacedBy(PytSpace.s2)) {
            Text(
                text = "矿脉暂时安静",
                style = MaterialTheme.typography.titleMedium,
                color = PytColors.textPrimary,
            )
            Text(
                text = "没有到期的遗忘回声，也没有未关闭的错题。多在训练场提交、让 FSRS 引擎积累复习节奏后再回来。",
                style = MaterialTheme.typography.bodyMedium,
                color = PytColors.textMuted,
            )
        }
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

private fun percent(value: Double): Int = (value * 100).toInt().coerceIn(0, 100)

private const val TAG_ALPHA = 0.25f
private const val BANNER_ALPHA = 0.18f
private const val LOADING_HEIGHT = 180
private val SECTION_DOT = 10.dp
