package it.meteomottavisconti

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private val splashDelayMillis = 1600L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Se l'app e' stata aperta da un App Link (link toccato altrove, non
        // digitato in Chrome), inoltra l'URL esatto a MainActivity invece di
        // aprire sempre e solo la home.
        val deepLinkUri = intent?.data

        Handler(Looper.getMainLooper()).postDelayed({
            val mainIntent = Intent(this, MainActivity::class.java)
            if (deepLinkUri != null) {
                mainIntent.data = deepLinkUri
            }
            startActivity(mainIntent)
            finish()
        }, splashDelayMillis)
    }
}
