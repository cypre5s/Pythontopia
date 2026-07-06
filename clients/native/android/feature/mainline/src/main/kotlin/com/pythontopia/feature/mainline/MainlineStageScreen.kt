package com.pythontopia.feature.mainline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.runtime.mutableStateListOf
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pythontopia.core.data.dto.mainline.PracticeAnswerResultDto
import com.pythontopia.core.data.dto.mainline.PracticeQuestionType
import com.pythontopia.core.data.dto.mainline.StageDetailDto
import com.pythontopia.core.data.dto.mainline.StagePracticeQuestionDto
import com.pythontopia.core.data.dto.mainline.StageProgressDto
import com.pythontopia.core.design.PytErrorRetry
import com.pythontopia.core.design.PytVideoPlayer

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
                onTogglePage = { pageId -> viewModel.togglePage(pageId) },
                onExampleDone = { problemId -> viewModel.markExampleDone(problemId) },
                onAnswer = { questionId, answers -> viewModel.answerQuestion(questionId, answers) },
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
    onTogglePage: (Long) -> Unit,
    onExampleDone: (Long) -> Unit,
    onAnswer: (Long, List<String>) -> Unit,
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
        WatchSection(state = state, onWatch = onWatch)
        ReadSection(state = state, onReadAll = onReadAll, onTogglePage = onTogglePage)
        PracticeSection(state = state, onExampleDone = onExampleDone, onAnswer = onAnswer)
    }
}

