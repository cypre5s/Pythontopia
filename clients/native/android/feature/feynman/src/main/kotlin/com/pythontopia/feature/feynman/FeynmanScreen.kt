package com.pythontopia.feature.feynman

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pythontopia.core.design.PytColors
import com.pythontopia.core.design.PytLoading
import com.pythontopia.core.design.PytSpace

@Composable
fun FeynmanScreen(
    prefillKc: String? = null,
    viewModel: FeynmanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(prefillKc) {
        if (!prefillKc.isNullOrBlank()) viewModel.prefillKc(prefillKc)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PytColors.bgPrimary)
            .padding(PytSpace.s3)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PytSpace.s3),
    ) {
        IntroHeader(
            kcValue = state.kcCode,
            onKcChange = viewModel::onKcChange,
            onOpen = viewModel::openSession,
            loading = state.loading,
            hasSession = state.currentSession != null,
        )

        if (state.error != null) {
            ErrorBanner(state.error!!)
        }

        when {
            state.loading && state.currentSession == null -> PytLoading()
            state.currentSession == null -> HintPanel()
            else -> SessionBody(state, viewModel)
        }
    }
}

@Composable
private fun IntroHeader(
    kcValue: String,
    onKcChange: (String) -> Unit,
    onOpen: () -> Unit,
    loading: Boolean,
    hasSession: Boolean,
) {
    Panel {
        Text("创新点 C · 费曼反演", color = PytColors.textMuted, fontSize = 12.sp)
        Text(
            "教会会犯你同款错的 AI 学弟",
            color = PytColors.textPrimary,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = PytSpace.s1),
        )
        Text(
            "后端用你的真实错题与事件抽取学弟的误解（每条带 event_id 溯源）。用自己的话讲通，" +
                "再让它写代码、跑真判题——learning-by-teaching。",
            color = PytColors.textHint,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = PytSpace.s1),
        )
        Text(
            "知识点（KC 代码）",
            color = PytColors.textMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = PytSpace.s2),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = PytSpace.s1),
            horizontalArrangement = Arrangement.spacedBy(PytSpace.s2),
        ) {
            Surface(
                color = PytColors.bgSoft,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(2.dp, PytColors.borderStrong),
                modifier = Modifier.weight(1f),
            ) {
                BasicTextField(
                    value = kcValue,
                    onValueChange = onKcChange,
                    singleLine = true,
                    textStyle = TextStyle(color = PytColors.textPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(PytColors.warning),
                    decorationBox = { inner ->
                        if (kcValue.isEmpty()) {
                            Text("例如 PY43-KC-016", color = PytColors.textHint, fontSize = 14.sp)
                        }
                        inner()
                    },
                    modifier = Modifier.fillMaxWidth().padding(PytSpace.s3),
                )
            }
            Button(
                onClick = onOpen,
                enabled = !loading && kcValue.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PytColors.primary,
                    contentColor = PytColors.onPrimary,
                ),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(if (hasSession) "重新开始" else "进入反演")
            }
        }
    }
}

