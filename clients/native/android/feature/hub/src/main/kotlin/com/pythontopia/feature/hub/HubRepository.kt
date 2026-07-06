package com.pythontopia.feature.hub

import com.pythontopia.core.data.dto.world.CalendarTodayResponse
import com.pythontopia.core.data.dto.world.GameBootstrapResponseDto
import com.pythontopia.core.data.dto.world.PlayerStatePatch
import com.pythontopia.core.data.local.CalendarDao
import com.pythontopia.core.data.local.toCacheEntity
import com.pythontopia.core.data.local.toResponse
import com.pythontopia.core.network.api.BootstrapApi
import com.pythontopia.core.network.api.CalendarApi
import javax.inject.Inject
import javax.inject.Singleton

/** 中央小镇数据仓库；bootstrap 不伪造快照。 */
@Singleton
class HubRepository @Inject constructor(
    private val bootstrapApi: BootstrapApi,
    private val calendarApi: CalendarApi,
    private val calendarDao: CalendarDao,
) {

    suspend fun bootstrap(): GameBootstrapResponseDto = bootstrapApi.bootstrap()

    /** 日历：网络优先并写 Room 缓存，失败回退缓存，皆无则 null。 */
    suspend fun today(): CalendarTodayResponse? {
        val fromNetwork = runCatching { calendarApi.today() }.getOrNull()
        if (fromNetwork != null) {
            runCatching { calendarDao.upsert(fromNetwork.toCacheEntity()) }
            return fromNetwork
        }
        return runCatching { calendarDao.latest()?.toResponse() }.getOrNull()
    }

    suspend fun reportMove(tileX: Int, tileY: Int, direction: String) =
        runCatching {
            bootstrapApi.updatePlayer(PlayerStatePatch(tileX, tileY, direction))
        }.getOrNull()
}
