package com.pythontopia.feature.fields

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** 知识田顶部 HUD：环境概览与 KC 状态汇总。 */
@Composable
fun FieldsHud(
    state: FieldsUiState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        color = FieldsPalette.hudBackground,
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(PaddingValues(horizontal = 16.dp, vertical = 12.dp)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "知识田",
                    style = MaterialTheme.typography.headlineSmall,
                    color = FieldsPalette.hudAccent,
                )
                HudChip(label = "季节", value = displaySeason(state.season))
                HudChip(label = "天气", value = displayWeather(state.weather))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBadge("总数", "${state.totalCount}", colorAccent = false)
                StatBadge("幼苗", "${state.growingCount}", colorAccent = false)
                StatBadge("成熟", "${state.matureCount}", colorAccent = true)
                StatBadge("已收", "${state.harvestedCount}", colorAccent = false)
            }
            if (state.isLoading) {
                Text(
                    text = "正在拉取 65 KC 网格...",
                    style = MaterialTheme.typography.bodySmall,
                    color = FieldsPalette.hudAccent,
                )
            }
            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun HudChip(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(6.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = FieldsPalette.hudAccent)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StatBadge(label: String, value: String, colorAccent: Boolean) {
    Surface(
        color = if (colorAccent) FieldsPalette.tileMature.copy(alpha = 0.18f) else FieldsPalette.tileSeed.copy(alpha = 0.4f),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.background(FieldsPalette.hudBackground),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = FieldsPalette.hudAccent)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

internal fun displaySeason(raw: String): String = when (raw.uppercase()) {
    "SPRING" -> "春"
    "SUMMER" -> "夏"
    "AUTUMN", "FALL" -> "秋"
    "WINTER" -> "冬"
    else -> raw
}

internal fun displayWeather(raw: String): String = when (raw.uppercase()) {
    "CLEAR", "SUNNY" -> "晴"
    "RAIN", "RAINY" -> "雨"
    "SNOW", "SNOWY" -> "雪"
    "CLOUDY" -> "阴"
    else -> raw
}

