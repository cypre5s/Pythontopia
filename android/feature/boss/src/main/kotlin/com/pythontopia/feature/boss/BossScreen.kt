package com.pythontopia.feature.boss

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pythontopia.core.design.PytColors
import com.pythontopia.core.design.PytLoading
import com.pythontopia.core.design.PytSpace

object BossDestination {
    const val ROUTE = "boss"
}

@Composable
fun BossScreen(viewModel: BossViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PytColors.bgPrimary)
            .padding(PytSpace.s3)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PytSpace.s3),
    ) {
        when (state.phase) {
            BossPhase.PICK_CHAPTER -> ChapterPicker(onPick = viewModel::selectChapter)
            BossPhase.BATTLE -> BattleView(state, viewModel)
            BossPhase.VICTORY -> VictoryView(state, onAgain = viewModel::reset)
        }
    }
}

@Composable
private fun ChapterPicker(onPick: (Int) -> Unit) {
    Text("章末 BOSS 战", color = PytColors.textPrimary, fontSize = 20.sp)
    Text(
        "选择章节挑战 BOSS：连闯 4 个阶段，每阶段由 AI 按你的画像出题，服务端判题。",
        color = PytColors.textHint,
        fontSize = 13.sp,
    )
    (1..7).forEach { ch ->
        Surface(
            color = PytColors.bgElevated,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(2.dp, PytColors.borderStrong),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(PytSpace.s3),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("第 $ch 章 BOSS", color = PytColors.textPrimary, fontSize = 15.sp)
                Button(
                    onClick = { onPick(ch) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PytColors.danger,
                        contentColor = PytColors.textPrimary,
                    ),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text("挑战")
                }
            }
        }
    }
}

@Composable
private fun BattleView(state: BossUiState, vm: BossViewModel) {
    StageBar(stage = state.stage, total = BossUiState.TOTAL_STAGES)

    when {
        state.loading -> PytLoading()
        state.error != null && state.problem == null -> ErrorPanel(state.error, onRetry = vm::loadStage)
        state.problem != null -> {
            val p = state.problem
            Text(
                "第 ${state.chapter} 章 · 阶段 ${state.stage}/${BossUiState.TOTAL_STAGES}",
                color = PytColors.textMuted,
                fontSize = 13.sp,
            )
            Panel {
                Text(p.statement, color = PytColors.textPrimary, fontSize = 14.sp)
                if (p.sampleInput != null || p.sampleOutput != null) {
                    Text("样例输入", color = PytColors.textHint, fontSize = 12.sp, modifier = Modifier.padding(top = PytSpace.s2))
                    MonoBlock(p.sampleInput.orEmpty())
                    Text("样例输出", color = PytColors.textHint, fontSize = 12.sp, modifier = Modifier.padding(top = PytSpace.s1))
                    MonoBlock(p.sampleOutput.orEmpty())
                }
                if (p.hints.isNotEmpty()) {
                    Text("提示", color = PytColors.textHint, fontSize = 12.sp, modifier = Modifier.padding(top = PytSpace.s2))
                    p.hints.forEach { h ->
                        Text("· $h", color = PytColors.textMuted, fontSize = 12.sp)
                    }
                }
            }

            CodeField(value = state.code, onChange = vm::onCodeChange)

            val result = state.result
            if (result == null) {
                Button(
                    onClick = vm::submit,
                    enabled = !state.submitting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PytColors.danger,
                        contentColor = PytColors.textPrimary,
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.submitting) "判题中…" else "提交解答")
                }
                if (state.error != null) {
                    Text(state.error, color = PytColors.danger, fontSize = 12.sp)
                }
            } else {
                ResultPanel(result = result, isLast = state.isLastStage, onNext = vm::advance, onRetry = vm::retry)
            }
        }
    }
}

@Composable
private fun StageBar(stage: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(PytSpace.s2), verticalAlignment = Alignment.CenterVertically) {
        (1..total).forEach { i ->
            val color = when {
                i < stage -> PytColors.success
                i == stage -> PytColors.warning
                else -> PytColors.bgSoft
            }
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(color, CircleShape),
            )
        }
        Text("阶段 $stage / $total", color = PytColors.textMuted, fontSize = 12.sp, modifier = Modifier.padding(start = PytSpace.s2))
    }
}

@Composable
private fun ResultPanel(result: BossAttemptResultDto, isLast: Boolean, onNext: () -> Unit, onRetry: () -> Unit) {
    val passed = result.passed
    Surface(
        color = PytColors.bgElevated,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(2.dp, if (passed) PytColors.success else PytColors.danger),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(PytSpace.s3), verticalArrangement = Arrangement.spacedBy(PytSpace.s1)) {
            Text(
                if (passed) "阶段通过！+${result.score} 分" else "未通过",
                color = if (passed) PytColors.success else PytColors.danger,
                fontSize = 16.sp,
            )
            Text("通过测试 ${result.passedTestCases}/${result.totalTestCases}", color = PytColors.textMuted, fontSize = 12.sp)
            if (result.feedback.isNotBlank()) {
                Text(result.feedback, color = PytColors.textPrimary, fontSize = 13.sp)
            }
            if (!passed && !result.failureFirstHint.isNullOrBlank()) {
                Text("提示：${result.failureFirstHint}", color = PytColors.warning, fontSize = 12.sp)
            }
            if (passed) {
                Button(
                    onClick = onNext,
                    colors = ButtonDefaults.buttonColors(containerColor = PytColors.success, contentColor = PytColors.bgPrimary),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isLast) "击败 BOSS！" else "进入下一阶段")
                }
            } else {
                OutlinedButton(
                    onClick = onRetry,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(2.dp, PytColors.borderStrong),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("重试本阶段", color = PytColors.textPrimary)
                }
            }
        }
    }
}

@Composable
private fun VictoryView(state: BossUiState, onAgain: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(top = PytSpace.s5), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(PytSpace.s3)) {
            Text("BOSS 已被击败", color = PytColors.success, fontSize = 22.sp)
            Text("第 ${state.chapter} 章 4 阶段全部通关，掌握度已巩固。", color = PytColors.textMuted, fontSize = 13.sp)
            Button(
                onClick = onAgain,
                colors = ButtonDefaults.buttonColors(containerColor = PytColors.danger, contentColor = PytColors.textPrimary),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text("挑战其它章节")
            }
        }
    }
}

@Composable
private fun ErrorPanel(message: String, onRetry: () -> Unit) {
    Surface(
        color = PytColors.bgElevated,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(2.dp, PytColors.danger),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(PytSpace.s3), verticalArrangement = Arrangement.spacedBy(PytSpace.s2)) {
            Text(message, color = PytColors.danger, fontSize = 13.sp)
            OutlinedButton(
                onClick = onRetry,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(2.dp, PytColors.borderStrong),
            ) {
                Text("重新召唤", color = PytColors.textPrimary)
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
                .heightIn(min = 160.dp)
                .padding(PytSpace.s3),
        )
    }
}
