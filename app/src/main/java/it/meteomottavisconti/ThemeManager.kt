package it.meteomottavisconti

import android.content.Context

// Preferenza tema salvata localmente; applicata alla WebView del sito
// tramite window.setAppTheme(...) (vedi MainActivity.setupWebView).
object ThemeManager {
    private const val PREFS = "theme_prefs"
    private const val DEFAULT_THEME = "scuro"

    // Chiave interna -> etichetta mostrata all'utente, nello stesso ordine del selettore.
    val THEMES = linkedMapOf(
        "scuro" to "Scuro (predefinito)",
        "chiaro" to "Chiaro",
        "black" to "Black Glass",
        "blackamoled" to "Black AMOLED",
        "blu" to "Blu",
        "green" to "Green",
        "pink" to "Pink",
        "red" to "Red",
        "violet" to "Violet",
        "orange" to "Orange",
        "teal" to "Teal",
        "gold" to "Gold",
        "notte" to "Notte"
    )

    fun getTheme(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("theme", DEFAULT_THEME) ?: DEFAULT_THEME

    fun setTheme(context: Context, theme: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("theme", theme).apply()
    }
}
