package com.pythontopia.core.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.pythontopia.core.data.dto.world.CalendarTodayResponse

/**
 * 日历缓存（单行）。离线/网络抖动时，hub HUD 仍能显示上次的季节/时段/天气，
 * 而不是空白或报错。是「离线缓存」模式的最小落地切片，其它 feature 可照此扩展。
 */
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

/** 纯映射（无 Android 依赖，可 JVM 单测）。 */
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
