package com.pythontopia.feature.expedition

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pythontopia.core.design.PytColors
import com.pythontopia.core.design.PytSpace
import kotlinx.coroutines.delay

/**
 * 无尽远征（Roguelike）页面：开局 → 分支地图选点 → 概念事件 / 战斗判题 → 结算。
 * 移植 Web `EndlessExpeditionPage.vue` 的交互真源（列表式分层地图；像素塔后续增强）。
 */
@Composable
fun ExpeditionScreen(
    onExit: () -> Unit = {},
    viewModel: ExpeditionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // 战斗计时：每秒 tick（VM 内部仅在 challenge 阶段累加）。
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            viewModel.tick()
        }
    }
    // 一次性提示：展示约 2.2s 后清除。
    LaunchedEffect(state.toast) {
        if (state.toast != null) {
            delay(2200)
            viewModel.clearToast()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PytColors.bgPrimary)
            .padding(PytSpace.s3)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PytSpace.s3),
    ) {
        Header(state, onExit)
        state.toast?.let { ToastBanner(it) }
        if (state.phase != RogueRunPhase.IDLE && state.relics.isNotEmpty()) RelicsBar(state.relics)

        when (state.phase) {
            RogueRunPhase.IDLE -> StartView(state, viewModel)
            RogueRunPhase.MAP -> MapView(state, viewModel)
            RogueRunPhase.EVENT -> EventView(state, viewModel)
            RogueRunPhase.CHALLENGE -> ChallengeView(state, viewModel)
            RogueRunPhase.CLEARED, RogueRunPhase.OVER -> EndView(state, viewModel, onExit)
        }
    }
}

@Composable
private fun Header(state: ExpeditionUiState, onExit: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("无尽远征 · 知识前沿", color = PytColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (state.phase != RogueRunPhase.IDLE) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(PytSpace.s3)) {
                    Stat("体力", "♥".repeat(state.hp) + "♡".repeat((state.maxHp - state.hp).coerceAtLeast(0)), PytColors.danger)
                    Stat("气势", "×${state.momentum}", PytColors.warning)
                    Stat("得分", "${state.score}", PytColors.warning)
                    Stat("深度", "${state.depth}", PytColors.info)
                }
            }
        }
        OutlinedButton(
            onClick = onExit,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(2.dp, PytColors.borderStrong),
        ) {
            Text("返回小镇", color = PytColors.textPrimary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = PytColors.textMuted, fontSize = 10.sp)
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ToastBanner(text: String) {
    Surface(
        color = PytColors.bgElevated,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, PytColors.warning),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, color = PytColors.textPrimary, fontSize = 13.sp, modifier = Modifier.padding(PytSpace.s2))
    }
}

@Composable
private fun RelicsBar(relics: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PytSpace.s2),
    ) {
        Text("学习增益", color = PytColors.textMuted, fontSize = 11.sp)
        relics.forEach { id ->
            val relic = RogueRelics.byId(id)
            Surface(color = PytColors.bgSoft, shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, PytColors.borderSoft)) {
                Text(
                    "${relic?.icon ?: "◆"} ${relic?.name ?: id}",
                    color = PytColors.textHint,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = PytSpace.s2, vertical = PytSpace.s1),
                )
            }
        }
    }
}

