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
 * 教师视角：65 KC 班级掌握度热力图（Compose Canvas 自绘）。
 *
 * 渲染策略：
 *  - 65 个 KC 排成接近正方形的网格（默认 9×8 = 72 格，最后 7 格空白），每格按
 *    [KcHeatmapEntry.avgMastery] 映射颜色：≥0.80 绿（[PytColors.success]）、
 *    [0.40, 0.80) 黄（[PytColors.warning]）、<0.40 红（[PytColors.danger]）；
 *  - 65 × class size ≤ 60 = 3900 格的精细矩阵留给 R-13 deep dive；
 *    Beta 阶段只渲染 KC 维度的平均色块，已足以让教师快速识别"哪个 KC 整体落后"；
 *  - 单次 `drawRect` 调用，无嵌套布局，性能预算 < 16ms 即使 65 格全更新；
 *  - 网格上方画图例（红/黄/绿色块 + 文字），下方留空让外层 [ClassroomScreen]
 *    叠加"点击 KC → 一键发任务"行为（本组件保持纯渲染，不处理交互）。
 *
 * 取舍：
 *  - 不依赖 `androidx.compose.foundation.lazy.grid.LazyVerticalGrid`，因为 65 格
 *    全可视，不需要 lazy；
 *  - 文字（KC 名称）不在 Canvas 内绘制，避免引入 nativeCanvas + Paint 跨平台差异；
 *    KC 名展示由外层列表（"红色 KC 详情"）承担。
 *  - `avgMastery` 后端是 BigDecimal 字符串（如 "0.62"）；本组件用 `toDoubleOrNull()`
 *    解析，失败按 0.0 处理（视觉上呈红色，提示"该 KC 无数据"）。
 *
 * @param entries 班级 KC 热力图数据；列表为空时显示"暂无数据"占位。
 * @param modifier 外层布局修饰符；建议传 `.fillMaxWidth().aspectRatio(1.2f)`。
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
