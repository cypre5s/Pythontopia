package com.pythontopia.feature.teacherreview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

@Composable
fun TeacherReviewScreen(viewModel: TeacherReviewViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PytColors.bgPrimary)
            .padding(PytSpace.s3),
        verticalArrangement = Arrangement.spacedBy(PytSpace.s2),
    ) {
        item {
            Column {
                Text("教师审题 · AI 出题兜底", color = PytColors.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(
                    "5 重审查链通过后仍由教师手动「上线/驳回」；审核决定为本会话本地记录。",
                    color = PytColors.textHint,
                    fontSize = 12.sp,
                )
            }
        }
        item { Toolbar(state, viewModel) }
        state.error?.let { item { Banner(it, PytColors.danger) { viewModel.fetch() } } }
        state.selected?.let { entry -> item { DetailCard(entry, viewModel) } }
        if (state.entries.isNotEmpty()) {
            item {
                Text("审核队列（${state.total}）", color = PytColors.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            items(state.entries, key = { it.key }) { entry ->
                QueueRow(entry, selected = entry.key == state.selectedKey) { viewModel.select(entry.key) }
            }
        } else {
            item {
                Text(
                    "队列为空：选择章/阶段后点「出题」，结果会出现在这里。",
                    color = PytColors.textHint,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(PytSpace.s2),
                )
            }
        }
    }
}

@Composable
private fun Toolbar(state: TeacherReviewUiState, vm: TeacherReviewViewModel) {
    Surface(
        color = PytColors.bgElevated,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(2.dp, PytColors.borderStrong),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(PytSpace.s2), verticalArrangement = Arrangement.spacedBy(PytSpace.s2)) {
            ChipScroller("章节") {
                for (c in 1..7) Chip("第 $c 章", state.chapter == c) { vm.setChapter(c) }
            }
            ChipScroller("阶段") {
                for (s in 1..4) Chip("Stage $s", state.stage == s) { vm.setStage(s) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PytSpace.s2)) {
                Action(if (state.loading) "拉题中…" else "调 graph 出题", PytColors.primary, enabled = !state.loading) { vm.fetch() }
                if (state.entries.isNotEmpty()) {
                    Action("清空", PytColors.bgSoft, enabled = true, fg = PytColors.textMuted) { vm.clearAll() }
                }
            }
            Text(
                "总 ${state.total} · 待审 ${state.pending} · 通过 ${state.approved} · 驳回 ${state.rejected}",
                color = PytColors.textHint,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun DetailCard(entry: ReviewEntry, vm: TeacherReviewViewModel) {
    val p = entry.problem
    var reason by remember(entry.key) { mutableStateOf(entry.reason.orEmpty()) }
    Surface(
        color = PytColors.bgElevated,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(2.dp, PytColors.borderStrong),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(PytSpace.s3), verticalArrangement = Arrangement.spacedBy(PytSpace.s1)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PytSpace.s2)) {
                Text(
                    p.problemId,
                    color = PytColors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Badge(if (p.reviewed) "5 重审查通过" else "未通过审查", if (p.reviewed) PytColors.success else PytColors.danger)
                Badge(if (p.aiGenerated) "AI" else "Golden", PytColors.info)
            }
            Text(
                "第 ${p.chapter} 章 · S${p.stage} · ${p.kcCode ?: "?"} · 难度 ${p.difficulty}/5 · ${p.totalTestCases} 用例",
                color = PytColors.textMuted,
                fontSize = 12.sp,
            )
            Label("题面")
            Mono(p.statement)
            if (!p.sampleInput.isNullOrBlank() || !p.sampleOutput.isNullOrBlank()) {
                Label("样例")
                Mono("输入: ${p.sampleInput.orEmpty().ifBlank { "(无)" }}\n输出: ${p.sampleOutput.orEmpty()}")
            }
            if (p.hints.isNotEmpty()) {
                Label("提示")
                p.hints.forEach { Text("· $it", color = PytColors.textMuted, fontSize = 12.sp) }
            }
            if (entry.decision != ReviewDecision.PENDING) {
                val (txt, color) = if (entry.decision == ReviewDecision.APPROVED) {
                    "已通过 · 上线" to PytColors.success
                } else {
                    "已驳回" to PytColors.danger
                }
                Text(
                    txt + (entry.reason?.let { "（$it）" } ?: ""),
                    color = color,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = PytSpace.s1),
                )
            }
            // 操作
            Action("通过 · 上线该题", PytColors.success, enabled = true) { vm.approve(entry.key) }
            Surface(
                color = PytColors.bgSoft,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(2.dp, PytColors.borderSoft),
                modifier = Modifier.fillMaxWidth(),
            ) {
                BasicTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    textStyle = TextStyle(color = PytColors.textPrimary, fontSize = 13.sp),
                    cursorBrush = SolidColor(PytColors.warning),
                    decorationBox = { inner ->
                        if (reason.isEmpty()) Text("驳回原因（建议填写）", color = PytColors.textHint, fontSize = 13.sp)
                        inner()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(PytSpace.s2),
                )
            }
            Action("驳回该题", PytColors.danger, enabled = true) { vm.reject(entry.key, reason) }
        }
    }
}

@Composable
private fun QueueRow(entry: ReviewEntry, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) PytColors.borderStrong else PytColors.borderSoft
    Surface(
        color = PytColors.bgElevated,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(2.dp, border),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(PytSpace.s2), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PytSpace.s1)) {
                Text(
                    "第 ${entry.problem.chapter} 章 S${entry.problem.stage}",
                    color = PytColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                entry.problem.kcCode?.let { Badge(it, PytColors.info) }
                Badge(if (entry.problem.aiGenerated) "AI" else "Golden", PytColors.textMuted)
                DecisionDot(entry.decision)
            }
            Text(
                entry.problem.statement.take(60).ifBlank { "(无题面)" } + "…",
                color = PytColors.textMuted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun ChipScroller(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(PytSpace.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$label ", color = PytColors.textMuted, fontSize = 11.sp)
        content()
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) PytColors.primary else PytColors.bgSoft,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(2.dp, if (selected) PytColors.borderStrong else PytColors.borderSoft),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text,
            color = if (selected) PytColors.onPrimary else PytColors.textMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = PytSpace.s2, vertical = 5.dp),
        )
    }
}

