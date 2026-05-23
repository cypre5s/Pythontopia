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

/**
 * 单个 KC 格子。占据 64dp × 64dp，由四部分组成：
 *
 *  - 外层 [Box]：圆角 + 边框（被选中时边框换成亮色）；
 *  - [ComposeCanvas]：按 [KcTileState.status] 绘制 4 种像素风格小图——
 *      * SEED：3 颗深色种子嵌在翻土上；
 *      * GROWING：双层圆形象征"嫩苗+土壤"；
 *      * MATURE：金色十字"麦穗"+ 边角光点；
 *      * HARVESTED：留 4 个钉孔表示已收割的空地；
 *  - 顶部 [Text]：显示 KC code 末尾 3 位（如 `001`），让网格保持课程结构可识别；
 *  - 底部进度条：mastery 百分比微指示，便于玩家一眼看到 `growing` 中 KC 的成长度。
 *
 * 单独把渲染逻辑（[drawKcGlyph]）做成纯函数，方便在 [KcTileTest] 中校验它对各 status 的分支。
 */
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

/**
 * 仅按 status 绘制核心 glyph；不依赖 KC 内容，纯像素风：
 *  - SEED：3 颗 4dp 圆点排成三角形；
 *  - GROWING：1 颗外径 12dp + 内径 6dp 的双层圆；
 *  - MATURE：5 颗 5dp 圆形成"+"号；
 *  - HARVESTED：4 个 2dp 钉孔分布四角。
 */
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

/** mastery 进度条画在格子底部 3dp 高的窄带，宽度 = mastery × 100%。 */
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

/** pending 浮层：四边外描边，提示当前 KC 正在执行 water/harvest。 */
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
