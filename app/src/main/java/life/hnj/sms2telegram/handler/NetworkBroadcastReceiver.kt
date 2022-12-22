package life.hnj.sms2telegram.handler;

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.util.Log
import life.hnj.sms2telegram.TAG
import life.hnj.sms2telegram.networking.isOnline

class NetworkBroadcastReceiver: BroadcastReceiver() {
    @Override
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Received  action: ${intent.action}")
        if (intent.action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
            val setOnline = isOnline(context)
            Log.d(TAG, "Setting isOnline: $setOnline")
            EventBus(context).sendNetworkChange(setOnline)
        }
    }
}
