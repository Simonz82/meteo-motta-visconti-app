package it.meteomottavisconti

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var menuBadge: TextView

    private val siteHost = "meteo.nas.vagitaly.it"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Da targetSdk 36 (Android 16) il contenuto viene disegnato sotto le barre di
        // sistema per obbligo (non e' piu' possibile disattivarlo): rimettiamo noi il
        // padding equivalente cosi' il pulsante menu e il contenuto della pagina restano
        // sotto la barra di stato e sopra la barra di navigazione, come prima.
        val contentFrame = findViewById<android.widget.FrameLayout>(R.id.contentFrame)
        ViewCompat.setOnApplyWindowInsetsListener(contentFrame) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        webView = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        menuBadge = findViewById(R.id.menuBadge)
        val btnMenu = findViewById<android.widget.ImageButton>(R.id.btnMenu)

        setupWebView()

        swipeRefresh.setOnRefreshListener { webView.reload() }

        btnMenu.setOnClickListener {
            updateAccountMenuItem(navigationView)
            drawerLayout.openDrawer(GravityCompat.END)
        }

        navigationView.setNavigationItemSelectedListener { item ->
            drawerLayout.closeDrawer(GravityCompat.END)
            when (item.itemId) {
                R.id.nav_account -> {
                    if (AccountManager.isLoggedIn(this)) {
                        startActivity(android.content.Intent(this, AccountActivity::class.java))
                    } else {
                        startActivity(android.content.Intent(this, LoginActivity::class.java))
                    }
                }
                R.id.nav_notifications -> startActivity(android.content.Intent(this, NotificationSettingsActivity::class.java))
                R.id.nav_theme -> showThemePicker()
                R.id.nav_about -> startActivity(android.content.Intent(this, AboutActivity::class.java))
            }
            true
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    drawerLayout.isDrawerOpen(GravityCompat.END) -> drawerLayout.closeDrawer(GravityCompat.END)
                    webView.canGoBack() -> webView.goBack()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })

        if (savedInstanceState == null) {
            // Se l'app e' stata aperta da un App Link (vedi SplashActivity), carica
            // esattamente quella pagina invece della home, purche' sia davvero il
            // nostro dominio (controllo di sicurezza contro un intent malformato).
            val deepLinkUrl = intent?.dataString
            val deepLinkHost = deepLinkUrl?.let { Uri.parse(it).host }
            if (deepLinkUrl != null && deepLinkHost == siteHost) {
                webView.loadUrl(deepLinkUrl)
            } else {
                // Parametri di campagna riconosciuti nativamente da Matomo (nessuna configurazione
                // lato Matomo necessaria): permettono di distinguere le visite dall'app in un Segmento.
                webView.loadUrl(getString(R.string.site_url) + "?mtm_campaign=app_android&mtm_source=app")
            }
        }

        checkForUpdate()
        requestNotificationPermissionIfNeeded()
        FcmHelper.registerCurrentToken(this)
        // (l'invio dati dispositivo parte da onResume, che segue sempre onCreate)
        maybeShowRegisterPrompt()
    }

    // Invito alla registrazione mostrato una sola volta, al primissimo avvio,
    // solo se l'utente non e' gia' loggato. Saltabile, non si ripete piu' dopo.
    private fun maybeShowRegisterPrompt() {
        if (AccountManager.isLoggedIn(this)) return
        if (AccountManager.hasSeenRegisterPrompt(this)) return

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.register_prompt_title))
            .setMessage(getString(R.string.register_prompt_message))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.register_prompt_positive)) { _, _ ->
                AccountManager.markRegisterPromptSeen(this)
                val intent = android.content.Intent(this, LoginActivity::class.java)
                intent.putExtra(LoginActivity.EXTRA_START_IN_REGISTER_MODE, true)
                startActivity(intent)
            }
            .setNegativeButton(getString(R.string.register_prompt_negative)) { _, _ ->
                AccountManager.markRegisterPromptSeen(this)
            }
            .show()
    }

    private var sessionStartMillis: Long = 0L

    override fun onResume() {
        super.onResume()
        sessionStartMillis = System.currentTimeMillis()
        // Rete di sicurezza: se l'invio dopo login/registrazione non e' arrivato
        // (versione app precedente al fix, o l'app e' stata chiusa troppo in
        // fretta prima che la richiesta di rete finisse), si corregge da solo
        // al prossimo utilizzo dell'app.
        DeviceInfoHelper.sendIfLoggedIn(this)
    }

    override fun onPause() {
        super.onPause()
        if (sessionStartMillis > 0) {
            val seconds = ((System.currentTimeMillis() - sessionStartMillis) / 1000).toInt()
            sessionStartMillis = 0L
            val token = AccountManager.getToken(this)
            if (token != null && seconds > 0) {
                ApiClient.addSessionTime(token, seconds) { }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
    }

    // Chiede direttamente a Google Play (non piu' al sito/NAS) se e' disponibile
    // una versione piu' recente di quella installata. Funziona per qualunque
    // installazione avvenuta tramite Play Store, incluso il canale di test
    // chiuso - un'installazione diretta dell'APK (se mai riaccadesse) non ha
    // nulla da confrontare e semplicemente non segnala mai nulla, per design
    // dell'API stessa (non e' un bug da correggere qui).
    private fun checkForUpdate() {
        val appUpdateManager = AppUpdateManagerFactory.create(this)
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) return@addOnSuccessListener

            // Pallino rosso sull'hamburger e sulla voce "Info app" del menu:
            // segnala l'aggiornamento disponibile anche senza aprire il dialogo.
            // (Il drawer NavigationView non ha un vero sistema di badge come le
            // barre di navigazione: il pallino viene disegnato sopra l'icona.)
            menuBadge.visibility = TextView.VISIBLE
            val aboutItem = navigationView.menu.findItem(R.id.nav_about)
            aboutItem.icon = badgeDrawable(android.R.drawable.ic_menu_info_details)

            AlertDialog.Builder(this)
                .setTitle(getString(R.string.update_available_title))
                .setMessage(getString(R.string.update_available_message_playstore))
                .setPositiveButton(getString(R.string.update_open_playstore)) { _, _ ->
                    try {
                        startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
                    } catch (e: Exception) {
                        try {
                            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
                        } catch (e2: Exception) {
                            Toast.makeText(this, "Impossibile aprire Play Store", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton(getString(R.string.update_later), null)
                .show()
        }
    }

    // Disegna un pallino rosso con "1" sopra l'icona passata: il drawer
    // NavigationView non ha un sistema di badge nativo come le barre di
    // navigazione (quello esiste solo per BottomNavigationView/NavigationRailView).
    private fun badgeDrawable(baseIconRes: Int): android.graphics.drawable.Drawable {
        val baseDrawable = androidx.core.content.ContextCompat.getDrawable(this, baseIconRes)!!.mutate()
        baseDrawable.setTint(android.graphics.Color.parseColor("#00d4ff"))
        val size = (24 * resources.displayMetrics.density).toInt()
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        baseDrawable.setBounds(0, 0, size, size)
        baseDrawable.draw(canvas)

        val radius = size * 0.16f
        val cx = size - radius - 1f
        val cy = radius + 1f
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.color = android.graphics.Color.parseColor("#ff3b30")
        canvas.drawCircle(cx, cy, radius, paint)
        paint.color = android.graphics.Color.WHITE
        paint.textSize = radius * 1.3f
        paint.textAlign = android.graphics.Paint.Align.CENTER
        val textY = cy - (paint.descent() + paint.ascent()) / 2
        canvas.drawText("1", cx, textY, paint)

        return android.graphics.drawable.BitmapDrawable(resources, bitmap)
    }

    private fun updateAccountMenuItem(navigationView: NavigationView) {
        val item = navigationView.menu.findItem(R.id.nav_account)
        item.title = if (AccountManager.isLoggedIn(this)) {
            val nome = AccountManager.getName(this)
            if (!nome.isNullOrBlank()) {
                getString(R.string.menu_account_greeting, nome)
            } else {
                AccountManager.getEmail(this)
            }
        } else {
            getString(R.string.menu_account_login)
        }
    }

    private fun showComingSoon(feature: String) {
        Toast.makeText(this, "$feature — disponibile in una prossima versione", Toast.LENGTH_SHORT).show()
    }

    // Elenco temi disponibili: applicato subito alla WebView già caricata,
    // e ri-applicato automaticamente ad ogni pagina successiva (onPageFinished).
    private fun showThemePicker() {
        val keys = ThemeManager.THEMES.keys.toList()
        val labels = ThemeManager.THEMES.values.toTypedArray()
        val current = ThemeManager.getTheme(this)
        val checkedIndex = keys.indexOf(current).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.theme_picker_title))
            .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                val chosen = keys[which]
                ThemeManager.setTheme(this, chosen)
                applyThemeToWebView(chosen)
                AccountManager.getToken(this)?.let { token -> ApiClient.setTheme(token, chosen) { } }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    private fun applyThemeToWebView(theme: String) {
        webView.evaluateJavascript(
            "if (window.setAppTheme) { window.setAppTheme('$theme'); }",
            null
        )
    }

    // Usato dal ponte JS (vedi WebAppBridge) per disattivare il pull-to-refresh
    // mentre un popup del sito e' aperto, e riattivarlo alla chiusura.
    fun setSwipeRefreshEnabled(enabled: Boolean) {
        swipeRefresh.isEnabled = enabled
    }

    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        // Marcatore per far distinguere al sito "sono nell'app" da "sono nel
        // browser": usato per nascondere il banner "scarica l'app" e il suo
        // pulsante lampeggiante quando la pagina gira gia' dentro l'app.
        settings.userAgentString = settings.userAgentString + " MeteoMottaAndroidApp/" + BuildConfig.VERSION_NAME

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                val uri: Uri = request.url
                return if (uri.host == siteHost) {
                    false // resta dentro la WebView
                } else {
                    // link esterni (GitHub, Telegram, webcam, ecc.) si aprono nel browser di sistema
                    try {
                        startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
                    } catch (e: Exception) {
                        // nessuna app in grado di gestire il link, ignora
                    }
                    true
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                swipeRefresh.isRefreshing = false
                applyThemeToWebView(ThemeManager.getTheme(this@MainActivity))
            }
        }

        // Senza questo listener, i link di download (es. l'APK stesso) cliccati
        // dentro la WebView non fanno nulla: serve passarli esplicitamente al sistema.
        webView.setDownloadListener { url, _, _, _, _ ->
            try {
                startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                Toast.makeText(this, "Impossibile avviare il download", Toast.LENGTH_SHORT).show()
            }
        }

        // Ponte pagina->app usato dalla sezione Notizie del sito per sapere se
        // l'utente e' loggato (senza questo la pagina vedrebbe sempre "non loggato"
        // dentro l'app, anche con AccountManager.isLoggedIn true).
        webView.addJavascriptInterface(WebAppBridge(this), "AndroidBridge")
    }

    // @JavascriptInterface espone metodi alla pagina web caricata nella WebView
    // (window.AndroidBridge.xxx() in JS). Nessun metodo qui deve mai toccare la
    // UI direttamente: gira su un thread del WebView, non sul thread principale.
    private class WebAppBridge(private val activity: MainActivity) {
        @JavascriptInterface
        fun isLoggedIn(): Boolean = AccountManager.isLoggedIn(activity)

        // Il pull-to-refresh nativo (SwipeRefreshLayout) guarda solo se la
        // WebView nel suo complesso puo' scorrere verso l'alto: non sa nulla
        // di un popup aperto sopra con un suo scroll interno, quindi un
        // trascinamento verso il basso dentro il popup veniva scambiato per
        // "sono in cima alla pagina, ricarica" invece di scorrere il popup.
        // Il sito chiama questo metodo all'apertura/chiusura di ogni popup.
        @JavascriptInterface
        fun setPullToRefreshEnabled(enabled: Boolean) {
            activity.runOnUiThread { activity.setSwipeRefreshEnabled(enabled) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        webView.restoreState(savedInstanceState)
    }
}
