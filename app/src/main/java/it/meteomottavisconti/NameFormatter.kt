package it.meteomottavisconti

// Capitalizza la prima lettera di ogni parola (es. "motta visconti" ->
// "Motta Visconti"), cosi' nome/cognome/citta' sono sempre coerenti anche
// se l'utente digita tutto minuscolo o con una tastiera fisica che ignora
// il suggerimento "textCapWords" della tastiera virtuale.
object NameFormatter {
    fun capitalizeWords(input: String): String {
        return input.trim().split(Regex("\\s+")).joinToString(" ") { word ->
            if (word.isEmpty()) word else word[0].uppercaseChar() + word.substring(1)
        }
    }
}
