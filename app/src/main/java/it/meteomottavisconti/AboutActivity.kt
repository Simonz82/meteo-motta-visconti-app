package it.meteomottavisconti

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val versionText: TextView = findViewById(R.id.versionText)
        val updateStatusText: TextView = findViewById(R.id.updateStatusText)
        val updateCard: android.widget.LinearLayout = findViewById(R.id.updateCard)
        val updateVersionText: TextView = findViewById(R.id.updateVersionText)
        val btnUpdateNow: Button = findViewById(R.id.btnUpdateNow)
        var installedVersionName = ""
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            installedVersionName = pInfo.versionName ?: ""
            versionText.text = getString(R.string.about_version_format, installedVersionName)
        } catch (e: Exception) {
            versionText.text = ""
        }

        findViewById<TextView>(R.id.changelogLink).setOnClickListener {
            val uri = Uri.parse("https://meteo.nas.vagitaly.it/changelog.html")
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }

        // Oggetto con prefisso diverso per categoria: permette di smistare/filtrare
        // le email in arrivo (supporto vs proposte) senza altra infrastruttura.
        findViewById<TextView>(R.id.supportLink).setOnClickListener {
            val body = getString(
                R.string.support_email_body,
                installedVersionName,
                android.os.Build.MANUFACTURER,
                android.os.Build.MODEL,
                android.os.Build.VERSION.RELEASE
            )
            sendCategorizedEmail(getString(R.string.support_email_subject), body)
        }

        findViewById<TextView>(R.id.proposeLink).setOnClickListener {
            sendCategorizedEmail(getString(R.string.propose_email_subject), "")
        }

        checkForUpdate(updateStatusText, updateCard, updateVersionText, btnUpdateNow)
    }

    private fun sendCategorizedEmail(subject: String, body: String) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(getString(R.string.support_email_address)))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            if (body.isNotEmpty()) putExtra(Intent.EXTRA_TEXT, body)
        }
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.email_chooser_title)))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.email_no_app_error), Toast.LENGTH_SHORT).show()
        }
    }

    // Come MainActivity.checkForUpdate(): chiede direttamente a Google Play,
    // non piu' al sito (latest_version.json non esiste piu' come fonte per
    // l'app, l'APK libero non e' piu' distribuito). Play Core non espone il
    // numero della versione disponibile, solo se ce n'e' una piu' nuova.
    private fun checkForUpdate(
        updateStatusText: TextView,
        updateCard: android.widget.LinearLayout,
        updateVersionText: TextView,
        btnUpdateNow: Button
    ) {
        val appUpdateManager = AppUpdateManagerFactory.create(this)
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) {
                updateStatusText.text = getString(R.string.about_up_to_date)
                updateStatusText.visibility = TextView.VISIBLE
                return@addOnSuccessListener
            }

            updateVersionText.text = getString(R.string.about_update_available_playstore)
            updateCard.visibility = android.widget.LinearLayout.VISIBLE
            btnUpdateNow.setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
                } catch (e: Exception) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
                    } catch (e2: Exception) {
                        Toast.makeText(this, "Impossibile aprire Play Store", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }.addOnFailureListener {
            // Nessuna connessione a Play Services o app non installata da Play Store
            // (es. durante lo sviluppo): non mostriamo nulla, ne' errore ne' "aggiornata".
        }
    }
}
