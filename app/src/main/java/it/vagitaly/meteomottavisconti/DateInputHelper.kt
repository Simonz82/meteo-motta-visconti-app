package it.vagitaly.meteomottavisconti

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import java.util.Calendar

// Gestisce l'inserimento della data di nascita come tre campi separati
// (giorno/mese/anno) con avanzamento automatico del focus — piu' veloce
// da compilare rispetto a un selettore calendario.
object DateInputHelper {

    fun setup(day: EditText, month: EditText, year: EditText) {
        day.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (s?.length == 2) month.requestFocus()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        month.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (s?.length == 2) year.requestFocus()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    // Combina i tre campi in una data ISO "YYYY-MM-DD", o null se incompleta/non valida.
    fun combine(day: EditText, month: EditText, year: EditText): String? {
        val d = day.text.toString().toIntOrNull() ?: return null
        val m = month.text.toString().toIntOrNull() ?: return null
        val y = year.text.toString().toIntOrNull() ?: return null
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        if (d !in 1..31 || m !in 1..12 || y < 1900 || y > currentYear) return null
        return String.format("%04d-%02d-%02d", y, m, d)
    }

    // Restituisce true solo se tutti e tre i campi sono vuoti (nessun tentativo di inserimento).
    fun isEmpty(day: EditText, month: EditText, year: EditText): Boolean {
        return day.text.isEmpty() && month.text.isEmpty() && year.text.isEmpty()
    }

    // Precompila i tre campi da una data ISO "YYYY-MM-DD".
    fun populate(iso: String, day: EditText, month: EditText, year: EditText) {
        val parts = iso.split("-")
        if (parts.size != 3) return
        year.setText(parts[0])
        month.setText(parts[1])
        day.setText(parts[2])
    }
}
