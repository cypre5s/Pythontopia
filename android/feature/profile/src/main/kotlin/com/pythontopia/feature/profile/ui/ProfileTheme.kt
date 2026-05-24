package com.pythontopia.feature.profile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pythontopia.core.design.PytColors

/**
 * Feature-local 像素风组件：保持 R-12 demo 视觉与 `frontend/src/components/{component}` 像素镇风格一致。
 *
 * 没有上提到 core/design 的原因：profile 是首个深度使用 dashboard 卡片的 feature，
 * 等 classroom / path 也需要再抽象，避免过早抽象（AGENTS.md "外科手术式修改"）。
 */

private val PixelCornerRadius = 4.dp
private val PixelBorderWidth = 2.dp

/** 像素风 Card：方角 + 金色描边 + 零阴影，与星露谷 UI 一致。 */
@Composable
fun PixelCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(PixelCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = PytColors.bgElevated,
            contentColor = PytColors.textPrimary,
        ),
        border = BorderStroke(PixelBorderWidth, PytColors.borderStrong),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        content()
    }
}

/**
 * 维度仪表条：左标题、右数值百分比、下方进度条。
 *
 * [score] 取值 `[0, 1]`，对齐后端 `dimensions.{key}.score`；超出会被 clamp 防异常。
 * [filled] = false 时进度条灰显，提示该维度数据尚未生成。
 */
@Composable
fun DimensionProgressBar(
    title: String,
    score: Double,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
) {
    val safeScore = score.coerceIn(0.0, 1.0).toFloat()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = PytColors.textPrimary)
            Text(
                text = if (filled) "${(safeScore * 100).toInt()}%" else "—",
                style = MaterialTheme.typography.labelMedium,
                color = if (filled) PytColors.textMuted else PytColors.textHint,
            )
        }
        LinearProgressIndicator(
            progress = { if (filled) safeScore else 0f },
            modifier = Modifier.fillMaxWidth(),
            color = if (filled) PytColors.primary else PytColors.borderSoft,
            trackColor = PytColors.bgSunken,
            strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
        )
    }
}
