package com.pythontopia.feature.profile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pythontopia.core.design.PytColors
import com.pythontopia.core.design.PytShape
import com.pythontopia.core.design.PytSpace

/**
 * Feature-local 像素风组件：保持 R-12 demo 视觉与 `frontend/src/components/{component}` 像素镇风格一致。
 *
 * 没有上提到 core/design 的原因：profile 是首个深度使用 dashboard 卡片的 feature，
 * 等 classroom / path 也需要再抽象，避免过早抽象（AGENTS.md "外科手术式修改"）。
 */

private val PixelBorderWidth = 2.dp

/** 像素风 Card：方角 + 金色描边 + 零阴影，与星露谷 UI 一致。 */
@Composable
fun PixelCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = PytShape.sm,
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
 * 维度仪表条：左标题、右数值百分比、下方进度条 + 元信息行（可自评徽标 / 置信度 / 证据数）。
 *
 * 所有数值都来自后端 `GET /api/learner/profile/dimensions`：
 *  - [score] `[0,1]` effective 分（含读时 decay）；超界会被 clamp 防异常。
 *  - [confidence] `[0,1]` 置信度；为 null 时不展示该项。
 *  - [evidenceCount] 累计证据条数；为 0 表示无真实信号（配合 [hasSignal] 灰显，提醒 0.5 是中性默认而非测量值）。
 *  - [selfEditable] 来自 DTO 的权威可编辑标志，决定徽标显示「可自评」还是「系统聚合」。
 */
@Composable
fun DimensionProgressBar(
    title: String,
    score: Double,
    modifier: Modifier = Modifier,
    confidence: Double? = null,
    evidenceCount: Int? = null,
    selfEditable: Boolean = false,
    hasSignal: Boolean = true,
) {
    val safeScore = score.coerceIn(0.0, 1.0).toFloat()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(PytSpace.s1),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = PytColors.textPrimary)
            Text(
                text = "${(safeScore * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = if (hasSignal) PytColors.textMuted else PytColors.textHint,
            )
        }
        LinearProgressIndicator(
            progress = { safeScore },
            modifier = Modifier.fillMaxWidth(),
            color = if (hasSignal) PytColors.primary else PytColors.borderSoft,
            trackColor = PytColors.bgSunken,
            strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
        )
        DimensionMetaLine(
            confidence = confidence,
            evidenceCount = evidenceCount,
            selfEditable = selfEditable,
        )
    }
}

/** 仪表条下方的小字元信息：可自评徽标 + 置信度 + 证据数。 */
@Composable
private fun DimensionMetaLine(
    confidence: Double?,
    evidenceCount: Int?,
    selfEditable: Boolean,
) {
    val metaParts = buildList {
        confidence?.let { add("置信度 ${(it.coerceIn(0.0, 1.0) * 100).toInt()}%") }
        when {
            evidenceCount == null -> Unit
            evidenceCount <= 0 -> add("信号不足")
            else -> add("证据 $evidenceCount 条")
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PytSpace.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (selfEditable) "可自评" else "系统聚合",
            style = MaterialTheme.typography.labelSmall,
            color = if (selfEditable) PytColors.info else PytColors.textHint,
        )
        if (metaParts.isNotEmpty()) {
            Text(
                text = metaParts.joinToString("  ·  "),
                style = MaterialTheme.typography.labelSmall,
                color = PytColors.textHint,
            )
        }
    }
}
