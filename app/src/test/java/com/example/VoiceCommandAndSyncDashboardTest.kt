package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.db.AppDatabase
import com.example.db.SyncQueueEntity
import com.example.speech.VoiceAction
import com.example.speech.VoiceCommandEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceCommandAndSyncDashboardTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testVoiceCommandRecognition() {
        // 1. "start recording"
        val startRec = VoiceCommandEngine.parseCommand("Doctor, please start recording now")
        assertNotNull(startRec)
        assertEquals(VoiceAction.START_RECORDING, startRec!!.action)

        // 2. "sync data"
        val syncData = VoiceCommandEngine.parseCommand("All patients finished, sync data")
        assertNotNull(syncData)
        assertEquals(VoiceAction.SYNC_DATA, syncData!!.action)

        // 3. "open consultation"
        val openConsult = VoiceCommandEngine.parseCommand("open consultation for next patient")
        assertNotNull(openConsult)
        assertEquals(VoiceAction.OPEN_CONSULTATION, openConsult!!.action)

        // 4. "view queue"
        val viewQueue = VoiceCommandEngine.parseCommand("let's view queue")
        assertNotNull(viewQueue)
        assertEquals(VoiceAction.VIEW_QUEUE, viewQueue!!.action)

        // 5. "scan abha"
        val scanAbha = VoiceCommandEngine.parseCommand("scan abha card please")
        assertNotNull(scanAbha)
        assertEquals(VoiceAction.SCAN_ABHA, scanAbha!!.action)

        // 6. Non-command casual conversation
        val noMatch = VoiceCommandEngine.parseCommand("Patient feels mild headache since yesterday morning")
        assertNull(noMatch)
    }

    @Test
    fun testSyncQueueDashboardPendingCountAndGranularRetryDelete() = runBlocking {
        val syncDao = db.syncQueueDao()

        val item1 = SyncQueueEntity(
            id = 101L,
            targetEndpoint = "NHCX",
            payloadType = "CLAIM_TRANSACTION",
            recordId = "REC-01",
            patientName = "Lakshmi Devi",
            priority = 1,
            status = "PENDING"
        )
        val item2 = SyncQueueEntity(
            id = 102L,
            targetEndpoint = "FHIR_ABDM",
            payloadType = "CLINICAL_IMPRESSION",
            recordId = "REC-02",
            patientName = "Ramesh Kumar",
            priority = 2,
            status = "PENDING"
        )
        val item3 = SyncQueueEntity(
            id = 103L,
            targetEndpoint = "NHCX",
            payloadType = "CLAIM_TRANSACTION",
            recordId = "REC-03",
            patientName = "Anjaneyulu M.",
            priority = 1,
            status = "FAILED",
            lastError = "HTTP 504 Gateway Timeout: District switch down",
            retryCount = 2
        )

        syncDao.enqueueItems(listOf(item1, item2, item3))

        // Check Pending count flow
        val pendingCount = syncDao.getPendingCountFlow().first()
        assertEquals(2, pendingCount)

        // Check Failed count flow
        val failedCount = syncDao.getFailedCountFlow().first()
        assertEquals(1, failedCount)

        // Test Retry Action on failed item
        syncDao.retryQueueItem(103L)
        val updatedPending = syncDao.getPendingCountFlow().first()
        val updatedFailed = syncDao.getFailedCountFlow().first()
        assertEquals(3, updatedPending) // Item 103 was moved from FAILED to PENDING
        assertEquals(0, updatedFailed)

        // Test Delete Action on item 101
        syncDao.deleteQueueItem(101L)
        val finalPending = syncDao.getPendingCountFlow().first()
        assertEquals(2, finalPending) // Item 101 removed
    }
}
