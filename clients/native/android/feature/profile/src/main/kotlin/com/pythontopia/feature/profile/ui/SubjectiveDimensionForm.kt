package com.pythontopia.feature.profile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pythontopia.core.design.PytColors
import com.pythontopia.core.design.PytSpace
import com.pythontopia.feature.profile.data.DimensionUpdateRequest
import com.pythontopia.feature.profile.data.ProfileDimensions
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 单道 Likert 1-5 评分项。
 *
 * `key` 是 metadata 中的字段名（snake_case），最终 metadata 形如
 * `{"items": {"goal_clarity": 4, "goal_confidence": 5, ...}}`，
 * 后端 [PassiveProfileService] 把 metadata 覆盖写入 `dimensions.{dimensionKey}.metadata`。
 */
data class LikertItem(
    val key: String,
    val prompt: String,
)

/** 4 主观维度的 Likert 题库（每维 3–5 题）；metadata 落库供 Agent 引用，非临床量表。 */
val SUBJECTIVE_DIMENSIONS: List<SubjectiveDimensionSpec> = listOf(
    SubjectiveDimensionSpec(
        key = ProfileDimensions.PROFESSIONAL_BACKGROUND,
        title = ProfileDimensions.label(ProfileDimensions.PROFESSIONAL_BACKGROUND),
        explanation = "你对当前专业与 Python 关系的主观判断（与画像访谈的 major 互为补充）。",
        items = listOf(
            LikertItem("major_identity", "我认同当前所学的专业"),
            LikertItem("cs_interest", "我对计算机相关课程感兴趣"),
            LikertItem("python_relevance", "Python 对我的专业未来有帮助"),
        ),
    ),
    SubjectiveDimensionSpec(
        key = ProfileDimensions.LEARNING_GOAL,
        title = ProfileDimensions.label(ProfileDimensions.LEARNING_GOAL),
        explanation = "聚焦你本学期的 Python 学习目标，PathPlannerAgent 用它排关卡难度。",
        items = listOf(
            LikertItem("goal_clarity", "我清楚自己想达到的水平"),
            LikertItem("goal_confidence", "我有信心在期末前完成目标"),
            LikertItem("goal_urgency", "我对截止时间感到紧迫"),
            LikertItem("goal_kc_match", "我的目标和课程 KC 是匹配的"),
        ),
    ),
    SubjectiveDimensionSpec(
        key = ProfileDimensions.LEARNING_PREFERENCE,
        title = ProfileDimensions.label(ProfileDimensions.LEARNING_PREFERENCE),
        explanation = "ResourceDesignerAgent 会优先用你打分高的媒介出资源。",
        items = listOf(
            LikertItem("pref_video", "我喜欢看视频学新概念"),
            LikertItem("pref_text", "我喜欢读教材 / 文档"),
            LikertItem("pref_mindmap", "我喜欢看思维导图把知识串起来"),
            LikertItem("pref_code", "我喜欢直接看 / 写代码示例"),
            LikertItem("pref_session", "我能一次专注学习超过 30 分钟"),
        ),
    ),
    SubjectiveDimensionSpec(
        key = ProfileDimensions.EMOTION_PERSISTENCE,
        title = ProfileDimensions.label(ProfileDimensions.EMOTION_PERSISTENCE),
        explanation = "DebugAgent 会根据你的情绪状态调整反馈语气与 hint 强度。",
        items = listOf(
            LikertItem("mood_recent", "最近一周我对学 Python 的心情是积极的"),
            LikertItem("resilience_bug", "卡在 Bug 也能耐心调试不放弃"),
            LikertItem("resilience_long", "我能坚持完成多周的学习计划"),
        ),
    ),
)

data class SubjectiveDimensionSpec(
    val key: String,
    val title: String,
    val explanation: String,
    val items: List<LikertItem>,
)

