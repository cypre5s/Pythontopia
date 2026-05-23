package com.pythontopia.feature.classroom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pythontopia.core.design.PytColors
import com.pythontopia.core.design.PytSpace

/**
 * 一条班级任务卡片，同时服务学生公告板与教师任务列表。
 *
 * 学生视角：
 *  - 顶部显示班级任务标题 + 截止日期；
 *  - 中间显示 KC chips + evidence 类型 chip；
 *  - 底部"提交完成"按钮：根据 [ClassroomQuest.studentQuestStatus] 切换文案 / 启用态。
 *
 * 教师视角：
 *  - 同样布局，但底部按钮被替换为"归档"占位文案（[onSubmit] 传 null 即学生模式
 *    隐藏按钮以教师只读展示）。
 *
 * 教师 / 学生分支由 [showSubmitButton] 控制，不在卡片内做角色判定（外层 [ClassroomScreen]
 * 已经按 role 分发了不同的 quests 列表，按钮可见性自然不同）。
 *
 * @param quest 一条班级任务 DTO。
 * @param showSubmitButton true = 展示底部"提交完成"按钮（学生公告板 tab）；
 *                         false = 隐藏（教师任务列表，仅展示）。
 * @param isSubmitting 当前卡片是否处于提交中（来自
 *        [ClassroomUiState.StudentState.submittingQuestId] == quest.studentQuestId）；
 *        为 true 时按钮替换为环形 loading。
 * @param onSubmit 提交按钮点击回调，外层负责弹出"提交 evidence"二级 sheet 或直接
 *                 传一条空 [QuestEvidence]。
 */
@Composable
fun ClassroomQuestCard(
    quest: ClassroomQuest,
    showSubmitButton: Boolean,
    isSubmitting: Boolean,
    onSubmit: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = PytSpace.s2),
        colors = CardDefaults.cardColors(containerColor = PytColors.bgElevated),
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
                Text(
                    text = quest.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = PytColors.textPrimary,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = "截止 ${quest.dueDate}",
                    style = MaterialTheme.typography.labelSmall,
                    color = dueDateColor(quest),
                )
            }
            if (!quest.description.isNullOrBlank()) {
                Text(
                    text = quest.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = PytColors.textMuted,
                )
            }
            if (quest.kcCodes.isNotEmpty() || quest.requiredEvidenceType != null) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(PytSpace.s2),
                ) {
                    items(quest.kcCodes) { kc ->
                        AssistChip(
                            onClick = {},
                            label = { Text(kc) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = PytColors.bgSoft,
                                labelColor = PytColors.textMuted,
                            ),
                        )
                    }
                    val evidence = quest.requiredEvidenceType
                    if (evidence != null) {
                        items(listOf(evidence)) {
                            AssistChip(
                                onClick = {},
                                label = { Text(evidenceLabel(it)) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = PytColors.bgSunken,
                                    labelColor = PytColors.warning,
                                ),
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = statusLabel(quest),
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor(quest),
                )
                if (showSubmitButton && onSubmit != null) {
                    if (isSubmitting) {
                        Box(
                            modifier = Modifier.size(36.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = PytColors.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    } else {
                        val enabled = quest.studentQuestStatus != STATUS_COMPLETED
                        Button(onClick = onSubmit, enabled = enabled) {
                            Text(if (enabled) "提交完成" else "已完成")
                        }
                    }
                }
            }
        }
    }
}

private fun dueDateColor(quest: ClassroomQuest): Color {
    if (quest.studentQuestStatus == STATUS_COMPLETED) return PytColors.success
    return PytColors.warning
}

private fun statusColor(quest: ClassroomQuest): Color = when (quest.studentQuestStatus) {
    STATUS_COMPLETED -> PytColors.success
    STATUS_IN_PROGRESS -> PytColors.info
    STATUS_SKIPPED -> PytColors.textHint
    else -> PytColors.textMuted
}

private fun statusLabel(quest: ClassroomQuest): String =
    when (quest.studentQuestStatus) {
        STATUS_COMPLETED -> "已完成"
        STATUS_IN_PROGRESS -> "进行中"
        STATUS_SKIPPED -> "已跳过"
        STATUS_ASSIGNED -> "待领取"
        null -> "教师视角 · ${quest.status}"
        else -> quest.studentQuestStatus
    }

private fun evidenceLabel(type: String): String = when (type) {
    EVIDENCE_SUBMISSION -> "需要：练习提交"
    EVIDENCE_RESOURCE -> "需要：资源消费"
    EVIDENCE_PAGE_READ -> "需要：课件阅读"
    EVIDENCE_KC_MASTERY -> "需要：KC 掌握度"
    else -> "需要：$type"
}

private const val STATUS_COMPLETED = "COMPLETED"
private const val STATUS_IN_PROGRESS = "IN_PROGRESS"
private const val STATUS_SKIPPED = "SKIPPED"
private const val STATUS_ASSIGNED = "ASSIGNED"

private const val EVIDENCE_SUBMISSION = "submission"
private const val EVIDENCE_RESOURCE = "resource"
private const val EVIDENCE_PAGE_READ = "page_read"
private const val EVIDENCE_KC_MASTERY = "kc_mastery"
