package it.vagitaly.meteomottavisconti

import android.content.Context

// Storage locale semplice (SharedPreferences). Verra' rafforzato con
// EncryptedSharedPreferences quando aggiungeremo lo sblocco biometrico.
object AccountManager {
    private const val PREFS = "account_prefs"
    private const val REMEMBER_PREFS = "remember_prefs"

    // Sopravvive al logout: serve solo a precompilare il campo email al prossimo accesso.
    fun rememberEmail(context: Context, email: String) {
        context.getSharedPreferences(REMEMBER_PREFS, Context.MODE_PRIVATE).edit()
            .putString("email", email).apply()
    }

    fun getRememberedEmail(context: Context): String? =
        context.getSharedPreferences(REMEMBER_PREFS, Context.MODE_PRIVATE).getString("email", null)

    fun save(context: Context, token: String, userId: Int, email: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("token", token)
            .putInt("user_id", userId)
            .putString("email", email)
            .apply()
    }

    fun getToken(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("token", null)

    fun getEmail(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("email", null)

    fun isLoggedIn(context: Context): Boolean = getToken(context) != null

    fun logout(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
