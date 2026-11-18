package it.meteomottavisconti

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
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

    // Selettore file per i campi <input type="file"> del sito (es. locandine
    // di sagre.html, sia invio sia "Modifica"). Senza un WebChromeClient che
    // gestisca onShowFileChooser, dentro la WebView il pulsante "Sfoglia" non
    // fa nulla. La callback va SEMPRE chiamata (anche con null se l'utente
    // annulla), altrimenti la WebView non riapre piu' il selettore.
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val uris: Array<Uri>? = if (result.resultCode == RESULT_OK && data != null) {
            val clip = data.clipData
            when {
                clip != null && clip.itemCount > 0 -> Array(clip.itemCount) { clip.getItemAt(it).uri }
                data.data != null -> arrayOf(data.data!!)
                else -> null
            }
        } else null
        filePathCallback?.onReceiveValue(uris)
        filePathCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Da targetSdk 36 (Android 16) il contenuto viene disegnato sotto le barre di
        // sistema per obbligo (non e' piu' possibile disattivarlo): rimettiamo noi il
        // padding equivalente cosi' il pulsante menu e il contenuto della pagina restano
        // sotto la barra di stato e sopra la barra di navigazione, come prima.
        //
        // L'inset della tastiera (ime()) va gestito qui per lo stesso motivo: con
        // l'edge-to-edge forzato, il solo windowSoftInputMode="adjustResize" nel
        // manifest non basta piu' a far restringere il WebView quando la tastiera si
        // apre (a differenza di Login/AccountActivity, che usano fitsSystemWindows e
        // quindi lo ottengono gratis). Senza questo padding, i popup dentro la pagina
        // (es. il modulo di registrazione) restano coperti dalla tastiera e non si puo'
        // scrollare per vedere i campi sotto.
        val contentFrame = findViewById<android.widget.FrameLayout>(R.id.contentFrame)
        val webViewForInsets = findViewById<WebView>(R.id.webView)
        val drawerLayoutForInsets = findViewById<DrawerLayout>(R.id.drawerLayout)
        // L'ascoltatore va agganciato sulla ROOT (drawerLayout), non su contentFrame:
        // DrawerLayout gestisce a modo suo gli inset per il proprio edge-to-edge e puo'
        // "consumarli" prima che arrivino ai figli, cosi' un listener piu' in basso
        // rischia di non vedere mai l'inset ime() aggiornarsi. Da qui applichiamo poi
        // il padding ai due punti giusti: contentFrame per le barre di sistema (come
        // prima), il WebView stesso per la tastiera - direttamente su di lui, non su
        // un antenato, cosi' non dipendiamo dalla propagazione a cascata del padding
        // attraverso SwipeRefreshLayout.
        ViewCompat.setOnApplyWindowInsetsListener(drawerLayoutForInsets) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            contentFrame.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            webViewForInsets.setPadding(0, 0, 0, ime.bottom)
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
    private var pausedAtMillis: Long = 0L

    override fun onResume() {
        super.onResume()
        sessionStartMillis = System.currentTimeMillis()
        // Tornando in primo piano dopo almeno 10 minuti la pagina si ricarica subito,
        // cosi' mostra sempre dati aggiornati (non quella rimasta in memoria).
        if (pausedAtMillis > 0 && System.currentTimeMillis() - pausedAtMillis > 10 * 60 * 1000L) {
            webView.reload()
        }
        pausedAtMillis = 0L
        // Rete di sicurezza: se l'invio dopo login/registrazione non e' arrivato
        // (versione app precedente al fix, o l'app e' stata chiusa troppo in
        // fretta prima che la richiesta di rete finisse), si corregge da solo
        // al prossimo utilizzo dell'app.
        DeviceInfoHelper.sendIfLoggedIn(this)
    }

    override fun onPause() {
        super.onPause()
        pausedAtMillis = System.currentTimeMillis()
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

    // Restyle 2026-09-26: card in stile "glass" (dialog_theme_picker.xml +
    // row_theme_item.xml) al posto del setSingleChoiceItems di sistema, coerente
    // con Notifiche/Account. Righe costruite a runtime dai colori reali di ogni
    // tema (ThemeManager.SWATCHES). Applicato subito alla WebView già caricata,
    // e ri-applicato automaticamente ad ogni pagina successiva (onPageFinished).
    private fun showThemePicker() {
        val view = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_theme_picker, null)
        val container = view.findViewById<android.widget.LinearLayout>(R.id.themeRowsContainer)
        val current = ThemeManager.getTheme(this)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(true)
            .create()

        val density = resources.displayMetrics.density
        container.removeAllViews()
        for ((key, label) in ThemeManager.THEMES) {
            val row = android.view.LayoutInflater.from(this).inflate(R.layout.row_theme_item, container, false)
            val rowRoot = row.findViewById<android.widget.LinearLayout>(R.id.themeRow)
            val swatch = row.findViewById<android.view.View>(R.id.themeSwatch)
            val labelView = row.findViewById<TextView>(R.id.themeLabel)
            val check = row.findViewById<TextView>(R.id.themeCheck)

            val sw = ThemeManager.SWATCHES[key]
            swatch.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(android.graphics.Color.parseColor(sw?.accent ?: "#00d4ff"))
                setStroke((2 * density).toInt(), android.graphics.Color.parseColor(sw?.border ?: "#000000"))
            }

            labelView.text = label
            val isSelected = key == current
            check.visibility = if (isSelected) android.view.View.VISIBLE else android.view.View.GONE
            labelView.setTypeface(null, if (isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            labelView.setTextColor(android.graphics.Color.parseColor(if (isSelected) "#ffffff" else "#e1e1e1"))
            rowRoot.setBackgroundColor(android.graphics.Color.parseColor(if (isSelected) "#1a00d4ff" else "#00000000"))

            rowRoot.setOnClickListener {
                ThemeManager.setTheme(this, key)
                applyThemeToWebView(key)
                AccountManager.getToken(this)?.let { token -> ApiClient.setTheme(token, key) { } }
                dialog.dismiss()
            }
            container.addView(row)
        }

        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCloseThemeDialog)
            .setOnClickListener { dialog.dismiss() }

        dialog.show()
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
        // Mai cache: ogni pagina si apre sempre fresca dal server (richiesta esplicita).
        settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
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

        // Gestisce il selettore file dei campi <input type="file"> (vedi
        // fileChooserLauncher). Come effetto collaterale utile, avere un
        // WebChromeClient fa comparire anche i dialoghi JS alert()/confirm()
        // del sito, che senza nessun WebChromeClient la WebView ignora.
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                // accept="image/jpeg,image/png,application/pdf" arriva come
                // elenco: si passa tutto come EXTRA_MIME_TYPES (createIntent()
                // di sistema ne userebbe uno solo).
                val mimeTypes = params.acceptTypes
                    .flatMap { it.split(",") }
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toTypedArray()
                val intent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                    addCategory(android.content.Intent.CATEGORY_OPENABLE)
                    type = if (mimeTypes.size == 1) mimeTypes[0] else "*/*"
                    if (mimeTypes.size > 1) putExtra(android.content.Intent.EXTRA_MIME_TYPES, mimeTypes)
                    putExtra(
                        android.content.Intent.EXTRA_ALLOW_MULTIPLE,
                        params.mode == FileChooserParams.MODE_OPEN_MULTIPLE
                    )
                }
                return try {
                    fileChooserLauncher.launch(android.content.Intent.createChooser(intent, "Scegli un file"))
                    true
                } catch (e: Exception) {
                    filePathCallback = null
                    Toast.makeText(this@MainActivity, "Nessuna app per scegliere i file", Toast.LENGTH_SHORT).show()
                    false
                }
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

        // Espone il vero token di sessione nativo (lo stesso usato da
        // ApiClient per le chiamate dell'app) alle pagine web caricate nella
        // WebView - senza questo, pagine come "Sagre nei dintorni" vedono
        // sempre "non loggato" dentro l'app: il sistema account "web"
        // (localStorage) e' un meccanismo separato che qui dentro non esiste
        // mai, per design. Null se non loggato, esattamente come isLoggedIn().
        @JavascriptInterface
        fun getToken(): String? = AccountManager.getToken(activity)

        // Il pull-to-refresh nativo (SwipeRefreshLayout) guarda solo se la
        // WebView nel suo complesso puo' scorrere verso l'alto: non sa nulla
        // di un popup aperto sopra con un suo scroll interno, quindi un
        // trascinamento verso il basso dentro il popup veniva scambiato per
        // "sono in cima alla pagina, ricarica" invece di scorrere il popup.
        // Il sito chiama questo metodo all'apertura/chiusura di ogni popup.
        // Popup "sezione riservata agli iscritti" del sito (area_riservata.js):
        // i suoi pulsanti aprono direttamente la schermata di login nativa.
        // mode = "google" (selettore account Google subito), "register" (modulo
        // di registrazione) o "login". Tornando indietro la pagina si sblocca da
        // sola: controlla getToken() ogni 1,5 secondi.
        @JavascriptInterface
        fun openLogin(mode: String?) {
            activity.runOnUiThread {
                if (AccountManager.isLoggedIn(activity)) return@runOnUiThread
                val intent = android.content.Intent(activity, LoginActivity::class.java)
                when (mode) {
                    "google" -> intent.putExtra(LoginActivity.EXTRA_AUTO_GOOGLE, true)
                    "register" -> intent.putExtra(LoginActivity.EXTRA_START_IN_REGISTER_MODE, true)
                }
                activity.startActivity(intent)
            }
        }

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
