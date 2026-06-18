package com.studytracker.data.repository

import com.studytracker.data.db.SettingsEntity
import com.studytracker.data.db.SessionEntity
import com.studytracker.data.db.SubjectEntity
import com.studytracker.data.db.dao.SessionDao
import com.studytracker.data.db.dao.SettingsDao
import com.studytracker.data.db.dao.SubjectDao
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StudyRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val subjectDao: SubjectDao,
    private val settingsDao: SettingsDao
) {
    suspend fun insertSession(session: SessionEntity): Long = sessionDao.insert(session)

    suspend fun deleteSession(session: SessionEntity) = sessionDao.delete(session)

    suspend fun deleteSessionById(id: Long) = sessionDao.deleteById(id)

    fun getAllSessions(): Flow<List<SessionEntity>> = sessionDao.getAllSessions()

    fun getSessionsBetween(start: Long, end: Long): Flow<List<SessionEntity>> =
        sessionDao.getSessionsBetween(start, end)

    suspend fun getSessionsBetweenSync(start: Long, end: Long): List<SessionEntity> =
        sessionDao.getSessionsBetweenSync(start, end)

    fun getTodaySessions(): Flow<List<SessionEntity>> {
        return sessionDao.getSessionsFromToday(getStartOfDay())
    }

    fun getTodayTotalDuration(): Flow<Int> = sessionDao.getTotalDurationFromToday(getStartOfDay())

    fun getTotalDurationBetween(start: Long, end: Long): Flow<Int> =
        sessionDao.getTotalDurationBetween(start, end)

    suspend fun getTotalDurationBetweenSync(start: Long, end: Long): Int =
        sessionDao.getTotalDurationBetweenSync(start, end)

    fun getSessionsBySubject(subjectId: Long): Flow<List<SessionEntity>> =
        sessionDao.getSessionsBySubject(subjectId)

    fun getSessionCount(): Flow<Int> = sessionDao.getSessionCount()

    suspend fun getDistinctStudyDatesSince(since: Long): List<String> =
        sessionDao.getDistinctStudyDatesSince(since)

    fun getAllSubjects(): Flow<List<SubjectEntity>> = subjectDao.getAllSubjects()

    suspend fun getAllSubjectsSync(): List<SubjectEntity> = subjectDao.getAllSubjectsSync()

    suspend fun getSubjectById(id: Long): SubjectEntity? = subjectDao.getSubjectById(id)

    fun getSubjectByIdFlow(id: Long): Flow<SubjectEntity?> = subjectDao.getSubjectByIdFlow(id)

    suspend fun insertSubject(subject: SubjectEntity): Long = subjectDao.insert(subject)

    suspend fun deleteSubject(subject: SubjectEntity) = subjectDao.delete(subject)

    suspend fun deleteSubjectById(id: Long) = subjectDao.deleteById(id)

    suspend fun getSubjectCount(): Int = subjectDao.getSubjectCount()

    fun getSettings(): Flow<SettingsEntity?> = settingsDao.getSettings()

    suspend fun getSettingsSync(): SettingsEntity? = settingsDao.getSettingsSync()

    suspend fun updateSettings(settings: SettingsEntity) = settingsDao.upsert(settings)

    suspend fun updateDailyGoal(minutes: Int) = settingsDao.updateDailyGoal(minutes)

    suspend fun updateReminder(enabled: Boolean, hour: Int, minute: Int) =
        settingsDao.updateReminder(enabled, hour, minute)

    suspend fun updateDarkMode(option: String) = settingsDao.updateDarkMode(option)

    fun getStartOfDay(): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    fun getStartOfWeek(): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    fun getStartOfMonth(): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }
}
