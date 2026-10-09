package it.meteomottavisconti

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class AccountActivity : AppCompatActivity() {

    companion object {
        // Passato da LoginActivity dopo un primo accesso con Google: citta' e data di
        // nascita non arrivano da Google e vanno chieste subito, come nella
        // registrazione via email. Finche' non sono compilate, questa schermata non
        // si puo' chiudere senza salvare (vedi onBackPressed sotto).
        const val EXTRA_FORCE_COMPLETE_PROFILE = "force_complete_profile"
    }

    private var forceCompleteProfile = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account)
        forceCompleteProfile = intent.getBooleanExtra(EXTRA_FORCE_COMPLETE_PROFILE, false)

        // Stesso fix IME di LoginActivity: con l'edge-to-edge forzato da targetSdk 36,
        // adjustResize da solo non fa piu' scorrere il form quando la tastiera si apre.
        val accountScroll: android.widget.ScrollView = findViewById(R.id.accountScroll)
        ViewCompat.setOnApplyWindowInsetsListener(accountScroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }

        val btnBack = findViewById<android.widget.ImageButton>(R.id.btnBack)
        if (forceCompleteProfile) {
            // Niente via di fuga finche' il profilo non e' completo: nascondiamo la X
            // e ignoriamo il tasto Indietro di sistema (vedi onBackPressed sotto).
            btnBack.visibility = android.view.View.GONE
            findViewById<TextView>(R.id.completeProfileBanner).visibility = android.view.View.VISIBLE
        } else {
            btnBack.setOnClickListener { finish() }
        }

        val emailText = findViewById<TextView>(R.id.emailText)
        val nomeInput = findViewById<EditText>(R.id.nomeInput)
        val cognomeInput = findViewById<EditText>(R.id.cognomeInput)
        val cittaInput = findViewById<EditText>(R.id.cittaInput)
        val dayInput = findViewById<EditText>(R.id.dayInput)
        val monthInput = findViewById<EditText>(R.id.monthInput)
        val yearInput = findViewById<EditText>(R.id.yearInput)
        DateInputHelper.setup(dayInput, monthInput, yearInput)
        val ageText = findViewById<TextView>(R.id.ageText)
        val noteInput = findViewById<EditText>(R.id.noteInput)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val contentGroup = findViewById<android.widget.LinearLayout>(R.id.contentGroup)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnLogout = findViewById<Button>(R.id.btnLogout)

        val editNomeLink = findViewById<TextView>(R.id.editNomeLink)
        val editCognomeLink = findViewById<TextView>(R.id.editCognomeLink)
        val editDataLink = findViewById<TextView>(R.id.editDataLink)
        val editCittaLink = findViewById<TextView>(R.id.editCittaLink)

        // Campi bloccati di default (mostrano il valore ma non si modificano per
        // sbaglio); un tocco sul link "Modifica" a fianco li sblocca. Le note
        // restano sempre libere, non hanno bisogno di questa protezione.
        fun lockField(vararg fields: EditText) {
            fields.forEach {
                it.isFocusable = false
                it.isFocusableInTouchMode = false
                it.isCursorVisible = false
                it.alpha = 0.65f
            }
        }
        fun unlockField(vararg fields: EditText) {
            fields.forEach {
                it.isFocusable = true
                it.isFocusableInTouchMode = true
                it.isCursorVisible = true
                it.alpha = 1f
            }
            fields.first().requestFocus()
        }

        editNomeLink.setOnClickListener {
            unlockField(nomeInput)
            editNomeLink.visibility = android.view.View.GONE
        }
        editCognomeLink.setOnClickListener {
            unlockField(cognomeInput)
            editCognomeLink.visibility = android.view.View.GONE
        }
        editDataLink.setOnClickListener {
            unlockField(dayInput, monthInput, yearInput)
            editDataLink.visibility = android.view.View.GONE
        }
        editCittaLink.setOnClickListener {
            unlockField(cittaInput)
            editCittaLink.visibility = android.view.View.GONE
        }

        // L'eta' e' calcolata dal server (fonte unica di verita', vedi calcola_eta
        // in db.php); il calcolo locale resta solo come fallback difensivo.
        fun aggiornaEta(json: org.json.JSONObject, iso: String) {
            val eta = if (json.has("eta") && !json.isNull("eta")) json.optInt("eta") else DateInputHelper.calculateAge(iso)
            ageText.text = if (eta != null && eta >= 0) getString(R.string.account_age_format, eta) else ""
        }

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
                nomeInput.setText(NameFormatter.capitalizeWords(result.json.optString("nome", "")))
                cognomeInput.setText(NameFormatter.capitalizeWords(result.json.optString("cognome", "")))
                cittaInput.setText(NameFormatter.capitalizeWords(result.json.optString("citta", "")))
                val data = result.json.optString("data_nascita", "")
                if (data.isNotEmpty()) {
                    DateInputHelper.populate(data, dayInput, monthInput, yearInput)
                    aggiornaEta(result.json, data)
                }
                noteInput.setText(result.json.optString("note", ""))
                contentGroup.visibility = android.view.View.VISIBLE

                // I campi precompilati partono bloccati (vedi lockField sopra).
                lockField(nomeInput, cognomeInput, cittaInput, dayInput, monthInput, yearInput)
                if (forceCompleteProfile) {
                    // Citta' e data di nascita sono quelle che mancano davvero (Google non
                    // le fornisce): le lasciamo subito sbloccate e pronte da compilare,
                    // nascondendo i rispettivi link "Modifica" che non servono piu'.
                    unlockField(cittaInput)
                    unlockField(dayInput, monthInput, yearInput)
                    editCittaLink.visibility = android.view.View.GONE
                    editDataLink.visibility = android.view.View.GONE
                }
                // Precompilare i campi data sposta il focus da un campo all'altro
                // (avanzamento automatico pensato per la digitazione manuale):
                // qui riportiamo il focus sul contenitore cosi' non resta un
                // cursore lampeggiante visibile appena si apre la schermata.
                contentGroup.requestFocus()
            } else if (result.statusCode == 401) {
                sessionExpired()
            } else {
                Toast.makeText(this, getString(R.string.error_network), Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        btnSave.setOnClickListener {
            val nome = NameFormatter.capitalizeWords(nomeInput.text.toString().trim())
            val cognome = NameFormatter.capitalizeWords(cognomeInput.text.toString().trim())
            val citta = NameFormatter.capitalizeWords(cittaInput.text.toString().trim())
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

            if (forceCompleteProfile) {
                // Stessi campi obbligatori della registrazione via email (vedi
                // register.php) - qui mancano solo perche' Google non li fornisce.
                if (citta.isEmpty()) {
                    Toast.makeText(this, getString(R.string.error_citta_required), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (dataNascita == null) {
                    Toast.makeText(this, getString(R.string.error_birth_date_required), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            btnSave.isEnabled = false
            ApiClient.updateAccount(token, nome, cognome, dataNascita, citta, note) { result ->
                btnSave.isEnabled = true
                if (result.success) {
                    nomeInput.setText(nome)
                    cognomeInput.setText(cognome)
                    cittaInput.setText(citta)
                    AccountManager.updateName(this, nome)
                    if (dataNascita != null) aggiornaEta(result.json, dataNascita) else ageText.text = ""
                    Toast.makeText(this, getString(R.string.account_save_success), Toast.LENGTH_SHORT).show()
                    // Profilo completato: da qui in poi e' un account a tutti gli effetti,
                    // si torna alla schermata principale come dopo una registrazione normale.
                    if (forceCompleteProfile) finish()
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

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (forceCompleteProfile) {
            // Stesso blocco della X nascosta sopra: il profilo va completato prima
            // di poter uscire da qui, coerente col fatto che questi campi sono
            // obbligatori anche nella registrazione via email.
            Toast.makeText(this, getString(R.string.account_complete_profile_banner), Toast.LENGTH_SHORT).show()
            return
        }
        super.onBackPressed()
    }

    private fun sessionExpired() {
        AccountManager.logout(this)
        Toast.makeText(this, getString(R.string.session_expired_message), Toast.LENGTH_LONG).show()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
