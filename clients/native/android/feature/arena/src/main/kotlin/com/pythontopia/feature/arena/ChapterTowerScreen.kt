package com.pythontopia.feature.arena

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ChapterTowerScreen(
    onNavigateToEndless: (chapter: Int, kind: String, levelId: String) -> Unit = { _, _, _ -> },
    onNavigateToBoss: (chapter: Int) -> Unit = {},
    viewModel: ChapterTowerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        ChapterTabRow(
            selectedChapter = state.selectedChapter,
            onSelect = viewModel::selectChapter,
        )
        Spacer(modifier = Modifier.height(8.dp))
        ProgressBar(cleared = state.chapterCleared, total = state.chapterTotal)
        Spacer(modifier = Modifier.height(8.dp))

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.errorMessage != null) {
            Text(
                text = state.errorMessage ?: "",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            TowerBody(
                levels = state.levels,
                selectedLevelId = state.selectedLevelId,
                onSelectLevel = viewModel::selectLevel,
                onEnter = { level ->
                    if (level.kind == "boss") {
                        onNavigateToBoss(level.chapter)
                    } else {
                        onNavigateToEndless(level.chapter, level.kind, level.id)
                    }
                },
            )
        }
    }
}

@Composable
private fun TowerBody(
    levels: List<ChapterTowerLevel>,
    selectedLevelId: String?,
    onSelectLevel: (String) -> Unit,
    onEnter: (ChapterTowerLevel) -> Unit,
) {
    val selectedLevel = levels.find { it.id == selectedLevelId }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth < COMPACT_WIDTH_BREAKPOINT.dp) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LevelList(
                    levels = levels,
                    selectedId = selectedLevelId,
                    onSelect = onSelectLevel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = COMPACT_LIST_MAX_HEIGHT.dp),
                )
                LevelDetail(
                    level = selectedLevel,
                    onEnter = onEnter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                LevelList(
                    levels = levels,
                    selectedId = selectedLevelId,
                    onSelect = onSelectLevel,
                    modifier = Modifier.weight(0.45f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                LevelDetail(
                    level = selectedLevel,
                    onEnter = onEnter,
                    modifier = Modifier.weight(0.55f),
                )
            }
        }
    }
}

@Composable
private fun ChapterTabRow(selectedChapter: Int, onSelect: (Int) -> Unit) {
    ScrollableTabRow(
        selectedTabIndex = selectedChapter - 1,
        edgePadding = 4.dp,
    ) {
        for (chapter in 1..7) {
            Tab(
                selected = chapter == selectedChapter,
                onClick = { onSelect(chapter) },
                text = { Text("第${chapter}章", fontSize = 12.sp) },
            )
        }
    }
}

@Composable
private fun ProgressBar(cleared: Int, total: Int) {
    Column {
        LinearProgressIndicator(
            progress = { if (total > 0) cleared.toFloat() / total else 0f },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = Color(0xFF4F8A3E),
            trackColor = Color(0xFFF1D896),
        )
        Text(
            text = "通关 $cleared / $total",
            fontSize = 11.sp,
            color = Color(0xFF6E4F1D),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun LevelList(
    levels: List<ChapterTowerLevel>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(levels, key = { it.id }) { level ->
            val bg = when {
                level.id == selectedId -> Color(0xFFFFE7B3)
                level.cleared -> Color(0xFFBBF7D0).copy(alpha = 0.55f)
                !level.unlocked -> Color(0xFFF5F5F5)
                else -> Color(0xFFFFF7E6)
            }
            val borderColor = when {
                level.kind == "boss" -> Color(0xFFB8442D)
                level.id == selectedId -> Color(0xFF6E4F1D)
                level.cleared -> Color(0xFF16A34A)
                else -> Color(0xFFC9A86B)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bg, shape = MaterialTheme.shapes.small)
                    .border(2.dp, borderColor, shape = MaterialTheme.shapes.small)
                    .clickable(enabled = level.unlocked) { onSelect(level.id) }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "${level.index}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF4D3613),
                )
                Text(
                    text = level.title,
                    fontSize = 12.sp,
                    color = if (level.unlocked) Color(0xFF3A2A14) else Color(0xFFAAAAAA),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Text(
                    text = when {
                        !level.unlocked -> "\uD83D\uDD12"
                        level.cleared -> "★"
                        else -> "·"
                    },
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun LevelDetail(
    level: ChapterTowerLevel?,
    onEnter: (ChapterTowerLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (level == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("选择左侧关卡查看详情", color = Color(0xFF6E4F1D))
        }
        return
    }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(Color(0xFFFFF7E6), shape = MaterialTheme.shapes.medium)
            .border(2.dp, Color(0xFF6E4F1D), shape = MaterialTheme.shapes.medium)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = level.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4D3613))
        Text(text = "难度 ${"★".repeat(level.difficulty)}${"☆".repeat(5 - level.difficulty)}", fontSize = 12.sp, color = Color(0xFFB8442D))
        Text(text = "KC: ${level.kcCodes.joinToString(" / ")}", fontSize = 12.sp, color = Color(0xFF8A6C2C))
        Text(text = level.hint, fontSize = 13.sp, color = Color(0xFF5D4524))

        if (level.cleared) {
            Text(text = "✓ 已通关", fontSize = 13.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { onEnter(level) },
            enabled = level.unlocked,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD166)),
        ) {
            Text(
                text = when {
                    !level.unlocked -> "🔒 解锁上一关再来"
                    level.kind == "boss" -> "★ 挑战 BOSS"
                    else -> "▶ 进入第${level.index}关"
                },
                color = Color(0xFF2A1A08),
            )
        }
    }
}

private const val COMPACT_WIDTH_BREAKPOINT: Int = 600
private const val COMPACT_LIST_MAX_HEIGHT: Int = 260
