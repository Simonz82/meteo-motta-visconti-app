package it.meteomottavisconti

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import org.json.JSONObject

class NotificationSettingsActivity : AppCompatActivity() {

    private var orarioDaMinuti = 480
    private var orarioAMinuti = 1380

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_settings)

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

        val checkAllerta = findViewById<CheckBox>(R.id.checkAllerta)
        val checkFulmine = findViewById<CheckBox>(R.id.checkFulmine)
        val checkPioggia = findViewById<CheckBox>(R.id.checkPioggia)
        val checkRecord = findViewById<CheckBox>(R.id.checkRecord)
        val checkAppUpdate = findViewById<CheckBox>(R.id.checkAppUpdate)
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
                checkRecord.isChecked = prefs.optBoolean("nuovo_record", true)
                checkAppUpdate.isChecked = prefs.optBoolean("app_update", true)
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
                .put("nuovo_record", checkRecord.isChecked)
                .put("app_update", checkAppUpdate.isChecked)
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
