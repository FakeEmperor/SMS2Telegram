package life.hnj.sms2telegram.networking

import android.content.Context
import android.util.Log
import com.android.volley.ClientError
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request.Method
import com.android.volley.Response.ErrorListener
import com.android.volley.Response.Listener
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import life.hnj.sms2telegram.TAG
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import com.github.kittinunf.result.Result
import com.github.kittinunf.result.isSuccess
import kotlinx.serialization.json.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString


@kotlinx.serialization.Serializable
data class QueueBehaviorParams(
    val sendImmediately: Boolean,
    val persistTask: Boolean = true,
    val maxRetriesBeforePersisting: Int = 3,
    val maxPersistableRetries: Int = 3
) {
    companion object {
        val DEFAULT: QueueBehaviorParams = QueueBehaviorParams(
            sendImmediately = false,
            persistTask = true,
            maxRetriesBeforePersisting = 3,
            maxPersistableRetries = 3
        )
    }
}

val JsonElement.extractedContent: Any?
    get() {
        if (this is JsonPrimitive) {
            if (this.jsonPrimitive.isString) {
                return this.jsonPrimitive.content
            }
            return this.jsonPrimitive.booleanOrNull ?: this.jsonPrimitive.intOrNull ?: this.jsonPrimitive.longOrNull
            ?: this.jsonPrimitive.floatOrNull ?: this.jsonPrimitive.doubleOrNull ?: this.jsonPrimitive.contentOrNull
        }
        if (this is JsonArray) {
            return this.jsonArray.map {
                it.extractedContent
            }
        }
        if (this is JsonObject) {
            return this.jsonObject.entries.associate {
                it.key to it.value.extractedContent
            }
        }
        return null
    }

@kotlinx.serialization.Serializable
data class PersistingQueueData(
    val url: String,
    val payload: JsonObject? = null,
    val method: Int,
) {
    fun toShortString(): String {
        return "[${getMethodName(method)}] $url (payload=$payload)"
    }

    val jsonObject: JSONObject?
        get() {
            if (payload == null) return null;

            return JSONObject(payload.toMap().mapValues {
                it.value.extractedContent
            })
        }

    companion object {

        fun getMethodName(method: Int): String {
            // enumerate all members
            return when (method) {
                Method.POST -> "POST"
                Method.GET -> "GET"
                Method.DELETE -> "DELETE"
                Method.HEAD -> "HEAD"
                Method.OPTIONS -> "OPTIONS"
                Method.PATCH -> "PATCH"
                Method.PUT -> "PUT"
                Method.TRACE -> "TRACE"
                else -> throw IllegalStateException("Method value $method is invalid")
            }
        }
    }
}

@kotlinx.serialization.Serializable
data class PersistingQueueItem(
    val data: PersistingQueueData,
    val behavior: QueueBehaviorParams = QueueBehaviorParams.DEFAULT
)


@kotlinx.serialization.Serializable
data class PersistingQueueItemState(
    val item: PersistingQueueItem,
    var persistableRetries: Int,
    val id: Int,
)


class PersistingRequest(
    private val state: PersistingQueueItemState,
    listener: Listener<JSONObject>,
    errorListener: ErrorListener
) :
    JsonObjectRequest(state.item.data.method, state.item.data.url, state.item.data.jsonObject, listener, errorListener) {
    override fun getPriority(): Priority {
        return if (state.item.behavior.sendImmediately) Priority.IMMEDIATE else Priority.NORMAL
    }

}

class PersistingQueue(private val applicationContext: Context, private val forceRecheckPriority: Boolean = false) {
    private val queue = Volley.newRequestQueue(applicationContext)
    private var lastId: Int = 0

    fun loadFromCache(id: Int): Result<PersistingQueueItemState, Throwable> {
        return Result.of {
            Json.decodeFromString<PersistingQueueItemState>(cacheFile(id).readText())
        }
    }

    val cacheIds: Result<List<Int>, Throwable>
        get() {
            val re = Regex("job_(\\d+)\\.json")
            return Result.of {
                applicationContext.cacheDir.list { _, name -> name.startsWith("job_") }?.map {
                    val match = re.find(it)
                    if (match != null) {
                        val (id) = match.destructured
                        id.toInt()
                    } else 0
                }
            }
        }

    private fun cacheFile(id: Int): File {
        return File(applicationContext.cacheDir.absolutePath + "/job_${id}.json")
    }

    fun removeCached(id: Int): Result<Boolean, Throwable> {
        return Result.of {
            val file = cacheFile(id)
            file.delete()
        }
    }

    fun saveToCache(state: PersistingQueueItemState): Result<Unit, Throwable> {
        return Result.of {
            val file = File(applicationContext.cacheDir.absolutePath + "/job_${state.id}.json")
            file.writeText(Json.encodeToString(state))
        }
    }

    fun handleRequestError(error: Throwable, state: PersistingQueueItemState) {
        val cacheAllowed =
            state.item.behavior.persistTask &&
                    !(error is ClientError && (error.networkResponse.statusCode >= 400 || error.networkResponse.statusCode >= 500))
        state.persistableRetries += 1
        Log.e(
            TAG,
            "Failed to handle request #${state.id} [will cache: ${cacheAllowed}]" +
                    "(${state.persistableRetries}/${state.item.behavior.maxPersistableRetries} retries) " +
                    state.item.data.toShortString(),
            error
        )
        if (cacheAllowed && state.persistableRetries < state.item.behavior.maxPersistableRetries) {
            Log.d(TAG, "Saving ${state.item}")
            saveToCache(state)
        } else {
            Log.d(TAG, "Removing cache ${state.id}: ${removeCached(state.id).isSuccess()}")
        }
    }

    fun enqueue(state: PersistingQueueItemState): Future<JSONObject> {
        val future = CompletableFuture<JSONObject>()
        val req = PersistingRequest(
            state,
            { response ->
                Log.d(TAG, "MSG send success")
                future.complete(response)
                removeCached(state.id)
            },
            { error ->
                Log.d(TAG, "MSG send error: (${error.javaClass}) ${error.message}")
                handleRequestError(error, state)
                future.completeExceptionally(error)
            })
        req.retryPolicy = DefaultRetryPolicy(
            5000,
            state.item.behavior.maxRetriesBeforePersisting, 2.0F
        )
        queue.add(req)
        return future
    }

    fun enqueue(state: PersistingQueueItem): Future<JSONObject> {
        return enqueue(PersistingQueueItemState(state, id = makeUniqueId(), persistableRetries = 0))
    }

    @Synchronized
    private fun makeUniqueId(): Int {
        // this method relies on that the cache can be changed only with one instance of a queue
        if (lastId == 0 || forceRecheckPriority) {
            // reload cache
            val ids = cacheIds.get().sortedDescending()
            if (ids.isEmpty()) {
                lastId += 1
            } else {
                lastId = ids[0] + 1
            }
        } else {
            lastId += 1
        }
        return lastId
    }
}