package it.meteomottavisconti

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_START_IN_REGISTER_MODE = "start_in_register_mode"
    }

    private var isRegisterMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        // Se l'activity viene ricreata mentre e' aperto il selettore account di Google il risultato
        // andrebbe perso: lo segnaliamo al server per capirlo.
        if (savedInstanceState != null) ApiClient.googleDiag("activity_recreated", "savedInstanceState presente")

        // Con l'edge-to-edge forzato da targetSdk 36 (vedi lo stesso fix in
        // MainActivity), windowSoftInputMode="adjustResize" da solo non basta piu':
        // senza applicare qui l'inset della tastiera come padding, i campi in fondo
        // al form (citta', data di nascita) restano coperti e non scrollabili quando
        // la tastiera si apre.
        val loginScroll: android.widget.ScrollView = findViewById(R.id.loginScroll)
        ViewCompat.setOnApplyWindowInsetsListener(loginScroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }

        val emailInput: EditText = findViewById(R.id.emailInput)
        val passwordInput: EditText = findViewById(R.id.passwordInput)
        val nomeInput: EditText = findViewById(R.id.nomeInput)
        val cognomeInput: EditText = findViewById(R.id.cognomeInput)
        val cittaInput: EditText = findViewById(R.id.cittaInput)
        val dataNascitaLabel: TextView = findViewById(R.id.dataNascitaLabel)
        val dataNascitaRow: android.widget.LinearLayout = findViewById(R.id.dataNascitaRow)
        val dayInput: EditText = findViewById(R.id.dayInput)
        val monthInput: EditText = findViewById(R.id.monthInput)
        val yearInput: EditText = findViewById(R.id.yearInput)
        DateInputHelper.setup(dayInput, monthInput, yearInput)
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
                        AccountManager.save(this, token, userId, bioEmail, result.json.optString("nome", ""))
                        AccountManager.rememberEmail(this, bioEmail)
                        FcmHelper.registerCurrentToken(this)
                        DeviceInfoHelper.sendIfLoggedIn(this)
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

        // "Continua con Google" funziona identico in modalita' login o registrazione:
        // il backend decide da solo se e' un accesso a un account gia' collegato o
        // una registrazione nuova (vedi google_login.php), quindi il pulsante resta
        // sempre lo stesso in entrambe le modalita'.
        findViewById<Button>(R.id.btnGoogle).setOnClickListener {
            signInWithGoogle(progressBar, errorText)
        }

        fun applyMode() {
            errorText.visibility = TextView.GONE
            val visibility = if (isRegisterMode) EditText.VISIBLE else EditText.GONE
            nomeInput.visibility = visibility
            cognomeInput.visibility = visibility
            dataNascitaLabel.visibility = visibility
            dataNascitaRow.visibility = visibility
            cittaInput.visibility = visibility
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

        // Chi arriva dall'invito alla registrazione al primo avvio (MainActivity)
        // vede subito il modulo di registrazione, senza dover toccare "Registrati".
        if (intent.getBooleanExtra(EXTRA_START_IN_REGISTER_MODE, false)) {
            isRegisterMode = true
            applyMode()
        }

        toggleModeText.setOnClickListener {
            isRegisterMode = !isRegisterMode
            applyMode()
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
            var dataNascita: String? = null
            if (isRegisterMode) {
                if (nomeInput.text.toString().trim().isEmpty()) {
                    showError(errorText, getString(R.string.error_nome_required))
                    return@setOnClickListener
                }
                if (cognomeInput.text.toString().trim().isEmpty()) {
                    showError(errorText, getString(R.string.error_cognome_required))
                    return@setOnClickListener
                }
                if (cittaInput.text.toString().trim().isEmpty()) {
                    showError(errorText, getString(R.string.error_citta_required))
                    return@setOnClickListener
                }
                dataNascita = DateInputHelper.combine(dayInput, monthInput, yearInput)
                if (dataNascita == null) {
                    showError(errorText, getString(R.string.error_birth_date_required))
                    return@setOnClickListener
                }
            }

            progressBar.visibility = ProgressBar.VISIBLE
            btnPrimary.isEnabled = false

            val onResult: (ApiClient.ApiResult) -> Unit = { result ->
                progressBar.visibility = ProgressBar.GONE
                btnPrimary.isEnabled = true
                if (result.success) {
                    val token = result.json.optString("token")
                    val userId = result.json.optInt("user_id")
                    AccountManager.save(this, token, userId, email, result.json.optString("nome", ""))
                    AccountManager.rememberEmail(this, email)
                    FcmHelper.registerCurrentToken(this)
                    DeviceInfoHelper.sendIfLoggedIn(this)
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
                    NameFormatter.capitalizeWords(nomeInput.text.toString().trim()),
                    NameFormatter.capitalizeWords(cognomeInput.text.toString().trim()),
                    dataNascita,
                    NameFormatter.capitalizeWords(cittaInput.text.toString().trim()),
                    onResult
                )
            } else {
                ApiClient.login(email, password, onResult)
            }
        }
    }

    // Avvia il selettore account di Google (Credential Manager) e, se l'utente ne
    // sceglie uno, manda il token risultante al backend (google_login.php) che
    // decide da solo se collegare/creare/loggare l'account - qui gestiamo solo
    // l'esito. filterByAuthorizedAccounts(false) mostra tutti gli account Google
    // presenti sul telefono, non solo quelli gia' usati in passato con questa app
    // (altrimenti un utente nuovo non vedrebbe alcuna opzione la prima volta).
    // Riferimenti usati dal ritorno del metodo "classico" (startActivityForResult).
    private var googleCmError: String = ""

    private val legacyGoogleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        // Le view si cercano ORA (non da campi salvati): cosi' il risultato non si perde mai.
        val progress = findViewById<ProgressBar>(R.id.progressBar)
        val errorText = findViewById<TextView>(R.id.errorText)
        ApiClient.googleDiag("legacy_result", "resultCode=${res.resultCode} hasData=${res.data != null}")
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(res.data).getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken.isNullOrEmpty()) {
                progress.visibility = ProgressBar.GONE
                ApiClient.googleDiag("legacy_no_token", "account ok ma idToken vuoto")
                showError(errorText, "Google (classico): token vuoto. $googleCmError")
            } else {
                ApiClient.googleDiag("legacy_ok", "token ricevuto")
                finishGoogleLogin(idToken, progress, errorText)
            }
        } catch (e: ApiException) {
            progress.visibility = ProgressBar.GONE
            android.util.Log.e("GoogleSignIn", "Metodo classico fallito, codice=${e.statusCode}", e)
            ApiClient.googleDiag("legacy_error", "codice=${e.statusCode} ${GoogleSignInStatusCodes.getStatusCodeString(e.statusCode)}")
            // Mai in silenzio, neanche per l'annullamento: 12501 dopo la scelta dell'account e'
            // proprio il sintomo da capire (di solito firma/client ID non riconosciuti).
            val annullato = if (e.statusCode == GoogleSignInStatusCodes.SIGN_IN_CANCELLED) "annullato o non completato" else "errore"
            showError(errorText, "Google (classico): $annullato, codice ${e.statusCode} ${GoogleSignInStatusCodes.getStatusCodeString(e.statusCode)}. Prima: $googleCmError")
        }
    }

    // Diagnostica: impronta SHA-1 del certificato con cui e' firmata l'app installata e da dove e' stata
    // installata. Serve a confrontarla con quelle registrate in Firebase (DEVELOPER_ERROR = non coincidono).
    private fun firmaInfo(): String {
        return try {
            val info = packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
            val sig = info.signingInfo?.apkContentsSigners?.firstOrNull()
            val sha1 = if (sig == null) "?" else java.security.MessageDigest.getInstance("SHA-1").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
            val inst = if (android.os.Build.VERSION.SDK_INT >= 30) {
                packageManager.getInstallSourceInfo(packageName).installingPackageName ?: "sconosciuto"
            } else {
                @Suppress("DEPRECATION") packageManager.getInstallerPackageName(packageName) ?: "sconosciuto"
            }
            "cert_sha1=$sha1 installer=$inst pkg=$packageName"
        } catch (e: Exception) {
            "firma_non_leggibile=${e::class.simpleName}"
        }
    }

    private fun signInWithGoogle(progressBar: ProgressBar, errorText: TextView) {
        errorText.visibility = TextView.GONE
        googleCmError = ""
        ApiClient.googleDiag("cm_start", "avvio del flusso " + firmaInfo())
        // Flusso pensato per un pulsante "Accedi con Google" toccato dall'utente: mostra sempre
        // il selettore account. (GetGoogleIdOption e' per l'accesso automatico e su questi
        // telefoni rispondeva sempre TYPE_NO_CREDENTIAL.)
        val signInOption = GetSignInWithGoogleOption.Builder(GoogleAuthConfig.WEB_CLIENT_ID).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()
        val credentialManager = CredentialManager.create(this)

        progressBar.visibility = ProgressBar.VISIBLE
        lifecycleScope.launch {
            try {
                val response = credentialManager.getCredential(this@LoginActivity, request)
                val credential = response.credential
                android.util.Log.i("GoogleSignIn", "Credenziale ricevuta, tipo: ${credential.type}, classe: ${credential::class.simpleName}")
                ApiClient.googleDiag("cm_ok", "tipo=${credential.type}")
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        finishGoogleLogin(googleIdTokenCredential.idToken, progressBar, errorText)
                    } catch (e: Exception) {
                        progressBar.visibility = ProgressBar.GONE
                        android.util.Log.e("GoogleSignIn", "Errore nel parsing del token Google", e)
                        showError(errorText, "Parsing token: ${e::class.simpleName} - ${e.message}")
                    }
                } else {
                    progressBar.visibility = ProgressBar.GONE
                    showError(errorText, "Tipo credenziale inatteso: ${credential.type}")
                }
            } catch (e: GetCredentialCancellationException) {
                // Puo' essere l'utente che ha chiuso il selettore, ma anche un fallimento subito dopo
                // la scelta dell'account: lo mostriamo sempre, con il tipo, per non restare al buio.
                val dettaglio = "${e.type} - ${e.errorMessage ?: e.message ?: ""}"
                ApiClient.googleDiag("cm_cancel", dettaglio)
                if (dettaglio.contains("reauth", ignoreCase = true) || dettaglio.contains("[16]")) {
                    // Play Services rifiuta l'account appena scelto ("Account reauth failed"): non e' un
                    // vero annullamento dell'utente. Il metodo classico sa richiedere la nuova verifica
                    // dell'account, oppure da' un codice d'errore preciso.
                    googleCmError = dettaglio
                    startLegacyGoogleSignIn(progressBar, errorText)
                } else {
                    progressBar.visibility = ProgressBar.GONE
                    showError(errorText, "Google: accesso annullato o non completato (${e.type})")
                }
            } catch (e: GetCredentialException) {
                android.util.Log.e("GoogleSignIn", "GetCredentialException tipo=${e.type}", e)
                googleCmError = "${e.type} - ${e.errorMessage ?: e.message ?: "nessun dettaglio"}"
                ApiClient.googleDiag("cm_error", googleCmError)
                // Rete di sicurezza: metodo "classico" di Google Sign-In (Play Services). Se anche
                // questo fallisce, il codice d'errore dice finalmente qual e' la causa reale.
                startLegacyGoogleSignIn(progressBar, errorText)
            }
        }
    }

    private fun startLegacyGoogleSignIn(progressBar: ProgressBar, errorText: TextView) {
        ApiClient.googleDiag("legacy_start", "fallback al metodo classico")
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(GoogleAuthConfig.WEB_CLIENT_ID)
                .requestEmail()
                .build()
            val client = GoogleSignIn.getClient(this, gso)
            // signOut() prima, cosi' appare sempre il selettore account.
            client.signOut().addOnCompleteListener {
                legacyGoogleLauncher.launch(client.signInIntent)
            }
        } catch (e: Exception) {
            progressBar.visibility = ProgressBar.GONE
            android.util.Log.e("GoogleSignIn", "Impossibile avviare il metodo classico", e)
            showError(errorText, "Google: $googleCmError | classico: ${e::class.simpleName} - ${e.message}")
        }
    }

    private fun finishGoogleLogin(idToken: String, progressBar: ProgressBar, errorText: TextView) {
        ApiClient.googleDiag("backend_call", "invio del token al server")
        ApiClient.googleLogin(idToken) { result ->
            progressBar.visibility = ProgressBar.GONE
            ApiClient.googleDiag("backend_result", "success=${result.success} status=${result.statusCode} error=${result.json.optString("error", "")}")
            android.util.Log.i("GoogleSignIn", "Risposta backend: success=${result.success} status=${result.statusCode} body=${result.json}")
            if (!result.success) {
                showError(errorText, "Backend: ${result.json.optString("error", "?")} (HTTP ${result.statusCode})")
            } else {
                handleGoogleLoginResult(result, errorText)
            }
        }
    }

    private fun handleGoogleLoginResult(result: ApiClient.ApiResult, errorText: TextView) {
        if (!result.success) {
            showError(errorText, getString(R.string.error_google_signin))
            return
        }
        val token = result.json.optString("token")
        val userId = result.json.optInt("user_id")
        val email = result.json.optString("email")
        val nome = result.json.optString("nome", "")
        val profileIncomplete = result.json.optBoolean("profile_incomplete", false)

        AccountManager.save(this, token, userId, email, nome)
        AccountManager.rememberEmail(this, email)
        FcmHelper.registerCurrentToken(this)
        DeviceInfoHelper.sendIfLoggedIn(this)

        if (profileIncomplete) {
            // Account nuovo (o gia' esistente ma senza citta'/data di nascita, es. da una
            // versione precedente): Google non fornisce questi dati, vanno chiesti subito,
            // esattamente come nella registrazione via email - vedi AccountActivity.
            startActivity(
                Intent(this, AccountActivity::class.java)
                    .putExtra(AccountActivity.EXTRA_FORCE_COMPLETE_PROFILE, true)
            )
        } else {
            Toast.makeText(this, getString(R.string.login_success), Toast.LENGTH_SHORT).show()
        }
        // Niente offerta di accesso biometrico qui: un account creato via Google non ha
        // una password utilizzabile (BiometricAuth.enable ripete /login.php con email+password).
        finish()
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
        "nome_required" -> getString(R.string.error_nome_required)
        "cognome_required" -> getString(R.string.error_cognome_required)
        "birth_date_required" -> getString(R.string.error_birth_date_required)
        "citta_required" -> getString(R.string.error_citta_required)
        else -> getString(R.string.error_generic)
    }
}
