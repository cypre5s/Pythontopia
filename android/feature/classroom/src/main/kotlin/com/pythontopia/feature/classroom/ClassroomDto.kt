package com.pythontopia.feature.classroom

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `:feature:classroom` 模块对外暴露的网络层 DTO 集合。
 *
 * 设计取舍：
 *  - 后端契约（`com.pythontopia.classroom.*` Java records）字段众多（[ClassroomQuestDto]
 *    后端共 20 字段，多数 R-13 子任务才用），Android 学生 / 教师视角实际只读 7~9 字段。
 *    因此本文件**不**直接 1:1 镜像后端 record，只挑出 R-12 班级 P1 闭环必需字段，
 *    其余字段在反序列化时被 [kotlinx.serialization.json.Json] `ignoreUnknownKeys=true`
 *    丢弃，避免后端微调字段就触发 Android 端解析崩溃。
 *  - `@Serializable` 让 Retrofit 的 kotlinx-serialization converter 能直接解析，无需手写
 *    `JsonObject` 适配；也方便 [test/ClassroomRepositoryTest] 用 [okhttp3.mockwebserver.MockResponse]
 *    返回 JSON 字符串后做端到端断言。
 *  - 字段命名遵循后端 camelCase 习惯，与 `core/data/dto/world/{dto}` 现有 DTO 风格一致；
 *    `dueDate` / `createdAt` 用 String 而非 `LocalDate`，因为后端用 Jackson 默认 ISO-8601
 *    输出，Android 端目前没有引入 kotlinx-datetime 序列化适配，String 透传可减少一类 issue。
 */

/**
 * 班级聚合。
 *
 * 对应后端 `ClassroomDto(classroomId, name, description, teacherId, packId, status, joinCode)`。
 *
 * @property classroomId 班级唯一 ID（数据库自增主键）。
 * @property name 班级显示名（教师创建时填写）。
 * @property description 班级简介；R-12 教师可选填，学生公告板会在卡片折叠状态隐藏。
 * @property teacherId 主教师 user_id；用于学生本地判断"我是不是这个班的教师"快速分支。
 * @property packId 关联课程包；R-12 / R-13 恒为 43L（`Python语言基础`），但保留字段
 *           以避免 R-14 接入多课程时再回头加字段。
 * @property status 班级生命周期；R-11 现网为 `ACTIVE` / `ARCHIVED` 二态，R-13 扩展到
 *           `DRAFT / RECRUITING / IN_SESSION / MIDTERM_LOCK / FINAL_LOCK / ARCHIVED` 六态
 *           （详见 `docs/gameplay/social-classroom.md` §1.2）。Android 仅展示字符串，不
 *           做枚举校验，避免与后端节奏脱钩。
 * @property joinCode 6 位邀请码（[A-Z0-9]{6}）；学生端 [JoinClassroomDialog] 输入校验
 *           按此 pattern 做客户端预校验，但**真校验由后端**做（避免本地正则与后端漂移）。
 */
@Serializable
data class Classroom(
    val classroomId: Long,
    val name: String,
    val description: String? = null,
    val teacherId: Long,
    val packId: Long,
    val status: String,
    val joinCode: String,
)

/**
 * 班级成员条目（学生视角）。
 *
 * 对应后端 `ClassroomMemberDto(studentId, username, displayName, status)`。
 * Android 不展示班级成员列表（避免社交压力扩散到练习路径），但 [ClassroomRepository]
 * 仍保留该 DTO 以备教师视角"个体诊断"面板使用（R-13 落地）。
 */
@Serializable
data class ClassroomMember(
    val studentId: Long,
    val username: String,
    val displayName: String? = null,
    val status: String,
)

