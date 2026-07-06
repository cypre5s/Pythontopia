package com.pythontopia.feature.fields

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.Retrofit
import retrofit2.http.GET

/** packId=43 共 65 KC 网格；[waterKc]/[harvestKc] 不本地写 mastery，须走后端 evidence。 */
@Singleton
class FieldsRepository @Inject constructor(
    private val api: FieldsApi,
) {

    /** 拉取 65 KC 网格；后端缺项由 [KcSeedCatalog] 补 0，不伪造 mastery。 */
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

    /** 浇水仅为视觉隐喻；正式 mastery 须通过资源/练习 evidence 更新。 */
    suspend fun waterKc(kcCode: String): KcTileState {
        requireSeed(kcCode)
        throw UnsupportedOperationException("请通过阅读资源或完成练习触发后端 mastery 更新")
    }

    /** 收获须对应后端 evidence，不做本地状态推进。 */
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

/** `GET /api/learner/mastery` → `List<KcMasteryDto>`。 */
interface FieldsApi {

    @GET("api/learner/mastery")
    suspend fun fetchMastery(): List<KcMasteryDto>
}

/** 从共享 [Retrofit] 派生 [FieldsApi]，绑定留在本 feature 模块内。 */
@Module
@InstallIn(SingletonComponent::class)
object FieldsNetworkModule {

    @Provides
    @Singleton
    fun provideFieldsApi(retrofit: Retrofit): FieldsApi =
        retrofit.create(FieldsApi::class.java)
}
