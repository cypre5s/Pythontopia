package com.pythontopia.core.network.api

import com.pythontopia.core.data.dto.world.CalendarTodayResponse
import retrofit2.http.GET

/** 游戏日历查询。背包/货币/商店在 R-14 世界收缩后不存在，无对应端点。 */
interface CalendarApi {

    @GET("api/world/calendar/today")
    suspend fun today(): CalendarTodayResponse
}
