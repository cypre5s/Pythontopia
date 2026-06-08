package com.pythontopia.core.network.api

import com.pythontopia.core.data.dto.world.CalendarTodayResponse
import com.pythontopia.core.data.dto.world.InventoryItemDto
import retrofit2.http.GET

/** 游戏日历 [today] 与背包 [inventory] 查询。 */
interface CalendarApi {

    @GET("api/world/calendar/today")
    suspend fun today(): CalendarTodayResponse

    @GET("api/world/inventory")
    suspend fun inventory(): List<InventoryItemDto>
}
