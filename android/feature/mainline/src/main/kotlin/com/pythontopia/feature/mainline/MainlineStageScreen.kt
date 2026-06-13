package com.pythontopia.feature.mainline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pythontopia.core.data.dto.mainline.StageDetailDto
import com.pythontopia.core.data.dto.mainline.StageProgressDto
import com.pythontopia.core.design.PytErrorRetry

private const val VIDEO_TICK_SEC = 60

/** 主线小节学习页（看/学/练三件套）：读取 stage 详情并对各件套打点。 */
@Composable
fun MainlineStageScreen(
    stageId: Long,
    onBack: () -> Unit = {},
    viewModel: MainlineStageViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(stageId) { viewModel.load(stageId) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            state.detail == null && state.error != null -> Box(
                Modifier.fillMaxSize().padding(16.dp),
                Alignment.Center,
            ) {
                PytErrorRetry(message = state.error!!, onRetry = { viewModel.load(stageId) })
            }
            else -> StageContent(
                state = state,
                onBack = onBack,
                onWatch = { viewModel.reportVideo(VIDEO_TICK_SEC) },
                onReadAll = viewModel::markAllPagesRead,
                onExampleDone = { problemId -> viewModel.markExampleDone(problemId) },
            )
        }
    }
}

@Composable
private fun StageContent(
    state: MainlineStageUiState,
    onBack: () -> Unit,
    onWatch: () -> Unit,
    onReadAll: () -> Unit,
    onExampleDone: (Long) -> Unit,
) {
    val detail = state.detail
    val progress = state.progress
    val busy = state.busy
    val message = state.message ?: state.error
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ 返回") }
            Text(
                text = detail?.let { "第 ${it.chapter} 章 · ${it.title ?: ""}" } ?: "小节",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
        WatchSection(detail = detail, progress = progress, busy = busy, onWatch = onWatch)
        ReadSection(detail = detail, progress = progress, busy = busy, onReadAll = onReadAll)
        PracticeSection(detail = detail, progress = progress, busy = busy, onExampleDone = onExampleDone)
    }
}

@Composable
private fun WatchSection(
    detail: StageDetailDto?,
    progress: StageProgressDto?,
    busy: Boolean,
    onWatch: () -> Unit,
) {
    val watched = progress?.videoWatchedSec ?: detail?.videoWatchedSec ?: 0
    val required = progress?.requiredVideoSec ?: detail?.requiredVideoSec ?: 0
    SectionCard(title = "看 · 课程视频", subtitle = "已观看 $watched / $required 秒") {
        detail?.videos?.forEach { video ->
            Text("· ${video.title ?: "视频 #${video.id}"}（${video.durationSec}s）", style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = onWatch, enabled = !busy) { Text("上报观看 ${VIDEO_TICK_SEC}s") }
    }
}

@Composable
private fun ReadSection(
    detail: StageDetailDto?,
    progress: StageProgressDto?,
    busy: Boolean,
    onReadAll: () -> Unit,
) {
    val read = progress?.pagesRead ?: 0
    val required = progress?.requiredPages ?: detail?.requiredPages ?: 0
    val pageCount = detail?.coursewarePageIds?.size ?: 0
    SectionCard(title = "学 · 课件页", subtitle = "已读 $read / $required（共 $pageCount 页）") {
        Button(onClick = onReadAll, enabled = !busy && pageCount > 0) { Text("标记全部课件已读") }
    }
}

@Composable
private fun PracticeSection(
    detail: StageDetailDto?,
    progress: StageProgressDto?,
    busy: Boolean,
    onExampleDone: (Long) -> Unit,
) {
    val done = progress?.examplesDone ?: 0
    val required = progress?.requiredExamples ?: detail?.requiredExamples ?: 0
    val questionCount = detail?.questions?.size ?: 0
    SectionCard(title = "练 · 例题与客观题", subtitle = "例题 $done / $required · 客观题 $questionCount 道") {
        detail?.examples?.forEach { example ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${example.displayNo ?: "OJ"} ${example.displayName ?: "例题 #${example.id}"}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { onExampleDone(example.id) }, enabled = !busy) {
                    Text("标记完成")
                }
            }
        }
        if (detail?.examples.isNullOrEmpty()) {
            Text("本小节暂无例题。", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}
