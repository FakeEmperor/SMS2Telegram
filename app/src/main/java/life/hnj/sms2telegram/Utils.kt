package life.hnj.sms2telegram

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking


val Any.TAG: String
    get() {
        return if (!javaClass.isAnonymousClass) javaClass.simpleName else javaClass.name
    }

fun sync2TelegramKey(resources: android.content.res.Resources): Preferences.Key<Boolean> {
    return booleanPreferencesKey(resources.getString(R.string.enable_telegram_sync_key))
}

fun checkPermission(
    perm: String,
    requestPermissionLauncher: ActivityResultLauncher<String>,
    applicationContext: Context
) {
    if (ContextCompat.checkSelfPermission(
            applicationContext, perm
        ) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        // You can directly ask for the permission.
        // The registered ActivityResultCallback gets the result of this request.
        requestPermissionLauncher.launch(perm)
    }
}

fun checkPermission(
    perm: Array<String>,
    requestPermissionLauncher: ActivityResultLauncher<Array<String>>,
    applicationContext: Context
) {
    if (!hasPermissions(applicationContext, *perm)) {
        // You can directly ask for the permission.
        // The registered ActivityResultCallback gets the result of this request.
        requestPermissionLauncher.launch(perm)
    }
}

fun hasPermissions(context: Context, vararg permissions: String): Boolean {
    for (permission in permissions) {
        if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
    }
    return true
}

fun getBooleanVal(
    applicationContext: Context,
    key: Preferences.Key<Boolean>
): Boolean {
    val sync2TgEnabledFlow =
        applicationContext.dataStore.data.map { preferences ->
            preferences[key] ?: false
        }
    return runBlocking { sync2TgEnabledFlow.first() }
}