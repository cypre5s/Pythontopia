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
import com.pythontopia.feature.profile.data.ProfileDimensionDto
import com.pythontopia.feature.profile.data.ProfileDimensions
import com.pythontopia.feature.profile.ui.DimensionProgressBar
import com.pythontopia.feature.profile.ui.PassiveDimensionView
import com.pythontopia.feature.profile.ui.PixelCard
import com.pythontopia.feature.profile.ui.SubjectiveDimensionForm

@Composable
fun ProfileScreen(viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (state.dimensions.isEmpty() && state.error == null) {
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
                    ProfileTab.DASHBOARD -> DashboardContent(
                        profile = state.profile,
                        dimensions = state.dimensions,
                    )
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
private fun DashboardContent(
    profile: LearnerProfileDto?,
    dimensions: List<ProfileDimensionDto>,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PytSpace.s4),
    ) {
        if (dimensions.isEmpty()) {
            EmptyProfileHint()
        } else {
            EightDimensionDashboard(dimensions)
            if (profile == null) {
                InterviewPendingHint()
            }
            profile?.narrativeSummary?.takeIf { it.isNotBlank() }?.let { summary ->
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
            if (profile != null) {
                PassiveDimensionView(profile = profile)
            }
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

/** 有 8 维快照但画像主体尚未生成（GET /profile 204）时的解释卡：分数为中性默认，去访谈后变准。 */
@Composable
private fun InterviewPendingHint() {
    PixelCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(PytSpace.s3)) {
            Text(
                "画像访谈未完成",
                style = MaterialTheme.typography.titleMedium,
                color = PytColors.textPrimary,
            )
            Text(
                "以上为系统默认 / 被动聚合的初始值（多数维度信号不足）。去镇长 Py 那里完成访谈后，分数与置信度会随真实学习行为更新。",
                style = MaterialTheme.typography.bodySmall,
                color = PytColors.textMuted,
                modifier = Modifier.padding(top = PytSpace.s2),
            )
        }
    }
}

/** 8 维仪表盘；数据来自 GET /api/learner/profile/dimensions，evidenceCount==0 时灰显（0.5 为中性默认）。 */
@Composable
private fun EightDimensionDashboard(dimensions: List<ProfileDimensionDto>) {
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
            dimensions.forEach { dim ->
                DimensionProgressBar(
                    title = dim.label.ifBlank { ProfileDimensions.label(dim.key) },
                    score = dim.score,
                    confidence = dim.confidence,
                    evidenceCount = dim.evidenceCount,
                    selfEditable = dim.selfEditable,
                    hasSignal = dim.evidenceCount > 0,
                )
            }
        }
    }
}
