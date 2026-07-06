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
 * 班级网络仓库：收口 `/api/classrooms*`、`/api/classroom/quests*`、
 * 教师 dashboard 与 `POST /api/quests/{id}/complete`。
 * [myClassrooms] 失败回空列表；[join]/[submitEvidence] 失败上抛。
 */
@Singleton
class ClassroomRepository @Inject internal constructor(
    private val api: ClassroomApi,
) {

    /** `GET /api/classrooms/mine`；异常时回空列表。 */
    suspend fun myClassrooms(): List<Classroom> =
        runCatching { api.myClassrooms() }.getOrDefault(emptyList())

    /** `POST /api/classrooms/join`；失败上抛供 ViewModel 展示错误。 */
    suspend fun join(joinCode: String): Classroom = api.join(JoinRequest(joinCode))

    /**
     * 班级任务；教师走 `/api/teacher/.../quests`，学生走 `/api/classroom/quests/mine` 后本地按 [classroomId] 过滤。
     */
    suspend fun quests(classroomId: Long, asTeacher: Boolean = false): List<ClassroomQuest> =
        if (asTeacher) api.teacherQuests(classroomId)
        else api.myQuests().filter { it.classroomId == classroomId }

    /**
     * 提交 evidence；[questId] 须为 [ClassroomQuest.studentQuestId]（个人 quest 表），非 [ClassroomQuest.id]。
     */
    suspend fun submitEvidence(questId: Long, evidence: QuestEvidence): ClassroomQuest =
        api.completeQuest(questId, evidence)

    /** 教师 KC 热力图；`GET .../dashboard/kc-heatmap`。 */
    suspend fun heatmap(classroomId: Long): List<KcHeatmapEntry> = api.kcHeatmap(classroomId)

    /** 教师高频误区；`GET .../dashboard/error-hotspots`。 */
    suspend fun errorHotspots(classroomId: Long): List<ErrorHotspot> = api.errorHotspots(classroomId)
}

/** 班级 Retrofit 接口；`classrooms`（复数）与 `classroom`（单数）端点勿混用。 */
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

/** `POST /api/classrooms/join` 请求体。 */
@kotlinx.serialization.Serializable
internal data class JoinRequest(val joinCode: String)

/** 从共享 [Retrofit] 派生 [ClassroomApi]。 */
@Module
@InstallIn(SingletonComponent::class)
internal object ClassroomNetworkModule {

    @Provides
    @Singleton
    fun provideClassroomApi(retrofit: Retrofit): ClassroomApi =
        retrofit.create(ClassroomApi::class.java)
}
