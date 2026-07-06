package com.pythontopia.feature.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pythontopia.core.data.dto.world.CalendarTodayResponse
import com.pythontopia.core.design.PytColors
import com.pythontopia.core.design.PytSpace

/**
 * Hub HUD 覆盖层；透明背景不拦截触摸，状态由 [HubScreen] 参数传入。
 * 顶栏显示日历，底栏为快捷栏占位。
 */
@Composable
fun HudOverlay(
    calendar: CalendarTodayResponse?,
    nearbyNpcKey: String?,
    quickSlots: List<HudQuickSlot> = DEFAULT_QUICKSLOTS,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        TopStatusBar(
            calendar = calendar,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .align(Alignment.TopCenter),
        )
        BottomQuickslotBar(
            slots = quickSlots,
            highlight = nearbyNpcKey,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun TopStatusBar(
    calendar: CalendarTodayResponse?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(PytColors.bgPrimary.copy(alpha = HUD_BACKGROUND_ALPHA))
            .border(width = 1.dp, color = PytColors.borderSoft.copy(alpha = HUD_BORDER_ALPHA))
            .padding(horizontal = PytSpace.s3, vertical = PytSpace.s2),
        horizontalArrangement = Arrangement.spacedBy(PytSpace.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelHudChip(label = "季节", value = displaySeason(calendar?.season))
        PixelHudChip(label = "天", value = calendar?.dayIndex?.toString() ?: "—")
        PixelHudChip(label = "时段", value = displayTimeOfDay(calendar?.timeOfDay))
        PixelHudChip(label = "天气", value = displayWeather(calendar?.weather))
    }
}

@Composable
private fun BottomQuickslotBar(
    slots: List<HudQuickSlot>,
    highlight: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(PytColors.bgPrimary.copy(alpha = HUD_BACKGROUND_ALPHA))
            .border(width = 1.dp, color = PytColors.borderSoft.copy(alpha = HUD_BORDER_ALPHA))
            .padding(horizontal = PytSpace.s3, vertical = PytSpace.s2),
        horizontalArrangement = Arrangement.spacedBy(PytSpace.s2, alignment = Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        slots.take(QUICK_SLOT_COUNT).forEachIndexed { index, slot ->
            QuickSlotBox(
                slot = slot,
                index = index + 1,
                highlighted = highlight != null && index == 0,
            )
        }
    }
}

@Composable
private fun PixelHudChip(label: String, value: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(PytColors.bgElevated.copy(alpha = HUD_CHIP_ALPHA))
            .border(width = 1.dp, color = PytColors.border)
            .padding(horizontal = PytSpace.s2, vertical = PytSpace.s1),
    ) {
        Text(
            text = label,
            color = PytColors.textHint,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        )
        Text(
            text = value,
            color = PytColors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun QuickSlotBox(
    slot: HudQuickSlot,
    index: Int,
    highlighted: Boolean,
) {
    val border = if (highlighted) PytColors.warning else PytColors.border
    Box(
        modifier = Modifier
            .size(QUICK_SLOT_SIZE.dp)
            .background(PytColors.bgElevated.copy(alpha = HUD_CHIP_ALPHA))
            .border(width = if (highlighted) 2.dp else 1.dp, color = border),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = index.toString(),
                color = PytColors.textHint,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            )
            Text(
                text = slot.glyph,
                color = if (slot.enabled) PytColors.textPrimary else PytColors.textMuted,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (highlighted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PytColors.warning.copy(alpha = 0.10f)),
            )
        }
    }
}

/** 快捷栏槽位数据。 */
data class HudQuickSlot(
    val key: String,
    val glyph: String,
    val enabled: Boolean,
)

private val DEFAULT_QUICKSLOTS: List<HudQuickSlot> = listOf(
    HudQuickSlot(key = "talk", glyph = "对", enabled = true),
    HudQuickSlot(key = "bag", glyph = "包", enabled = false),
    HudQuickSlot(key = "tool", glyph = "具", enabled = false),
    HudQuickSlot(key = "retro", glyph = "复", enabled = false),
    HudQuickSlot(key = "map", glyph = "图", enabled = true),
)

private fun displaySeason(raw: String?): String = when (raw?.uppercase()) {
    "SPRING" -> "春"
    "SUMMER" -> "夏"
    "AUTUMN", "FALL" -> "秋"
    "WINTER" -> "冬"
    null -> "—"
    else -> raw
}

private fun displayTimeOfDay(raw: String?): String = when (raw?.uppercase()) {
    "MORNING" -> "晨"
    "NOON" -> "午"
    "AFTERNOON" -> "下午"
    "EVENING" -> "黄昏"
    "NIGHT" -> "夜"
    null -> "—"
    else -> raw
}

private fun displayWeather(raw: String?): String = when (raw?.uppercase()) {
    "CLEAR" -> "晴"
    "CLOUDY" -> "阴"
    "RAIN", "RAINY" -> "雨"
    "SNOW", "SNOWY" -> "雪"
    "STORM" -> "暴风"
    null -> "—"
    else -> raw
}

private const val HUD_BACKGROUND_ALPHA = 0.55f
private const val HUD_BORDER_ALPHA = 0.85f
private const val HUD_CHIP_ALPHA = 0.80f
private const val QUICK_SLOT_COUNT = 5
private const val QUICK_SLOT_SIZE = 44
