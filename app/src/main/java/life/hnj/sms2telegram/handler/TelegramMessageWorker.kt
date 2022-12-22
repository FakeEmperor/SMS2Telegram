package life.hnj.sms2telegram.handler

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.work.*
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import life.hnj.sms2telegram.TAG
import life.hnj.sms2telegram.networking.*


class TelegramMessageWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) :
    ListenableWorker(appContext, workerParams) {

    private val queue: PersistingQueue = PersistingQueue(appContext, true)

    override fun startWork(): ListenableFuture<Result> {
        return CallbackToFutureAdapter.getFuture { completer: CallbackToFutureAdapter.Completer<Result> ->
            sendRequest(completer)
        }
    }

    private fun sendRequest(completer: CallbackToFutureAdapter.Completer<Result>) {
        val data: PersistingQueueData
        try {
            data = Json.decodeFromString(inputData.getString("parcel")!!)
        } catch (e: Throwable) {
            completer.setException(e)
            throw e
        }

        try {
            queue.enqueue(PersistingQueueItem(data))
            completer.set(Result.success())
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to send ${data.toShortString()}", e)
            completer.set(Result.failure())
        }
    }

    companion object {
        fun sendWork(store: SharedPreferences, context: Context, dataCallback: ((com.github.kittinunf.result.Result<TelegramQueueAdapter, Exception>) -> PersistingQueueData?)): Operation? {
            val item: PersistingQueueData = dataCallback(com.github.kittinunf.result.Result.of {
                TelegramQueueAdapter.ensureTelegram(store, context)
            }) ?: return null
            val data = Data.Builder()
            data.putString("parcel", Json.encodeToString(item))

            val tgMsgTask: WorkRequest =
                OneTimeWorkRequestBuilder<TelegramMessageWorker>().setInputData(data.build()).build()
            return WorkManager.getInstance(context).enqueue(tgMsgTask)
        }
    }

}