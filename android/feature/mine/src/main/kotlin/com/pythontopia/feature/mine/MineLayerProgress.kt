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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pythontopia.core.design.PytColors
import com.pythontopia.core.design.PytSpace

/**
 * 5 层副本进度条。
 *
 * 横排 5 个等宽段（B1..B5），每段三态：
 *  - 已通过（layer < currentLayer）：填充对应 [layerColor]
 *  - 当前（layer == currentLayer）：边框加粗 + 半填充，标签加粗
 *  - 未解锁（layer > currentLayer）：低饱和虚拟边框
 *
 * 与卡牌库 §2 表格的 layer 顺序一致：B1 NameError → B5 RecursionError。
 *
 * @param currentLayer 当前所在层 1..5；越界（如 0 或 6+）会自动 coerce 到 1..5。
 */
@Composable
fun MineLayerProgress(
    currentLayer: Int,
    modifier: Modifier = Modifier,
) {
    val layer = currentLayer.coerceIn(1, MineUiState.TOTAL_LAYERS)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PytSpace.s2),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PytSpace.s2),
        ) {
            for (i in 1..MineUiState.TOTAL_LAYERS) {
                LayerSegment(
                    layer = i,
                    state = when {
                        i < layer -> SegmentState.COMPLETED
                        i == layer -> SegmentState.CURRENT
                        else -> SegmentState.LOCKED
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            text = "当前层：B$layer · ${layerName(layer)}",
            style = MaterialTheme.typography.labelMedium,
            color = PytColors.textMuted,
        )
    }
}

@Composable
private fun LayerSegment(
    layer: Int,
    state: SegmentState,
    modifier: Modifier = Modifier,
) {
    val color = layerColor(layer)
    val (fillAlpha, borderAlpha, labelColor, labelWeight) = when (state) {
        SegmentState.COMPLETED -> SegmentStyle(
            fillAlpha = FILL_ALPHA_COMPLETED,
            borderAlpha = BORDER_ALPHA_COMPLETED,
            labelColor = PytColors.textPrimary,
            labelWeight = FontWeight.Normal,
        )
        SegmentState.CURRENT -> SegmentStyle(
            fillAlpha = FILL_ALPHA_CURRENT,
            borderAlpha = BORDER_ALPHA_CURRENT,
            labelColor = PytColors.textPrimary,
            labelWeight = FontWeight.Bold,
        )
        SegmentState.LOCKED -> SegmentStyle(
            fillAlpha = FILL_ALPHA_LOCKED,
            borderAlpha = BORDER_ALPHA_LOCKED,
            labelColor = PytColors.textHint,
            labelWeight = FontWeight.Normal,
        )
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PytSpace.s1),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(SEGMENT_HEIGHT.dp)
                .background(
                    color.copy(alpha = fillAlpha),
                    RoundedCornerShape(PytSpace.s1),
                )
                .border(
                    width = if (state == SegmentState.CURRENT) CURRENT_BORDER_WIDTH.dp else 1.dp,
                    color = color.copy(alpha = borderAlpha),
                    shape = RoundedCornerShape(PytSpace.s1),
                ),
        )
        Text(
            text = "B$layer",
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            fontWeight = labelWeight,
            modifier = Modifier.padding(top = PytSpace.s1),
        )
    }
}

private data class SegmentStyle(
    val fillAlpha: Float,
    val borderAlpha: Float,
    val labelColor: Color,
    val labelWeight: FontWeight,
)

private enum class SegmentState { COMPLETED, CURRENT, LOCKED }

/** 5 层错误大类名称（对齐卡牌库 §2 表格主题列）。 */
private fun layerName(layer: Int): String = when (layer) {
    1 -> "NameError / 未定义变量"
    2 -> "IndentationError / 缩进"
    3 -> "IndexError / KeyError"
    4 -> "TypeError / ValueError"
    5 -> "RecursionError / 无限循环"
    else -> "未知"
}

private const val SEGMENT_HEIGHT = 10
private const val CURRENT_BORDER_WIDTH = 2
private const val FILL_ALPHA_COMPLETED = 0.85f
private const val FILL_ALPHA_CURRENT = 0.40f
private const val FILL_ALPHA_LOCKED = 0.08f
private const val BORDER_ALPHA_COMPLETED = 0.9f
private const val BORDER_ALPHA_CURRENT = 1f
private const val BORDER_ALPHA_LOCKED = 0.35f