@Composable
private fun StartView(state: ExpeditionUiState, vm: ExpeditionViewModel) {
    var focus by remember { mutableStateOf<Int?>(state.focusChapter) }
    var showConstellation by remember { mutableStateOf(false) }

    Panel {
        Text(
            "按你的掌握度 + 画像生成分支远征：选路闯关、攒气势；答错温和扣体力，掌握度永久保留。",
            color = PytColors.textHint,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(PytSpace.s2))
        Text("聚焦章节", color = PytColors.textMuted, fontSize = 12.sp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(PytSpace.s2),
        ) {
            FocusChip("全局", focus == null) { focus = null }
            for (ch in 1..7) FocusChip("第 $ch 章", focus == ch) { focus = ch }
        }
    }

    Button(
        onClick = { vm.start(focus) },
        colors = ButtonDefaults.buttonColors(containerColor = PytColors.primary, contentColor = PytColors.onPrimary),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("开始远征")
    }
    OutlinedButton(
        onClick = { vm.startDaily() },
        enabled = state.dailyAvailable,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(2.dp, PytColors.borderStrong),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (state.dailyAvailable) "☀ 今日挑战 · 弱项" else "今日挑战已完成", color = PytColors.textPrimary)
    }

    Text(
        if (showConstellation) "收起知识星座 ▴" else "✦ 查看知识星座（跨局进度）",
        color = PytColors.info,
        fontSize = 12.sp,
        modifier = Modifier.clickable { showConstellation = !showConstellation }.padding(vertical = PytSpace.s1),
    )
    if (showConstellation) Constellation(state.mastery)
}

@Composable
private fun FocusChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) PytColors.primary else PytColors.bgSoft,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(2.dp, if (selected) PytColors.borderStrong else PytColors.borderSoft),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            label,
            color = if (selected) PytColors.onPrimary else PytColors.textMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = PytSpace.s2, vertical = PytSpace.s1),
        )
    }
}

@Composable
private fun MapView(state: ExpeditionUiState, vm: ExpeditionViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("点亮的房间可前往，选了同层就不回头——一路打到顶层 BOSS。", color = PytColors.textMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text("🏳 弃局", color = PytColors.danger, fontSize = 12.sp, modifier = Modifier.clickable { vm.abort() }.padding(start = PytSpace.s2))
    }
    val layers = state.nodesByLayer
    // 顶层在上：倒序展示（boss 在最前）。
    for (li in layers.indices.reversed()) {
        val layer = layers[li]
        if (layer.isEmpty()) continue
        Column(verticalArrangement = Arrangement.spacedBy(PytSpace.s1)) {
            Text(if (li == state.layers - 1) "顶层" else "第 ${li + 1} 层", color = PytColors.textMuted, fontSize = 11.sp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(PytSpace.s2),
            ) {
                layer.forEach { node -> NodeCard(node) { vm.enterNode(node.id) } }
            }
        }
    }
}

