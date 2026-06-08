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

/** 谜题卡只读展示；交互由父级编辑器与提交按钮承担。 */
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

/** B1..B5 难度色；越界回退 [PytColors.border]。 */
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