@Composable
private fun WatchSection(state: MainlineStageUiState, onWatch: () -> Unit) {
    val detail = state.detail
    val progress = state.progress
    val watched = progress?.videoWatchedSec ?: detail?.videoWatchedSec ?: 0
    val required = progress?.requiredVideoSec ?: detail?.requiredVideoSec ?: 0
    SectionCard(title = "看 · 课程视频", subtitle = "已观看 $watched / $required 秒") {
        if (state.videoUrl != null) {
            PytVideoPlayer(
                url = state.videoUrl,
                modifier = Modifier.fillMaxWidth().height(200.dp),
            )
        }
        detail?.videos?.forEach { video ->
            Text("· ${video.title ?: "视频 #${video.id}"}（${video.durationSec}s）", style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = onWatch, enabled = !state.busy) { Text("上报观看 ${VIDEO_TICK_SEC}s") }
    }
}

@Composable
private fun ReadSection(
    state: MainlineStageUiState,
    onReadAll: () -> Unit,
    onTogglePage: (Long) -> Unit,
) {
    val detail = state.detail
    val pages = state.coursePages
    val required = state.progress?.requiredPages ?: detail?.requiredPages ?: 0
    val readCount = if (pages.isNotEmpty()) pages.count { it.id in state.readPageIds } else (state.progress?.pagesRead ?: 0)
    val denom = if (required > 0) required else pages.size
    SectionCard(title = "学 · 课件页", subtitle = "已读 $readCount / $denom") {
        if (pages.isEmpty()) {
            Text("本小节暂无课件页，或正在加载…", style = MaterialTheme.typography.bodySmall)
            Button(onClick = onReadAll, enabled = !state.busy && (detail?.coursewarePageIds?.isNotEmpty() == true)) {
                Text("标记全部课件已读")
            }
            return@SectionCard
        }
        pages.forEach { page ->
            val isRead = page.id in state.readPageIds
            val expanded = state.expandedPageId == page.id
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().clickable { onTogglePage(page.id) },
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${if (isRead) "✓ " else ""}P${page.pageNo} ${page.title.orEmpty()}".trim(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (expanded) FontWeight.Bold else FontWeight.Normal,
                        color = if (isRead) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    if (expanded) {
                        Text(
                            page.contentMarkdown ?: page.excerpt ?: "（本页无文本内容）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    } else if (!page.excerpt.isNullOrBlank()) {
                        Text(
                            page.excerpt!!.take(60),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PracticeSection(
    state: MainlineStageUiState,
    onExampleDone: (Long) -> Unit,
    onAnswer: (Long, List<String>) -> Unit,
) {
    val detail = state.detail
    val progress = state.progress
    val busy = state.busy
    val questions = detail?.questions.orEmpty().sortedBy { it.ordinal }
    val requiredQuestions = (detail?.requiredQuestions ?: 0).let { if (it > 0) it else questions.size }
    val quizCorrect = questions.count { it.id in state.correctQuestionIds }
    val done = progress?.examplesDone ?: detail?.examplesDoneIds?.size ?: 0
    val required = progress?.requiredExamples ?: detail?.requiredExamples ?: 0

    SectionCard(
        title = "练 · 客观题与编程实战",
        subtitle = buildString {
            if (questions.isNotEmpty()) append("客观题 $quizCorrect / $requiredQuestions")
            if (detail?.examples?.isNotEmpty() == true) {
                if (isNotEmpty()) append(" · ")
                append("编程 $done / $required")
            }
            if (isEmpty()) append("本小节暂无练习")
        },
    ) {
        // 一、客观题（选/判/填）：站内真实判分（服务端不泄题，回传正确答案 + 解析自检）。
        if (questions.isNotEmpty()) {
            Text("一、客观题", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            questions.forEachIndexed { idx, q ->
                PracticeQuestionCard(
                    index = idx + 1,
                    question = q,
                    done = q.id in state.correctQuestionIds,
                    result = state.questionResults[q.id],
                    answering = q.id in state.answeringIds,
                    onSubmit = { answers -> onAnswer(q.id, answers) },
                )
            }
        }

        // 二、编程实战（OJ）：去训练场真实判题，完成后打点。
        if (detail?.examples?.isNotEmpty() == true) {
            Text("二、编程实战", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            detail.examples.orEmpty().forEach { example ->
                val isDone = example.id in (detail.examplesDoneIds.toSet())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${if (isDone) "✓ " else ""}${example.displayNo ?: "OJ"} ${example.displayName ?: "例题 #${example.id}"}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = { onExampleDone(example.id) }, enabled = !busy && !isDone) {
                        Text(if (isDone) "已完成" else "标记完成")
                    }
                }
            }
        }

        if (questions.isEmpty() && detail?.examples.isNullOrEmpty()) {
            Text("本小节暂无练习。", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * 站内客观题答题卡（移植 Web `PracticeQuestionCard.vue`）：按题型渲染选/判/填，
 * 提交走服务端判分；答对锁题，无论对错都展示正确答案 + 解析供自检。
 */
@Composable
private fun PracticeQuestionCard(
    index: Int,
    question: StagePracticeQuestionDto,
    done: Boolean,
    result: PracticeAnswerResultDto?,
    answering: Boolean,
    onSubmit: (List<String>) -> Unit,
) {
    val type = question.type ?: PracticeQuestionType.CHOICE
    var choiceIndex by remember(question.id) { mutableStateOf<Int?>(null) }
    var judgeValue by remember(question.id) { mutableStateOf<Boolean?>(null) }
    val blankCount = if (type == PracticeQuestionType.FILL) maxOf(1, question.blankCount) else 0
    val blanks = remember(question.id) { mutableStateListOf<String>().apply { repeat(blankCount) { add("") } } }
    val locked = done || result?.correct == true

    val typeLabel = when (type) {
        PracticeQuestionType.CHOICE -> "单选题"
        PracticeQuestionType.JUDGE -> "判断题"
        PracticeQuestionType.FILL -> "填空题"
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "$index. ($typeLabel) ${question.stem.orEmpty()}${if (locked) "  ✓" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )

            when (type) {
                PracticeQuestionType.CHOICE -> question.options.forEachIndexed { idx, opt ->
                    val letter = ('A' + idx).toString()
                    val correctOpt = result?.correctAnswers?.contains(opt) == true
                    OptionRow(
                        text = "$letter. $opt",
                        selected = choiceIndex == idx,
                        correct = result != null && correctOpt,
                        wrong = result != null && choiceIndex == idx && !result.correct && !correctOpt,
                        enabled = !locked,
                        onClick = { choiceIndex = idx },
                    )
                }
                PracticeQuestionType.JUDGE -> listOf(true to "正确", false to "错误").forEach { (value, label) ->
                    val correctOpt = result?.correctAnswers?.contains(label) == true
                    OptionRow(
                        text = label,
                        selected = judgeValue == value,
                        correct = result != null && correctOpt,
                        wrong = result != null && judgeValue == value && !result.correct && !correctOpt,
                        enabled = !locked,
                        onClick = { judgeValue = value },
                    )
                }
                PracticeQuestionType.FILL -> for (i in 0 until blankCount) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("第 ${i + 1} 空", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp))
                        BlankField(
                            value = blanks.getOrElse(i) { "" },
                            enabled = !locked,
                            onChange = { if (i < blanks.size) blanks[i] = it },
                        )
                    }
                }
            }

            val canSubmit = !locked && !answering && when (type) {
                PracticeQuestionType.CHOICE -> choiceIndex != null
                PracticeQuestionType.JUDGE -> judgeValue != null
                PracticeQuestionType.FILL -> (0 until blankCount).all { blanks.getOrElse(it) { "" }.isNotBlank() }
            }
            Button(
                onClick = {
                    val answers = when (type) {
                        PracticeQuestionType.CHOICE -> listOf(question.options.getOrElse(choiceIndex ?: 0) { "" })
                        PracticeQuestionType.JUDGE -> listOf(if (judgeValue == true) "正确" else "错误")
                        PracticeQuestionType.FILL -> (0 until blankCount).map { blanks.getOrElse(it) { "" } }
                    }
                    onSubmit(answers)
                },
                enabled = canSubmit,
            ) {
                Text(if (answering) "判分中…" else if (locked) "已答对" else "提交")
            }

            if (result != null) {
                val ok = result.correct
                Text(
                    if (ok) "✓ 答对了" else "✗ 答错了",
                    color = if (ok) CorrectGreen else WrongRed,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                )
                if (result.correctAnswers.isNotEmpty()) {
                    Text("正确答案：${result.correctAnswers.joinToString(" / ")}", style = MaterialTheme.typography.bodySmall)
                }
                if (!result.explanation.isNullOrBlank()) {
                    Text("解析：${result.explanation}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun OptionRow(
    text: String,
    selected: Boolean,
    correct: Boolean,
    wrong: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val border = when {
        correct -> CorrectGreen
        wrong -> WrongRed
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, border),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
    }
}

@Composable
private fun BlankField(value: String, enabled: Boolean, onChange: (String) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.width(220.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

private val CorrectGreen = Color(0xFF1FA657)
private val WrongRed = Color(0xFFE25C4A)

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
