package com.pythontopia.feature.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pythontopia.core.design.PytColors
import com.pythontopia.core.design.PytSpace

/**
 * 连续失败 [MineUiState.ESCAPE_THRESHOLD] 次后的撤退对话框。
 * 放弃本卡（-2 Pythoco）、召唤 NPC 提示（奖励×0.7）、再试一次（失败计数不归零）。
 */
@Composable
fun MineEscapeDialog(
    onAbandonCard: () -> Unit,
    onSummonNpc: () -> Unit,
    onDismiss: () -> Unit,
    failureCount: Int = MineUiState.ESCAPE_THRESHOLD,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "卡住了？",
                style = MaterialTheme.typography.titleLarge,
                color = PytColors.textPrimary,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PytSpace.s2)) {
                Text(
                    text = "你已经连续失败 $failureCount 次。这是个高难卡，慢一点没关系。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PytColors.textPrimary,
                )
                Text(
                    text = "可以放弃本卡继续刷副本（扣 2 Pythoco，不计入 70% 通关率），" +
                        "或者召唤同学「勤勉小柒」给你一条思路提示（不会直接给答案，但通关奖励会 × 0.7）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = PytColors.textMuted,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("再试一次")
            }
        },
        dismissButton = {
            Column(verticalArrangement = Arrangement.spacedBy(PytSpace.s2)) {
                OutlinedButton(
                    onClick = onSummonNpc,
                    modifier = Modifier.widthIn(min = MIN_BUTTON_WIDTH.dp),
                ) {
                    Text("召唤同学 NPC 提示")
                }
                OutlinedButton(
                    onClick = onAbandonCard,
                    modifier = Modifier.widthIn(min = MIN_BUTTON_WIDTH.dp),
                ) {
                    Text("放弃本卡（-2 Pythoco）")
                }
            }
        },
        containerColor = PytColors.bgElevated,
        titleContentColor = PytColors.textPrimary,
        textContentColor = PytColors.textPrimary,
    )
}

/** 窄屏按钮最小宽度。 */
private const val MIN_BUTTON_WIDTH = 192