/**
 * 班级任务（公告板单元）。
 *
 * 对应后端 `ClassroomQuestDto`。后端字段较多，这里只保留 Android 公告板 / 教师任务列表
 * 必需的字段：
 *  - 学生视角：title / description / dueDate / status / requiredEvidenceType /
 *    studentQuestId / studentQuestStatus（任务对个人是 ASSIGNED / IN_PROGRESS / COMPLETED 哪态）
 *  - 教师视角：classroomId / nodeType / kcCodes / targetProblemId / targetResourceType
 *    用于"哪个 KC、哪道题、哪种资源"汇总
 *
 * 不保留：payload(JsonNode)、deepLinkParams(JsonNode) —— Android demo 不消费 deep link
 * 跳转，避免引入 JSON 树解析逻辑；如 R-13 需要再扩展为 `JsonElement` 字段即可。
 *
 * @property id 班级任务 ID（教师维度）；学生提交 evidence 时用 [studentQuestId] 而非这个。
 * @property classroomId 所属班级；教师视角"按班级聚合"必读字段。
 * @property title 任务标题（教师填写，最长 256 字）。
 * @property description 任务说明；可能为 null 或空串。
 * @property kcCodes 关联 KC 编码列表（如 `["KC_FOR_LOOP", "KC_LIST_BASIC"]`）；用于
 *           教师 KC 热力图点击 → 一键发布"补练任务"的回填校验，也用于学生卡片底部 chip 展示。
 * @property nodeType 任务节点类型枚举字符串；目前后端有 `SOLVE_PROBLEM / READ_RESOURCE /
 *           FIX_BUG / PLANT_KC` 四种，Android 仅展示，不做行为分支。
 * @property dueDate 截止日期（ISO-8601 `yyyy-MM-dd`）。
 * @property status 班级任务状态：`PUBLISHED` / `ARCHIVED`。
 * @property targetProblemId 绑定题目 ID；与 [targetResourceType] 互斥（后端 publish 时校验）。
 * @property targetResourceType 绑定资源类型：`HANDOUT / MIND_MAP / PRACTICE / CODE_LAB
 *           / READING / STORYBOARD`；与 [targetProblemId] 互斥。
 * @property studentQuestId 学生侧个人任务 ID；学生公告板 → 提交 evidence 时用此 ID 调用
 *           `POST /api/quests/{id}/complete`。**仅在 `/api/classroom/quests/mine` 响应里
 *           非 null**；教师视角 `/api/teacher/.../quests` 这个字段恒为 null。
 * @property studentQuestStatus 学生侧个人任务状态：`ASSIGNED / IN_PROGRESS / COMPLETED
 *           / SKIPPED`；仅学生视角有意义。
 * @property requiredEvidenceType 所需证据类型：`submission / resource / page_read /
 *           kc_mastery` 四种之一，或 null（旧 quest 不要求）；学生提交时按此分支决定
 *           [QuestEvidence] 哪个字段必填，与后端 `QuestEvidenceVerifier` 校验对齐。
 */
@Serializable
data class ClassroomQuest(
    val id: Long,
    val classroomId: Long,
    val title: String,
    val description: String? = null,
    val kcCodes: List<String> = emptyList(),
    val nodeType: String,
    val dueDate: String,
    val status: String,
    val targetProblemId: Long? = null,
    val targetResourceType: String? = null,
    val studentQuestId: Long? = null,
    val studentQuestStatus: String? = null,
    val requiredEvidenceType: String? = null,
)

/**
 * KC 热力图单格（教师视角）。
 *
 * 对应后端 `KcHeatmapEntryDto`。65 个 KC × 班级人数 ≤ 60 = 3900 格的渲染压力交给
 * [KcHeatmapView]（Compose Canvas）；本 DTO 只承担"行"的语义：一个 KC 在班级里
 * 的统计聚合。
 *
 * @property kcCode KC 唯一编码（如 `KC_FOR_LOOP`）。
 * @property kcName KC 显示名（"for 循环基础"）。
 * @property chapterId 所属章节 ID；R-12 用于按章节分组着色。
 * @property chapterOrder 章节顺序；UI 排序使用，从 1 起。
 * @property avgMastery 班级在该 KC 上的平均掌握度 [0.00, 1.00]；用 [String] 透传后端
 *           BigDecimal 输出（保留 2 位小数），避免 Float/Double 精度漂移。Compose Canvas
 *           内部解析为 Float 后映射颜色。
 * @property studentCount 已开始学习该 KC 的学生数。
 * @property masteredCount mastery ≥ 0.80 的学生数（绿色"已掌握"）。
 * @property learningCount mastery ∈ [0.40, 0.80) 的学生数（黄色"学习中"）。
 * @property strugglingCount mastery < 0.40 的学生数（红色"挣扎中"）。
 */
