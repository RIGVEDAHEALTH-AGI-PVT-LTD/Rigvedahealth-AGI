package com.example.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientDao {
    @Query("SELECT * FROM patients ORDER BY lastVisitTimestamp DESC")
    fun getAllPatients(): Flow<List<PatientEntity>>

    @Query("SELECT * FROM patients WHERE patientId = :patientId LIMIT 1")
    suspend fun getPatientById(patientId: String): PatientEntity?

    @Query("SELECT * FROM patients WHERE patientId = :patientId LIMIT 1")
    fun getPatientByIdFlow(patientId: String): Flow<PatientEntity?>

    @Query("SELECT * FROM patients WHERE abhaNumber = :abha LIMIT 1")
    suspend fun getPatientByAbha(abha: String): PatientEntity?

    @Query("SELECT * FROM patients WHERE abhaBlindIndex = :blindIndex LIMIT 1")
    suspend fun getPatientByAbhaBlindIndex(blindIndex: String): PatientEntity?

    @Query("SELECT * FROM patients WHERE abhaNumber = :abha LIMIT 1")
    fun getPatientByAbhaFlow(abha: String): Flow<PatientEntity?>

    @Query("SELECT * FROM patients WHERE name LIKE '%' || :query || '%' OR abhaNumber LIKE '%' || :query || '%' ORDER BY lastVisitTimestamp DESC")
    fun searchPatients(query: String): Flow<List<PatientEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatient(patient: PatientEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(patients: List<PatientEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatients(patients: List<PatientEntity>)

    @Update
    suspend fun updatePatient(patient: PatientEntity)

    @Delete
    suspend fun deletePatient(patient: PatientEntity)

    @Transaction
    @Query("SELECT * FROM patients ORDER BY lastVisitTimestamp DESC")
    fun getPatientsWithEncounters(): Flow<List<PatientWithEncounters>>
}

@Dao
interface EncounterDao {
    @Query("SELECT * FROM encounters ORDER BY timestamp DESC")
    fun getAllEncounters(): Flow<List<EncounterEntity>>

    @Query("SELECT * FROM encounters WHERE encounterId = :id LIMIT 1")
    suspend fun getEncounterById(id: String): EncounterEntity?

    @Query("SELECT * FROM encounters WHERE encounterId = :id LIMIT 1")
    fun getEncounterByIdFlow(id: String): Flow<EncounterEntity?>

    @Query("SELECT * FROM encounters WHERE patientId = :patientId ORDER BY timestamp DESC")
    suspend fun getEncountersForPatient(patientId: String): List<EncounterEntity>

    @Query("SELECT * FROM encounters WHERE patientId = :patientId ORDER BY timestamp DESC")
    fun getEncountersByPatientId(patientId: String): Flow<List<EncounterEntity>>

    @Query("SELECT * FROM encounters WHERE triageTier = :tier ORDER BY timestamp DESC")
    fun getEncountersByTier(tier: String): Flow<List<EncounterEntity>>

    @Query("SELECT * FROM encounters WHERE triageTier = 'RED' ORDER BY timestamp DESC")
    fun getRedEmergentEncounters(): Flow<List<EncounterEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEncounter(encounter: EncounterEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEncounters(encounters: List<EncounterEntity>)

    @Update
    suspend fun updateEncounter(encounter: EncounterEntity)

    @Delete
    suspend fun deleteEncounter(encounter: EncounterEntity)

    @Query("UPDATE encounters SET syncedNhcx = 1, isSynced = 1, syncStatus = 'SYNCED' WHERE encounterId = :id")
    suspend fun markNhcxSynced(id: String)

    @Query("UPDATE encounters SET syncedFhir = 1, isSynced = 1, syncStatus = 'SYNCED' WHERE encounterId = :id")
    suspend fun markFhirSynced(id: String)
}

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' OR status = 'QUEUED' ORDER BY priority ASC, createdAt ASC")
    fun getPendingQueue(): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' OR status = 'QUEUED' ORDER BY priority ASC, createdAt ASC")
    suspend fun getPendingQueueList(): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' OR status = 'QUEUED' ORDER BY priority ASC, createdAt ASC")
    fun getPendingQueueItemsFlow(): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' OR status = 'QUEUED' ORDER BY priority ASC, createdAt ASC")
    suspend fun getPendingQueueItems(): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING' OR status = 'QUEUED'")
    fun getPendingCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE priority = 1 AND (status = 'PENDING' OR status = 'QUEUED')")
    fun getEmergentPendingCountFlow(): Flow<Int>

    @Query("SELECT * FROM sync_queue ORDER BY createdAt DESC")
    fun getAllQueueEntries(): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue ORDER BY createdAt DESC")
    fun getAllQueueItemsFlow(): Flow<List<SyncQueueEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: SyncQueueEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueItem(item: SyncQueueEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueItems(items: List<SyncQueueEntity>)

    @Update
    suspend fun update(item: SyncQueueEntity)

    @Update
    suspend fun updateItem(item: SyncQueueEntity)

    @Query("UPDATE sync_queue SET status = 'SYNCED', syncedAt = :syncedAt, lastError = NULL WHERE id = :id OR queueId = :id")
    suspend fun markSynced(id: Long, syncedAt: Long = System.currentTimeMillis())

    @Query("UPDATE sync_queue SET status = 'FAILED', retryCount = retryCount + 1, attemptCount = attemptCount + 1, lastAttemptAt = :now, lastError = :error WHERE id = :id OR queueId = :id")
    suspend fun markFailed(id: Long, now: Long, error: String)

    @Query("UPDATE sync_queue SET status = 'PENDING', lastError = NULL WHERE id = :id OR queueId = :id")
    suspend fun retryQueueItem(id: Long)

    @Query("UPDATE sync_queue SET status = 'PENDING', lastError = NULL WHERE status = 'FAILED'")
    suspend fun retryAllFailed()

    @Query("SELECT * FROM sync_queue WHERE status = 'FAILED' ORDER BY lastAttemptAt DESC")
    fun getFailedQueueEntries(): Flow<List<SyncQueueEntity>>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'FAILED'")
    fun getFailedCountFlow(): Flow<Int>

    @Query("DELETE FROM sync_queue WHERE status = 'SYNCED'")
    suspend fun clearSynced()

    @Query("DELETE FROM sync_queue WHERE status = 'SYNCED'")
    suspend fun clearCompletedItems()

    @Query("DELETE FROM sync_queue WHERE id = :id OR queueId = :id")
    suspend fun deleteQueueItem(id: Long)
}
