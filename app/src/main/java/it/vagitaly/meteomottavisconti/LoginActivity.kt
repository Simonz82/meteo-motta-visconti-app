package it.vagitaly.meteomottavisconti

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

class LoginActivity : AppCompatActivity() {

    private var isRegisterMode = false
    private var dataNascita: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val emailInput: EditText = findViewById(R.id.emailInput)
        val passwordInput: EditText = findViewById(R.id.passwordInput)
        val nomeInput: EditText = findViewById(R.id.nomeInput)
        val cognomeInput: EditText = findViewById(R.id.cognomeInput)
        val btnDataNascita: Button = findViewById(R.id.btnDataNascita)
        val errorText: TextView = findViewById(R.id.errorText)
        val btnPrimary: Button = findViewById(R.id.btnPrimary)
        val progressBar: ProgressBar = findViewById(R.id.progressBar)
        val toggleModeText: TextView = findViewById(R.id.toggleModeText)
        val titleText: TextView = findViewById(R.id.titleText)
        val btnBiometric: Button = findViewById(R.id.btnBiometric)

        AccountManager.getRememberedEmail(this)?.let { emailInput.setText(it) }

        if (BiometricAuth.isEnabled(this) && BiometricAuth.isAvailable(this)) {
            btnBiometric.visibility = Button.VISIBLE
        }
        btnBiometric.setOnClickListener {
            BiometricAuth.authenticate(this, onSuccess = { bioEmail, bioPassword ->
                progressBar.visibility = ProgressBar.VISIBLE
                ApiClient.login(bioEmail, bioPassword) { result ->
                    progressBar.visibility = ProgressBar.GONE
                    if (result.success) {
                        val token = result.json.optString("token")
                        val userId = result.json.optInt("user_id")
                        AccountManager.save(this, token, userId, bioEmail)
                        AccountManager.rememberEmail(this, bioEmail)
                        FcmHelper.registerCurrentToken(this)
                        Toast.makeText(this, getString(R.string.login_success), Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this, getString(R.string.error_network), Toast.LENGTH_SHORT).show()
                    }
                }
            }, onError = { message ->
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            })
        }

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<TextView>(R.id.forgotPasswordText).setOnClickListener {
            showForgotPasswordDialog(emailInput.text.toString().trim())
        }

        btnDataNascita.setOnClickListener {
            showDatePicker { picked ->
                dataNascita = picked
                btnDataNascita.text = formatDataDisplay(picked) ?: picked
            }
        }

        toggleModeText.setOnClickListener {
            isRegisterMode = !isRegisterMode
            errorText.visibility = TextView.GONE
            val visibility = if (isRegisterMode) EditText.VISIBLE else EditText.GONE
            nomeInput.visibility = visibility
            cognomeInput.visibility = visibility
            btnDataNascita.visibility = visibility
            if (isRegisterMode) {
                titleText.text = getString(R.string.register_title)
                btnPrimary.text = getString(R.string.register_button)
                toggleModeText.text = getString(R.string.switch_to_login)
            } else {
                titleText.text = getString(R.string.login_title)
                btnPrimary.text = getString(R.string.login_button)
                toggleModeText.text = getString(R.string.switch_to_register)
            }
        }

        btnPrimary.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()

            errorText.visibility = TextView.GONE

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                showError(errorText, getString(R.string.error_invalid_email))
                return@setOnClickListener
            }
            if (password.length < 6) {
                showError(errorText, getString(R.string.error_short_password))
                return@setOnClickListener
            }

            progressBar.visibility = ProgressBar.VISIBLE
            btnPrimary.isEnabled = false

            val onResult: (ApiClient.ApiResult) -> Unit = { result ->
                progressBar.visibility = ProgressBar.GONE
                btnPrimary.isEnabled = true
                if (result.success) {
                    val token = result.json.optString("token")
                    val userId = result.json.optInt("user_id")
                    AccountManager.save(this, token, userId, email)
                    AccountManager.rememberEmail(this, email)
                    FcmHelper.registerCurrentToken(this)
                    Toast.makeText(this, getString(R.string.login_success), Toast.LENGTH_SHORT).show()
                    maybeOfferBiometric(email, password)
                } else {
                    val error = result.json.optString("error", "unknown_error")
                    showError(errorText, mapError(error))
                }
            }

            if (isRegisterMode) {
                ApiClient.register(
                    email, password,
                    nomeInput.text.toString().trim(),
                    cognomeInput.text.toString().trim(),
                    dataNascita,
                    onResult
                )
            } else {
                ApiClient.login(email, password, onResult)
            }
        }
    }

    private fun maybeOfferBiometric(email: String, password: String) {
        if (!BiometricAuth.isAvailable(this) || BiometricAuth.isEnabled(this)) {
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.biometric_enable_dialog_title)
            .setMessage(R.string.biometric_enable_dialog_message)
            .setPositiveButton(R.string.biometric_enable_yes) { _, _ ->
                BiometricAuth.enable(this, email, password) { success, error ->
                    if (!success && error != null) {
                        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                    }
                    finish()
                }
            }
            .setNegativeButton(R.string.biometric_enable_no) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
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

    private fun formatDataDisplay(iso: String): String? {
        val parts = iso.split("-")
        if (parts.size != 3) return null
        return try {
            "${parts[2].toInt().toString().padStart(2, '0')}/${parts[1].toInt().toString().padStart(2, '0')}/${parts[0]}"
        } catch (e: Exception) {
            null
        }
    }

    private fun showForgotPasswordDialog(prefillEmail: String) {
        val input = EditText(this)
        input.hint = getString(R.string.email_hint)
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        if (prefillEmail.isNotEmpty()) input.setText(prefillEmail)

        AlertDialog.Builder(this)
            .setTitle(R.string.forgot_password_dialog_title)
            .setMessage(R.string.forgot_password_dialog_message)
            .setView(input)
            .setPositiveButton(R.string.forgot_password_send) { _, _ ->
                val email = input.text.toString().trim()
                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    Toast.makeText(this, getString(R.string.error_invalid_email), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                ApiClient.requestPasswordReset(email) { result ->
                    val message = if (result.success) {
                        result.json.optString("message", getString(R.string.forgot_password_sent))
                    } else {
                        getString(R.string.error_network)
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showError(errorText: TextView, message: String) {
        errorText.text = message
        errorText.visibility = TextView.VISIBLE
    }

    private fun mapError(error: String): String = when (error) {
        "email_already_registered" -> getString(R.string.error_email_taken)
        "invalid_credentials" -> getString(R.string.error_invalid_credentials)
        "network_error" -> getString(R.string.error_network)
        else -> getString(R.string.error_generic)
    }
}
