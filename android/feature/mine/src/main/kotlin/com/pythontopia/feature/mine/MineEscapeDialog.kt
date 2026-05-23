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
 * 连续失败 [MineUiState.ESCAPE_THRESHOLD] 次后弹出的「卡住了？」对话框。
 *
 * 对应卡牌库 §8.1 / §8.3：玩家可以选择：
 *  - 「放弃本卡」：扣 2 Pythoco，跳到下一卡，记入 `emotion_persistence.recent_mood`
 *  - 「召唤同学 NPC 提示」：召出「勤勉小柒」给思路提示（不是答案），buff × 0.7 通关奖励
 *  - 「再试一次」：关闭对话框继续编辑；失败计数不归零（不能反复躲过门槛刷掉对话框）
 *
 * UI 用 Material 3 [AlertDialog]：标题 + 描述 + 三个按钮。三个按钮按"破坏性 → 中性 → 安全"
 * 顺序排列：放弃在最左（按钮 Outlined 警示），召唤居中，再试一次居右（TextButton 默认）。
 *
 * 不在内部维护状态：所有控制权交给 [MineViewModel]，避免双源真相。
 *
 * @param onAbandonCard 玩家选择「放弃本卡」时回调，通常对接 [MineViewModel.escapeCurrentCard]。
 * @param onSummonNpc 玩家选择「召唤 NPC 提示」时回调，通常对接 [MineViewModel.summonNpcHint]。
 * @param onDismiss 玩家关闭对话框（点遮罩 / 返回键 / 再试一次按钮）时回调。
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

/** 保证按钮在窄屏不被挤成一行省略号；最小宽度给中文按钮充足空间。 */
private const val MIN_BUTTON_WIDTH = 192
