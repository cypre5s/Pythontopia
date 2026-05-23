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

/**
 * 学习路径"小地图"：Compose Canvas 画纵向链式节点 + 连接线。
 *
 * 设计取舍：
 *  - 不在 Canvas 内做点击 / 拖拽（Compose Canvas 无 pointerInput per-shape API），
 *    交互由下方 [PathNodeCard] 列表承担；本视图只做"路径全貌可视化"。
 *  - 节点按 [nodes] 顺序均匀排布，连接线根据 [PathNodeDto.nextIds] 在节点之间画直线；
 *    后端拓扑保证 nextIds 节点存在，UI 不做合法性校验，缺失节点的连线跳过。
 *  - 当前节点（[currentNodeId]）外圈加 PytColors.primary 高亮 ring，用户能一眼定位。
 *  - 节点为空时整个视图退化为占位 Text，避免 Canvas 0 高度崩。
 */
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
    val rowHeightDp = 56.dp
    val verticalPadding = PytSpace.s2
    val totalHeight = rowHeightDp * nodes.size + verticalPadding * 2

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
