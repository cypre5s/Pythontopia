package com.pythontopia.feature.profile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pythontopia.core.design.PytColors
import com.pythontopia.core.design.PytSpace
import com.pythontopia.feature.profile.data.BaseLevelDto
import com.pythontopia.feature.profile.data.CognitiveStyleDto
import com.pythontopia.feature.profile.data.ErrorPatternsDto
import com.pythontopia.feature.profile.data.LearnerProfileDto
import com.pythontopia.feature.profile.data.PracticeCapabilityDto
import com.pythontopia.feature.profile.data.ProfileDimensions

/** 4 个被动维度（非 [ProfileDimensions.SELF_EDITABLE]）只读展示；由 PassiveProfileService 被动聚合。 */
@Composable
fun PassiveDimensionView(
    profile: LearnerProfileDto?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PytSpace.s3),
    ) {
        Text(
            "被动维度（系统聚合）",
            style = MaterialTheme.typography.titleLarge,
            color = PytColors.textPrimary,
        )
        Text(
            "以下 4 维由你的访谈、提交、调试、任务完成事件自动聚合，不可直接修改；继续完成练习与任务即可。",
            style = MaterialTheme.typography.bodySmall,
            color = PytColors.textMuted,
        )

        PassiveDimensionCard(
            title = ProfileDimensions.label(ProfileDimensions.KNOWLEDGE_BASE),
            explanation = "通过访谈与初次评估得出，反映你已经会的编程语言与 Python 水平。",
            valueLines = profile?.baseLevel.toLines(),
        )

        PassiveDimensionCard(
            title = ProfileDimensions.label(ProfileDimensions.COGNITIVE_STYLE),
            explanation = "field_dependent 越高越喜欢有结构的引导；sequential_vs_holistic 偏正表示偏整体直觉。",
            valueLines = profile?.cognitiveStyle.toLines(),
        )

        PassiveDimensionCard(
            title = ProfileDimensions.label(ProfileDimensions.ERROR_PATTERN),
            explanation = "由判题失败 + Bug 矿洞记录推断，帮助 ResourceDesignerAgent 给你定向出题。",
            valueLines = profile?.errorPatterns.toLines(),
        )

        PassiveDimensionCard(
            title = ProfileDimensions.label(ProfileDimensions.PRACTICAL_ABILITY),
            explanation = "你的练习题平均尝试次数与通过率；通过率越高，PathPlanner 会推更难的关卡。",
            valueLines = profile?.practiceCapability.toLines(),
        )
    }
}

@Composable
private fun PassiveDimensionCard(
    title: String,
    explanation: String,
    valueLines: List<String>,
) {
    PixelCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PytSpace.s3),
            verticalArrangement = Arrangement.spacedBy(PytSpace.s2),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = PytColors.textPrimary)
            Text(explanation, style = MaterialTheme.typography.bodySmall, color = PytColors.textMuted)
            if (valueLines.isEmpty()) {
                Text(
                    "尚未生成数据 — 完成镇长画像访谈后会自动填充。",
                    style = MaterialTheme.typography.bodySmall,
                    color = PytColors.textHint,
                )
            } else {
                valueLines.forEach { line ->
                    Text("• $line", style = MaterialTheme.typography.bodyMedium, color = PytColors.textPrimary)
                }
            }
        }
    }
}

private fun BaseLevelDto?.toLines(): List<String> {
    val src = this ?: return emptyList()
    return buildList {
        src.programmingYears?.let { add("编程年限：$it 年") }
        src.languagesKnown?.takeIf { it.isNotEmpty() }
            ?.let { add("已掌握：${it.joinToString("、")}") }
        src.pythonSelfRatedLevel?.let { add("Python 自评：$it / 5") }
    }
}

private fun CognitiveStyleDto?.toLines(): List<String> {
    val src = this ?: return emptyList()
    return buildList {
        src.fieldDependent?.let { add("场依存度：%.2f".format(it)) }
        src.sequentialVsHolistic?.let { add("顺序-整体倾向：%.2f".format(it)) }
    }
}

private fun ErrorPatternsDto?.toLines(): List<String> {
    val src = this ?: return emptyList()
    return buildList {
        src.frequentKcCodes?.takeIf { it.isNotEmpty() }
            ?.let { add("频繁失败 KC：${it.take(MAX_PREVIEW).joinToString("、")}") }
        src.commonCategories?.takeIf { it.isNotEmpty() }
            ?.let { add("常见类别：${it.joinToString("、")}") }
    }
}

private fun PracticeCapabilityDto?.toLines(): List<String> {
    val src = this ?: return emptyList()
    return buildList {
        src.avgAttemptsPerProblem?.let { add("平均尝试次数：%.1f".format(it)) }
        src.successRate?.let { add("通过率：%d%%".format((it * 100).toInt())) }
    }
}

private const val MAX_PREVIEW = 5
