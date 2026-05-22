package com.pythontopia.feature.hub

import com.pythontopia.core.data.dto.world.CalendarTodayResponse
import com.pythontopia.core.data.dto.world.GameBootstrapResponseDto
import com.pythontopia.core.data.dto.world.PlayerStateDto
import com.pythontopia.core.data.dto.world.PlayerStatePatch
import com.pythontopia.core.network.api.BootstrapApi
import com.pythontopia.core.network.api.CalendarApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hub demo 的网络仓库：聚合 R-12 后端的 bootstrap / calendar / player 三个端点。
 *
 * - [bootstrap]：进入 hub 前一次拉齐 player + world + npc state。
 * - [today]：渲染 HUD 时拉取季节 / 天气 / 时段。
 * - [reportMove]：玩家每走一格上报后端，让其它客户端能看到。
 *
 * 与 [DialogRepository] 一致，所有方法都对网络异常做 fallback：bootstrap 失败时返回
 * 一个 demo 默认快照，让玩家仍能体验单机版本。
 */
@Singleton
class HubRepository @Inject constructor(
    private val bootstrapApi: BootstrapApi,
    private val calendarApi: CalendarApi,
) {

    suspend fun bootstrap(): GameBootstrapResponseDto =
        runCatching { bootstrapApi.bootstrap() }.getOrElse { DEMO_FALLBACK }

    suspend fun today(): CalendarTodayResponse? =
        runCatching { calendarApi.today() }.getOrNull()

    suspend fun reportMove(tileX: Int, tileY: Int, direction: String): PlayerStateDto? =
        runCatching {
            bootstrapApi.updatePlayer(PlayerStatePatch(tileX, tileY, direction))
        }.getOrNull()

    companion object {
        private val DEMO_FALLBACK: GameBootstrapResponseDto = GameBootstrapResponseDto(
            world = com.pythontopia.core.data.dto.world.WorldSnapshotDto(
                worldSessionId = -1L,
                dayIndex = 1,
                monthIndex = 1,
                weekday = "MONDAY",
                season = "SPRING",
                timeOfDay = "MORNING",
                weather = "CLEAR",
                inGameMinutes = 480,
            ),
            player = PlayerStateDto(
                tileX = 25,
                tileY = 20,
                direction = "DOWN",
                mapKey = "town_hub",
            ),
            inventory = emptyList(),
            currency = null,
            npcStates = emptyList(),
        )
    }
}
