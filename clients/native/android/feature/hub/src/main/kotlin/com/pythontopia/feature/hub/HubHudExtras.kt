package com.pythontopia.feature.hub

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pythontopia.core.design.PytColors
import com.pythontopia.core.design.PytSpace
import com.pythontopia.game.MinimapData
import kotlin.math.max

/** 小镇小地图：建筑块 + NPC 点 + 玩家点（y-up → 画布 y-down 翻转）；点选瞬移。 */
@Composable
fun Minimap(
    data: MinimapData,
    playerX: Int,
    playerY: Int,
    nearbyNpc: String?,
    modifier: Modifier = Modifier,
    onTeleport: (Int, Int) -> Unit = { _, _ -> },
) {
    Box(
        modifier = modifier
            .background(PytColors.bgPrimary.copy(alpha = 0.65f))
            .border(1.dp, PytColors.borderSoft.copy(alpha = 0.85f)),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp)
                .pointerInput(data) {
                    detectTapGestures { off ->
                        val cw = size.width / data.width.toFloat()
                        val ch = size.height / data.height.toFloat()
                        val tx = (off.x / cw).toInt().coerceIn(0, data.width - 1)
                        val row = (off.y / ch).toInt().coerceIn(0, data.height - 1)
                        onTeleport(tx, data.height - 1 - row)
                    }
                },
        ) {
            val cw = size.width / data.width
            val ch = size.height / data.height
            fun topY(tileY: Int, h: Int) = (data.height - tileY - h) * ch
            fun cellCenter(x: Int, y: Int) = Offset(x * cw + cw / 2f, (data.height - 1 - y) * ch + ch / 2f)

            drawRect(color = PytColors.bgElevated.copy(alpha = 0.8f), topLeft = Offset.Zero, size = Size(size.width, size.height))
            data.buildings.forEach { b ->
                drawRect(
                    color = PytColors.borderStrong.copy(alpha = 0.7f),
                    topLeft = Offset(b.x * cw, topY(b.y, b.h)),
                    size = Size(b.w * cw, b.h * ch),
                )
            }
            data.npcs.forEach { n ->
                val near = n.key == nearbyNpc
                drawCircle(
                    color = if (near) PytColors.warning else PytColors.info,
                    radius = max(1.5f, cw * (if (near) 0.9f else 0.55f)),
                    center = cellCenter(n.x, n.y),
                )
            }
            drawCircle(color = PytColors.success, radius = max(2.5f, cw * 0.9f), center = cellCenter(playerX, playerY))
        }
    }
}

/** HUD 右侧动作按钮：随身助教 / 快速旅行 / 菜单。 */
@Composable
fun HudActionColumn(
    onCompanion: () -> Unit,
    onFastTravel: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PytSpace.s2)) {
        HudActionButton("助教", onCompanion)
        HudActionButton("旅行", onFastTravel)
        HudActionButton("菜单", onMenu)
    }
}

@Composable
private fun HudActionButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(PytColors.bgElevated.copy(alpha = 0.85f))
            .border(1.dp, PytColors.border, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = PytColors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** 快速旅行：跳转到核心学习入口（对齐 Web FastTravelPanel；Android 经 dock 路由导航）。 */
private val FAST_TRAVEL: List<Pair<String, String>> = listOf(
    "mainline" to "主线冒险",
    "workshop" to "百晓工坊",
    "library" to "课程图书馆",
    "lecture" to "视频讲义",
    "arena" to "训练场",
    "expedition" to "无尽远征",
    "mine" to "Bug 矿洞",
    "retro" to "复盘大厅",
    "profile" to "学习画像",
    "board" to "任务板",
)

@Composable
fun FastTravelSheet(onNavigate: (String) -> Unit, onClose: () -> Unit) {
    Scrim(onClose) {
        SheetCard(title = "快速旅行") {
            FAST_TRAVEL.forEach { (route, name) ->
                SheetRow(name) {
                    onClose()
                    onNavigate(route)
                }
            }
        }
    }
}

/** 暂停菜单：操作帮助 / 致谢 / 退出登录（对齐 Web PauseMenu「系统」组）。 */
@Composable
fun PauseMenuSheet(onNavigate: (String) -> Unit, onLogout: () -> Unit, onClose: () -> Unit) {
    Scrim(onClose) {
        SheetCard(title = "菜单") {
            SheetRow("继续游戏") { onClose() }
            SheetRow("操作帮助") { onClose(); onNavigate("guide") }
            SheetRow("制作致谢") { onClose(); onNavigate("credits") }
            SheetRow("退出登录", danger = true) { onClose(); onLogout() }
        }
    }
}

@Composable
private fun Scrim(onClose: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PytColors.bgSunken.copy(alpha = 0.72f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun SheetCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .width(260.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(PytColors.bgElevated)
            .border(2.dp, PytColors.borderStrong, RoundedCornerShape(8.dp))
            .padding(PytSpace.s3),
        verticalArrangement = Arrangement.spacedBy(PytSpace.s2),
    ) {
        Text(title, color = PytColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun SheetRow(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(PytColors.bgSoft)
            .border(1.dp, if (danger) PytColors.danger.copy(alpha = 0.6f) else PytColors.borderSoft, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = PytSpace.s3, vertical = PytSpace.s2),
    ) {
        Text(label, color = if (danger) PytColors.danger else PytColors.textPrimary, fontSize = 14.sp)
    }
}
