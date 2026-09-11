package it.vagitaly.meteomottavisconti

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import org.json.JSONObject
import java.util.Locale
import java.util.TimeZone

// Raccoglie informazioni tecniche non invasive sul dispositivo (marca, modello,
// versione OS, rete, schermo, RAM) e le invia al backend per l'utente loggato.
// Niente di sensibile: nessuna posizione, nessun identificativo pubblicitario.
object DeviceInfoHelper {

    fun sendIfLoggedIn(context: Context) {
        val token = AccountManager.getToken(context) ?: return
        val body = collect(context)
        ApiClient.updateDeviceInfo(token, body) { }
    }

    private fun collect(context: Context): JSONObject {
        val metrics: DisplayMetrics = context.resources.displayMetrics

        var ramTotalMb: Long? = null
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            ramTotalMb = info.totalMem / (1024 * 1024)
        } catch (e: Exception) { /* non disponibile su alcuni dispositivi */ }

        var connectionType = "sconosciuta"
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            connectionType = when {
                caps == null -> "assente"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "dati_mobili"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                else -> "altro"
            }
        } catch (e: Exception) { /* ignora */ }

        var carrierName = ""
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            carrierName = tm.networkOperatorName ?: ""
        } catch (e: Exception) { /* ignora */ }

        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        } catch (e: Exception) { "" }

        return JSONObject().apply {
            put("device_brand", Build.MANUFACTURER ?: "")
            put("device_model", Build.MODEL ?: "")
            put("os_name", "Android")
            put("os_version", Build.VERSION.RELEASE ?: "")
            put("api_level", Build.VERSION.SDK_INT)
            put("app_version_name", BuildConfig.VERSION_NAME)
            put("app_version_code", BuildConfig.VERSION_CODE)
            put("device_language", Locale.getDefault().toString())
            put("device_timezone", TimeZone.getDefault().id)
            put("connection_type", connectionType)
            put("carrier_name", carrierName)
            put("screen_resolution", "${metrics.widthPixels}x${metrics.heightPixels}")
            put("screen_density", "${metrics.densityDpi}dpi")
            if (ramTotalMb != null) put("ram_total_mb", ramTotalMb)
            put("android_id", androidId)
            put("ultimo_tema", ThemeManager.getTheme(context))
        }
    }
}
