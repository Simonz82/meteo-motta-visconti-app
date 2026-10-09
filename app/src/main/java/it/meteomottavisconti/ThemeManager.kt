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

    // Colori reali per il selettore tema in stile "card" (2026-09-26): bg/accent
    // presi da index.html :root[data-theme="..."] di ognuno dei 13 temi. "border"
    // e' il colore dell'anello attorno al pallino colore: di norma coincide col
    // bg del tema (invisibile su sfondo nero), tranne "black" (Black Glass) che
    // ha lo stesso identico accent di "blackamoled" e sarebbe indistinguibile —
    // bordo chiaro apposta solo per quello, su richiesta esplicita dell'utente.
    data class ThemeSwatch(val bg: String, val accent: String, val border: String)

    val SWATCHES: Map<String, ThemeSwatch> = mapOf(
        "scuro" to ThemeSwatch("#111111", "#00d4ff", "#111111"),
        "chiaro" to ThemeSwatch("#eef1f5", "#0090c8", "#eef1f5"),
        "black" to ThemeSwatch("#000000", "#00e5ff", "#dddddd"),
        "blackamoled" to ThemeSwatch("#000000", "#00e5ff", "#000000"),
        "blu" to ThemeSwatch("#000000", "#3fa9ff", "#000000"),
        "green" to ThemeSwatch("#000000", "#2fdb85", "#000000"),
        "pink" to ThemeSwatch("#000000", "#ff5cad", "#000000"),
        "red" to ThemeSwatch("#000000", "#ff5252", "#000000"),
        "violet" to ThemeSwatch("#000000", "#b388ff", "#000000"),
        "orange" to ThemeSwatch("#000000", "#ff9800", "#000000"),
        "teal" to ThemeSwatch("#000000", "#00bcd4", "#000000"),
        "gold" to ThemeSwatch("#000000", "#ffc107", "#000000"),
        "notte" to ThemeSwatch("#060d1a", "#4fd8ff", "#060d1a"),
    )


    fun setTheme(context: Context, theme: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("theme", theme).apply()
    }
}
