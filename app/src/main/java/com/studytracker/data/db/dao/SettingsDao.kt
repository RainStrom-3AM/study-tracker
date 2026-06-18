package com.studytracker.data.db.dao

import androidx.room.*
import com.studytracker.data.db.SettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: SettingsEntity)

    @Query("SELECT * FROM settings WHERE id = 1")
    fun getSettings(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun getSettingsSync(): SettingsEntity?

    @Query("UPDATE settings SET dailyGoalMinutes = :minutes WHERE id = 1")
    suspend fun updateDailyGoal(minutes: Int)

    @Query("UPDATE settings SET reminderEnabled = :enabled, reminderHour = :hour, reminderMinute = :minute WHERE id = 1")
    suspend fun updateReminder(enabled: Boolean, hour: Int, minute: Int)

    @Query("UPDATE settings SET darkModeOption = :option WHERE id = 1")
    suspend fun updateDarkMode(option: String)
}
