package com.pythontopia.feature.fields

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
 * Fields feature 的网络仓库：聚合 Alethicode `packId=43` 65 KC 的"庄稼网格"业务。
 *
 *  - [fetchKcGrid]：拉一次完整 65 KC 网格；网络失败时 fall back 到本地 [KcSeedCatalog]，
 *    保证 demo 未登录也能看到完整田地（mastery 全 0.0 → 全 SEED 状态）。
 *  - [waterKc]：触发"浇水"业务事件。等价于 docs/gameplay/economy-system.md §2.2 中
 *    `tool_watering_can_basic` 工具的使用，后端会按 `+0.05` 推高 mastery。本仓库优先
 *    走真实 API；离线降级时本地 +0.05 立即反馈，让玩家依旧能感觉到点击反馈。
 *  - [harvestKc]：作物 mastery≥0.8 时显式收获，状态切到 HARVESTED；离线降级时
 *    本地把 mastery 锁到 1.0 + 状态 HARVESTED，避免 UI 卡在 MATURE。
 *
 * 与 [com.pythontopia.feature.hub.HubRepository]、[com.pythontopia.feature.dialog.DialogRepository]
 * 一致，所有方法对网络异常 `runCatching { ... }.getOrElse { fallback }` 静默降级，
 * Beta demo 不引入网络断流报错 Toast，避免影响首发体验。
 */
@Singleton
class FieldsRepository @Inject constructor(
    private val api: FieldsApi,
) {

    /**
     * 拉取 65 KC 网格。
     *
     * 流程：
     *  1. 尝试 `GET /api/learner/mastery/kc?packId=43`；
     *  2. 若失败，返回 [KcSeedCatalog.entries] 生成的全 SEED 网格；
     *  3. 若 API 返回不全（< 65 条），用 catalog 补齐缺失 KC，保证 grid 8×9 永远满 65 格。
     */
    suspend fun fetchKcGrid(): List<KcTileState> {
        val remote = runCatching { api.fetchMasteryKc(packId = ALETHICODE_PACK_ID) }
            .getOrElse { emptyList() }
        val byCode = remote.associateBy { it.kcCode }
        return KcSeedCatalog.entries.map { seed ->
            val dto = byCode[seed.kcCode]
            if (dto != null) {
                dto.toTileState().copy(
                    title = seed.title.takeIf { dto.title.isBlank() } ?: dto.title,
                    chapterOrder = seed.chapterOrder.takeIf { dto.chapterOrder !in 1..MAX_CHAPTER }
                        ?: dto.chapterOrder,
                )
            } else {
                seedTile(seed)
            }
        }
    }

    /**
     * 浇水：本地 mastery 推高 [WATER_DELTA]，封顶 1.0；API 成功时以服务端值为准。
     */
    suspend fun waterKc(kcCode: String): KcTileState {
        val seed = requireSeed(kcCode)
        val updated = runCatching { api.water(kcCode) }
            .getOrElse {
                KcMasteryDto(
                    kcCode = kcCode,
                    title = seed.title,
                    mastery = WATER_DELTA,
                    chapterOrder = seed.chapterOrder,
                    status = null,
                )
            }
        return updated.toTileState().withSeedFallback(seed)
    }

    /**
     * 收获：mastery 锁到 1.0，status 强制 HARVESTED；API 成功时尊重服务端响应。
     * 调用方应自行确保 [KcTileState.status] == [KcStatus.MATURE]，否则视为无效操作。
     */
    suspend fun harvestKc(kcCode: String): KcTileState {
        val seed = requireSeed(kcCode)
        val updated = runCatching { api.harvest(kcCode) }
            .getOrElse {
                KcMasteryDto(
                    kcCode = kcCode,
                    title = seed.title,
                    mastery = 1.0,
                    chapterOrder = seed.chapterOrder,
                    status = KcStatus.HARVESTED.name,
                )
            }
        return updated.toTileState().withSeedFallback(seed).let {
            if (it.status == KcStatus.HARVESTED) it else it.copy(status = KcStatus.HARVESTED)
        }
    }

    private fun seedTile(seed: KcSeed): KcTileState = KcTileState(
        kcCode = seed.kcCode,
        title = seed.title,
        chapterOrder = seed.chapterOrder,
        mastery = 0.0,
        status = KcStatus.SEED,
    )

    private fun KcTileState.withSeedFallback(seed: KcSeed): KcTileState = copy(
        title = title.ifBlank { seed.title },
        chapterOrder = if (chapterOrder in 1..MAX_CHAPTER) chapterOrder else seed.chapterOrder,
    )

    private fun requireSeed(kcCode: String): KcSeed =
        KcSeedCatalog.byCode(kcCode)
            ?: error("Unknown KC code: $kcCode (must be one of Alethicode pack 43 65 KC)")

    private companion object {
        const val ALETHICODE_PACK_ID: Int = 43
        const val WATER_DELTA: Double = 0.05
        const val MAX_CHAPTER: Int = 7
    }
}

/**
 * Retrofit 接口：本应迁移到 `:core:network/api/`，本次 R-8 在 feature 内自洽实现，
 * 待 R-13 OpenAPI Generator 接入后由生成器接管。
 *
 * 后端契约（与 `docs/android/01-feature-port-checklist.md` §4.4 一致）：
 *  - `GET /api/learner/mastery/kc?packId=43` → `List<KcMasteryDto>`；
 *  - `POST /api/learner/mastery/kc/{kcCode}/water` → 单条 [KcMasteryDto]（浇水后新 mastery）；
 *  - `POST /api/learner/mastery/kc/{kcCode}/harvest` → 单条 [KcMasteryDto]（status=HARVESTED）。
 */
interface FieldsApi {

    @GET("api/learner/mastery/kc")
    suspend fun fetchMasteryKc(@Query("packId") packId: Int): List<KcMasteryDto>

    @POST("api/learner/mastery/kc/{kcCode}/water")
    suspend fun water(@Path("kcCode") kcCode: String): KcMasteryDto

    @POST("api/learner/mastery/kc/{kcCode}/harvest")
    suspend fun harvest(@Path("kcCode") kcCode: String): KcMasteryDto
}

/**
 * Hilt 注入：复用 `:core:network/NetworkModule` 已注册的 [Retrofit] 单例，
 * 仅在 fields feature 本地把它转成 [FieldsApi]。
 *
 * 这避免污染 [com.pythontopia.core.network.NetworkModule]，符合 ADR-001 §6
 * "feature 自治"原则：API 仅在使用它的 feature 模块内被绑定。
 */
@Module
@InstallIn(SingletonComponent::class)
object FieldsNetworkModule {

    @Provides
    @Singleton
    fun provideFieldsApi(retrofit: Retrofit): FieldsApi =
        retrofit.create(FieldsApi::class.java)
}
