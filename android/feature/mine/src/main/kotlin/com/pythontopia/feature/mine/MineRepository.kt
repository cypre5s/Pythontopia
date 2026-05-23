package com.pythontopia.feature.mine

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Bug 矿洞 Retrofit 接口。
 *
 * 端点路径与 R-12 后端 `BugMineController` 对齐：
 * - `POST /api/mine/sessions`             开新副本（可携带触发的 submissionId）
 * - `GET  /api/mine/sessions/{id}/cards`  拉取本次会话的 5 张卡（已含主卡 + 候补）
 * - `POST /api/mine/cards/{id}/attempt`   单卡判题，body 是玩家代码
 * - `POST /api/mine/sessions/{id}/next`   推进下一张卡；无更多卡返回 404 → null
 *
 * 设为 `internal` 是因为外层调用方走 [MineRepository] 包装，不应该绕过仓库直接调 API；
 * Hilt 通过 [MineApiModule] 注入。
 */
internal interface MineApi {

    @POST("api/mine/sessions")
    suspend fun startSession(@Body request: MineStartRequest): MineSession

    @GET("api/mine/sessions/{id}/cards")
    suspend fun cards(@Path("id") sessionId: Long): List<MineCard>

    @POST("api/mine/cards/{id}/attempt")
    suspend fun attempt(
        @Path("id") cardId: Long,
        @Body request: MineAttemptRequest,
    ): MineAttempt

    @POST("api/mine/sessions/{id}/next")
    suspend fun next(@Path("id") sessionId: Long): MineCard
}

/**
 * Hilt 把 :core:network 提供的 [Retrofit] 单例转成 [MineApi]。
 *
 * 之所以放在 :feature:mine 而不是 :core:network/NetworkModule，是为了让 mine feature
 * 保持自包含，不必每加一个 feature 就动 :core:network；缺点是 mine 模块需要直接依赖
 * `retrofit2.Retrofit`，已在 build.gradle.kts 中 implementation。
 */
@Module
@InstallIn(SingletonComponent::class)
internal object MineApiModule {

    @Provides
    @Singleton
    fun provideMineApi(retrofit: Retrofit): MineApi = retrofit.create(MineApi::class.java)
}

/**
 * Bug 矿洞副本仓库。
 *
 * 业务定位（对齐 `docs/gameplay/r12-deepen/04-bugmine-puzzle-deck.md` §8.3 状态机）：
 *  1. [startSession] 从 ENTRY 进入；可携带 `sourceSubmissionId` 把"刚失败的提交"作为
 *     选卡上下文，让后端 `BugMineCardSelectorService` 推 3 卡套餐。
 *  2. [cards] 拉取本次会话的全部卡（首屏 ≥ 1 张，后端可补送候补）。
 *  3. [attempt] 单卡判题；服务端为权威，Pyodide 仅前端预览。
 *  4. [next] 当前卡通过后请求下一张；无更多卡返回 null，调用方可结束会话。
 *
 * 错误处理策略：
 *  - 网络异常 / HTTP 5xx 由 ViewModel 捕获并展示 errorMessage，不在仓库降级，
 *    因为副本是「玩家正主动尝试」的强交互场景，假数据会让玩家误以为通关。
 *  - [next] 单独包了 404 → null 的语义，因为「副本结束」本身就是合法状态而非错误。
 *
 * 关于 OFFLINE FALLBACK：与 [com.pythontopia.feature.hub.HubRepository] / DialogRepository 的演示
 * fallback 不同，副本判题不允许 fallback。fallback 出来的 "通过" 会触发后端 quest_evidence
 * 写入，违反 AGENTS.md「Pyodide 不能当正式判题结果」原则。
 */
@Singleton
class MineRepository @Inject internal constructor(
    private val api: MineApi,
) {

    /**
     * 开新副本。
     *
     * @param srcSubmissionId 触发副本的失败提交 ID；纯主动入口（hub 直接进入 / 教师任务）传 null。
     */
    suspend fun startSession(srcSubmissionId: Long?): MineSession =
        api.startSession(MineStartRequest(sourceSubmissionId = srcSubmissionId))

    /** 拉取本次会话所有可用卡片，按 layer 升序、同 layer 内按推荐权重排列。 */
    suspend fun cards(sessionId: Long): List<MineCard> = api.cards(sessionId)

    /**
     * 提交单卡的修复代码。
     *
     * @param cardId 当前卡 ID（来自 [MineCard.id]）。
     * @param code 玩家修改后的完整代码；后端会原样运行并比对 [MineCard.expectedOutput]。
     */
    suspend fun attempt(cardId: Long, code: String): MineAttempt =
        api.attempt(cardId, MineAttemptRequest(code = code))

    /**
     * 请求下一张卡。
     *
     * 后端 404 / 410 表示「副本已结束」是合法状态，捕获后返回 null，调用方据此进入完结态。
     * 其他错误（5xx / 网络）抛给 ViewModel，避免把网络问题误判成「副本结束」。
     */
    suspend fun next(sessionId: Long): MineCard? = try {
        api.next(sessionId)
    } catch (e: HttpException) {
        if (e.code() == HTTP_NOT_FOUND || e.code() == HTTP_GONE) null else throw e
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
        const val HTTP_GONE = 410
    }
}
