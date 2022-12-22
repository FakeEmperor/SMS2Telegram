package life.hnj.sms2telegram.networking

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import com.android.volley.Request
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import life.hnj.sms2telegram.TAG

// This queue adapter prepares parcels for queue
class TelegramQueueAdapter(
    private val token: String,
    private val chatId: String
) {

    fun checkAccess(): PersistingQueueData {
        return call("getMe", payload = null, method=Request.Method.GET)
    }

    private fun apiUrl(callName: String): String {
        return "${BASE_URL}/bot${token}/${callName}"
    }

    fun call(
        callName: String,
        payload: JsonObject?,
        method: Int = Request.Method.POST,
    ): PersistingQueueData {
        return PersistingQueueData(
            url = apiUrl(callName),
            payload = payload,
            method = method
        )
    }

    fun sendText(text: String): PersistingQueueData {
        val payload = buildJsonObject {
            this.put("chat_id", chatId)
            this.put("text", text)
        }
        return call("sendMessage", payload = payload)
    }

    companion object {
        const val BASE_URL = "https://api.telegram.org"

        @Throws(RuntimeException::class)
        fun ensureTelegram(store: SharedPreferences,
                           context: Context): TelegramQueueAdapter {
            val botKey = store.getString("telegram_bot_key", "")
            if (botKey.isNullOrEmpty()) {
                throw RuntimeException("Telegram bot key is not configured")
            }

            val chatId = store.getString("telegram_chat_id", "")
            if (chatId.isNullOrEmpty()) {
                throw RuntimeException("Telegram chat id is not configured")
            }

            return TelegramQueueAdapter(token=botKey, chatId=chatId)
        }
    }
}