@Composable
private fun Action(text: String, bg: Color, enabled: Boolean, fg: Color = PytColors.onPrimary, onClick: () -> Unit) {
    Surface(
        color = if (enabled) bg else bg.copy(alpha = 0.4f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(2.dp, PytColors.borderStrong),
        modifier = Modifier.fillMaxWidth().then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Text(
            text,
            color = fg,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = PytSpace.s3),
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = PytColors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = PytSpace.s1))
}

@Composable
private fun Mono(text: String) {
    Surface(color = PytColors.bgSunken, shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text.ifBlank { "(空)" },
            color = PytColors.textPrimary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(PytSpace.s2),
        )
    }
}

@Composable
private fun Badge(text: String, fg: Color) {
    Surface(color = PytColors.bgSoft, shape = RoundedCornerShape(4.dp)) {
        Text(text, color = fg, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
private fun DecisionDot(decision: ReviewDecision) {
    val (text, color) = when (decision) {
        ReviewDecision.APPROVED -> "已通过" to PytColors.success
        ReviewDecision.REJECTED -> "已驳回" to PytColors.danger
        ReviewDecision.PENDING -> "待审" to PytColors.textHint
    }
    Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun Banner(message: String, accent: Color, onClick: () -> Unit) {
    Surface(
        color = PytColors.bgElevated,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(2.dp, accent),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Text(message, color = accent, fontSize = 13.sp, modifier = Modifier.padding(PytSpace.s3))
    }
}
