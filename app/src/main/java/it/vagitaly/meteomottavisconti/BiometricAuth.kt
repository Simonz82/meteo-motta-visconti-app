package it.vagitaly.meteomottavisconti

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Login con impronta/viso: la password viene cifrata con una chiave del Keystore
// hardware, utilizzabile SOLO dopo una verifica biometrica live (setUserAuthenticationRequired).
// Anche estraendo i dati dell'app, la password resta illeggibile senza il sensore del device.
object BiometricAuth {
    private const val PREFS = "biometric_prefs"
    private const val KEY_ALIAS = "meteo_biometric_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isAvailable(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean("enabled", false)

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun enable(activity: FragmentActivity, email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        } catch (e: Exception) {
            onResult(false, "Impossibile preparare la cifratura biometrica")
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                try {
                    val resultCipher = result.cryptoObject?.cipher!!
                    val encrypted = resultCipher.doFinal(password.toByteArray(Charsets.UTF_8))
                    prefs(activity).edit()
                        .putString("email", email)
                        .putString("password_enc", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                        .putString("iv", Base64.encodeToString(resultCipher.iv, Base64.NO_WRAP))
                        .putBoolean("enabled", true)
                        .apply()
                    onResult(true, null)
                } catch (e: Exception) {
                    onResult(false, "Errore durante il salvataggio biometrico")
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onResult(false, errString.toString())
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Abilita accesso biometrico")
            .setSubtitle("Conferma per attivare l'accesso rapido con impronta o volto")
            .setNegativeButtonText("Annulla")
            .build()

        prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    }

    fun authenticate(
        activity: FragmentActivity,
        onSuccess: (email: String, password: String) -> Unit,
        onError: (String) -> Unit
    ) {
        val ivString = prefs(activity).getString("iv", null)
        val encString = prefs(activity).getString("password_enc", null)
        val email = prefs(activity).getString("email", null)
        if (ivString == null || encString == null || email == null) {
            onError("Accesso biometrico non configurato")
            return
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            val iv = Base64.decode(ivString, Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        } catch (e: Exception) {
            disable(activity)
            onError("Accesso biometrico non più valido, accedi con la password")
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                try {
                    val resultCipher = result.cryptoObject?.cipher!!
                    val encrypted = Base64.decode(encString, Base64.NO_WRAP)
                    val password = String(resultCipher.doFinal(encrypted), Charsets.UTF_8)
                    onSuccess(email, password)
                } catch (e: Exception) {
                    onError("Errore durante la verifica biometrica")
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errString.toString())
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Accedi con impronta o volto")
            .setNegativeButtonText("Usa la password")
            .build()

        prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    }

    fun disable(context: Context) {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            keyStore.deleteEntry(KEY_ALIAS)
        } catch (e: Exception) {
            // ignora: se la chiave non esiste non c'e' nulla da rimuovere
        }
        prefs(context).edit().clear().apply()
    }
}
