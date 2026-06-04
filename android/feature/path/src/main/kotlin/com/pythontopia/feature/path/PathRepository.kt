package com.pythontopia.feature.path

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.POST

interface LearningPathApi {
    @GET("api/learner/path/active")
    suspend fun active(): LearningPathWireDto

    @POST("api/learner/path/plan")
    suspend fun plan(): PlanPathResponseWireDto
}

/**
 * 学习路径仓库。
 *
 * 后端以当前登录用户为边界：Android 不再上传 demo userId，也不在本地伪造完成进度。
 * 当用户尚未生成路径时，仓库会调用 `/api/learner/path/plan` 生成一次真实路径。
 */
@Singleton
class PathRepository @Inject constructor(
    private val api: LearningPathApi,
) {

    @Suppress("UnusedParameter")
    suspend fun fetchPath(userId: Long, packId: Long = DEFAULT_PACK_ID): PathDto {
        require(packId == DEFAULT_PACK_ID) { "Only Alethicode pack $DEFAULT_PACK_ID is supported" }
        return try {
            api.active().toPathDto()
        } catch (exception: HttpException) {
            if (exception.code() == HTTP_NOT_FOUND) {
                api.plan().path.toPathDto()
            } else {
                throw exception
            }
        }
    }

    suspend fun markNodeStarted(pathId: String, nodeId: String): PathDto =
        throw UnsupportedOperationException(
            "Android 不再本地推进学习路径节点 path=$pathId node=$nodeId；请进入对应练习/资源并由后端 evidence 更新路径。",
        )

    suspend fun markNodeDone(pathId: String, nodeId: String): PathDto =
        throw UnsupportedOperationException(
            "Android 不再本地完成学习路径节点 path=$pathId node=$nodeId；完成必须来自 submission/resource/page_read evidence。",
        )

    companion object {
        const val DEFAULT_PACK_ID: Long = 43L
        private const val HTTP_NOT_FOUND = 404
    }
}

@Module
@InstallIn(SingletonComponent::class)
object PathNetworkModule {

    @Provides
    @Singleton
    fun provideLearningPathApi(retrofit: Retrofit): LearningPathApi =
        retrofit.create(LearningPathApi::class.java)
}

@Serializable
data class PlanPathResponseWireDto(
    val path: LearningPathWireDto,
)

@Serializable
data class LearningPathWireDto(
    val id: Long,
    val userId: Long,
    val packId: Long,
    val nodes: List<LearningPathNodeWireDto> = emptyList(),
) {
    fun toPathDto(): PathDto {
        val ordered = nodes.sortedWith(compareBy<LearningPathNodeWireDto> { it.dayIndex }.thenBy { it.orderInDay })
        val mapped = ordered.mapIndexed { index, node ->
            val nextId = ordered.getOrNull(index + 1)?.id?.toString()
            node.toPathNodeDto(
                status = if (index == 0) PathNodeStatus.IN_PROGRESS else PathNodeStatus.UNLOCKED,
                nextIds = nextId?.let(::listOf).orEmpty(),
            )
        }
        return PathDto(
            pathId = id.toString(),
            userId = userId,
            packId = packId,
            currentNodeId = mapped.firstOrNull()?.id,
            nodes = mapped,
        )
    }
}

@Serializable
data class LearningPathNodeWireDto(
    val id: Long,
    val dayIndex: Int,
    val orderInDay: Int,
    val kcCode: String? = null,
    val nodeType: String,
    val reasonSummary: String? = null,
    val evidenceSummary: String? = null,
    val suggestedProblemId: Long? = null,
) {
    fun toPathNodeDto(status: PathNodeStatus, nextIds: List<String>): PathNodeDto =
        PathNodeDto(
            id = id.toString(),
            type = nodeType.toPathNodeType(),
            title = reasonSummary?.takeIf { it.isNotBlank() }
                ?: evidenceSummary?.takeIf { it.isNotBlank() }
                ?: suggestedProblemId?.let { "练习题 #$it" }
                ?: kcCode?.let { "学习 $it" }
                ?: "学习节点 #$id",
            kcCode = kcCode,
            status = status,
            nextIds = nextIds,
        )
}

private fun String.toPathNodeType(): PathNodeType = when (this) {
    "SOLVE_PROBLEM" -> PathNodeType.PRACTICE
    "FIX_BUG" -> PathNodeType.DEBUG
    "PROFILE_UPDATE" -> PathNodeType.QUEST
    "READ_RESOURCE",
    "PLANT_KC" -> PathNodeType.READ
    else -> PathNodeType.READ
}
