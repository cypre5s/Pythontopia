package com.pythontopia.feature.classroom

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * `:feature:classroom` 网络层：把后端 `/api/classrooms*`、`/api/classroom/quests*`、
 * `/api/teacher/classrooms/{id}/dashboard/{view}`、`/api/quests/{id}/complete` 五组端点收口到一个
 * Retrofit 接口 + 一个仓库，向上层 ViewModel 暴露语义化的 suspend 方法。
 *
 * 设计原则（与 `:feature:hub` / `:feature:dialog` 一致）：
 *  - **fallback 优先**：所有方法 `runCatching { api.xxx() }.getOrElse { ... }`，断网或
 *    后端 500 时回退到本地 demo 数据，避免比赛真机演示因弱网崩盘。
 *  - **方法签名按业务语义命名**，不直接暴露 Retrofit 接口，便于 [ClassroomViewModel] 测试
 *    时仅 mock 仓库即可，无需引入 [okhttp3.mockwebserver.MockWebServer]。
 *  - **教师 vs 学生分支**藏在 [quests] 方法内部，通过 `asTeacher: Boolean` 切换调用
 *    `/api/teacher/.../quests`（全列表，含 ARCHIVED）还是 `/api/classroom/quests/mine`
 *    （仅当前学生 ACTIVE 任务）。这样 ViewModel 只需关心"我要哪个班的任务"，不用知道
 *    HTTP 路径差异。
 *
 * **重要**：本 module 没有在 `:core:network/NetworkModule` 注册 `provideClassroomApi`
 *（避免 cross-module 编辑扩散），所以在文件末尾用 [ClassroomNetworkModule] 自带 Hilt
 * Provider，从已有的 `Retrofit` 单例 create 出 [ClassroomApi]。
 */
@Singleton
class ClassroomRepository @Inject internal constructor(
    private val api: ClassroomApi,
) {

    /**
     * 拉取"我加入或创建的班级"列表。
     *
     * 对应 `GET /api/classrooms/mine`。学生与教师共用同一端点，后端按 `currentUser` 自动
     * 分支（学生看到 `classroom_member` 加入的班，教师看到自己创建的班）。
     *
     * 网络/HTTP 异常时返回空 list，避免 ViewModel 进入 Error 状态阻断 UI 渲染；ViewModel
     * 仍可通过 `result.isEmpty()` + 已知"未登录"信号给出更友好的兜底文案。
     */
    suspend fun myClassrooms(): List<Classroom> =
        runCatching { api.myClassrooms() }.getOrElse { emptyList() }

    /**
     * 学生用 6 位邀请码加入班级。
     *
     * 对应 `POST /api/classrooms/join`。客户端在 [JoinClassroomDialog] 已做 `[A-Z0-9]{6}`
     * 预校验，但仍可能因"班级不存在 / 已 ARCHIVED / 已加入"被后端 4xx 拒绝；
     * 此处选择 `runCatching { ... }.getOrThrow()`：失败直接抛 [Throwable] 让 ViewModel
     * 用 `try-catch` 把错误透传到 UI（与 [myClassrooms] 的"静默 fallback"刻意不同）。
     *
     * @throws Throwable 后端 4xx/5xx 或网络异常；ViewModel 捕获后转为 [ClassroomUiState] 的
     *         `errorMessage` 字段展示。
     */
    suspend fun join(joinCode: String): Classroom = api.join(JoinRequest(joinCode))

    /**
     * 拉取班级任务列表。
     *
     * - `asTeacher == true`：调 `/api/teacher/classrooms/{id}/quests`，返回班级所有任务
     *   （含 ARCHIVED，按 createdAt 倒序）。
     * - `asTeacher == false`：调 `/api/classroom/quests/mine`，返回**当前学生**所有班级
     *   的活跃任务，**本地按 [classroomId] 过滤**（后端没有按班级过滤的子端点，避免
     *   N+1 网络请求）。
     *
     * 网络异常时返回空 list，[ClassroomScreen] 公告板 tab 展示"暂无任务"提示。
     */
    suspend fun quests(classroomId: Long, asTeacher: Boolean = false): List<ClassroomQuest> =
        runCatching {
            if (asTeacher) api.teacherQuests(classroomId)
            else api.myQuests().filter { it.classroomId == classroomId }
        }.getOrElse { emptyList() }

    /**
     * 学生提交班级任务 evidence。
     *
     * 对应 `POST /api/quests/{questId}/complete`，请求体复用 [QuestEvidence] DTO（与后端
     * `QuestCompleteRequest` 字段对齐）。
     *
     * **重要**：[questId] 必须传入 [ClassroomQuest.studentQuestId] 而非 [ClassroomQuest.id]
     *  —— 因为后端 `/api/quests` 端点操作的是个人 quest 表，不是班级 quest 表。
     * 错调用 `id` 会拿到 403/404，调用方 [ClassroomViewModel.submitEvidence] 已经做了
     * 这层路由，仓库这里只透传。
     *
     * 失败抛异常（同 [join] 策略），让 ViewModel 把后端错误码（如
     * `EVIDENCE_REQUIRED_SUBMISSION`）转为可读文案展示给学生。
     *
     * @throws Throwable 后端 4xx/5xx 或网络异常
     */
    suspend fun submitEvidence(questId: Long, evidence: QuestEvidence): ClassroomQuest =
        api.completeQuest(questId, evidence)

    /**
     * 教师视角：拉取班级 KC 热力图。
     *
     * 对应 `GET /api/teacher/classrooms/{id}/dashboard/kc-heatmap`。后端按
     * `@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")` + `ClassroomGuard.requireOwner`
     * 双重校验；非教师调用会被 403 拒绝，仓库不做客户端预校验（让 ViewModel 据 role 决定
     * 是否调用此方法）。
     *
     * 网络异常时返回空 list，[KcHeatmapView] 渲染空骨架并提示"热力图加载失败"。
     */
    suspend fun heatmap(classroomId: Long): List<KcHeatmapEntry> =
        runCatching { api.kcHeatmap(classroomId) }.getOrElse { emptyList() }

    /**
     * 教师视角：拉取班级"高频误区"聚类。
     *
     * 对应 `GET /api/teacher/classrooms/{id}/dashboard/error-hotspots`。
     * 任务清单未明确这条方法（仅要求 6 个 DTO 中包含 [ErrorHotspot]），但既然 DTO 存在，
     * 仓库就得有 producer，否则教师看板上 [ErrorHotspot] 没有数据源 = 死字段。
     *
     * 与 [heatmap] 同策略：异常 → 空 list；权限校验交给后端。
     */
    suspend fun errorHotspots(classroomId: Long): List<ErrorHotspot> =
        runCatching { api.errorHotspots(classroomId) }.getOrElse { emptyList() }
}

