package com.pythontopia.feature.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.pythontopia.feature.dialog.DemoDialogTree
import com.pythontopia.feature.dialog.DialogViewModel
import com.pythontopia.feature.dialog.NpcDialogPanel
import com.pythontopia.game.HubEvent
import kotlinx.coroutines.delay

/**
 * Hub 主屏：上半 50% 是 LibGDX 渲染（[HubLibGdxView]），下半 50% 是 NPC 对话面板
 * （[NpcDialogPanel]）或操作 HUD（[HubHud]）。
 *
 * UI 状态完全由 [HubViewModel] 驱动；LibGDX 渲染线程通过 [HubViewModel.bridge] 单向
 * 上报 [HubEvent]，Compose 用 [LaunchedEffect] 轮询 bridge 把事件灌进 ViewModel。
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
        val bridge = hubViewModel.bridge
        while (true) {
            var event = bridge.poll()
            while (event != null) {
                hubViewModel.consumeBridgeEvent(event)
                event = bridge.poll()
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    LaunchedEffect(state.dialogOpenedFor) {
        val key = state.dialogOpenedFor
        if (key != null && !dialogState.visible) {
            dialogViewModel.openFor(key)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                HubLibGdxView(
                    bridge = hubViewModel.bridge,
                    modifier = Modifier.fillMaxSize(),
                )
                HudOverlay(
                    calendar = state.calendar,
                    nearbyNpcKey = state.nearbyNpcKey,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (dialogState.visible) {
                    NpcDialogPanel(
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
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Pythontopia · Hub", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HudChip(label = "季节", value = state.calendar?.season ?: "—")
            HudChip(label = "时段", value = state.calendar?.timeOfDay ?: "—")
            HudChip(label = "天气", value = state.calendar?.weather ?: "—")
        }
        if (state.isBootstrapping) {
            Text("正在加载世界...", style = MaterialTheme.typography.bodyMedium)
        }
        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(8.dp))
        val nearby = state.nearbyNpcKey
        if (nearby != null) {
            Text(
                text = "附近 NPC：${nearby}（${displayNameFor(nearby)}）",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Button(onClick = onInteract, modifier = Modifier.fillMaxWidth()) {
                Text("和 ${displayNameFor(nearby)} 对话")
            }
        } else {
            Text(
                text = "用 WASD / 方向键 / 触摸方向走到 NPC 旁。Demo 仅启用「镇长 Py」(tile 25, 10)。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Text("退出登录")
        }
    }
}

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

private fun displayNameFor(npcKey: String): String = when (npcKey) {
    "mayor_py" -> "镇长 Py"
    DemoDialogTree.NPC_KEY -> "镇长 Py"
    else -> npcKey
}

private const val POLL_INTERVAL_MS: Long = 200L
