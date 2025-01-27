package life.hnj.sms2telegram

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.preference.PreferenceManager
import kotlinx.coroutines.runBlocking
import life.hnj.sms2telegram.handler.IncomingHandleForegroundService
import life.hnj.sms2telegram.handler.TelegramMessageWorker

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val requestPermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean ->
                if (isGranted) {
                    // Permission is granted. Continue the action or workflow in your
                    // app.
                } else {
                    // Explain to the user that the feature is unavailable because the
                    // features requires a permission that the user has denied. At the
                    // same time, respect the user's decision. Don't link to system
                    // settings in an effort to convince the user to change their
                    // decision.
                    Toast.makeText(
                        applicationContext,
                        "SMS2Telegram needs SMS read permission",
                        Toast.LENGTH_LONG
                    ).show()
                    this.finishAffinity()
                }
            }


        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.action_bar))

        val sync2TgEnabledKey = sync2TelegramKey(resources)
        val sync2TgEnabled = getBooleanVal(applicationContext, sync2TgEnabledKey)

        val toggle = findViewById<SwitchCompat>(R.id.enable_telegram_sync)
        val serviceIntent = Intent(
            applicationContext, IncomingHandleForegroundService::class.java
        )
        if (sync2TgEnabled) {
            checkPermission(Manifest.permission.RECEIVE_SMS, requestPermissionLauncher, applicationContext)
            startForegroundHandlerService(serviceIntent)
        }
        toggle.isChecked = sync2TgEnabled
        toggle.setOnCheckedChangeListener { _, isChecked ->
            runBlocking { setSync2TgEnabled(sync2TgEnabledKey, isChecked) }
            if (isChecked) {
                checkPermission(Manifest.permission.RECEIVE_SMS, requestPermissionLauncher, applicationContext)
                startForegroundHandlerService(serviceIntent)
            } else {
                applicationContext.stopService(serviceIntent)
                Toast.makeText(applicationContext, "The service stopped", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startForegroundHandlerService(serviceIntent: Intent) {
        val alreadyStarted = applicationContext.startService(serviceIntent)
        if (alreadyStarted != null) {
            Log.d(TAG, "The service is already started")
            Toast.makeText(applicationContext, "The service is already started", Toast.LENGTH_SHORT)
                .show()
        } else {
            Log.d(TAG, "Background service started")
            Toast.makeText(applicationContext, "The service started", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun setSync2TgEnabled(
        sync2TgEnabledKey: Preferences.Key<Boolean>,
        value: Boolean
    ) {
        applicationContext.dataStore.edit { settings ->
            settings[sync2TgEnabledKey] = value
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val toolbar = findViewById<Toolbar>(R.id.action_bar)
        toolbar.inflateMenu(R.menu.actionbar_menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java).apply {
                    action = Intent.CATEGORY_PREFERENCE
                }
                startActivity(intent)
                true
            }
            R.id.action_test -> {
                val toggle = findViewById<SwitchCompat>(R.id.enable_telegram_sync)
                if (toggle.isChecked) {
                    val store = PreferenceManager.getDefaultSharedPreferences(applicationContext)
                    TelegramMessageWorker.sendWork(store, applicationContext) {
                        try {
                            it.get().sendText("Test message")
                        } catch (err: Exception) {
                            Log.e(TAG, err.message, err)
                            Toast.makeText(applicationContext, err.message, Toast.LENGTH_LONG).show()
                            null
                        }
                    }
                } else {
                    Toast.makeText(applicationContext, "Enable sync to test messages", Toast.LENGTH_SHORT).show()
                }
                true
            }
            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }
}