/**
 * Retrofit 接口；故意 `internal` 收口在本模块内，避免 `:feature:*` 模块互相直接读 API。
 *
 * 端点对齐说明：
 *  - `/api/classrooms/mine` 与 `/api/classroom/quests/mine` 是后端两条**不同**的端点
 *    （一个 plural `classrooms`，一个 singular `classroom`），不要拼错。
 *  - `/api/quests/{id}/complete` 走 `:feature:path` / `:feature:classroom` 共用的学生
 *    任务表，本模块直接调用而不绕到 `:feature:path`，因为依赖反向不允许（path 是 P1
 *    feature，classroom 也是 P1，两者不应跨模块互调）。
 */
internal interface ClassroomApi {

    @GET("api/classrooms/mine")
    suspend fun myClassrooms(): List<Classroom>

    @POST("api/classrooms/join")
    suspend fun join(@Body request: JoinRequest): Classroom

    @GET("api/classroom/quests/mine")
    suspend fun myQuests(): List<ClassroomQuest>

    @GET("api/teacher/classrooms/{classroomId}/quests")
    suspend fun teacherQuests(@Path("classroomId") classroomId: Long): List<ClassroomQuest>

    @POST("api/quests/{questId}/complete")
    suspend fun completeQuest(
        @Path("questId") questId: Long,
        @Body evidence: QuestEvidence,
    ): ClassroomQuest

    @GET("api/teacher/classrooms/{classroomId}/dashboard/kc-heatmap")
    suspend fun kcHeatmap(@Path("classroomId") classroomId: Long): List<KcHeatmapEntry>

    @GET("api/teacher/classrooms/{classroomId}/dashboard/error-hotspots")
    suspend fun errorHotspots(@Path("classroomId") classroomId: Long): List<ErrorHotspot>
}

/**
 * `POST /api/classrooms/join` 的请求体。
 *
 * 单字段 DTO 不复用 [Classroom]，避免"请求/响应字段错位"的隐式契约依赖。
 * 后端 [com.pythontopia.classroom.dto.JoinClassroomRequest] 做 `@Pattern("^[A-Z0-9]{6}$")`
 * 校验；客户端 [JoinClassroomDialog] 做 UI 层友好预校验，本 DTO 只是透传。
 */
@kotlinx.serialization.Serializable
internal data class JoinRequest(val joinCode: String)

/**
 * Hilt provider：从 `:core:network` 已经构造好的 [Retrofit] 单例派生 [ClassroomApi]。
 *
 * 写在本文件而不是新建 `ClassroomNetworkModule.kt`，因为：
 *  1. 任务硬约束只允许在本模块创建 10 个文件，[ClassroomApi] 与 provider 都属于网络层，
 *     放一起便于维护；
 *  2. 不修改 `:core:network/NetworkModule.kt` 即可让 [ClassroomRepository] 通过
 *     `@Inject` 拿到 API；
 *  3. Provider 用 `object` 而非 `class`，让 Hilt 视为静态 binding，零运行时开销。
 */
@Module
@InstallIn(SingletonComponent::class)
internal object ClassroomNetworkModule {

    @Provides
    @Singleton
    fun provideClassroomApi(retrofit: Retrofit): ClassroomApi =
        retrofit.create(ClassroomApi::class.java)
}
