package com.pythontopia.feature.path

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.pythontopia.core.design.PytColors
import com.pythontopia.core.design.PytSpace

/** 学习路径纵向链式小地图；仅可视化，点击交互由 [PathNodeCard] 列表承担。缺失 [PathNodeDto.nextIds] 目标时跳过连线。 */
@Composable
fun PathGraphView(
    nodes: List<PathNodeDto>,
    currentNodeId: String?,
    modifier: Modifier = Modifier,
) {
    if (nodes.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(PytColors.bgSoft, RoundedCornerShape(8.dp))
                .padding(PytSpace.s4),
        ) {
            Text(
                text = "暂无路径节点",
                style = MaterialTheme.typography.bodySmall,
                color = PytColors.textHint,
            )
        }
        return
    }

    val nodeRadiusDp = 10.dp
    val verticalPadding = PytSpace.s2
    val rawHeight = GRAPH_ROW_HEIGHT.dp * nodes.size + verticalPadding * 2
    val totalHeight = rawHeight
        .coerceAtLeast(GRAPH_MIN_HEIGHT.dp)
        .coerceAtMost(GRAPH_MAX_HEIGHT.dp)

    val idToIndex = remember(nodes) { nodes.mapIndexed { i, n -> n.id to i }.toMap() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeight)
            .background(PytColors.bgSoft, RoundedCornerShape(8.dp)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val nodeRadiusPx = nodeRadiusDp.toPx()
            val paddingPx = verticalPadding.toPx()
            val drawableHeight = size.height - paddingPx * 2
            val rowHeightPx = if (nodes.size > 1) drawableHeight / nodes.size else drawableHeight
            val baseY = paddingPx + rowHeightPx / 2f

            fun nodeCenter(index: Int): Offset = Offset(centerX, baseY + rowHeightPx * index)

            nodes.forEachIndexed { i, node ->
                val from = nodeCenter(i)
                node.nextIds.forEach { nextId ->
                    val j = idToIndex[nextId] ?: return@forEach
                    val to = nodeCenter(j)
                    drawLine(
                        color = PytColors.borderSoft,
                        start = from,
                        end = to,
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }

            nodes.forEachIndexed { i, node ->
                val center = nodeCenter(i)
                val color = node.status.dotColor()
                drawCircle(color = color, radius = nodeRadiusPx, center = center)
                if (node.id == currentNodeId) {
                    drawCircle(
                        color = PytColors.primary,
                        radius = nodeRadiusPx + 4.dp.toPx(),
                        center = center,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
        }
    }
}

private fun PathNodeStatus.dotColor(): Color = when (this) {
    PathNodeStatus.LOCKED -> PytColors.textHint
    PathNodeStatus.UNLOCKED -> PytColors.info
    PathNodeStatus.IN_PROGRESS -> PytColors.warning
    PathNodeStatus.DONE -> PytColors.success
}

private const val GRAPH_ROW_HEIGHT: Int = 56
private const val GRAPH_MIN_HEIGHT: Int = 96
private const val GRAPH_MAX_HEIGHT: Int = 240
