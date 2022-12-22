package life.hnj.sms2telegram.handler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import life.hnj.sms2telegram.TAG
import life.hnj.sms2telegram.networking.PersistingQueueItemState
import life.hnj.sms2telegram.networking.isOnline


// This class centralizes communications between broadcast receivers and foreground service
class EventBus(private val appContext: Context) {
    private val onBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            Log.d(TAG, "Received broadcast: ${intent.action}")
            if (context != null) {
                when (intent.action) {
                    Constants.INTENT_CHANGE_INTERNET_CONNECTIVITY -> onNetworkChange?.invoke(isOnline(context))
                    Constants.INTENT_CHANGE_CALL_CAPTURE -> onCallCaptureChange?.invoke(
                        intent.getBooleanExtra(
                            "isEnabled",
                            false
                        )
                    )
                }
            }
        }
    }

    fun sendNetworkChange(isOnline: Boolean) {
        val intent = Intent(Constants.INTENT_CHANGE_INTERNET_CONNECTIVITY)
        intent.putExtra("isOnline", isOnline)
        appContext.sendBroadcast(intent)
    }

    fun sendCaptureCallsChange(isEnabled: Boolean) {
        val intent = Intent(Constants.INTENT_CHANGE_CALL_CAPTURE)
        intent.putExtra("isEnabled", isEnabled)
        appContext.sendBroadcast(intent)
    }


    // event listener when a message failed to be sent
    var onMessageSendError: ((error: Exception, message: PersistingQueueItemState) -> Unit)? = null

    // event listener when a message was sent
    var onMessageSendSuccess: ((message: PersistingQueueItemState) -> Unit)? = null

    // event listener when networking changed
    var onNetworkChange: ((isConnected: Boolean) -> Unit)? = null

    // event listener when call capture enabled value changes
    var onCallCaptureChange: ((isEnabled: Boolean) -> Unit)? = null

    // event listener when credentials changed
    var onCredentialsCheckFinished: ((isValid: Boolean) -> Unit)? = null


    fun register() {
        appContext.registerReceiver(onBroadcastReceiver, IntentFilter().apply {
            addAction(Constants.INTENT_CHANGE_INTERNET_CONNECTIVITY)
            addAction(Constants.INTENT_CHANGE_CALL_CAPTURE)
            addAction(Constants.INTENT_SENT_MESSAGE)
        })
    }

    fun unregister() {
        try {
            appContext.unregisterReceiver(onBroadcastReceiver)
        } catch (_: java.lang.IllegalArgumentException) {
        }
    }
}