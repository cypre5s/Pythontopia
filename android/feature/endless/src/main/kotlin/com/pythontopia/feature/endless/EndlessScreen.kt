package com.pythontopia.feature.endless

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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

object EndlessDestination {
    const val ROUTE = "endless"
}

@Composable
fun EndlessScreen(viewModel: EndlessViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PytColors.bgPrimary)
            .padding(PytSpace.s3)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PytSpace.s3),
    ) {
        StatsBar(state)

        when {
            state.loadingProblem -> PytLoading()
            state.error != null && state.problem == null ->
                ErrorPanel(state.error!!, onRetry = viewModel::nextProblem)
            state.problem != null -> {
                val p = state.problem!!
                Panel {
                    Text(p.title.ifBlank { "练习题" }, color = PytColors.textPrimary, fontSize = 16.sp)
                    if (p.kcCode.isNotBlank()) {
                        Text(p.kcCode, color = PytColors.textHint, fontSize = 11.sp)
                    }
                    Text(
                        p.statementMarkdown,
                        color = PytColors.textPrimary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = PytSpace.s2),
                    )
                }

                CodeField(value = state.code, onChange = viewModel::onCodeChange)

                val result = state.result
                if (result == null) {
                    Button(
                        onClick = viewModel::submit,
                        enabled = !state.submitting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PytColors.success,
                            contentColor = PytColors.bgPrimary,
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(state.liveStatus?.label ?: "提交判题")
                    }
                } else {
                    ResultPanel(result = result, onNext = viewModel::nextProblem)
                }
                if (state.error != null) {
                    Text(state.error!!, color = PytColors.danger, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun StatsBar(state: EndlessUiState) {
    Surface(
        color = PytColors.bgElevated,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(2.dp, PytColors.borderStrong),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(PytSpace.s3),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Stat("连胜", state.streak.toString(), PytColors.warning)
            Stat("最佳", state.bestStreak.toString(), PytColors.success)
            Stat("已解", state.solved.toString(), PytColors.info)
            Stat("积分", state.score.toString(), PytColors.textPrimary)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 18.sp)
        Text(label, color = PytColors.textHint, fontSize = 11.sp)
    }
}

@Composable
private fun ResultPanel(result: EndlessSubmissionDto, onNext: () -> Unit) {
    val passed = result.status.isSuccess
    Surface(
        color = PytColors.bgElevated,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(2.dp, if (passed) PytColors.success else PytColors.danger),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(PytSpace.s3), verticalArrangement = Arrangement.spacedBy(PytSpace.s1)) {
            Text(
                if (passed) "通过！连胜继续" else result.status.label,
                color = if (passed) PytColors.success else PytColors.danger,
                fontSize = 16.sp,
            )
            if (result.totalCases != null) {
                Text("通过用例 ${result.passedCases ?: 0}/${result.totalCases}", color = PytColors.textMuted, fontSize = 12.sp)
            }
            if (!passed && !result.traceback.isNullOrBlank()) {
                Text(result.traceback.take(300), color = PytColors.warning, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(containerColor = PytColors.success, contentColor = PytColors.bgPrimary),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("下一题")
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
