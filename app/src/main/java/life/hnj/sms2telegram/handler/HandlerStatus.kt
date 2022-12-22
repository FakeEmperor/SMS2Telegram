package life.hnj.sms2telegram.handler

import android.net.wifi.hotspot2.pps.Credential.CertificateCredential
import android.util.Log
import life.hnj.sms2telegram.TAG


// Handler status using specialized property
enum class CurrentStatus {
    RUNNING,
    STOPPED,
}

class HandlerStatus(status: CurrentStatus, connectedNetwork: Boolean, validCredentials: Boolean) {
    // how many messages to send to Telegram
    var inQueue: Int = 0
        @Synchronized
        set(newValue) {
            val oldValue = field
            field = newValue
            try {
                onSentChange?.invoke(newValue, oldValue)
            } catch (e: Throwable) {
                Log.e(TAG, "Exception caught while invoking onSentChange", e)
            }
        }
        @Synchronized
        get

    // how many messages sent to Telegram
    var sent: Int = 0
        @Synchronized
        set(newValue) {
            val oldValue = field
            field = newValue
            try {
                onInQueueChange?.invoke(newValue, oldValue)
            } catch (e: Throwable) {
                Log.e(TAG, "Exception caught while invoking onInQueueChange", e)
            }
        }

    var connectedNetwork: Boolean = connectedNetwork
        @Synchronized
        set(newValue) {
            val oldValue = field
            field = newValue
            try {
                onNetworkChange?.invoke(newValue, oldValue)
            } catch (e: Throwable) {
                Log.e(TAG, "Exception caught while invoking onInQueueChange", e)
            }
        }
    var validCredentials: Boolean = validCredentials
        @Synchronized
        set(newValue) {
            val oldValue = field
            field = newValue
            try {
                onCredentialsChange?.invoke(newValue, oldValue)
            } catch (e: Throwable) {
                Log.e(TAG, "Exception caught while invoking onInQueueChange", e)
            }
        }
        @Synchronized
        get
    // current status
    var status: CurrentStatus = status
        @Synchronized
        set(newStatus) {
            val oldStatus = status
            field = newStatus
            try {
                onStatusChange?.invoke(oldStatus, newStatus)

            } catch (e: Throwable) {
                Log.e(TAG, "Exception caught while invoking onStatusChange", e)
            }
        }
        @Synchronized
        get

    // event listener when status changes
    var onStatusChange: ((oldStatus: CurrentStatus, newStatus: CurrentStatus) -> Void)? = null
    // event listener when number of in-queue messages changes
    var onInQueueChange: ((newValue: Int, oldValue: Int) -> Void)? = null
    // event listener when number of sent messages changes
    var onSentChange: ((newValue: Int, oldValue: Int) -> Void)? = null
    // event listener when networking changed
    var onNetworkChange: ((newValue: Boolean, oldValue: Boolean) -> Void)? = null
    // event listener when credentials changed
    var onCredentialsChange: ((newValue: Boolean, oldValue: Boolean) -> Void)? = null
}