package it.meteomottavisconti

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import org.json.JSONObject

class NotificationSettingsActivity : AppCompatActivity() {

    private var orarioDaMinuti = 480
    private var orarioAMinuti = 1380

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_settings)

        // Fix 2026-09-26: schermo mai messo in sicurezza per l'edge-to-edge
        // forzato da targetSdk 36 (stesso problema gia' risolto a suo tempo su
        // MainActivity/LoginActivity/AccountActivity, ma non su questo schermo).
        // La X sotto la status bar e il contenuto tagliato dalla barra di
        // sistema in basso erano dovuti a questo, non al restyle di ieri.
        val notifScroll: android.widget.ScrollView = findViewById(R.id.notifScroll)
        ViewCompat.setOnApplyWindowInsetsListener(notifScroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val loggedOutGroup = findViewById<android.widget.LinearLayout>(R.id.loggedOutGroup)
        val loggedInGroup = findViewById<android.widget.LinearLayout>(R.id.loggedInGroup)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        val token = AccountManager.getToken(this)

        if (token == null) {
            loggedOutGroup.visibility = android.view.View.VISIBLE
            progressBar.visibility = android.view.View.GONE
            findViewById<android.widget.Button>(R.id.btnGoLogin).setOnClickListener {
                startActivity(Intent(this, LoginActivity::class.java))
            }
            return
        }

        // 2026-09-25: CheckBox -> SwitchMaterial (restyle in stile "card" come la
        // dashboard web), stessa API isChecked, nessun'altra modifica di logica.
        val checkAllerta = findViewById<SwitchMaterial>(R.id.checkAllerta)
        val checkFulmine = findViewById<SwitchMaterial>(R.id.checkFulmine)
        val checkPioggia = findViewById<SwitchMaterial>(R.id.checkPioggia)
        val checkUv = findViewById<SwitchMaterial>(R.id.checkUv)
        val checkRecord = findViewById<SwitchMaterial>(R.id.checkRecord)
        val checkAppUpdate = findViewById<SwitchMaterial>(R.id.checkAppUpdate)
        val checkNewsMotta = findViewById<SwitchMaterial>(R.id.checkNewsMotta)
        val checkNewsCasorate = findViewById<SwitchMaterial>(R.id.checkNewsCasorate)
        val checkNewsBereguardo = findViewById<SwitchMaterial>(R.id.checkNewsBereguardo)
        val checkNewsBesate = findViewById<SwitchMaterial>(R.id.checkNewsBesate)
        val btnOrarioDa = findViewById<Button>(R.id.btnOrarioDa)
        val btnOrarioA = findViewById<Button>(R.id.btnOrarioA)
        val btnSave = findViewById<android.widget.Button>(R.id.btnSave)

        btnOrarioDa.setOnClickListener {
            showTimePicker(orarioDaMinuti) { m ->
                orarioDaMinuti = m
                btnOrarioDa.text = formatMinuti(m)
            }
        }
        btnOrarioA.setOnClickListener {
            showTimePicker(orarioAMinuti) { m ->
                orarioAMinuti = m
                btnOrarioA.text = formatMinuti(m)
            }
        }

        ApiClient.getNotificationPrefs(token) { result ->
            progressBar.visibility = android.view.View.GONE
            if (result.success) {
                val prefs = result.json.optJSONObject("prefs") ?: JSONObject()
                checkAllerta.isChecked = prefs.optBoolean("allerta_meteo", true)
                checkFulmine.isChecked = prefs.optBoolean("fulmine_vicino", true)
                checkPioggia.isChecked = prefs.optBoolean("inizia_a_piovere", false)
                // Stesso default (1/true) usato lato backend, vedi notification_prefs
                // in db.php (colonna uv_alto aggiunta 2026-09-13).
                checkUv.isChecked = prefs.optBoolean("uv_alto", true)
                checkRecord.isChecked = prefs.optBoolean("nuovo_record", true)
                checkAppUpdate.isChecked = prefs.optBoolean("app_update", true)
                checkNewsMotta.isChecked = prefs.optBoolean("news_motta", false)
                checkNewsCasorate.isChecked = prefs.optBoolean("news_casorate", false)
                checkNewsBereguardo.isChecked = prefs.optBoolean("news_bereguardo", false)
                checkNewsBesate.isChecked = prefs.optBoolean("news_besate", false)
                orarioDaMinuti = prefs.optInt("orario_da", 480)
                orarioAMinuti = prefs.optInt("orario_a", 1380)
                btnOrarioDa.text = formatMinuti(orarioDaMinuti)
                btnOrarioA.text = formatMinuti(orarioAMinuti)
                loggedInGroup.visibility = android.view.View.VISIBLE
            } else if (result.statusCode == 401) {
                AccountManager.logout(this)
                Toast.makeText(this, getString(R.string.session_expired_message), Toast.LENGTH_LONG).show()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            } else {
                Toast.makeText(this, getString(R.string.error_network), Toast.LENGTH_SHORT).show()
            }
        }

        btnSave.setOnClickListener {
            val prefs = JSONObject()
                .put("allerta_meteo", checkAllerta.isChecked)
                .put("fulmine_vicino", checkFulmine.isChecked)
                .put("inizia_a_piovere", checkPioggia.isChecked)
                .put("uv_alto", checkUv.isChecked)
                .put("nuovo_record", checkRecord.isChecked)
                .put("app_update", checkAppUpdate.isChecked)
                .put("news_motta", checkNewsMotta.isChecked)
                .put("news_casorate", checkNewsCasorate.isChecked)
                .put("news_bereguardo", checkNewsBereguardo.isChecked)
                .put("news_besate", checkNewsBesate.isChecked)
                .put("orario_da", orarioDaMinuti)
                .put("orario_a", orarioAMinuti)

            btnSave.isEnabled = false
            ApiClient.setNotificationPrefs(token, prefs) { result ->
                btnSave.isEnabled = true
                if (result.success) {
                    Toast.makeText(this, getString(R.string.preferences_saved), Toast.LENGTH_SHORT).show()
                } else if (result.statusCode == 401) {
                    AccountManager.logout(this)
                    Toast.makeText(this, getString(R.string.session_expired_message), Toast.LENGTH_LONG).show()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, getString(R.string.error_network), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun formatMinuti(minuti: Int): String = String.format("%02d:%02d", minuti / 60, minuti % 60)

    private fun showTimePicker(current: Int, onPicked: (Int) -> Unit) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(current / 60)
            .setMinute(current % 60)
            .setInputMode(MaterialTimePicker.INPUT_MODE_KEYBOARD)
            .setTitleText(getString(R.string.notif_time_picker_title))
            .build()
        picker.addOnPositiveButtonClickListener {
            onPicked(picker.hour * 60 + picker.minute)
        }
        picker.show(supportFragmentManager, "time_picker")
    }
}
