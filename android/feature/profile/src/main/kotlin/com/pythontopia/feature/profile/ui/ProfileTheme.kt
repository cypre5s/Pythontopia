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

/** profile feature 本地像素风组件，尚未上提到 core/design。 */

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

/** 维度仪表条；[score]/[confidence]/[evidenceCount]/[selfEditable] 均来自 dimensions 快照，score 超界会 clamp。 */
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
