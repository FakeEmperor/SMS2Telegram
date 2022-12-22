package life.hnj.sms2telegram.handler

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.ConnectivityManager
import android.os.IBinder
import android.provider.Telephony
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.work.*
import life.hnj.sms2telegram.MainActivity
import life.hnj.sms2telegram.R
import life.hnj.sms2telegram.TAG
import life.hnj.sms2telegram.getBooleanVal
import life.hnj.sms2telegram.hasPermissions
import life.hnj.sms2telegram.networking.PersistingQueueWorker
import java.time.Duration


class IncomingHandleForegroundService : Service() {
    private var bus: EventBus? = null
    private val smsReceiver = SMSReceiver()
    private val callReceiver = CallReceiver()
    private val networkReceiver = NetworkBroadcastReceiver()

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Registering the receiver")
        val notification = createNotification()
        startForeground(1, notification)

        // register event bus
        bus = EventBus(applicationContext)
        bus?.register()
        bus!!.onNetworkChange = {
            if (it) {
                val sendTask = OneTimeWorkRequestBuilder<PersistingQueueWorker>().build()
                WorkManager.getInstance(applicationContext)
                    .enqueueUniqueWork("sync_failed_tasks", ExistingWorkPolicy.REPLACE, sendTask)
            }
        }
        bus!!.onCallCaptureChange = {
            setCallCaptureReceiver(it)
        }
        val sendPeriodicTask = PeriodicWorkRequest.Builder(PersistingQueueWorker::class.java, Duration.ofSeconds(900)).build()
        WorkManager.getInstance(applicationContext)
            .enqueueUniquePeriodicWork("sync_failed_tasks", ExistingPeriodicWorkPolicy.REPLACE, sendPeriodicTask)

        // register SMS receiver
        registerReceiver(
            smsReceiver, IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION), Manifest.permission.BROADCAST_SMS, null
        )

        // register Call receiver
        val callCaptureEnabled =
            getBooleanVal(applicationContext, booleanPreferencesKey(resources.getString(R.string.enable_telegram_sync_key)))
        if (callCaptureEnabled) {
            setCallCaptureReceiver(true)
        }


        // register Network change receiver
        if (hasPermissions(applicationContext, Manifest.permission.ACCESS_NETWORK_STATE)) {
            registerReceiver(
                networkReceiver,
                IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION),
                Manifest.permission.ACCESS_NETWORK_STATE,
                null
            )
        } else {
            Log.i(TAG, "Cannot use network access detection: no permissions.")
            Toast.makeText(applicationContext, "Skipping network detection", Toast.LENGTH_SHORT).show()
        }
        // Restart when closed
        return START_STICKY
    }

    private fun setCallCaptureReceiver(isEnabled: Boolean) {
        Log.i(TAG, "Setting callCaptureReceiver: $isEnabled")
        // first unregister even if it's enabled, so we won't leak receivers
        try {
            unregisterReceiver(callReceiver)
        } catch (_: java.lang.IllegalArgumentException) {
        }
        if (isEnabled) {
            if (hasPermissions(applicationContext, Manifest.permission.READ_PHONE_STATE)) {
                try {
                    registerReceiver(
                        callReceiver,
                        IntentFilter("android.intent.action.PHONE_STATE"),
                    )
                } catch (e: java.lang.IllegalArgumentException) {
                    /// do nothing
                }

            } else {
                Log.i(TAG, "Skipping starting callReceiver due to no permissions.")
                Toast.makeText(applicationContext, "Skipping incoming call tracking", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createNotification(): Notification {
        val input = "SMS2Telegram running in the background"
        val notificationIntent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(applicationContext, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        val channelId = createNotificationChannel("SMS2TELEGRAM", "SMS2TelegramService")
        return NotificationCompat.Builder(applicationContext, channelId).setContentTitle("SMS2Telegram Service")
            .setContentText(input).setContentIntent(pendingIntent).build()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(networkReceiver)
        } catch (_: java.lang.IllegalArgumentException) {
        }
        try {
            unregisterReceiver(callReceiver)
        } catch (_: java.lang.IllegalArgumentException) {
        }
        try {
            unregisterReceiver(smsReceiver)
        } catch (_: java.lang.IllegalArgumentException) {
        }
        bus?.unregister()
        super.onDestroy()
    }

    private fun createNotificationChannel(channelId: String, channelName: String): String {
        val channel = NotificationChannel(
            channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.lightColor = Color.BLUE
        channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(channel)
        return channelId
    }
}
