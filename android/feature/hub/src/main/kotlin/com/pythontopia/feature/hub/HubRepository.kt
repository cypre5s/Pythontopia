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

/** Network repository for the native hub. It never fabricates a bootstrap snapshot. */
@Singleton
class HubRepository @Inject constructor(
    private val bootstrapApi: BootstrapApi,
    private val calendarApi: CalendarApi,
    private val calendarDao: CalendarDao,
) {

    suspend fun bootstrap(): GameBootstrapResponseDto = bootstrapApi.bootstrap()

    /**
     * 日历：优先网络（成功则写入 Room 缓存），失败回退最近一次缓存（离线/抖动仍有内容）。
     * 二者皆无返回 null（首启离线场景）。
     */
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
