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
 * Hub HUD 覆盖层：透明 [Box] 铺满 LibGDX 渲染区，顶端横幅显示季节 / 天数 / 时段 / 天气，
 * 底端 5 个像素方框作为道具/技能快捷栏占位（R-13 接入真实背包后绑定 KC / 道具 slot）。
 *
 * 设计取舍：
 * - 完全透明背景，让 LibGDX 渲染穿透显示；只有顶/底两条像素半透明带；
 * - 顶栏 4 个 chip + 底栏 5 个 slot，全部用 [PixelHudChip] / [QuickSlotBox] 自绘
 *   像素描边 + tonal 颜色，匹配 `docs/stardew/02-frames-9slice-spec.md` 的"硬边 1px 描边"；
 * - 不响应触摸（[Modifier.fillMaxSize] 不带 clickable），让 LibGDX 的虚拟摇杆 + Compose
 *   下方面板正常接收点击（`docs/android/03-rendering-libgdx-vs-compose.md` §7.2 红线 1）。
 * - 不持有 ViewModel；状态全部由 [HubScreen] 通过参数传入，方便 Preview 与单测。
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

/**
 * HUD 顶部状态条：季节 / 天数 / 时段 / 天气 四个 chip。
 * calendar 为 null 时（未联网 demo）显示 "—" 占位。
 */
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

/**
 * 底部 5 个 quickslot；highlight 命中（如 NPC 在范围内）时第 1 个 slot 高亮成金边，
 * 提示玩家"按交互键 / 点 slot 1 触发对话"。
 */
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

/**
 * Quickslot 数据；R-12 demo 用 hardcoded 5 项占位（对话 / 背包 / 农具 / 复盘 / 地图），
 * R-13 接入背包系统后由 ViewModel 提供动态列表。
 */
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
