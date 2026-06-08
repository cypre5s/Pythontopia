package com.pythontopia.feature.fields

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas as ComposeCanvas

/** 64dp KC 格；[drawKcGlyph] 按 [KcStatus] 绘制像素 glyph，供单测覆盖。 */
@Composable
fun KcTile(
    tile: KcTileState,
    isSelected: Boolean,
    isPending: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isSelected) FieldsPalette.tileBorderActive else FieldsPalette.tileBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp
    Box(
        modifier = modifier
            .size(TILE_SIZE_DP.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(FieldsPalette.statusFill(tile.status))
            .border(borderWidth, borderColor, RoundedCornerShape(6.dp))
            .clickable(enabled = !isPending, onClick = onClick)
            .semantics {
                contentDescription = tileContentDescription(tile, isSelected, isPending)
            }
            .padding(4.dp),
    ) {
        ComposeCanvas(modifier = Modifier.fillMaxSize()) {
            drawKcGlyph(tile.status, FieldsPalette.statusInner(tile.status))
            if (tile.mastery > 0.0 && tile.status != KcStatus.HARVESTED) {
                drawMasteryBar(tile.mastery)
            }
            if (isPending) {
                drawPendingOverlay()
            }
        }
        Text(
            text = tile.kcCode.takeLast(KC_CODE_TAIL),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFFEF7E3),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopStart),
        )
    }
}

/** 按 [KcStatus] 绘制核心 glyph（纯 Canvas，不依赖 KC 内容）。 */
internal fun DrawScope.drawKcGlyph(status: KcStatus, inner: Color) {
    val w = size.width
    val h = size.height
    when (status) {
        KcStatus.SEED -> {
            drawCircle(inner, radius = w * SEED_RADIUS_RATIO, center = Offset(w * 0.3f, h * 0.55f))
            drawCircle(inner, radius = w * SEED_RADIUS_RATIO, center = Offset(w * 0.7f, h * 0.55f))
            drawCircle(inner, radius = w * SEED_RADIUS_RATIO, center = Offset(w * 0.5f, h * 0.75f))
        }
        KcStatus.GROWING -> {
            drawCircle(inner, radius = w * GROW_OUTER_RATIO, center = Offset(w * 0.5f, h * 0.6f))
            drawCircle(
                FieldsPalette.tileSeed,
                radius = w * GROW_INNER_RATIO,
                center = Offset(w * 0.5f, h * 0.6f),
            )
        }
        KcStatus.MATURE -> {
            val cx = w * 0.5f
            val cy = h * 0.55f
            drawCircle(inner, radius = w * MATURE_RADIUS_RATIO, center = Offset(cx, cy))
            drawCircle(inner, radius = w * MATURE_RADIUS_RATIO, center = Offset(cx - w * 0.22f, cy))
            drawCircle(inner, radius = w * MATURE_RADIUS_RATIO, center = Offset(cx + w * 0.22f, cy))
            drawCircle(inner, radius = w * MATURE_RADIUS_RATIO, center = Offset(cx, cy - h * 0.22f))
            drawCircle(inner, radius = w * MATURE_RADIUS_RATIO, center = Offset(cx, cy + h * 0.22f))
        }
        KcStatus.HARVESTED -> {
            listOf(0.25f to 0.3f, 0.75f to 0.3f, 0.25f to 0.75f, 0.75f to 0.75f).forEach { (fx, fy) ->
                drawCircle(inner, radius = w * HARVEST_RADIUS_RATIO, center = Offset(w * fx, h * fy))
            }
        }
    }
}

/** 底部 mastery 窄条，宽度按掌握度比例。 */
internal fun DrawScope.drawMasteryBar(mastery: Double) {
    val barHeight = size.height * 0.06f
    val barY = size.height * 0.94f
    drawRect(
        FieldsPalette.tileBorder,
        topLeft = Offset(0f, barY),
        size = Size(size.width, barHeight),
    )
    drawRect(
        FieldsPalette.hudAccent,
        topLeft = Offset(0f, barY),
        size = Size(size.width * mastery.toFloat().coerceIn(0f, 1f), barHeight),
    )
}

/** pending 外描边，提示当前格正在执行 water/harvest。 */
internal fun DrawScope.drawPendingOverlay() {
    drawRect(
        color = FieldsPalette.tileBorderActive.copy(alpha = 0.6f),
        topLeft = Offset.Zero,
        size = size,
        style = Stroke(width = 2f),
    )
}

internal fun tileContentDescription(
    tile: KcTileState,
    isSelected: Boolean,
    isPending: Boolean,
): String {
    val statusLabel = when (tile.status) {
        KcStatus.SEED -> "种子"
        KcStatus.GROWING -> "幼苗"
        KcStatus.MATURE -> "成熟"
        KcStatus.HARVESTED -> "已收获"
    }
    val percent = (tile.mastery * 100).toInt()
    val prefix = if (isSelected) "已选 · " else ""
    val suffix = if (isPending) " · 正在操作" else ""
    return "$prefix${tile.kcCode} ${tile.title} · $statusLabel · 掌握度 $percent%$suffix"
}

const val TILE_SIZE_DP: Int = 64

private const val KC_CODE_TAIL: Int = 3
private const val SEED_RADIUS_RATIO: Float = 0.07f
private const val GROW_OUTER_RATIO: Float = 0.22f
private const val GROW_INNER_RATIO: Float = 0.10f
private const val MATURE_RADIUS_RATIO: Float = 0.08f
private const val HARVEST_RADIUS_RATIO: Float = 0.04f
