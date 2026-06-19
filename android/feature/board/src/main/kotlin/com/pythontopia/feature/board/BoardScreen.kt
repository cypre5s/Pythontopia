package com.pythontopia.feature.board

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pythontopia.core.data.dto.learner.QuestDto
import com.pythontopia.core.data.dto.learner.QuestSource
import com.pythontopia.core.data.dto.learner.QuestStatus
import com.pythontopia.core.design.PytColors
import com.pythontopia.core.design.PytLoading
import com.pythontopia.core.design.PytSpace

@Composable
fun BoardScreen(viewModel: BoardViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PytColors.bgPrimary)
            .padding(PytSpace.s3),
        verticalArrangement = Arrangement.spacedBy(PytSpace.s2),
    ) {
        Column {
            Text("任务公告板", color = PytColors.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(
                "今日任务 ${state.openCount} 项待办" + if (state.loading) " · 加载中…" else "",
                color = PytColors.textHint,
                fontSize = 12.sp,
            )
        }

        state.notice?.let { Banner(it, PytColors.warning) { viewModel.clearNotice() } }
        state.error?.let { Banner(it, PytColors.danger) { viewModel.load() } }

        when {
            state.loading && state.quests.isEmpty() -> PytLoading()
            state.quests.isEmpty() -> Text(
                "今天没有待办任务，去小镇逛逛或规划学习路径吧。",
                color = PytColors.textHint,
                fontSize = 13.sp,
                modifier = Modifier.padding(PytSpace.s3),
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(PytSpace.s2)) {
                items(state.sorted, key = { it.id }) { quest ->
                    QuestCard(
                        quest = quest,
                        busy = state.actionInFlightId == quest.id,
                        onStart = { viewModel.start(quest.id) },
                        onComplete = { viewModel.complete(quest.id) },
                        onSkip = { viewModel.skip(quest.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuestCard(
    quest: QuestDto,
    busy: Boolean,
    onStart: () -> Unit,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
) {
    Surface(
        color = PytColors.bgElevated,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(2.dp, PytColors.borderSoft),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(PytSpace.s3), verticalArrangement = Arrangement.spacedBy(PytSpace.s1)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PytSpace.s2)) {
                Text(
                    quest.title,
                    color = PytColors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(quest.status)
            }
            quest.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = PytColors.textMuted, fontSize = 13.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(PytSpace.s1), verticalAlignment = Alignment.CenterVertically) {
                quest.kcCode?.let { Badge(it, PytColors.info) }
                Badge(sourceLabel(quest.source), PytColors.textMuted)
                if (quest.priority > 0) Badge("P${quest.priority}", PytColors.warning)
                quest.dueDate?.let { Text("截止 $it", color = PytColors.textHint, fontSize = 11.sp) }
            }

            if (quest.isActionable()) {
                Row(
                    modifier = Modifier.padding(top = PytSpace.s1),
                    horizontalArrangement = Arrangement.spacedBy(PytSpace.s2),
                ) {
                    if (quest.status == QuestStatus.OPEN) {
                        ActionButton("开始", PytColors.info, enabled = !busy, onClick = onStart)
                    }
                    if (quest.requiresEvidence()) {
                        ActionButton("跳过", PytColors.bgSoft, enabled = !busy, onClick = onSkip, fg = PytColors.textMuted)
                    } else {
                        ActionButton("完成", PytColors.success, enabled = !busy, onClick = onComplete)
                        ActionButton("跳过", PytColors.bgSoft, enabled = !busy, onClick = onSkip, fg = PytColors.textMuted)
                    }
                }
                if (quest.requiresEvidence()) {
                    Text(
                        "需在对应练习/资源中产生证据（${quest.requiredEvidenceType}），由系统判定完成。",
                        color = PytColors.textHint,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = PytSpace.s1),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    bg: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    fg: Color = PytColors.onPrimary,
) {
    Surface(
        color = if (enabled) bg else bg.copy(alpha = 0.4f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(2.dp, PytColors.borderStrong),
        modifier = Modifier.then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Text(text, color = fg, fontSize = 13.sp, modifier = Modifier.padding(horizontal = PytSpace.s3, vertical = 6.dp))
    }
}

@Composable
private fun StatusBadge(status: QuestStatus?) {
    val (text, color) = when (status) {
        QuestStatus.OPEN -> "待办" to PytColors.info
        QuestStatus.IN_PROGRESS -> "进行中" to PytColors.warning
        QuestStatus.COMPLETED -> "已完成" to PytColors.success
        QuestStatus.SKIPPED -> "已跳过" to PytColors.textMuted
        QuestStatus.EXPIRED -> "已过期" to PytColors.danger
        null -> "—" to PytColors.textMuted
    }
    Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun Badge(text: String, fg: Color) {
    Surface(color = PytColors.bgSoft, shape = RoundedCornerShape(4.dp)) {
        Text(text, color = fg, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

private fun sourceLabel(source: QuestSource?): String = when (source) {
    QuestSource.PATH_PLANNER -> "路径"
    QuestSource.CLASSROOM -> "班级"
    QuestSource.SYSTEM_DEFAULT -> "系统"
    null -> "任务"
}

@Composable
private fun Banner(message: String, accent: Color, onClick: () -> Unit) {
    Surface(
        color = PytColors.bgElevated,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(2.dp, accent),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Text(message, color = accent, fontSize = 13.sp, modifier = Modifier.padding(PytSpace.s3))
    }
}