@Composable
private fun HintPanel() {
    Panel {
        Text(
            "先填入一个你想巩固的知识点 KC 代码，再开始费曼反演。",
            color = PytColors.textHint,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun SessionBody(state: FeynmanUiState, vm: FeynmanViewModel) {
    var teachingText by rememberSaveable(state.currentSession?.id) { mutableStateOf("") }

    SectionTitle("教会 AI 学弟：${state.activeKc}")

    // 学弟状态：误解列表
    Panel {
        Text("学弟状态", color = PytColors.textMuted, fontSize = 13.sp)
        val items = state.misconceptions
        if (items.isEmpty()) {
            Text(
                "这个 KC 暂无真实错误史，不生成虚构误解。",
                color = PytColors.textHint,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = PytSpace.s1),
            )
        } else {
            items.forEach { m ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = PytSpace.s1),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(m.tag, color = PytColors.textPrimary, fontSize = 13.sp)
                    Text(
                        if (m.resolved) "已讲通" else "待讲清",
                        color = if (m.resolved) PytColors.success else PytColors.warning,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }

    // 学弟对话：turns
    Panel {
        Text("学弟对话", color = PytColors.textMuted, fontSize = 13.sp)
        if (state.turns.isEmpty()) {
            Text(
                "学弟会在你讲解后复述自己的理解。",
                color = PytColors.textHint,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = PytSpace.s1),
            )
        } else {
            state.turns.forEach { turn ->
                Column(modifier = Modifier.padding(top = PytSpace.s1)) {
                    Text(
                        if (turn.role == "TUTEE") "AI 学弟" else "我",
                        color = PytColors.info,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(turn.content, color = PytColors.textPrimary, fontSize = 13.sp)
                }
            }
        }
    }

    // 你的教学
    Panel {
        Text("你的教学", color = PytColors.textMuted, fontSize = 13.sp)
        Surface(
            color = PytColors.bgSoft,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(2.dp, PytColors.borderStrong),
            modifier = Modifier.fillMaxWidth().padding(top = PytSpace.s1),
        ) {
            BasicTextField(
                value = teachingText,
                onValueChange = { teachingText = it },
                textStyle = TextStyle(color = PytColors.textPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(PytColors.warning),
                decorationBox = { inner ->
                    if (teachingText.isEmpty()) {
                        Text(
                            "用自己的话解释概念、指出学弟错在哪里，但不要直接贴完整答案。",
                            color = PytColors.textHint,
                            fontSize = 13.sp,
                        )
                    }
                    inner()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(PytSpace.s3),
            )
        }
        Button(
            onClick = { vm.teach(teachingText) },
            enabled = state.canTeach && teachingText.trim().isNotEmpty(),
            colors = ButtonDefaults.buttonColors(
                containerColor = PytColors.primary,
                contentColor = PytColors.onPrimary,
            ),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth().padding(top = PytSpace.s2),
        ) {
            Text(if (state.teaching) "学弟思考中…" else "讲给学弟听")
        }
    }

    // 学弟代码
    Panel {
        Text("学弟代码", color = PytColors.textMuted, fontSize = 13.sp)
        Surface(
            color = PytColors.bgSunken,
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.fillMaxWidth().padding(top = PytSpace.s1),
        ) {
            Text(
                state.latestProducedCode.ifBlank { "等待学弟写出可判题代码" },
                color = PytColors.textPrimary,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(PytSpace.s2),
            )
        }
    }

    // 判题结果
    JudgePanel(state, vm)

    // 玻璃盒证据链
    EvidenceChainPanel(state)

    // 代码找茬限时小游戏
    BugHunterPanel(state.bugHunt, vm)
}

@Composable
private fun BugHunterPanel(bh: BugHunterState, vm: FeynmanViewModel) {
    // 倒计时：进入 PLAYING 后每秒 tick；离开该阶段时 LaunchedEffect 自动取消。
    LaunchedEffect(bh.phase) {
        if (bh.phase == BugHuntPhase.PLAYING) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                vm.tickBugHunt()
            }
        }
    }
    Panel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("代码找茬", color = PytColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (bh.phase == BugHuntPhase.IDLE) "30 秒 · 点出 bug 行" else bh.sourceLabel,
                    color = PytColors.textMuted,
                    fontSize = 11.sp,
                )
            }
            if (bh.streak > 1) Text("连胜 ×${bh.streak}", color = PytColors.warning, fontSize = 12.sp, modifier = Modifier.padding(end = PytSpace.s2))
            if (bh.best > 0) Text("最高 ${bh.best}", color = PytColors.textMuted, fontSize = 12.sp, modifier = Modifier.padding(end = PytSpace.s2))
            if (bh.phase != BugHuntPhase.IDLE) Text("${bh.score} 分", color = PytColors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = PytSpace.s2))
            Button(
                onClick = vm::startBugHunt,
                enabled = !bh.loading,
                colors = ButtonDefaults.buttonColors(containerColor = PytColors.primary, contentColor = PytColors.onPrimary),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(if (bh.loading) "生成中…" else if (bh.phase == BugHuntPhase.IDLE) "开始找茬" else "换一局")
            }
        }

        if (bh.phase == BugHuntPhase.PLAYING) {
            // 倒计时进度条
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = PytSpace.s2)
                    .height(6.dp)
                    .background(PytColors.bgSunken, RoundedCornerShape(3.dp)),
            ) {
                val frac = (bh.secondsLeft.toFloat() / BugHunterState.ROUND_SECONDS).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(frac)
                        .height(6.dp)
                        .background(if (frac < 0.25f) PytColors.danger else PytColors.primary, RoundedCornerShape(3.dp)),
                )
            }
        }

        if (bh.phase != BugHuntPhase.IDLE && !bh.playable) {
            Text(
                bh.hintMd.ifBlank { "还没有可玩样本，先做几道题再来。" },
                color = PytColors.textHint,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = PytSpace.s2),
            )
        } else if (bh.phase != BugHuntPhase.IDLE) {
            Surface(
                color = PytColors.bgSunken,
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, PytColors.borderStrong),
                modifier = Modifier.fillMaxWidth().padding(top = PytSpace.s2),
            ) {
                Column(modifier = Modifier.padding(vertical = PytSpace.s1)) {
                    bh.lines.forEachIndexed { index, line ->
                        CodeLine(index, line, bh, onClick = { vm.toggleBugLine(index) })
                    }
                }
            }
            if (bh.phase == BugHuntPhase.PLAYING) {
                Button(
                    onClick = vm::finishBugHunt,
                    enabled = bh.selected.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = PytColors.primary, contentColor = PytColors.onPrimary),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = PytSpace.s2),
                ) {
                    Text("提交（已选 ${bh.selected.size} 行）")
                }
                if (bh.hintMd.isNotBlank()) {
                    Text(bh.hintMd, color = PytColors.textHint, fontSize = 11.sp, modifier = Modifier.padding(top = PytSpace.s1))
                }
            }
            bh.result?.let { r ->
                Text(
                    "${if (r.perfect) "完美！" else "结束"} 命中 ${r.hits} · 误 ${r.wrong} · 漏 ${r.missed} · ${bh.score} 分" +
                        if (r.comboBonus > 0) "（+${r.comboBonus}）" else "",
                    color = if (r.perfect) PytColors.success else PytColors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = PytSpace.s2),
                )
            }
        }
    }
}