@Composable
private fun NodeCard(node: RogueNode, onClick: () -> Unit) {
    val available = node.state == RogueNodeState.AVAILABLE
    val cleared = node.state == RogueNodeState.CLEARED
    val border = when {
        available -> PytColors.warning
        cleared -> PytColors.success
        else -> PytColors.borderSoft
    }
    val alpha = if (node.state == RogueNodeState.LOCKED || node.state == RogueNodeState.BYPASSED) 0.45f else 1f
    Surface(
        color = if (cleared) PytColors.bgSoft else PytColors.bgElevated,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(2.dp, border.copy(alpha = alpha)),
        modifier = Modifier
            .width(150.dp)
            .clickable(enabled = available, onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(PytSpace.s2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(RogueConfig.icon(node.kind), fontSize = 14.sp)
                Text(RogueConfig.label(node.kind), color = PytColors.textPrimary.copy(alpha = alpha), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (cleared) Text("✓", color = PytColors.success, fontSize = 12.sp)
            }
            if (node.kcCode != null) Text(kcDisplayName(node.kcCode), color = PytColors.textHint.copy(alpha = alpha), fontSize = 11.sp)
            if (node.difficulty > 0) Text(stars(node.difficulty), color = PytColors.danger.copy(alpha = alpha), fontSize = 11.sp)
        }
    }
}

@Composable
private fun EventView(state: ExpeditionUiState, vm: ExpeditionViewModel) {
    Panel {
        Text("概念事件 · ${kcDisplayName(state.activeNode?.kcCode)}", color = PytColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(PytSpace.s2))
        Text(state.activeEventText, color = PytColors.textHint, fontSize = 13.sp)
    }
    RogueEvents.CHOICES.forEach { choice ->
        Surface(
            color = PytColors.bgElevated,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(2.dp, PytColors.borderStrong),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { vm.chooseEvent(choice.key) },
        ) {
            Column(modifier = Modifier.padding(PytSpace.s3)) {
                Text("${choice.icon} ${choice.label}", color = PytColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(choice.desc, color = PytColors.textMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ChallengeView(state: ExpeditionUiState, vm: ExpeditionViewModel) {
    val node = state.activeNode
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(PytSpace.s1),
    ) {
        Chip("${RogueConfig.icon(node?.kind ?: RogueNodeKind.BATTLE)} ${RogueConfig.label(node?.kind ?: RogueNodeKind.BATTLE)}")
        Chip(kcDisplayName(node?.kcCode))
        Chip("难度 ${node?.difficulty ?: 0}/5")
        if (node?.kind == RogueNodeKind.BOSS) Chip("阶段 ${state.bossPhase}/${state.bossPhasesTotal}", PytColors.danger)
        Chip("⏱ ${state.elapsedSec}s", PytColors.warning)
    }

    when {
        state.loading && state.activeProblem == null -> Text("正在出题…", color = PytColors.textHint, fontSize = 13.sp)
        state.errorMessage.isNotEmpty() && state.activeProblem == null -> ErrorRetry(state.errorMessage) { vm.retryChallenge() }
        state.activeProblem != null -> {
            val p = state.activeProblem
            Panel {
                Text(p.statement.ifBlank { "（题目加载中）" }, color = PytColors.textPrimary, fontSize = 14.sp)
                if (!p.sampleInput.isNullOrBlank() || !p.sampleOutput.isNullOrBlank()) {
                    Text("样例输入", color = PytColors.textHint, fontSize = 11.sp, modifier = Modifier.padding(top = PytSpace.s1))
                    MonoBlock(p.sampleInput.orEmpty())
                    Text("样例输出", color = PytColors.textHint, fontSize = 11.sp, modifier = Modifier.padding(top = PytSpace.s1))
                    MonoBlock(p.sampleOutput.orEmpty())
                }
            }
            CodeField(state.activeCode, vm::onCodeChange)
            if (state.errorMessage.isNotEmpty()) Text(state.errorMessage, color = PytColors.danger, fontSize = 12.sp)

            val result = state.challengeResult
            if (result == null) {
                Button(
                    onClick = vm::submit,
                    enabled = !state.loading,
                    colors = ButtonDefaults.buttonColors(containerColor = PytColors.primary, contentColor = PytColors.onPrimary),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.loading) "评测中…" else "提交答案")
                }
            } else {
                ResultPanel(result, node?.kind == RogueNodeKind.BOSS, state.hp, vm)
            }
        }
    }
}

@Composable
private fun ResultPanel(result: ExpeditionAttemptResultDto, isBoss: Boolean, hp: Int, vm: ExpeditionViewModel) {
    Surface(
        color = PytColors.bgElevated,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(2.dp, if (result.passed) PytColors.success else PytColors.danger),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(PytSpace.s3), verticalArrangement = Arrangement.spacedBy(PytSpace.s1)) {
            Text(
                if (result.passed) "通过！+${result.score} 分" else "未通过 · 体力 $hp",
                color = if (result.passed) PytColors.success else PytColors.danger,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Text("通过测试 ${result.passedTestCases}/${result.totalTestCases}", color = PytColors.textMuted, fontSize = 12.sp)
            if (result.feedback.isNotBlank()) Text(result.feedback, color = PytColors.textPrimary, fontSize = 13.sp)
            if (!result.passed && !result.failureFirstHint.isNullOrBlank()) {
                Text("提示：${result.failureFirstHint}", color = PytColors.warning, fontSize = 12.sp)
            }
            if (result.passed) {
                Button(
                    onClick = vm::continueAfterChallenge,
                    colors = ButtonDefaults.buttonColors(containerColor = PytColors.success, contentColor = PytColors.bgPrimary),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isBoss) "通关！" else "继续闯关")
                }
            } else if (hp > 0) {
                OutlinedButton(
                    onClick = vm::retryChallenge,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(2.dp, PytColors.borderStrong),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("再试一次（换一题）", color = PytColors.textPrimary)
                }
            } else {
                Button(
                    onClick = vm::continueAfterChallenge,
                    colors = ButtonDefaults.buttonColors(containerColor = PytColors.danger, contentColor = PytColors.onPrimary),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("体力耗尽 · 查看结算")
                }
            }
        }
    }
}

@Composable
private fun EndView(state: ExpeditionUiState, vm: ExpeditionViewModel, onExit: () -> Unit) {
    val win = state.phase == RogueRunPhase.CLEARED
    Panel {
        Text(
            if (win) "击穿知识闸 · 本局胜利！" else "本局远征结束",
            color = if (win) PytColors.success else PytColors.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(PytSpace.s2))
        Text("深度 ${state.depth} 层 · 得分 ${state.score} · 最高气势 ×${state.bestMomentum} · 答对 ${state.passedCount}/${state.attempts}", color = PytColors.textPrimary, fontSize = 13.sp)
        Text("做过的每题都已入库，掌握度永久保留。", color = PytColors.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = PytSpace.s1))
    }
    Constellation(state.mastery)
    Button(
        onClick = { vm.restart(state.focusChapter) },
        colors = ButtonDefaults.buttonColors(containerColor = PytColors.primary, contentColor = PytColors.onPrimary),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("再来一局")
    }
    OutlinedButton(
        onClick = onExit,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(2.dp, PytColors.borderStrong),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("返回小镇", color = PytColors.textPrimary)
    }
}

/** 知识星座：65 KC 掌握度小点阵（绿=已掌握，黄=进行中，灰=未点亮）。 */
@Composable
private fun Constellation(mastery: Map<String, Double>) {
    val codes = RogueConfig.allKcCodes()
    val mastered = codes.count { (mastery[it] ?: 0.0) >= 0.8 }
    Panel {
        Text("知识星座 · 已点亮 $mastered / ${codes.size}", color = PytColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(PytSpace.s1))
        codes.chunked(13).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                row.forEach { code ->
                    val m = mastery[code] ?: 0.0
                    val c = when {
                        m >= 0.8 -> PytColors.success
                        m >= 0.2 -> PytColors.warning
                        else -> PytColors.borderSoft
                    }
                    Box(modifier = Modifier.size(12.dp).background(c, RoundedCornerShape(3.dp)))
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, color: Color = PytColors.info) {
    Surface(color = PytColors.bgSoft, shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, color)) {
        Text(text, color = color, fontSize = 11.sp, modifier = Modifier.padding(horizontal = PytSpace.s2, vertical = 2.dp))
    }
}

@Composable
private fun ErrorRetry(message: String, onRetry: () -> Unit) {
    Surface(
        color = PytColors.bgElevated,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(2.dp, PytColors.danger),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(PytSpace.s3), verticalArrangement = Arrangement.spacedBy(PytSpace.s2)) {
            Text(message, color = PytColors.danger, fontSize = 13.sp)
            OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(6.dp), border = BorderStroke(2.dp, PytColors.borderStrong)) {
                Text("重试", color = PytColors.textPrimary)
            }
        }
    }
}

@Composable
private fun Panel(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        color = PytColors.bgElevated,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(2.dp, PytColors.borderStrong),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(PytSpace.s3), content = content)
    }
}

@Composable
private fun MonoBlock(text: String) {
    Surface(color = PytColors.bgSoft, shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text.ifBlank { "（空）" },
            color = PytColors.textPrimary,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(PytSpace.s2),
        )
    }
}

@Composable
private fun CodeField(value: String, onChange: (String) -> Unit) {
    Surface(
        color = PytColors.bgSoft,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(2.dp, PytColors.borderStrong),
        modifier = Modifier.fillMaxWidth(),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = TextStyle(color = PytColors.textPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
            cursorBrush = SolidColor(PytColors.warning),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp)
                .padding(PytSpace.s3),
        )
    }
}

private fun stars(n: Int): String = "★".repeat(n.coerceIn(0, 5)) + "☆".repeat((5 - n).coerceIn(0, 5))
