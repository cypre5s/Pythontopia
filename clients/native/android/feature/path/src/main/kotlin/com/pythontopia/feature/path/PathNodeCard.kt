package com.pythontopia.feature.path

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pythontopia.core.design.PytColors
import com.pythontopia.core.design.PytSpace

/** 单路径节点卡片；LOCKED 不可点，跳转由 [PathScreen] 经 [onClick] 处理。 */
@Composable
fun PathNodeCard(
    node: PathNodeDto,
    isCurrent: Boolean,
    isAdvancing: Boolean,
    onClick: (PathNodeDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = node.status.palette()
    val clickable = node.status != PathNodeStatus.LOCKED
    val cardModifier = modifier.fillMaxWidth().then(
        if (clickable && !isAdvancing) Modifier.clickable { onClick(node) } else Modifier,
    )
    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = palette.background),
        border = BorderStroke(
            width = if (isCurrent) 2.dp else 1.dp,
            color = if (isCurrent) PytColors.primary else palette.border,
        ),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight()
                        .background(PytColors.primary),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(PytSpace.s4),
                verticalArrangement = Arrangement.spacedBy(PytSpace.s2),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TypeBadge(type = node.type, color = palette.accent)
                    Spacer(modifier = Modifier.width(PytSpace.s2))
                    Text(
                        text = node.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = palette.foreground,
                        modifier = Modifier.weight(1f),
                    )
                    StatusPrefixIcon(status = node.status, color = palette.accent)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (node.kcCode != null) {
                        KcChip(kcCode = node.kcCode, color = palette.accent)
                        Spacer(modifier = Modifier.width(PytSpace.s2))
                    }
                    Text(
                        text = node.status.labelZh(),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    when {
                        isAdvancing -> CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = palette.accent,
                        )
                        clickable -> ActionHint(status = node.status, color = palette.accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeBadge(type: PathNodeType, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.18f), shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = type.labelZh(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun KcChip(kcCode: String, color: Color) {
    Box(
        modifier = Modifier
            .border(width = 1.dp, color = color.copy(alpha = 0.6f), shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = kcCode,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun StatusPrefixIcon(status: PathNodeStatus, color: Color) {
    val symbol = when (status) {
        PathNodeStatus.LOCKED -> "\uD83D\uDD12"
        PathNodeStatus.UNLOCKED -> "\u25CB"
        PathNodeStatus.IN_PROGRESS -> "\u25CF"
        PathNodeStatus.DONE -> "\u2713"
    }
    Text(text = symbol, color = color, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun ActionHint(status: PathNodeStatus, color: Color) {
    val label = when (status) {
        PathNodeStatus.UNLOCKED -> "开始 →"
        PathNodeStatus.IN_PROGRESS -> "完成 →"
        PathNodeStatus.DONE -> "复习 →"
        PathNodeStatus.LOCKED -> ""
    }
    if (label.isNotEmpty()) {
        Text(text = label, color = color, style = MaterialTheme.typography.labelSmall)
    }
}

private data class StatusPalette(
    val background: Color,
    val border: Color,
    val accent: Color,
    val foreground: Color,
    val muted: Color,
)

private fun PathNodeStatus.palette(): StatusPalette = when (this) {
    PathNodeStatus.LOCKED -> StatusPalette(
        background = PytColors.bgSunken,
        border = PytColors.borderSoft,
        accent = PytColors.textHint,
        foreground = PytColors.textHint,
        muted = PytColors.textHint,
    )
    PathNodeStatus.UNLOCKED -> StatusPalette(
        background = PytColors.bgSoft,
        border = PytColors.border,
        accent = PytColors.info,
        foreground = PytColors.textPrimary,
        muted = PytColors.textMuted,
    )
    PathNodeStatus.IN_PROGRESS -> StatusPalette(
        background = PytColors.bgElevated,
        border = PytColors.warning,
        accent = PytColors.warning,
        foreground = PytColors.textPrimary,
        muted = PytColors.textMuted,
    )
    PathNodeStatus.DONE -> StatusPalette(
        background = PytColors.bgSoft,
        border = PytColors.success.copy(alpha = 0.7f),
        accent = PytColors.success,
        foreground = PytColors.textPrimary,
        muted = PytColors.textMuted,
    )
}
