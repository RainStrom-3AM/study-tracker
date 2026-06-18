package com.studytracker.data.db.dao

import androidx.room.*
import com.studytracker.data.db.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity): Long

    @Delete
    suspend fun delete(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE startTime BETWEEN :start AND :end ORDER BY startTime DESC")
    fun getSessionsBetween(start: Long, end: Long): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE startTime BETWEEN :start AND :end ORDER BY startTime DESC")
    suspend fun getSessionsBetweenSync(start: Long, end: Long): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE startTime >= :startOfDay ORDER BY startTime DESC")
    fun getSessionsFromToday(startOfDay: Long): Flow<List<SessionEntity>>

    @Query("SELECT COALESCE(SUM(durationSeconds), 0) FROM sessions WHERE startTime BETWEEN :start AND :end")
    fun getTotalDurationBetween(start: Long, end: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(durationSeconds), 0) FROM sessions WHERE startTime BETWEEN :start AND :end")
    suspend fun getTotalDurationBetweenSync(start: Long, end: Long): Int

    @Query("SELECT COALESCE(SUM(durationSeconds), 0) FROM sessions WHERE startTime >= :startOfDay")
    fun getTotalDurationFromToday(startOfDay: Long): Flow<Int>

    @Query("SELECT DISTINCT date(startTime / 1000, 'unixepoch', 'localtime') as studyDate FROM sessions WHERE startTime >= :since ORDER BY studyDate DESC")
    suspend fun getDistinctStudyDatesSince(since: Long): List<String>

    @Query("SELECT * FROM sessions WHERE subjectId = :subjectId ORDER BY startTime DESC")
    fun getSessionsBySubject(subjectId: Long): Flow<List<SessionEntity>>

    @Query("SELECT COUNT(*) FROM sessions")
    fun getSessionCount(): Flow<Int>
}
