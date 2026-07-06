package com.pythontopia.feature.problems

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.pythontopia.core.design.PytLoading
import com.pythontopia.core.design.PytSpace

@Composable
fun ProblemsScreen(viewModel: ProblemsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var expandedId by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PytColors.bgPrimary)
            .padding(PytSpace.s3),
        verticalArrangement = Arrangement.spacedBy(PytSpace.s2),
    ) {
        Header(total = state.all.size, shown = state.visible.size, loading = state.loading)
        FilterBar(state, viewModel)

        if (state.error != null) {
            ErrorBanner(state.error!!, onRetry = viewModel::load)
        }

        when {
            state.loading && state.all.isEmpty() -> PytLoading()
            else -> {
                val rows = state.visible
                if (rows.isEmpty()) {
                    Text(
                        "没有题目命中当前筛选。",
                        color = PytColors.textHint,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(PytSpace.s3),
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(PytSpace.s2)) {
                        items(rows, key = { it.problemId }) { row ->
                            ProblemCard(
                                row = row,
                                expanded = expandedId == row.problemId,
                                onToggle = {
                                    expandedId = if (expandedId == row.problemId) null else row.problemId
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(total: Int, shown: Int, loading: Boolean) {
    Column {
        Text("Pythontopia 题库", color = PytColors.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(
            "packId=43 · 共 $total 题 · 已筛 $shown 题" + if (loading) " · 加载中…" else "",
            color = PytColors.textHint,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun FilterBar(state: ProblemsUiState, vm: ProblemsViewModel) {
    Surface(
        color = PytColors.bgElevated,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(2.dp, PytColors.borderStrong),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(PytSpace.s2),
            verticalArrangement = Arrangement.spacedBy(PytSpace.s2),
        ) {
            // 搜索
            Surface(
                color = PytColors.bgSoft,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(2.dp, PytColors.borderSoft),
                modifier = Modifier.fillMaxWidth(),
            ) {
                BasicTextField(
                    value = state.search,
                    onValueChange = vm::setSearch,
                    singleLine = true,
                    textStyle = TextStyle(color = PytColors.textPrimary, fontSize = 13.sp),
                    cursorBrush = SolidColor(PytColors.warning),
                    decorationBox = { inner ->
                        if (state.search.isEmpty()) {
                            Text("搜索题号、标题、知识点…", color = PytColors.textHint, fontSize = 13.sp)
                        }
                        inner()
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = PytSpace.s2, vertical = PytSpace.s2),
                )
            }

            ChipRow(label = "难度") {
                Chip("全部", state.difficulty == null) { vm.setDifficulty(null) }
                Chip("简单", state.difficulty == ProblemDifficulty.LOW) { vm.setDifficulty(ProblemDifficulty.LOW) }
                Chip("中等", state.difficulty == ProblemDifficulty.MEDIUM) { vm.setDifficulty(ProblemDifficulty.MEDIUM) }
                Chip("困难", state.difficulty == ProblemDifficulty.HIGH) { vm.setDifficulty(ProblemDifficulty.HIGH) }
            }
            ChipRow(label = "状态") {
                Chip("全部", state.status == null) { vm.setStatus(null) }
                Chip("未做", state.status == ProblemStatus.NOT_TRIED) { vm.setStatus(ProblemStatus.NOT_TRIED) }
                Chip("已通过", state.status == ProblemStatus.PASSED) { vm.setStatus(ProblemStatus.PASSED) }
                Chip("失败", state.status == ProblemStatus.FAILED) { vm.setStatus(ProblemStatus.FAILED) }
            }
            ChipRow(label = "排序") {
                ProblemSort.entries.forEach { sort ->
                    Chip(sort.label, state.sort == sort) { vm.setSort(sort) }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PytSpace.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KcDropdown(
                    options = state.kcOptions,
                    selected = state.kcCode,
                    modifier = Modifier.weight(1f),
                    onSelect = vm::setKc,
                )
                if (state.hasActiveFilters) {
                    Chip("清空", selected = false, onClick = vm::clearFilters)
                }
            }
        }
    }
}

@Composable
private fun ChipRow(label: String, content: @Composable () -> Unit) {
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
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = PytSpace.s2, vertical = 6.dp),
        )
    }
}

@Composable
private fun KcDropdown(
    options: List<Pair<String, String>>,
    selected: String?,
    modifier: Modifier = Modifier,
    onSelect: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val selectedLabel = selected?.let { code -> options.firstOrNull { it.first == code }?.second ?: code }
        ?: "全部知识点（${options.size}）"
    Box(modifier = modifier) {
        Surface(
            color = PytColors.bgSoft,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(2.dp, PytColors.borderSoft),
            modifier = Modifier.fillMaxWidth().clickable { open = true },
        ) {
            Text(
                "知识点：$selectedLabel",
                color = PytColors.textPrimary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = PytSpace.s2, vertical = 8.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("全部知识点", fontSize = 13.sp) },
                onClick = { onSelect(null); open = false },
            )
            options.forEach { (code, name) ->
                DropdownMenuItem(
                    text = { Text(name, fontSize = 13.sp) },
                    onClick = { onSelect(code); open = false },
                )
            }
        }
    }
}

@Composable
private fun ProblemCard(row: ProblemRow, expanded: Boolean, onToggle: () -> Unit) {
    Surface(
        color = PytColors.bgElevated,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(2.dp, PytColors.borderSoft),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
    ) {
        Column(modifier = Modifier.padding(PytSpace.s3), verticalArrangement = Arrangement.spacedBy(PytSpace.s1)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PytSpace.s2)) {
                Text("#${row.problemId}", color = PytColors.textHint, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Text(
                    row.title,
                    color = PytColors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(row.status)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(PytSpace.s1), verticalAlignment = Alignment.CenterVertically) {
                Badge("第 ${row.chapterOrder ?: "?"} 章", PytColors.bgSoft, PytColors.textMuted)
                Badge(row.kcName, PytColors.bgSoft, PytColors.info)
                Badge(row.difficulty.label, difficultyBg(row.difficulty), PytColors.textPrimary)
                if (row.attempts > 0) {
                    Text("尝试 ${row.attempts}", color = PytColors.textHint, fontSize = 11.sp)
                }
            }
            if (expanded) {
                DetailBlock("题面", row.statementMarkdown)
                if (row.inputDescription.isNotBlank()) DetailBlock("输入", row.inputDescription)
                if (row.outputDescription.isNotBlank()) DetailBlock("输出", row.outputDescription)
                if (row.sourceLabel.isNotBlank()) {
                    Text(
                        "来源：${row.sourceLabel}",
                        color = PytColors.textHint,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = PytSpace.s1),
                    )
                }
            } else {
                Text("点击展开题面", color = PytColors.textHint, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun DetailBlock(label: String, body: String) {
    Column(modifier = Modifier.padding(top = PytSpace.s1)) {
        Text(label, color = PytColors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(body, color = PytColors.textPrimary, fontSize = 13.sp)
    }
}

@Composable
private fun StatusBadge(status: ProblemStatus) {
    val (text, color) = when (status) {
        ProblemStatus.PASSED -> "已通过" to PytColors.success
        ProblemStatus.FAILED -> "失败" to PytColors.danger
        ProblemStatus.NOT_TRIED -> "未做" to PytColors.textMuted
    }
    Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun Badge(text: String, bg: Color, fg: Color) {
    Surface(color = bg, shape = RoundedCornerShape(4.dp)) {
        Text(text, color = fg, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

private fun difficultyBg(d: ProblemDifficulty): Color = when (d) {
    ProblemDifficulty.LOW -> PytColors.success.copy(alpha = 0.25f)
    ProblemDifficulty.MEDIUM -> PytColors.warning.copy(alpha = 0.25f)
    ProblemDifficulty.HIGH -> PytColors.danger.copy(alpha = 0.25f)
    ProblemDifficulty.UNKNOWN -> PytColors.bgSoft
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Surface(
        color = PytColors.bgElevated,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(2.dp, PytColors.danger),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onRetry),
    ) {
        Text(
            "$message（点此重试）",
            color = PytColors.danger,
            fontSize = 13.sp,
            modifier = Modifier.padding(PytSpace.s3),
        )
    }
}
