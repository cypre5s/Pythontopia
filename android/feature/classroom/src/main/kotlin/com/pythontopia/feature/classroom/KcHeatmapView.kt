package com.pythontopia.feature.classroom

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pythontopia.core.design.PytColors
import com.pythontopia.core.design.PytSpace
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * 教师 KC 掌握度热力图（Compose Canvas）；按 [KcHeatmapEntry.avgMastery] 映射绿/黄/红，
 * 纯渲染，不处理交互。
 */
@Composable
fun KcHeatmapView(
    entries: List<KcHeatmapEntry>,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PytColors.bgSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "暂无班级 KC 数据",
                style = MaterialTheme.typography.bodyMedium,
                color = PytColors.textHint,
            )
        }
        return
    }

    val cellCount = entries.size
    val columns = remember(cellCount) { ceil(sqrt(cellCount.toDouble())).toInt().coerceAtLeast(1) }
    val rows = remember(cellCount, columns) { ceil(cellCount / columns.toDouble()).toInt().coerceAtLeast(1) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PytSpace.s2),
    ) {
        HeatmapLegend()
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(columns.toFloat() / rows.toFloat())
                .clip(RoundedCornerShape(8.dp))
                .background(PytColors.bgSunken)
                .padding(PytSpace.s1),
        ) {
            val cellW = size.width / columns
            val cellH = size.height / rows
            entries.forEachIndexed { idx, entry ->
                val col = idx % columns
                val row = idx / columns
                val mastery = entry.avgMastery.toDoubleOrNull() ?: 0.0
                drawRect(
                    color = masteryColor(mastery),
                    topLeft = Offset(col * cellW + 1f, row * cellH + 1f),
                    size = Size(cellW - 2f, cellH - 2f),
                )
            }
        }
        Text(
            text = "共 ${entries.size} 个 KC · 红色块代表 < 40% 学生掌握",
            style = MaterialTheme.typography.labelSmall,
            color = PytColors.textHint,
        )
    }
}

@Composable
private fun HeatmapLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PytSpace.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendDot(PytColors.success, "已掌握 ≥ 80%")
        LegendDot(PytColors.warning, "学习中 40–80%")
        LegendDot(PytColors.danger, "挣扎 < 40%")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Text(
            text = "  $label",
            style = MaterialTheme.typography.labelSmall,
            color = PytColors.textMuted,
        )
    }
}

internal fun masteryColor(mastery: Double): Color = when {
    mastery >= MASTERY_THRESHOLD_HIGH -> PytColors.success
    mastery >= MASTERY_THRESHOLD_LOW -> PytColors.warning
    else -> PytColors.danger
}

private const val MASTERY_THRESHOLD_HIGH = 0.80
private const val MASTERY_THRESHOLD_LOW = 0.40
