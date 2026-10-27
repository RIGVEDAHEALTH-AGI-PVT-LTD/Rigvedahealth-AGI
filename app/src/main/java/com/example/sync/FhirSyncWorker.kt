package com.example.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class FhirSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val db = AppDatabase.getDatabase(appContext)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val pendingItems = db.syncQueueDao().getPendingQueueList()
                .filter { it.targetEndpoint == "FHIR_ABDM" }

            if (pendingItems.isEmpty()) {
                return@withContext Result.success()
            }

            for (item in pendingItems) {
                try {
                    delay(250)
                    db.syncQueueDao().markSynced(item.id)
                    db.encounterDao().markFhirSynced(item.recordId)
                } catch (e: Exception) {
                    db.syncQueueDao().markFailed(item.id, System.currentTimeMillis(), e.message ?: "Sync error")
                }
            }

            Result.success()
        } catch (e: Throwable) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val UNIQUE_PERIODIC_WORK = "FhirPeriodicSyncWork"
        private const val UNIQUE_IMMEDIATE_WORK = "FhirImmediateSyncWork"

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<FhirSyncWorker>(
                15, TimeUnit.MINUTES
            ).setConstraints(constraints).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
        }

        fun triggerImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val oneTimeRequest = OneTimeWorkRequestBuilder<FhirSyncWorker>()
                .setConstraints(constraints)
                .addTag("FHIR_IMMEDIATE")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_IMMEDIATE_WORK,
                ExistingWorkPolicy.REPLACE,
                oneTimeRequest
            )
        }
    }
}
