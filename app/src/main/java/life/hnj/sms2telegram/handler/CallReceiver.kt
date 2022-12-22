package life.hnj.sms2telegram.handler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.preference.PreferenceManager
import life.hnj.sms2telegram.TAG
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter


class CallReceiver : BroadcastReceiver() {
    private var telephonyManager: TelephonyManager? = null

    override fun onReceive(context: Context, intent: Intent) {
        if (telephonyManager == null) {
            telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            telephonyManager?.listen(object : PhoneStateListener() {
                @Suppress("OVERRIDE_DEPRECATION")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    callHook(context, state, phoneNumber, null)
                }
            }, PhoneStateListener.LISTEN_CALL_STATE)
        }


    }

    companion object {
        fun callHook(context: Context, state: Int, incomingNumber: String?, simCardIdx: Int?) {
            val store = PreferenceManager.getDefaultSharedPreferences(context)
            if (state == TelephonyManager.CALL_STATE_RINGING) {
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z")
                val msg = "New phone call from $incomingNumber [${ZonedDateTime.now().format(formatter)}]"
                Log.d(TAG, "callHook: $msg")
                TelegramMessageWorker.sendWork(store, context) {
                    try {
                        it.get().sendText(msg)
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