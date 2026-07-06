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

/** Room 本地库；当前仅 calendar_cache。扩展实体时加入 entities 并升 version；exportSchema=false。 */
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