@Composable
private fun CodeLine(index: Int, line: String, bh: BugHunterState, onClick: () -> Unit) {
    val isBug = index in bh.bugLines
    val isPicked = index in bh.selected
    val bg = when {
        bh.phase == BugHuntPhase.DONE && isBug && isPicked -> PytColors.success.copy(alpha = 0.25f)
        bh.phase == BugHuntPhase.DONE && isBug -> PytColors.danger.copy(alpha = 0.25f)
        bh.phase == BugHuntPhase.DONE && isPicked -> PytColors.danger.copy(alpha = 0.12f)
        isPicked -> PytColors.warning.copy(alpha = 0.2f)
        else -> PytColors.bgSunken
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .let { if (bh.phase == BugHuntPhase.PLAYING) it.clickable(onClick = onClick) else it }
            .padding(horizontal = PytSpace.s2, vertical = 2.dp),
    ) {
        Text(
            "${index + 1}".padStart(2, ' '),
            color = PytColors.textHint,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(end = PytSpace.s2),
        )
        Text(
            line.ifEmpty { " " },
            color = PytColors.textPrimary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun EvidenceChainPanel(state: FeynmanUiState) {
    Panel {
        Text("玻璃盒证据链", color = PytColors.textMuted, fontSize = 13.sp)
        EvidenceRow("误解", state.misconceptions.joinToString("、") { it.tag }.ifBlank { "暂无真实误解来源" })
        EvidenceRow("判题", judgeText(state))
        EvidenceRow(
            "证据",
            if (state.evidenceEventIds.isEmpty()) {
                "等待真实 event_id 回链"
            } else {
                state.evidenceEventIds.joinToString(" · ") { "event #$it" }
            },
        )
    }
}

@Composable
private fun JudgePanel(state: FeynmanUiState, vm: FeynmanViewModel) {
    val result = state.latestJudgeResult
    val accent = when (result?.status) {
        "PASSED" -> PytColors.success
        "FAILED", "ERROR" -> PytColors.danger
        else -> PytColors.warning
    }
    Surface(
        color = PytColors.bgElevated,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(2.dp, accent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(PytSpace.s3), verticalArrangement = Arrangement.spacedBy(PytSpace.s2)) {
            Text("判题结果", color = PytColors.textMuted, fontSize = 13.sp)
            Text(judgeText(state), color = PytColors.textPrimary, fontSize = 13.sp)
            Button(
                onClick = vm::judge,
                enabled = state.canJudge,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = PytColors.onPrimary,
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.judging) "判题中…" else "真判题裁定")
            }
        }
    }
}

private fun judgeText(state: FeynmanUiState): String =
    state.latestJudgeResult?.messageZh ?: "等待学弟提交可判题代码"

@Composable
private fun EvidenceRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = PytSpace.s1)) {
        Text(
            "$label ",
            color = PytColors.textHint,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(value, color = PytColors.textMuted, fontSize = 12.sp)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = PytColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        color = PytColors.bgElevated,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(2.dp, PytColors.danger),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            message,
            color = PytColors.danger,
            fontSize = 13.sp,
            modifier = Modifier.padding(PytSpace.s3),
        )
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
