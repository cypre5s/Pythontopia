package com.pythontopia.feature.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pythontopia.core.design.PytErrorRetry
import com.pythontopia.feature.dialog.DemoDialogTree
import com.pythontopia.feature.dialog.DialogViewModel
import com.pythontopia.feature.dialog.NpcDialogPanel

/**
 * Hub 主屏：LibGDX 渲染（[HubLibGdxView]）铺满整个视口，Compose 只负责 HUD 与
 * 底部操作面板覆盖层。
 *
 * UI 状态完全由 [HubViewModel] 驱动；LibGDX 渲染线程通过 [HubViewModel.bridge] 单向
 * 上报事件，Compose collect bridge 的 Flow 把事件灌进 ViewModel。
 */
@Composable
fun HubScreen(
    onLogout: () -> Unit = {},
    hubViewModel: HubViewModel = hiltViewModel(),
    dialogViewModel: DialogViewModel = hiltViewModel(),
) {
    val state by hubViewModel.state.collectAsStateWithLifecycle()
    val dialogState by dialogViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(hubViewModel.bridge) {
        hubViewModel.bridge.events().collect { event ->
            hubViewModel.consumeBridgeEvent(event)
        }
    }

    LaunchedEffect(state.dialogOpenedFor) {
        val key = state.dialogOpenedFor
        if (key != null && !dialogState.visible) {
            dialogViewModel.openFor(key)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            HubLibGdxView(
                bridge = hubViewModel.bridge,
                modifier = Modifier.fillMaxSize(),
            )
            HudOverlay(
                calendar = state.calendar,
                nearbyNpcKey = state.nearbyNpcKey,
                modifier = Modifier.fillMaxSize(),
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 76.dp)
                    .heightIn(max = BOTTOM_PANEL_MAX_HEIGHT.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = BOTTOM_PANEL_ALPHA),
                tonalElevation = 6.dp,
                shape = MaterialTheme.shapes.medium,
            ) {
                if (dialogState.visible) {
                    NpcDialogPanel(
                        modifier = Modifier.fillMaxWidth(),
                        viewModel = dialogViewModel,
                        onDismissed = { hubViewModel.dialogDismissed() },
                    )
                } else {
                    HubHud(
                        state = state,
                        onInteract = hubViewModel::requestInteract,
                        onLogout = onLogout,
                    )
                }
            }
            state.errorMessage?.let { message ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 64.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.small,
                    tonalElevation = 4.dp,
                ) {
                    PytErrorRetry(
                        message = message,
                        onRetry = hubViewModel::retry,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HubHud(
    state: HubUiState,
    onInteract: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Pythontopia · Hub", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HudChip(label = "地图", value = state.mapKey)
            HudChip(label = "季节", value = state.calendar?.season ?: "—")
            HudChip(label = "时段", value = state.calendar?.timeOfDay ?: "—")
            HudChip(label = "天气", value = state.calendar?.weather ?: "—")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HudChip(label = "背包", value = "${state.world?.inventory?.sumOf { it.qty } ?: 0}")
            HudChip(label = "金币", value = "${state.world?.currency?.coins ?: 0}")
            HudChip(label = "宝石", value = "${state.world?.currency?.gems ?: 0}")
        }
        if (state.isBootstrapping) {
            Text("正在加载世界...", style = MaterialTheme.typography.bodyMedium)
        }
        state.statusMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
        val nearby = state.nearbyNpcKey
        if (nearby != null) {
            Text(
                text = "附近 NPC：${nearby}（${DemoDialogTree.displayNameFor(nearby)}）",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Button(onClick = onInteract, modifier = Modifier.fillMaxWidth()) {
                Text("和 ${DemoDialogTree.displayNameFor(nearby)} 对话")
            }
        } else {
            Text(
                text = "用 WASD / 方向键 / 触摸方向探索 5 张地图，走到 NPC 旁即可对话。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Text("退出登录")
        }
    }
}

private const val BOTTOM_PANEL_MAX_HEIGHT: Int = 360
private const val BOTTOM_PANEL_ALPHA: Float = 0.94f

@Composable
private fun HudChip(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
