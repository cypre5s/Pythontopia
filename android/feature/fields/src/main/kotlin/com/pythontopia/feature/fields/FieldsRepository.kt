package com.pythontopia.feature.fields

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.Retrofit
import retrofit2.http.GET

/**
 * Fields feature 的网络仓库：聚合 Alethicode `packId=43` 65 KC 的"庄稼网格"业务。
 *
 *  - [fetchKcGrid]：拉一次当前登录用户的 KC mastery 快照；后端未返回的 KC 以课程目录补 0。
 *  - [waterKc] / [harvestKc]：Android 不再本地加 mastery。掌握度只能由后端 evidence
 *    链路更新，例如资源阅读、练习通过、失败练习和 Bug 修复。
 */
@Singleton
class FieldsRepository @Inject constructor(
    private val api: FieldsApi,
) {

    /**
     * 拉取 65 KC 网格。
     *
     * 流程：
     *  1. 尝试 `GET /api/learner/mastery`；
     *  2. 若 API 返回不全（< 65 条），用 catalog 补齐缺失 KC 为 0，保证 grid 8×9 永远满 65 格；
     *  3. 网络/鉴权错误向上抛出，由 ViewModel 展示加载失败，不伪造业务数据。
     */
    suspend fun fetchKcGrid(): List<KcTileState> {
        val remote = api.fetchMastery()
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
     * 浇水是视觉隐喻，不是正式 mastery 写入端点。学生需要进入资源/练习完成 evidence。
     */
    suspend fun waterKc(kcCode: String): KcTileState {
        requireSeed(kcCode)
        throw UnsupportedOperationException("请通过阅读资源或完成练习触发后端 mastery 更新")
    }

    /**
     * 收获必须对应后端已记录的学习证据，Android 不做本地状态推进。
     */
    suspend fun harvestKc(kcCode: String): KcTileState {
        requireSeed(kcCode)
        throw UnsupportedOperationException("收获必须来自后端 evidence；请先完成对应资源或练习")
    }

    private fun seedTile(seed: KcSeed): KcTileState = KcTileState(
        kcCode = seed.kcCode,
        title = seed.title,
        chapterOrder = seed.chapterOrder,
        mastery = 0.0,
        status = KcStatus.SEED,
    )

    private fun requireSeed(kcCode: String): KcSeed =
        KcSeedCatalog.byCode(kcCode)
            ?: error("Unknown KC code: $kcCode (must be one of Alethicode pack 43 65 KC)")

    private companion object {
        const val MAX_CHAPTER: Int = 7
    }
}

/**
 * Retrofit 接口：本应迁移到 `:core:network/api/`，本次 R-8 在 feature 内自洽实现，
 * 待 R-13 OpenAPI Generator 接入后由生成器接管。
 *
 * 后端契约：`GET /api/learner/mastery` → `List<KcMasteryDto>`。
 */
interface FieldsApi {

    @GET("api/learner/mastery")
    suspend fun fetchMastery(): List<KcMasteryDto>
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
