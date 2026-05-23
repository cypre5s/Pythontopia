package com.pythontopia.feature.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pythontopia.core.design.PytColors
import com.pythontopia.core.design.PytSpace

/**
 * 单张谜题卡 Composable。
 *
 * 视觉层级（从上到下）：
 *  1. 顶部色带（[layerColor]）+ 卡牌业务编号 deckCardId（如 `B1-01`） + KC 编号 chip
 *  2. 大字标题 [MineCard.title]
 *  3. 错误代码片段 [MineCard.snippet]（等宽字体、深底浅字、自适应高度）
 *  4. 期望输出 [MineCard.expectedOutput]（绿色高亮，与代码段视觉区分）
 *  5. 奖励 / 难度系数小字脚注
 *
 * 不接收 onClick：卡片本身是只读展示，交互由父级的 Submit 按钮与编辑器承担，避免
 * 玩家误点卡片触发副作用。
 */
@Composable
fun MineCardView(
    card: MineCard,
    modifier: Modifier = Modifier,
) {
    val layerColor = layerColor(card.layer)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PytSpace.s3),
        color = PytColors.bgElevated,
        tonalElevation = PytSpace.s1,
    ) {
        Column(
            modifier = Modifier.padding(PytSpace.s4),
            verticalArrangement = Arrangement.spacedBy(PytSpace.s3),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(LAYER_BAND_HEIGHT.dp)
                    .background(layerColor, RoundedCornerShape(PytSpace.s1)),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PytSpace.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TagChip(text = card.deckCardId, bg = layerColor.copy(alpha = TAG_BG_ALPHA))
                TagChip(text = card.kcCode, bg = PytColors.info.copy(alpha = TAG_BG_ALPHA))
                if (card.pythocoReward > 0) {
                    TagChip(
                        text = "${card.pythocoReward} P",
                        bg = PytColors.warning.copy(alpha = TAG_BG_ALPHA),
                    )
                }
            }
            Text(
                text = card.title,
                style = MaterialTheme.typography.titleLarge,
                color = PytColors.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            CodeBlock(
                label = "错误代码（请修复后提交）",
                content = card.snippet,
                accent = PytColors.danger,
            )
            CodeBlock(
                label = "期望 stdout",
                content = card.expectedOutput,
                accent = PytColors.success,
            )
        }
    }
}

@Composable
private fun TagChip(text: String, bg: Color) {
    Surface(
        shape = RoundedCornerShape(PytSpace.s1),
        color = bg,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = PytSpace.s2, vertical = PytSpace.s1),
            style = MaterialTheme.typography.labelSmall,
            color = PytColors.textPrimary,
        )
    }
}

@Composable
private fun CodeBlock(label: String, content: String, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(PytSpace.s1)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = PytColors.textMuted,
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, accent.copy(alpha = ACCENT_BORDER_ALPHA), RoundedCornerShape(PytSpace.s2)),
            shape = RoundedCornerShape(PytSpace.s2),
            color = PytColors.bgSunken,
        ) {
            Text(
                text = if (content.isEmpty()) "（空）" else content,
                modifier = Modifier.padding(PytSpace.s3),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
                color = PytColors.textPrimary,
            )
        }
    }
}

/**
 * B1..B5 难度色映射，配合卡牌库 §2 难度系数：
 * - B1 = 入门绿（success）；B2 = 浅蓝；B3 = 警戒黄；B4 = 警示橙；B5 = 危险红。
 * 超出范围回退到 [PytColors.border]，避免脏数据让 UI 渲染异常。
 */
internal fun layerColor(layer: Int): Color = when (layer) {
    1 -> PytColors.success
    2 -> PytColors.info
    3 -> PytColors.warning
    4 -> Color(0xFFE08A40)
    5 -> PytColors.danger
    else -> PytColors.border
}

private const val LAYER_BAND_HEIGHT = 6
private const val TAG_BG_ALPHA = 0.25f
private const val ACCENT_BORDER_ALPHA = 0.6f
