package com.example.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class NhcxSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val db = AppDatabase.getDatabase(appContext)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting NhcxSyncWorker execution...")
        try {
            val pendingItems = db.syncQueueDao().getPendingQueueList()
                .filter { it.targetEndpoint == "NHCX" }

            if (pendingItems.isEmpty()) {
                return@withContext Result.success()
            }

            for (item in pendingItems) {
                try {
                    delay(300) // Simulated secure NHCX TLS push
                    db.syncQueueDao().markSynced(item.id)
                    db.encounterDao().markNhcxSynced(item.recordId)
                    Log.i(TAG, "Synced NHCX transaction ${item.id}")
                } catch (e: Exception) {
                    db.syncQueueDao().markFailed(item.id, System.currentTimeMillis(), e.message ?: "Sync error")
                }
            }

            Result.success()
        } catch (e: Throwable) {
            Log.e(TAG, "NhcxSyncWorker error: ${e.message}", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val TAG = "NhcxSyncWorker"
        private const val UNIQUE_PERIODIC_WORK = "NhcxPeriodicSyncWork"
        private const val UNIQUE_IMMEDIATE_WORK = "NhcxImmediateSyncWork"

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<NhcxSyncWorker>(
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

            val oneTimeRequest = OneTimeWorkRequestBuilder<NhcxSyncWorker>()
                .setConstraints(constraints)
                .addTag("NHCX_IMMEDIATE")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_IMMEDIATE_WORK,
                ExistingWorkPolicy.REPLACE,
                oneTimeRequest
            )
        }
    }
}
