package it.vagitaly.meteomottavisconti

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging

// Registra il token FCM del dispositivo sul backend, se l'utente e' loggato.
// Chiamato all'avvio dell'app e subito dopo un login/registrazione riuscita,
// cosi' il token e' associato all'utente il prima possibile.
object FcmHelper {
    fun registerCurrentToken(context: Context) {
        val authToken = AccountManager.getToken(context) ?: return
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val fcmToken = task.result
                ApiClient.registerDevice(authToken, fcmToken) { }
            }
        }
    }
}