/** 单维度 Likert 表单状态；纯数据层便于 JVM 单测，与 Compose UI 解耦。 */
data class DimensionFormState(
    val scores: Map<String, Int> = emptyMap(),
) {
    fun setScore(itemKey: String, score: Int): DimensionFormState {
        require(score in MIN_SCORE..MAX_SCORE) { "Likert score must be in [$MIN_SCORE,$MAX_SCORE], got $score" }
        return copy(scores = scores + (itemKey to score))
    }

    fun isFilled(spec: SubjectiveDimensionSpec): Boolean =
        spec.items.all { it.key in scores }

    /**
     * 构造 PUT 请求体：
     *  - `scoreDelta` = (avg(items) - 3) / 4，把 1..5 Likert 映射到 [-0.5, 0.5]，
     *    与后端 [SelfDimensionRequest] 的 `[-1, 1]` 范围一致。
     *  - `evidenceIncrement = 1`：每次自评算一条 evidence。
     *  - `metadata.items` 保留每项原始分，供后续 agent 引用。
     */
    fun buildRequestFor(spec: SubjectiveDimensionSpec): DimensionUpdateRequest? {
        if (!isFilled(spec)) return null
        val avg = spec.items.map { scores.getValue(it.key) }.average()
        val delta = ((avg - MID_SCORE) / HALF_RANGE).coerceIn(-1.0, 1.0)
        val itemsObj = JsonObject(
            spec.items.associate { item -> item.key to JsonPrimitive(scores.getValue(item.key)) },
        )
        val metadata = JsonObject(mapOf("items" to itemsObj))
        return DimensionUpdateRequest(
            scoreDelta = delta,
            evidenceIncrement = 1,
            metadata = metadata,
        )
    }

    companion object {
        const val MIN_SCORE = 1
        const val MAX_SCORE = 5
        private const val MID_SCORE = 3.0
        private const val HALF_RANGE = 4.0
    }
}

/** 4 主观维度自评表单；表单状态保存在 Composable 本地，与 ViewModel 解耦。 */
@Composable
fun SubjectiveDimensionForm(
    isSaving: Boolean,
    savedKey: String?,
    onSubmit: (dimensionKey: String, request: DimensionUpdateRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PytSpace.s4),
    ) {
        Text(
            "主观维度自评",
            style = MaterialTheme.typography.titleLarge,
            color = PytColors.textPrimary,
        )
        Text(
            "为以下 4 维分别打分；提交后会增量更新画像，并触发 Agent 重新规划资源。",
            style = MaterialTheme.typography.bodySmall,
            color = PytColors.textMuted,
        )
        SUBJECTIVE_DIMENSIONS.forEach { spec ->
            DimensionFormCard(
                spec = spec,
                isSaving = isSaving,
                justSaved = savedKey == spec.key,
                onSubmit = { request -> onSubmit(spec.key, request) },
            )
        }
    }
}

@Composable
private fun DimensionFormCard(
    spec: SubjectiveDimensionSpec,
    isSaving: Boolean,
    justSaved: Boolean,
    onSubmit: (DimensionUpdateRequest) -> Unit,
) {
    var formState by remember(spec.key) { mutableStateOf(DimensionFormState()) }

    PixelCard(modifier = Modifier.fillMaxWidth().testTag("DimensionFormCard:${spec.key}")) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(PytSpace.s3),
            verticalArrangement = Arrangement.spacedBy(PytSpace.s2),
        ) {
            Text(spec.title, style = MaterialTheme.typography.titleMedium, color = PytColors.textPrimary)
            Text(spec.explanation, style = MaterialTheme.typography.bodySmall, color = PytColors.textMuted)

            spec.items.forEach { item ->
                LikertRow(
                    item = item,
                    selected = formState.scores[item.key],
                    onSelect = { score -> formState = formState.setScore(item.key, score) },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val statusText = when {
                    justSaved -> "已保存 ✓"
                    isSaving -> "保存中…"
                    else -> "${formState.scores.size} / ${spec.items.size} 题已填"
                }
                Text(
                    statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (justSaved) PytColors.success else PytColors.textMuted,
                )
                Button(
                    onClick = {
                        formState.buildRequestFor(spec)?.let(onSubmit)
                    },
                    enabled = !isSaving && formState.isFilled(spec),
                    modifier = Modifier.testTag("SubmitButton:${spec.key}"),
                ) {
                    Text("提交")
                }
            }
        }
    }
}

@Composable
private fun LikertRow(
    item: LikertItem,
    selected: Int?,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(item.prompt, style = MaterialTheme.typography.bodyMedium, color = PytColors.textPrimary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PytSpace.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (score in DimensionFormState.MIN_SCORE..DimensionFormState.MAX_SCORE) {
                Row(
                    modifier = Modifier
                        .selectable(
                            selected = selected == score,
                            onClick = { onSelect(score) },
                            role = Role.RadioButton,
                        )
                        .testTag("LikertOption:${item.key}:$score"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selected == score,
                        onClick = null,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        score.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = PytColors.textPrimary,
                    )
                }
            }
        }
    }
}
