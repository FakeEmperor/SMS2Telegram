package life.hnj.sms2telegram.networking

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import life.hnj.sms2telegram.TAG


class PersistingQueueWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) :
    Worker(appContext, workerParams) {
    private val queue: PersistingQueue = PersistingQueue(appContext, true)

    override fun doWork(): Result {
        val jobs = queue.cacheIds.get().mapNotNull {
            when (val m = queue.loadFromCache(it)) {
                is com.github.kittinunf.result.Result.Failure -> {
                    Log.w(TAG, "Could not load cached work $it", m.error)
                    null
                }
                is com.github.kittinunf.result.Result.Success -> m.value
            }
        };
        for (job in jobs) {
            queue.enqueue(job)
        }
        return Result.success()
    }

}