package it.vagitaly.meteomottavisconti

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val versionText: TextView = findViewById(R.id.versionText)
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            versionText.text = getString(R.string.about_version_format, pInfo.versionName)
        } catch (e: Exception) {
            versionText.text = ""
        }

        findViewById<TextView>(R.id.changelogLink).setOnClickListener {
            val uri = Uri.parse("https://meteo.nas.vagitaly.it/changelog.html")
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }
}
