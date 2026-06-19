package com.pythontopia.feature.mainline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.pythontopia.core.data.dto.mainline.ChapterTestPaperDto
import com.pythontopia.core.data.dto.mainline.ChapterTestQuestionResultDto
import com.pythontopia.core.data.dto.mainline.ChapterTestQuestionViewDto
import com.pythontopia.core.data.dto.mainline.ChapterTestResultDto
import com.pythontopia.core.design.PytErrorRetry

/** 章末测评：开考题面作答 + 交卷结果（含解析）。[assessment] 区分大测试 / 快测。 */
@Composable
fun ChapterTestScreen(
    chapter: Int,
    onBack: () -> Unit = {},
    assessment: ChapterAssessment = ChapterAssessment.TEST,
    viewModel: ChapterTestViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(chapter, assessment) { viewModel.load(chapter, assessment) }

    val title = if (assessment == ChapterAssessment.QUIZ) "章末快测" else "章末测试"

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val result = state.result
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            result != null -> ResultView(result = result, onBack = onBack)
            state.paper != null -> PaperView(
                paper = state.paper!!,
                title = title,
                answers = state.answers,
                submitting = state.submitting,
                onAnswer = viewModel::setAnswer,
                onSubmit = viewModel::submit,
                onBack = onBack,
            )
            else -> Box(Modifier.fillMaxSize().padding(16.dp), Alignment.Center) {
                PytErrorRetry(
                    message = state.error ?: "无法开考",
                    onRetry = { viewModel.load(chapter, assessment) },
                )
            }
        }
    }
}

@Composable
private fun PaperView(
    paper: ChapterTestPaperDto,
    title: String,
    answers: Map<String, String>,
    submitting: Boolean,
    onAnswer: (String, String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ 返回") }
                Text(
                    text = "第 ${paper.chapter} 章 · $title（第 ${paper.attemptNo} 次）",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        item {
            Text(
                text = "限时 ${paper.timeLimitSec / 60} 分钟 · 截止 ${paper.deadlineAt ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(paper.items, key = { it.qid }) { question ->
            QuestionCard(
                question = question,
                answer = answers[question.qid],
                onAnswer = { value -> onAnswer(question.qid, value) },
            )
        }
        item {
            Button(
                onClick = onSubmit,
                enabled = !submitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (submitting) "判分中…" else "交卷")
            }
        }
    }
}

@Composable
private fun QuestionCard(
    question: ChapterTestQuestionViewDto,
    answer: String?,
    onAnswer: (String) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "[${question.type ?: ""} · ${question.score}分] ${question.stem ?: question.title ?: ""}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            question.statementMarkdown?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when (question.type?.uppercase()) {
                "CHOICE" -> ChoiceBody(question.options, answer, onAnswer)
                "JUDGE" -> JudgeBody(answer, onAnswer)
                "CODING" -> CodingBody(question, answer, onAnswer)
                else -> FillBody(answer, onAnswer)
            }
        }
    }
}

@Composable
private fun ChoiceBody(options: List<String>, answer: String?, onAnswer: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            val selected = answer == option
            Surface(
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().clickable { onAnswer(option) },
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun JudgeBody(answer: String?, onAnswer: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("正确", "错误").forEach { label ->
            val selected = answer == label
            if (selected) {
                Button(onClick = { onAnswer(label) }) { Text(label) }
            } else {
                OutlinedButton(onClick = { onAnswer(label) }) { Text(label) }
            }
        }
    }
}

@Composable
private fun FillBody(answer: String?, onAnswer: (String) -> Unit) {
    OutlinedTextField(
        value = answer.orEmpty(),
        onValueChange = onAnswer,
        label = { Text("填空作答") },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CodingBody(
    question: ChapterTestQuestionViewDto,
    answer: String?,
    onAnswer: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        question.samples.forEach { sample ->
            Text(
                text = "样例输入：${sample.input ?: ""}  →  期望输出：${sample.output ?: ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = answer.orEmpty(),
            onValueChange = onAnswer,
            label = { Text("Python 源码") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ResultView(result: ChapterTestResultDto, onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ 返回") }
                Text(
                    text = if (result.passed) "✓ 通过" else "未通过",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (result.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
        }
        item {
            Text(
                text = "得分 ${result.score ?: 0} / ${result.maxScore ?: 0} · 星级 ${result.stars}★" +
                    if (result.overtime) " · 超时" else "",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        items(result.items, key = { it.qid }) { item -> ResultQuestionCard(item) }
    }
}

@Composable
private fun ResultQuestionCard(item: ChapterTestQuestionResultDto) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "${if (item.correct) "✓" else "✗"} ${item.type ?: ""} · ${item.awardedScore}/${item.maxScore} 分",
                style = MaterialTheme.typography.titleSmall,
                color = if (item.correct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            item.yourAnswer?.let { Text("你的作答：$it", style = MaterialTheme.typography.bodySmall) }
            item.correctAnswer?.let {
                Text(
                    "正确答案：$it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item.explain?.takeIf { it.isNotBlank() }?.let {
                Text("解析：$it", style = MaterialTheme.typography.bodySmall)
            }
            if (item.totalCases != null) {
                Text(
                    "用例：${item.passedCases ?: 0}/${item.totalCases}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
