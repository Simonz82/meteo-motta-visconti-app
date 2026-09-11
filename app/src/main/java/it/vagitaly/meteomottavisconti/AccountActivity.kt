package it.vagitaly.meteomottavisconti

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class AccountActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account)

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val emailText = findViewById<TextView>(R.id.emailText)
        val nomeInput = findViewById<EditText>(R.id.nomeInput)
        val cognomeInput = findViewById<EditText>(R.id.cognomeInput)
        val cittaInput = findViewById<EditText>(R.id.cittaInput)
        val dayInput = findViewById<EditText>(R.id.dayInput)
        val monthInput = findViewById<EditText>(R.id.monthInput)
        val yearInput = findViewById<EditText>(R.id.yearInput)
        DateInputHelper.setup(dayInput, monthInput, yearInput)
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

        findViewById<TextView>(R.id.changeEmailLink).setOnClickListener {
            showChangeEmailDialog(token, emailText)
        }

        ApiClient.getAccount(token) { result ->
            progressBar.visibility = android.view.View.GONE
            if (result.success) {
                emailText.text = result.json.optString("email")
                nomeInput.setText(result.json.optString("nome", ""))
                cognomeInput.setText(result.json.optString("cognome", ""))
                cittaInput.setText(result.json.optString("citta", ""))
                val data = result.json.optString("data_nascita", "")
                if (data.isNotEmpty()) {
                    DateInputHelper.populate(data, dayInput, monthInput, yearInput)
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
            val citta = cittaInput.text.toString().trim()
            val note = noteInput.text.toString().trim()
            val dataNascita = if (DateInputHelper.isEmpty(dayInput, monthInput, yearInput)) {
                null
            } else {
                val combined = DateInputHelper.combine(dayInput, monthInput, yearInput)
                if (combined == null) {
                    Toast.makeText(this, getString(R.string.error_invalid_birth_date), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                combined
            }

            btnSave.isEnabled = false
            ApiClient.updateAccount(token, nome, cognome, dataNascita, citta, note) { result ->
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
                    // L'accesso biometrico resta attivo dopo il logout: e' un'app
                    // meteo, non serve richiedere di nuovo la password ogni volta.
                    AccountManager.logout(this)
                    Toast.makeText(this, "Disconnesso", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun showChangeEmailDialog(token: String, emailText: TextView) {
        val container = android.widget.LinearLayout(this)
        container.orientation = android.widget.LinearLayout.VERTICAL
        val padding = (16 * resources.displayMetrics.density).toInt()
        container.setPadding(padding, padding, padding, 0)

        val newEmailInput = EditText(this)
        newEmailInput.hint = getString(R.string.change_email_new_hint)
        newEmailInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        container.addView(newEmailInput)

        val passwordInput = EditText(this)
        passwordInput.hint = getString(R.string.change_email_password_hint)
        passwordInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        container.addView(passwordInput)

        AlertDialog.Builder(this)
            .setTitle(R.string.change_email_dialog_title)
            .setView(container)
            .setPositiveButton(R.string.change_email_confirm) { _, _ ->
                val newEmail = newEmailInput.text.toString().trim()
                val password = passwordInput.text.toString()
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
                    Toast.makeText(this, getString(R.string.error_invalid_email), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (password.isEmpty()) {
                    Toast.makeText(this, getString(R.string.error_missing_password), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                ApiClient.changeEmail(token, newEmail, password) { result ->
                    if (result.success) {
                        val updatedEmail = result.json.optString("email", newEmail)
                        emailText.text = updatedEmail
                        AccountManager.rememberEmail(this, updatedEmail)
                        Toast.makeText(this, getString(R.string.change_email_success), Toast.LENGTH_SHORT).show()
                    } else {
                        val message = when (result.json.optString("error")) {
                            "wrong_password" -> getString(R.string.error_wrong_password)
                            "same_email" -> getString(R.string.error_same_email)
                            "email_already_used" -> getString(R.string.error_email_already_used)
                            "too_many_attempts" -> getString(R.string.error_too_many_attempts)
                            "invalid_email" -> getString(R.string.error_invalid_email)
                            else -> getString(R.string.error_network)
                        }
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun sessionExpired() {
        AccountManager.logout(this)
        Toast.makeText(this, getString(R.string.session_expired_message), Toast.LENGTH_LONG).show()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