@Serializable
data class KcHeatmapEntry(
    val kcCode: String,
    val kcName: String,
    val chapterId: Long? = null,
    val chapterOrder: Int? = null,
    val avgMastery: String,
    val studentCount: Int,
    val masteredCount: Int,
    val learningCount: Int,
    val strugglingCount: Int,
)

/**
 * 高频误区聚类（教师视角）。
 *
 * 对应后端 `ErrorHotspotDto(kcCode, kcName, failedSubmissionCount, affectedStudentCount,
 * categories: List<ErrorCategoryDto>)`。教师看板"高频误区"卡片用这个 DTO 渲染。
 *
 * @property kcCode 误区聚集的 KC 编码。
 * @property kcName 显示名。
 * @property failedSubmissionCount 该 KC 下失败提交数。
 * @property affectedStudentCount 受影响学生数（同一学生多次失败只计 1）。
 * @property categories 错误分类细分（按错误类型聚合），按计数降序由后端排好。
 */
@Serializable
data class ErrorHotspot(
    val kcCode: String,
    val kcName: String,
    val failedSubmissionCount: Int,
    val affectedStudentCount: Int,
    val categories: List<ErrorCategory> = emptyList(),
) {
    /**
     * 单条错误分类。
     *
     * @property category 分类标签（如 `SYNTAX_ERROR` / `INDEX_OUT_OF_RANGE`）。
     * @property count 该分类下的提交数。
     */
    @Serializable
    data class ErrorCategory(
        val category: String,
        val count: Int,
    )
}

/**
 * 学生提交班级任务时附带的"证据"。
 *
 * 对应后端 `QuestCompleteRequest(note, submissionId, resourceId, pageReadId, kcMasteryDelta)`。
 *
 * **强校验约定**（对齐后端 `QuestEvidenceVerifier`）：
 *  - 当 [ClassroomQuest.requiredEvidenceType] == `"submission"` 时，[submissionId] 必填
 *  - 当 == `"resource"` 时，[resourceId] 必填
 *  - 当 == `"page_read"` 时，[pageReadId] 必填
 *  - 当 == `"kc_mastery"` 时，[kcMasteryDelta] 必填（且 ∈ [0.00, 1.00]）
 *  - 当 == null 时，全部字段可选（兼容老 quest，仅记录 [note]）
 *
 * Android 端 [ClassroomViewModel.submitEvidence] 做客户端预校验，但**最终决定权在后端**，
 * 客户端不阻止用户跳过校验（避免把判定逻辑分散两处）；后端 422 错误码会冒泡到 UI。
 *
 * @property note 学生留言，最长 256 字；空串等价于 null。
 * @property submissionId 引用一次已 PASSED 的练习提交 ID（题目须在 quest.targetProblemIds 内）。
 * @property resourceId 引用一次资源消费记录 ID。
 * @property pageReadId 引用一次课件页阅读记录 ID。
 * @property kcMasteryDelta KC 掌握度提升量（仅 KC_MASTERY 类型有效，0.00~1.00）。
 */
@Serializable
data class QuestEvidence(
    val note: String? = null,
    val submissionId: Long? = null,
    val resourceId: Long? = null,
    val pageReadId: Long? = null,
    @SerialName("kcMasteryDelta") val kcMasteryDelta: Double? = null,
) {
    /**
     * 仅留言、无结构化证据；用于 `requiredEvidenceType == null` 的兼容路径。
     */
    constructor(note: String?) : this(note = note, submissionId = null)
}
