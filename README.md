# 📱 Meteo Motta Visconti — App Android

App Android nativa per la [Stazione Meteo di Motta Visconti](https://meteo.nas.vagitaly.it/) — mostra la stessa dashboard del sito web con registrazione opzionale per personalizzare le notifiche.

🔗 **Sito web / progetto sorella:** [meteo-motta-visconti-web](https://github.com/Simonz82/meteo-motta-visconti-web)
📲 **Download:** dal pulsante nel footer del [sito](https://meteo.nas.vagitaly.it/) — aggiornamento in-app automatico dopo la prima installazione

## Cos'è

Un'app nativa (Kotlin) che avvolge il sito in una WebView per la dashboard principale, con schermate completamente native per tutto ciò che riguarda l'account e le notifiche:

- 🌦️ **Dashboard live** — stessa homepage del sito, sempre aggiornata
- 👤 **Account opzionale** — non obbligatorio per usare l'app; se ci si registra, servono email, password, nome, cognome, data di nascita e città di residenza
- 🔒 **Accesso biometrico** — impronta o volto per un accesso rapido, password cifrata con una chiave hardware del telefono (Android Keystore), utilizzabile solo dopo verifica biometrica live
- 🔔 **Notifiche personalizzabili** — Allerta Meteo, Fulmine vicino (<5 km), Inizio pioggia, Nuovo record storico, Nuova versione app — ciascuna con fascia oraria configurabile, consegnate via Firebase Cloud Messaging
- 🎨 **Temi selezionabili** — Scuro (predefinito), Chiaro, Black (AMOLED), Blu, Green, Pink, Red
- 🔄 **Aggiornamento automatico** — l'app stessa rileva quando è disponibile una versione più recente e propone il download, senza necessità del Play Store
- 🔑 **Recupero password** — richiesta di reset via email

## Stack tecnico

Kotlin nativo, `WebView` per la dashboard, `AndroidX` (AppCompat, Material, Biometric, WebKit), Firebase Cloud Messaging per le notifiche push. Backend PHP + SQLite (non incluso in questo repository per motivi di privacy e sicurezza — vedi nota sotto).

Nessun framework ibrido (Cordova/React Native): l'unica parte "web" è la dashboard stessa, identica al sito.

## Changelog

Le modifiche vengono registrate in [CHANGELOG.md](CHANGELOG.md).

## Struttura del repository

- `app/src/main/java/it/meteomottavisconti/` — codice sorgente Kotlin (schermate, client API, autenticazione biometrica, integrazione Firebase)
- `app/src/main/res/` — layout, stringhe, icone
- `app/src/main/AndroidManifest.xml` — permessi e componenti dichiarati

## Nota

Questo repository documenta il concept, l'architettura e l'evoluzione dell'app. **Non sono inclusi**: la chiave di firma (keystore), il file di configurazione Firebase (`google-services.json`), né il codice del backend (autenticazione, database, invio notifiche push) — quest'ultimo per gli stessi motivi di privacy/sicurezza del [progetto sorella](https://github.com/Simonz82/meteo-motta-visconti-web).

## ☕ Vuoi darmi una mano?

Il contenuto di questa pagina è completamente gratuito e lo scopo non è certamente fare soldi. Se vuoi darmi una mano per le spese e il tempo perso, ecco alcuni modi:

| | |
|---|---|
| [![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/C0C713VTGJ) | Offrimi un caffè su Ko-fi |
| [![PayPal](https://github.com/Simonz82/desktop-tutorial/blob/main/paypal.svg)](https://www.paypal.com/paypalme/simongmail) | Una donazione libera su PayPal |
| [![Amazon](https://github.com/Simonz82/desktop-tutorial/blob/main/Amazon_logo.png)](https://amzn.to/3XWWTgz) | Fai i tuoi acquisti Amazon partendo da questo link |

**Canali Telegram:**

| | |
|---|---|
| [![Home_Assistant_News](https://github.com/Simonz82/desktop-tutorial/blob/main/home_assistant_news.jpg)](https://t.me/Home_Assistant_News) | Notizie dedicate a Home Assistant |
| [![Offerte Domotica](https://github.com/Simonz82/desktop-tutorial/blob/main/offerte_domotica.jpg)](https://t.me/offerte_domotica_ita) | Offerte sui prodotti di domotica |

---

Sviluppato e curato da [Simonz82](https://t.me/Simonz82) · © 2026
