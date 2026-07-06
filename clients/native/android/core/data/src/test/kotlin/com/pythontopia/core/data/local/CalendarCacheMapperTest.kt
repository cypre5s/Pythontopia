package com.pythontopia.core.data.local

import com.pythontopia.core.data.dto.world.CalendarTodayResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CalendarCacheMapperTest {

    @Test
    fun `dto to entity to dto round trips losslessly`() {
        val dto = CalendarTodayResponse(
            dayIndex = 3,
            monthIndex = 1,
            weekday = "周三",
            season = "春",
            timeOfDay = "上午",
            weather = "晴",
            inGameMinutes = 540,
        )

        val restored = dto.toCacheEntity().toResponse()

        assertEquals(dto, restored)
    }

    @Test
    fun `cache entity uses singleton primary key`() {
        val entity = CalendarTodayResponse(0, 0, "周一", "夏", "夜晚", "雨", 0).toCacheEntity()
        assertEquals(CalendarCacheEntity.SINGLETON_ID, entity.id)
    }
}
