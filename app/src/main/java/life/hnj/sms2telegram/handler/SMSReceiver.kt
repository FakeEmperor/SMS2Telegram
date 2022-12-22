package life.hnj.sms2telegram.handler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log
import android.widget.Toast
import androidx.preference.PreferenceManager
import life.hnj.sms2telegram.TAG
import life.hnj.sms2telegram.getBooleanVal
import life.hnj.sms2telegram.sync2TelegramKey
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.max


class SMSReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sync2TgEnabledKey = sync2TelegramKey(context.resources)
        val sync2TgEnabled = getBooleanVal(context, sync2TgEnabledKey)
        if (!sync2TgEnabled) {
            Log.d(TAG, "sync2TgEnabled is false, returning")
            return
        }

        Log.d(TAG, "sync2TgEnabled, and received new sms")
        val bundle = intent.extras
        val format = bundle?.getString("format")
        val pdus = bundle!!["pdus"] as Array<*>?
        val simIndex =
            max(bundle.getInt("phone", -1), bundle.getInt("android.telephony.extra.SLOT_INDEX", -1))
        Log.d(TAG, bundle.toString())
        // will use Context.MODE_PRIVATE for this
        val store = PreferenceManager.getDefaultSharedPreferences(context)
        val phoneNum = when (simIndex) {
            0 -> store.getString("sim0_number", "SIM1")
            1 -> store.getString("sim1_number", "SIM2")
            else -> "Unsupported SIM index (${simIndex}) (please contact the developer)"
        }

        if (pdus != null) {
            val msgs: List<SmsMessage?> =
                pdus.map { i -> SmsMessage.createFromPdu(i as ByteArray, format) }
            val fromAddrToMsgBody = HashMap<String, String>()
            for (msg in msgs) {
                val fromAddr = msg?.originatingAddress!!
                fromAddrToMsgBody[fromAddr] =
                    fromAddrToMsgBody.getOrDefault(fromAddr, "") + msg.messageBody
            }
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z")
            for (entry in fromAddrToMsgBody) {
                // Build the message to show.
                val strMessage = """
                    New SMS from ${entry.key} [${ZonedDateTime.now().format(formatter)}]
                    to $phoneNum:

                """.trimIndent() + entry.value

                Log.d(TAG, "onReceive: $strMessage")
                TelegramMessageWorker.sendWork(store, context) {
                    try {
                        it.get().sendText(strMessage)
                    } catch (err: Exception) {
                        Log.e(TAG, err.message, err)
                        Toast.makeText(context, err.message, Toast.LENGTH_LONG).show()
                        null
                    }
                }
            }
        }
    }
}