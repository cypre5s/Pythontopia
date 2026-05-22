package com.pythontopia.core.network.api

import com.pythontopia.core.data.dto.world.GameBootstrapResponseDto
import com.pythontopia.core.data.dto.world.PlayerStateDto
import com.pythontopia.core.data.dto.world.PlayerStatePatch
import com.pythontopia.core.data.dto.world.WorldSnapshotDto
import retrofit2.http.Body
import retrofit2.http.PATCH
import retrofit2.http.POST

/**
 * R-12 新增端点：单次聚合 bootstrap + 玩家移动上报 + 一夜推进。
 *
 * 不属于这里：
 *  - `/api/world/npcs/...` 见 [NpcDialogApi]
 *  - `/api/world/calendar/...` 见 [CalendarApi]
 */
interface BootstrapApi {

    @POST("api/game/bootstrap")
    suspend fun bootstrap(): GameBootstrapResponseDto

    @PATCH("api/world/player")
    suspend fun updatePlayer(@Body patch: PlayerStatePatch): PlayerStateDto

    @POST("api/world/sleep")
    suspend fun sleep(): WorldSnapshotDto
}
