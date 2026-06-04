package com.pythontopia.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Pythontopia 本地缓存库。当前仅含日历缓存（最小离线切片）；新增可缓存实体时
 * 加进 `entities` 并升 `version`。`exportSchema = false`：演示期不导出 schema 文件，
 * 上架前应改 true 并入 `schemas/` 以支持可审计迁移。
 */
@Database(
    entities = [CalendarCacheEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class PytDatabase : RoomDatabase() {
    abstract fun calendarDao(): CalendarDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PytDatabase =
        Room.databaseBuilder(context, PytDatabase::class.java, "pythontopia.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideCalendarDao(database: PytDatabase): CalendarDao = database.calendarDao()
}
