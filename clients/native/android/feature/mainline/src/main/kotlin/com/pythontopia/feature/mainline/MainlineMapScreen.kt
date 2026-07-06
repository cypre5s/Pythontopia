package com.pythontopia.feature.mainline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pythontopia.core.data.dto.mainline.ChapterOverviewDto
import com.pythontopia.core.data.dto.mainline.StageOverviewDto
import com.pythontopia.core.design.PytErrorRetry

private const val MAX_CHAPTER_STARS = 3

/** 章节卡回调集合（避免 Composable 参数列表过长）。 */
private data class MainlineMapActions(
    val onOpenStage: (Long) -> Unit,
    val onOpenTest: (Int) -> Unit,
    val onOpenQuiz: (Int) -> Unit,
    val onClaimRecovery: (Int) -> Unit,
    val onOpenEndless: (Int) -> Unit,
)

/** R-15 主线冒险地图：章节 → 小节（看/学/练进度），点小节进入学习页。 */
@Composable
fun MainlineMapScreen(
    onOpenStage: (Long) -> Unit = {},
    onOpenTest: (Int) -> Unit = {},
    onOpenQuiz: (Int) -> Unit = {},
    onOpenEndless: (Int) -> Unit = {},
    viewModel: MainlineMapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            state.isLoading -> LoadingBox()
            state.error != null -> Box(Modifier.fillMaxSize().padding(16.dp), Alignment.Center) {
                PytErrorRetry(message = state.error!!, onRetry = viewModel::load)
            }
            else -> MapContent(
                totalStars = state.totalStars,
                chapters = state.chapters,
                message = state.message,
                actions = MainlineMapActions(
                    onOpenStage = onOpenStage,
                    onOpenTest = onOpenTest,
                    onOpenQuiz = onOpenQuiz,
                    onClaimRecovery = viewModel::claimErrorbookRecovery,
                    onOpenEndless = onOpenEndless,
                ),
            )
        }
    }
}

@Composable
private fun LoadingBox() {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun MapContent(
    totalStars: Int,
    chapters: List<ChapterOverviewDto>,
    message: String?,
    actions: MainlineMapActions,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "主线 · 冒险地图",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "★ $totalStars",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        message?.let { text ->
            item {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (chapters.isEmpty()) {
            item {
                Text(
                    text = "暂无章节数据。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(chapters, key = { it.chapter }) { chapter ->
            ChapterCard(chapter = chapter, actions = actions)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChapterCard(
    chapter: ChapterOverviewDto,
    actions: MainlineMapActions,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "第 ${chapter.chapter} 章 · ${chapter.title ?: ""}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                StarRow(stars = chapter.stars)
            }
            Text(
                text = chapterSubtitle(chapter),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (chapter.unlocked) {
                chapter.stages.forEach { stage ->
                    StageRow(stage = stage, onClick = { actions.onOpenStage(stage.id) })
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { actions.onOpenQuiz(chapter.chapter) }) { Text("章末快测 ›") }
                    TextButton(onClick = { actions.onOpenTest(chapter.chapter) }) { Text("章末测试 ›") }
                    if (chapter.recovery?.errorbookDone != true) {
                        TextButton(onClick = { actions.onClaimRecovery(chapter.chapter) }) { Text("错题补星") }
                    }
                    if (chapter.recovery?.endlessDone != true) {
                        TextButton(onClick = { actions.onOpenEndless(chapter.chapter) }) { Text("无尽挑战") }
                    }
                }
            } else {
                Text(
                    text = "🔒 未解锁（完成上一章解锁）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StarRow(stars: Int) {
    Text(
        text = buildString {
            repeat(MAX_CHAPTER_STARS) { append(if (it < stars) "★" else "☆") }
        },
        color = MaterialTheme.colorScheme.tertiary,
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun StageRow(stage: StageOverviewDto, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${stage.index}. ${stage.title ?: "小节"}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (stage.completed) "✓ 完成" else "进行中",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (stage.completed) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProgressChip("看", stage.videoWatchedSec, stage.requiredVideoSec)
                ProgressChip("学", stage.pagesRead, stage.requiredPages)
                ProgressChip("练", stage.examplesDone, stage.requiredExamples)
            }
        }
    }
}

@Composable
private fun ProgressChip(label: String, current: Int, required: Int) {
    val done = required > 0 && current >= required
    Surface(
        color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = "$label $current/$required",
            style = MaterialTheme.typography.labelSmall,
            color = if (done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

private fun chapterSubtitle(chapter: ChapterOverviewDto): String {
    val test = chapter.testStatus?.let { "章测：$it" } ?: "章测：未开始"
    val best = chapter.bestTestScore?.let { " · 最高分 $it" } ?: ""
    return "$test$best"
}
