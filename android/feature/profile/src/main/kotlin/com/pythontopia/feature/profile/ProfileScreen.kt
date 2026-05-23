package com.pythontopia.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pythontopia.core.design.PytColors
import com.pythontopia.core.design.PytSpace
import com.pythontopia.feature.profile.data.LearnerProfileDto
import com.pythontopia.feature.profile.data.ProfileDimensions
import com.pythontopia.feature.profile.ui.DimensionProgressBar
import com.pythontopia.feature.profile.ui.PassiveDimensionView
import com.pythontopia.feature.profile.ui.PixelCard
import com.pythontopia.feature.profile.ui.SubjectiveDimensionForm

@Composable
fun ProfileScreen(viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (state.profile == null && state.error == null) {
            viewModel.loadProfile()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PytSpace.s4),
        verticalArrangement = Arrangement.spacedBy(PytSpace.s3),
    ) {
        Text("我的画像", style = MaterialTheme.typography.headlineMedium, color = PytColors.textPrimary)

        TabRow(selectedTabIndex = state.tab.ordinal) {
            ProfileTab.entries.forEach { tab ->
                Tab(
                    selected = state.tab == tab,
                    onClick = { viewModel.selectTab(tab) },
                    text = {
                        Text(
                            when (tab) {
                                ProfileTab.DASHBOARD -> "我的画像"
                                ProfileTab.SUBJECTIVE -> "自评"
                            },
                        )
                    },
                )
            }
        }

        when {
            state.isLoading && state.profile == null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = PytColors.primary)
            }

            state.error != null -> Text(
                "加载失败：${state.error?.message}",
                style = MaterialTheme.typography.bodyMedium,
                color = PytColors.danger,
            )

            else -> Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                when (state.tab) {
                    ProfileTab.DASHBOARD -> DashboardContent(state.profile)
                    ProfileTab.SUBJECTIVE -> SubjectiveDimensionForm(
                        isSaving = state.isSaving,
                        savedKey = state.savedDimensionKey,
                        onSubmit = viewModel::updateSubjective,
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardContent(profile: LearnerProfileDto?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PytSpace.s4),
    ) {
        if (profile == null) {
            EmptyProfileHint()
        } else {
            EightDimensionDashboard(profile)
            profile.narrativeSummary?.takeIf { it.isNotBlank() }?.let { summary ->
                PixelCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(PytSpace.s3)) {
                        Text(
                            "Agent 画像速览",
                            style = MaterialTheme.typography.titleMedium,
                            color = PytColors.textPrimary,
                        )
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = PytColors.textPrimary,
                            modifier = Modifier.padding(top = PytSpace.s2),
                        )
                    }
                }
            }
            PassiveDimensionView(profile = profile)
        }
    }
}

@Composable
private fun EmptyProfileHint() {
    PixelCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(PytSpace.s4)) {
            Text(
                "尚未生成画像",
                style = MaterialTheme.typography.titleMedium,
                color = PytColors.textPrimary,
            )
            Text(
                "去镇长 Py 那里完成画像访谈，回来后这里会显示 8 维仪表和被动聚合数据。",
                style = MaterialTheme.typography.bodyMedium,
                color = PytColors.textMuted,
                modifier = Modifier.padding(top = PytSpace.s2),
            )
        }
    }
}

/**
 * 8 维仪表盘：每维一行 [DimensionProgressBar]。
 *
 * 分数估算策略（前端简化）：
 *  - 4 主观维度：用 PUT 后的 `dimensions.{key}.score`（暂不在 GET 暴露，placeholder 0.5）。
 *  - 4 被动维度：从结构化字段倒推一个粗略分（如 success_rate 直接用，frequent_kc 数 / 10 归一）。
 * 真实分由后端 `learner_profile.dimensions.{key}.score` 给出，R-13 再把它合到 GET payload。
 */
@Composable
private fun EightDimensionDashboard(profile: LearnerProfileDto) {
    PixelCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PytSpace.s4),
            verticalArrangement = Arrangement.spacedBy(PytSpace.s3),
        ) {
            Text(
                "8 维画像仪表",
                style = MaterialTheme.typography.titleLarge,
                color = PytColors.textPrimary,
            )
            ProfileDimensions.KEYS.forEach { key ->
                val score = profile.scoreFor(key)
                DimensionProgressBar(
                    title = ProfileDimensions.label(key),
                    score = score ?: PLACEHOLDER_SCORE,
                    filled = score != null,
                )
            }
        }
    }
}

private fun LearnerProfileDto.scoreFor(key: String): Double? = when (key) {
    ProfileDimensions.PROFESSIONAL_BACKGROUND -> major?.let { 0.5 }
    ProfileDimensions.KNOWLEDGE_BASE -> baseLevel?.pythonSelfRatedLevel?.let { it.toDouble() / 5.0 }
    ProfileDimensions.LEARNING_GOAL -> learningGoal?.kind?.let { 0.5 }
    ProfileDimensions.COGNITIVE_STYLE -> cognitiveStyle?.fieldDependent
    ProfileDimensions.LEARNING_PREFERENCE -> learningPreference?.sessionMinutesPerDay?.let { (it / 240.0).coerceIn(0.0, 1.0) }
    ProfileDimensions.ERROR_PATTERN -> errorPatterns?.frequentKcCodes?.let { 1.0 - (it.size / 10.0).coerceIn(0.0, 1.0) }
    ProfileDimensions.PRACTICAL_ABILITY -> practiceCapability?.successRate
    ProfileDimensions.EMOTION_PERSISTENCE -> emotionPersistence?.resilience
    else -> null
}

private const val PLACEHOLDER_SCORE = 0.0
