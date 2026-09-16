package it.meteomottavisconti

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object ApiClient {
    private const val BASE_URL = "https://meteo.nas.vagitaly.it"

    class ApiResult(val success: Boolean, val statusCode: Int, val json: JSONObject)

    private fun request(path: String, method: String, body: JSONObject?, token: String?): ApiResult {
        return try {
            val url = URL(BASE_URL + path)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("Content-Type", "application/json")
            if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                conn.doOutput = true
                OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText) ?: "{}"
            val json = try { JSONObject(text) } catch (e: Exception) { JSONObject() }
            ApiResult(code in 200..299, code, json)
        } catch (e: Exception) {
            val err = JSONObject().put("success", false).put("error", "network_error")
            ApiResult(false, -1, err)
        }
    }

    // Esegue la chiamata su un thread separato e richiama il callback sul thread principale.
    private fun runAsync(block: () -> ApiResult, callback: (ApiResult) -> Unit) {
        val mainHandler = Handler(Looper.getMainLooper())
        Thread {
            val result = block()
            mainHandler.post { callback(result) }
        }.start()
    }

    fun register(email: String, password: String, nome: String, cognome: String, dataNascita: String?, citta: String, callback: (ApiResult) -> Unit) {
        runAsync({
            val body = JSONObject().put("email", email).put("password", password)
                .put("nome", nome).put("cognome", cognome).put("citta", citta)
                .put("data_nascita", dataNascita ?: JSONObject.NULL)
            request("/register.php", "POST", body, null)
        }, callback)
    }

    fun login(email: String, password: String, callback: (ApiResult) -> Unit) {
        runAsync({
            val body = JSONObject().put("email", email).put("password", password)
            request("/login.php", "POST", body, null)
        }, callback)
    }

    fun getNotificationPrefs(token: String, callback: (ApiResult) -> Unit) {
        runAsync({ request("/notification_prefs.php", "GET", null, token) }, callback)
    }

    fun setNotificationPrefs(token: String, prefs: JSONObject, callback: (ApiResult) -> Unit) {
        runAsync({ request("/notification_prefs.php", "POST", prefs, token) }, callback)
    }

    fun requestPasswordReset(email: String, callback: (ApiResult) -> Unit) {
        runAsync({
            val body = JSONObject().put("email", email)
            request("/request_reset.php", "POST", body, null)
        }, callback)
    }

    fun getAccount(token: String, callback: (ApiResult) -> Unit) {
        runAsync({ request("/account.php", "GET", null, token) }, callback)
    }

    fun updateAccount(token: String, nome: String, cognome: String, dataNascita: String?, citta: String, note: String, callback: (ApiResult) -> Unit) {
        runAsync({
            val body = JSONObject().put("nome", nome).put("cognome", cognome).put("note", note)
                .put("citta", citta)
                .put("data_nascita", dataNascita ?: JSONObject.NULL)
            request("/account.php", "POST", body, token)
        }, callback)
    }

    fun getLatestVersion(callback: (ApiResult) -> Unit) {
        runAsync({ request("/latest_version.json", "GET", null, null) }, callback)
    }

    fun registerDevice(token: String, fcmToken: String, callback: (ApiResult) -> Unit) {
        runAsync({
            val body = JSONObject().put("fcm_token", fcmToken)
            request("/register_device.php", "POST", body, token)
        }, callback)
    }

    fun updateDeviceInfo(token: String, info: JSONObject, callback: (ApiResult) -> Unit) {
        runAsync({ request("/device_info.php", "POST", info, token) }, callback)
    }

    fun setTheme(token: String, theme: String, callback: (ApiResult) -> Unit) {
        runAsync({
            val body = JSONObject().put("theme", theme)
            request("/set_theme.php", "POST", body, token)
        }, callback)
    }

    fun addSessionTime(token: String, seconds: Int, callback: (ApiResult) -> Unit) {
        runAsync({
            val body = JSONObject().put("seconds", seconds)
            request("/add_session_time.php", "POST", body, token)
        }, callback)
    }

    fun changeEmail(token: String, newEmail: String, currentPassword: String, callback: (ApiResult) -> Unit) {
        runAsync({
            val body = JSONObject().put("new_email", newEmail).put("current_password", currentPassword)
            request("/change_email.php", "POST", body, token)
        }, callback)
    }
}
