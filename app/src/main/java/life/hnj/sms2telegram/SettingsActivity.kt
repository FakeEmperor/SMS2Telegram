package life.hnj.sms2telegram

import android.Manifest
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Switch
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import life.hnj.sms2telegram.handler.EventBus


class SettingsActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

        setSupportActionBar(findViewById(R.id.action_bar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, SettingsFragment())
            .commit()
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        var eventBus: EventBus? = null
        private val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { isGranted: Map<String, Boolean> ->
            if (isGranted.all { it.value }) {
                eventBus?.sendCaptureCallsChange(true)
            } else {
                // Explain to the user that the feature is unavailable because the
                // features requires a permission that the user has denied. At the
                // same time, respect the user's decision. Don't link to system
                // settings in an effort to convince the user to change their
                // decision.
                Toast.makeText(
                    requireContext(),
                    "SMS2Telegram needs CALL_LOGS and PHONE_STATE permission to use call handler",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            eventBus = EventBus(requireContext())

            val horizontalMargin = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                2F, resources.displayMetrics
            )
            val verticalMargin = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                2F, resources.displayMetrics
            )
            val topMargin = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                resources.getDimension(R.dimen.activity_vertical_margin) + 10,
                resources.displayMetrics
            )
            listView.setPadding(
                horizontalMargin.toInt(),
                topMargin.toInt(), horizontalMargin.toInt(), verticalMargin.toInt()
            )

            super.onViewCreated(view, savedInstanceState)
        }

        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            val pref = resources.getString(R.string.enable_capture_calls_key)
            if (preference.key == pref) {
                val isEnabled = preference.sharedPreferences!!.getBoolean(pref, false)
                if (isEnabled) {
                    if (!hasPermissions(requireContext(), Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG)) {
                        checkPermission(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG), requestPermissionLauncher, requireContext())
                    } else {
                        eventBus?.sendCaptureCallsChange(true)
                    }
                } else {
                    eventBus?.sendCaptureCallsChange(false)
                }
            }
            return super.onPreferenceTreeClick(preference)
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
        }
    }
}