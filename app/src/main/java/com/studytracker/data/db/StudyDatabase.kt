package com.studytracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.studytracker.data.db.dao.SessionDao
import com.studytracker.data.db.dao.SettingsDao
import com.studytracker.data.db.dao.SubjectDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [SessionEntity::class, SubjectEntity::class, SettingsEntity::class],
    version = 2,
    exportSchema = false
)
abstract class StudyDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun subjectDao(): SubjectDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        const val DATABASE_NAME = "study_tracker_db"

        fun createCallback(): Callback {
            return object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    val defaultSubjects = listOf(
                        Triple("Problems of Philosophy", "#4285F4", 1),
                        Triple("History of Philosophy", "#7B1FA2", 1),
                        Triple("Political Science", "#D32F2F", 1),
                        Triple("Psychology", "#00897B", 1),
                        Triple("Ethics", "#388E3C", 1),
                        Triple("History of Bangladesh", "#EF6C00", 1),
                        Triple("ICT", "#00ACC1", 1)
                    )
                    defaultSubjects.forEach { (name, color, isDefault) ->
                        db.execSQL(
                            "INSERT INTO subjects (name, colorHex, isDefault) VALUES (?, ?, ?)",
                            arrayOf(name, color, isDefault)
                        )
                    }
                    db.execSQL(
                        "INSERT OR REPLACE INTO settings (id, dailyGoalMinutes, reminderEnabled, reminderHour, reminderMinute, darkModeOption) VALUES (1, 120, 0, 9, 0, 'SYSTEM')"
                    )
                }
            }
        }
    }
}
