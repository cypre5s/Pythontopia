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

/** 班级任务卡片；学生/教师共用，[showSubmitButton] 控制提交按钮可见性。 */
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
