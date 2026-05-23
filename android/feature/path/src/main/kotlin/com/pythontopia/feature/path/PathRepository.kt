package com.pythontopia.feature.path

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 学习路径 Retrofit API。
 *
 *  - [fetchPath]      GET /api/learner/path?userId=&packId=          -> [PathDto]，404 表示该用户尚未规划路径
 *  - [markNodeStarted] POST /api/learner/path/{pathId}/nodes/{nodeId}/start -> 返回更新后的完整 [PathDto]
 *  - [markNodeDone]    POST /api/learner/path/{pathId}/nodes/{nodeId}/done  -> 返回更新后的完整 [PathDto]
 *
 * Pythontopia 默认 packId=43（`Python语言基础`），调用方一般可使用 [PathRepository.DEFAULT_PACK_ID]。
 */
interface LearningPathApi {

    @GET("api/learner/path")
    suspend fun fetchPath(
        @Query("userId") userId: Long,
        @Query("packId") packId: Long,
    ): PathDto

    @POST("api/learner/path/{pathId}/nodes/{nodeId}/start")
    suspend fun markNodeStarted(
        @Path("pathId") pathId: String,
        @Path("nodeId") nodeId: String,
    ): PathDto

    @POST("api/learner/path/{pathId}/nodes/{nodeId}/done")
    suspend fun markNodeDone(
        @Path("pathId") pathId: String,
        @Path("nodeId") nodeId: String,
    ): PathDto
}

/**
 * 仓库层：把 [LearningPathApi] 包装为 demo 友好的接口。
 *
 *  - [fetchPath]   网络异常时 fallback 到 [DEMO_FALLBACK]（4 节点链式样例），保证 UI 不空屏。
 *  - [markNodeStarted] / [markNodeDone] 网络异常时本地推进节点状态，返回更新后的快照；
 *    真实联调时若接口 200 则透传后端结果，避免本地状态机与服务端 mastery 偏差。
 *
 * **重要**：不在本地"伪造完成"教育性节点 —— `markNodeDone` 仅做 UI 占位推进；
 * 真实进度由后端 evidence（submission_id / page_read_id 等）入库后回填，遵循
 * `backend/QuestService` 强校验约束。
 */
@Singleton
class PathRepository @Inject constructor(
    private val api: LearningPathApi,
) {

    /**
     * 拉取活跃路径快照。
     *
     * @param userId 学习者 ID；调用方通常从 `SessionRepository.user.id` 取。
     * @param packId 课程包 ID；默认 [DEFAULT_PACK_ID]。
     */
    suspend fun fetchPath(userId: Long, packId: Long = DEFAULT_PACK_ID): PathDto =
        runCatching { api.fetchPath(userId, packId) }
            .getOrElse { DEMO_FALLBACK.copy(userId = userId, packId = packId) }

    /**
     * 标记节点进入"进行中"。后端会把 status: UNLOCKED -> IN_PROGRESS 并广播 LearningEvent。
     * 离线时本地推进，保证 demo 顺畅；联调时 server 结果覆盖。
     */
    suspend fun markNodeStarted(pathId: String, nodeId: String): PathDto =
        runCatching { api.markNodeStarted(pathId, nodeId) }
            .getOrElse { localTransition(pathId, nodeId, PathNodeStatus.IN_PROGRESS) }

    /**
     * 标记节点"完成"。后端会校验 evidence 是否合法（submission 通过 / 资源已读完等），
     * 然后把 status: IN_PROGRESS -> DONE 并解锁所有 nextIds 节点（LOCKED -> UNLOCKED）。
     * 离线时本地推进，仅作 UI 占位。
     */
    suspend fun markNodeDone(pathId: String, nodeId: String): PathDto =
        runCatching { api.markNodeDone(pathId, nodeId) }
            .getOrElse { localTransition(pathId, nodeId, PathNodeStatus.DONE) }

    private fun localTransition(
        pathId: String,
        nodeId: String,
        newStatus: PathNodeStatus,
    ): PathDto {
        val base = DEMO_FALLBACK
        val pivot = base.nodes.firstOrNull { it.id == nodeId }
            ?: return base.copy(pathId = pathId)
        val updated = base.nodes.map { node ->
            when {
                node.id == nodeId -> node.copy(status = newStatus)
                // DONE 时把 pivot 的 nextIds 从 LOCKED 提升为 UNLOCKED
                newStatus == PathNodeStatus.DONE &&
                    pivot.nextIds.contains(node.id) &&
                    node.status == PathNodeStatus.LOCKED ->
                    node.copy(status = PathNodeStatus.UNLOCKED)
                else -> node
            }
        }
        val nextCurrent = when (newStatus) {
            PathNodeStatus.IN_PROGRESS -> nodeId
            PathNodeStatus.DONE -> pivot.nextIds.firstOrNull()
            else -> base.currentNodeId
        }
        return base.copy(pathId = pathId, currentNodeId = nextCurrent, nodes = updated)
    }

    companion object {
        const val DEFAULT_PACK_ID: Long = 43L

        /**
         * 4 节点 demo 路径：READ -> PRACTICE -> QUEST -> DEBUG，覆盖 4 种 status / 4 种 type，
         * 用于 Beta+1 演示与离线场景。节点 ID 用 `n-NNN` 稳定格式。
         */
        internal val DEMO_FALLBACK: PathDto = PathDto(
            pathId = "demo-path-001",
            userId = -1L,
            packId = DEFAULT_PACK_ID,
            currentNodeId = "n-002",
            nodes = listOf(
                PathNodeDto(
                    id = "n-001",
                    type = PathNodeType.READ,
                    title = "input/print 概览",
                    kcCode = "KC-IO-01",
                    status = PathNodeStatus.DONE,
                    nextIds = listOf("n-002"),
                ),
                PathNodeDto(
                    id = "n-002",
                    type = PathNodeType.PRACTICE,
                    title = "你好 Pythontopia",
                    kcCode = "KC-IO-01",
                    status = PathNodeStatus.IN_PROGRESS,
                    nextIds = listOf("n-003"),
                ),
                PathNodeDto(
                    id = "n-003",
                    type = PathNodeType.QUEST,
                    title = "镇长任务：温度转换",
                    kcCode = "KC-EXPR-02",
                    status = PathNodeStatus.UNLOCKED,
                    nextIds = listOf("n-004"),
                ),
                PathNodeDto(
                    id = "n-004",
                    type = PathNodeType.DEBUG,
                    title = "矿洞：循环不收敛",
                    kcCode = "KC-LOOP-03",
                    status = PathNodeStatus.LOCKED,
                    nextIds = emptyList(),
                ),
            ),
        )
    }
}

/**
 * Hilt 模块：从 `:core:network` 注入 [Retrofit] 单例，create 出 [LearningPathApi]。
 *
 * 不在 `NetworkModule` 集中提供是因为 path 是 feature 模块，按"接口属于 feature"原则就近声明；
 * 同时把 Retrofit 实例复用，保证 baseUrl / AuthInterceptor 与其它 feature 一致。
 */
@Module
@InstallIn(SingletonComponent::class)
object PathNetworkModule {

    @Provides
    @Singleton
    fun provideLearningPathApi(retrofit: Retrofit): LearningPathApi =
        retrofit.create(LearningPathApi::class.java)
}
