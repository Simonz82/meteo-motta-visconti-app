package it.vagitaly.meteomottavisconti

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

class AccountActivity : AppCompatActivity() {

    private var dataNascita: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account)

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val emailText = findViewById<TextView>(R.id.emailText)
        val nomeInput = findViewById<EditText>(R.id.nomeInput)
        val cognomeInput = findViewById<EditText>(R.id.cognomeInput)
        val btnDataNascita = findViewById<Button>(R.id.btnDataNascita)
        val noteInput = findViewById<EditText>(R.id.noteInput)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val contentGroup = findViewById<android.widget.LinearLayout>(R.id.contentGroup)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnLogout = findViewById<Button>(R.id.btnLogout)

        val token = AccountManager.getToken(this)
        if (token == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        btnDataNascita.setOnClickListener {
            showDatePicker { picked ->
                dataNascita = picked
                btnDataNascita.text = formatDataDisplay(picked) ?: picked
            }
        }

        ApiClient.getAccount(token) { result ->
            progressBar.visibility = android.view.View.GONE
            if (result.success) {
                emailText.text = result.json.optString("email")
                nomeInput.setText(result.json.optString("nome", ""))
                cognomeInput.setText(result.json.optString("cognome", ""))
                val data = result.json.optString("data_nascita", "")
                val display = formatDataDisplay(data)
                if (display != null) {
                    dataNascita = data
                    btnDataNascita.text = display
                }
                noteInput.setText(result.json.optString("note", ""))
                contentGroup.visibility = android.view.View.VISIBLE
            } else if (result.statusCode == 401) {
                sessionExpired()
            } else {
                Toast.makeText(this, getString(R.string.error_network), Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        btnSave.setOnClickListener {
            val nome = nomeInput.text.toString().trim()
            val cognome = cognomeInput.text.toString().trim()
            val note = noteInput.text.toString().trim()

            btnSave.isEnabled = false
            ApiClient.updateAccount(token, nome, cognome, dataNascita, note) { result ->
                btnSave.isEnabled = true
                if (result.success) {
                    Toast.makeText(this, getString(R.string.account_save_success), Toast.LENGTH_SHORT).show()
                } else if (result.statusCode == 401) {
                    sessionExpired()
                } else if (result.json.optString("error") == "invalid_birth_date") {
                    Toast.makeText(this, getString(R.string.error_invalid_birth_date), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, getString(R.string.error_network), Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.account_logout_confirm_title)
                .setMessage(R.string.account_logout_confirm_message)
                .setPositiveButton(R.string.menu_account_login_logout_confirm) { _, _ ->
                    AccountManager.logout(this)
                    BiometricAuth.disable(this)
                    Toast.makeText(this, "Disconnesso", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun showDatePicker(onPicked: (String) -> Unit) {
        val cal = Calendar.getInstance()
        cal.set(2000, 0, 1)
        try {
            dataNascita?.split("-")?.takeIf { it.size == 3 }?.let { parts ->
                cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            }
        } catch (e: Exception) {
            // valore non valido: usa il default 2000-01-01 già impostato sopra
        }
        val dialog = DatePickerDialog(this, { _, year, month, day ->
            onPicked(String.format("%04d-%02d-%02d", year, month + 1, day))
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
        dialog.datePicker.maxDate = System.currentTimeMillis()
        dialog.show()
    }

    // Ritorna null se "iso" non è una data completa valida (es. valore vuoto o malformato),
    // cosi' il chiamante puo' scegliere di non mostrare nulla invece di andare in crash.
    private fun formatDataDisplay(iso: String): String? {
        val parts = iso.split("-")
        if (parts.size != 3) return null
        return try {
            "${parts[2].toInt()}".padStart(2, '0') + "/" +
                "${parts[1].toInt()}".padStart(2, '0') + "/" + parts[0]
        } catch (e: Exception) {
            null
        }
    }

    private fun sessionExpired() {
        AccountManager.logout(this)
        Toast.makeText(this, getString(R.string.session_expired_message), Toast.LENGTH_LONG).show()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
