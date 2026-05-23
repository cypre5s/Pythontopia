package com.pythontopia.feature.retro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pythontopia.core.design.PytColors
import com.pythontopia.core.design.PytSpace
import kotlin.math.absoluteValue
import kotlin.math.max

private const val MAX_BAR_LIMIT: Int = 10
private const val MIN_BAR_RATIO: Float = 0.04f

/**
 * Top-N KC 增量水平柱状图（默认最多 10 条），原生 Compose Canvas，无第三方图表库。
 *
 * 渲染规则：
 *  - 正增量用 [PytColors.success]，负增量用 [PytColors.danger]，0 增量退化为 [PytColors.borderSoft]；
 *  - 柱长按绝对值与全局最大绝对值的比例缩放，确保负值也能可视化遗忘程度；
 *  - 列表为空时显示占位文案，避免 Canvas 退化成空白让学生误判加载失败。
 */
@Composable
fun KcGainBarChart(
    gains: List<KcGain>,
    modifier: Modifier = Modifier,
    maxBars: Int = MAX_BAR_LIMIT,
) {
    if (gains.isEmpty()) {
        Text(
            text = "本周没有 KC 掌握度变化（也许该走出小镇练一题？）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = modifier.padding(vertical = PytSpace.s2),
        )
        return
    }
    val sorted = gains.sortedByDescending { it.masteryDelta }.take(maxBars)
    val scale = max(
        sorted.maxOfOrNull { it.masteryDelta.absoluteValue } ?: 1.0,
        0.0001,
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PytSpace.s2),
    ) {
        sorted.forEach { gain ->
            BarRow(gain = gain, scale = scale)
        }
    }
}

@Composable
private fun BarRow(gain: KcGain, scale: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = gain.kcName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(120.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = PytSpace.s2),
        ) {
            val ratio = (gain.masteryDelta / scale).toFloat()
            BarCanvas(ratio = ratio)
        }
        Text(
            text = formatDelta(gain.masteryDelta),
            style = MaterialTheme.typography.labelMedium,
            color = barColor(gain.masteryDelta),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(64.dp),
        )
    }
}

@Composable
private fun BarCanvas(ratio: Float) {
    val color = barColor(ratio.toDouble())
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(16.dp),
    ) {
        val effective = ratio.absoluteValue.coerceAtLeast(MIN_BAR_RATIO).coerceAtMost(1f)
        val barWidth = size.width * effective
        val originX = if (ratio < 0f) size.width - barWidth else 0f
        drawRect(
            color = PytColors.bgSunken,
            topLeft = Offset.Zero,
            size = size,
        )
        drawRect(
            color = color,
            topLeft = Offset(originX, 0f),
            size = Size(barWidth, size.height),
        )
    }
}

private fun barColor(delta: Double): Color = when {
    delta > 0.0 -> PytColors.success
    delta < 0.0 -> PytColors.danger
    else -> PytColors.borderSoft
}

private fun formatDelta(delta: Double): String {
    val sign = if (delta > 0.0) "+" else if (delta < 0.0) "-" else ""
    val abs = delta.absoluteValue
    return "$sign%.2f".format(abs)
}
