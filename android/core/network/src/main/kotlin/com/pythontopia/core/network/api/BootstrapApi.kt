package com.pythontopia.core.network.api

import com.pythontopia.core.data.dto.world.GameBootstrapResponseDto
import com.pythontopia.core.data.dto.world.PlayerStateDto
import com.pythontopia.core.data.dto.world.PlayerStatePatch
import com.pythontopia.core.data.dto.world.WorldSnapshotDto
import retrofit2.http.Body
import retrofit2.http.PATCH
import retrofit2.http.POST

/** 世界 bootstrap 与玩家状态 API：[bootstrap] 聚合初始化，[updatePlayer] 上报移动，[sleep] 推进一夜；NPC 见 [NpcDialogApi]，日历见 [CalendarApi]。 */
interface BootstrapApi {

    @POST("api/game/bootstrap")
    suspend fun bootstrap(): GameBootstrapResponseDto

    @PATCH("api/world/player")
    suspend fun updatePlayer(@Body patch: PlayerStatePatch): PlayerStateDto

    @POST("api/world/sleep")
    suspend fun sleep(): WorldSnapshotDto
}
