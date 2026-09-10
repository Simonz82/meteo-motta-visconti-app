package it.vagitaly.meteomottavisconti

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val versionText: TextView = findViewById(R.id.versionText)
        val updateStatusText: TextView = findViewById(R.id.updateStatusText)
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

        checkForUpdate(updateStatusText, btnUpdateNow, installedVersionName)
    }

    private fun checkForUpdate(updateStatusText: TextView, btnUpdateNow: Button, installedVersionName: String) {
        ApiClient.getLatestVersion { result ->
            if (!result.success) return@getLatestVersion
            val remoteVersionCode = result.json.optInt("version_code", -1)
            val remoteVersionName = result.json.optString("version_name", "")
            val file = result.json.optString("file", "")

            if (remoteVersionCode <= BuildConfig.VERSION_CODE || file.isEmpty()) {
                updateStatusText.text = getString(R.string.about_up_to_date)
                updateStatusText.visibility = TextView.VISIBLE
                return@getLatestVersion
            }

            updateStatusText.text = getString(
                R.string.about_update_available,
                remoteVersionName,
                installedVersionName
            )
            updateStatusText.visibility = TextView.VISIBLE
            btnUpdateNow.visibility = Button.VISIBLE
            btnUpdateNow.setOnClickListener {
                val url = getString(R.string.site_url) + file
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) {
                    Toast.makeText(this, "Impossibile avviare il download", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
