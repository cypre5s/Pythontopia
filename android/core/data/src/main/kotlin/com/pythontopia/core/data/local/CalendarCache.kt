package com.pythontopia.core.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.pythontopia.core.data.dto.world.CalendarTodayResponse

/** 日历单行缓存；离线或网络抖动时 hub HUD 显示上次季节/时段/天气。 */
@Entity(tableName = "calendar_cache")
data class CalendarCacheEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val dayIndex: Int,
    val monthIndex: Int,
    val weekday: String,
    val season: String,
    val timeOfDay: String,
    val weather: String,
    val inGameMinutes: Int,
) {
    companion object {
        const val SINGLETON_ID: Int = 0
    }
}

@Dao
interface CalendarDao {
    @Upsert
    suspend fun upsert(entity: CalendarCacheEntity)

    @Query("SELECT * FROM calendar_cache WHERE id = 0 LIMIT 1")
    suspend fun latest(): CalendarCacheEntity?
}

/** DTO↔Entity 映射，无 Android 依赖（可 JVM 单测）。 */
fun CalendarTodayResponse.toCacheEntity(): CalendarCacheEntity = CalendarCacheEntity(
    dayIndex = dayIndex,
    monthIndex = monthIndex,
    weekday = weekday,
    season = season,
    timeOfDay = timeOfDay,
    weather = weather,
    inGameMinutes = inGameMinutes,
)

fun CalendarCacheEntity.toResponse(): CalendarTodayResponse = CalendarTodayResponse(
    dayIndex = dayIndex,
    monthIndex = monthIndex,
    weekday = weekday,
    season = season,
    timeOfDay = timeOfDay,
    weather = weather,
    inGameMinutes = inGameMinutes,
)
