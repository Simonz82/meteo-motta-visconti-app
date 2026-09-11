# Changelog

Tutte le modifiche rilevanti all'app Android vengono registrate qui, in ordine cronologico inverso.

## v1.8.0 — 2026-09-11

### Aggiunto
- Cambio email dalla schermata Account: link "Cambia email" che chiede il nuovo indirizzo e la password attuale per conferma (evita che un accesso rubato basti da solo a cambiare l'email di accesso). Controlla formato email valido, email già in uso da un altro account, e limita i tentativi in caso di password sbagliate ripetute

## v1.7.3 — 2026-09-11

### Risolto
- Rete di sicurezza per i dati dispositivo: se l'invio non arriva a buon fine (versione precedente al fix di prima, o l'app viene chiusa troppo in fretta dopo la registrazione), ora riparte automaticamente ad ogni ripresa dell'app, senza bisogno di reinstallare o fare altro

## v1.7.2 — 2026-09-11

### Risolto
- I dati tecnici del dispositivo non venivano inviati subito dopo una registrazione o un login riusciti nella stessa sessione (partivano solo al riavvio completo dell'app): un utente che si registrava e restava nell'app vedeva il proprio profilo senza marca/modello/versione Android ecc. Ora l'invio parte subito dopo login/registrazione

## v1.7.1 — 2026-09-11

### Aggiunto
- Nella schermata Info: "Richiedi supporto" (apre l'email precompilata con versione app, marca/modello dispositivo e versione Android per facilitare la diagnosi) e "Proponi una novità" (dati, viste, funzioni) — oggetti diversi per categoria, cosi le email in arrivo si possono smistare facilmente

## v1.7.0 — 2026-09-11

### Aggiunto
- Invito alla registrazione al primissimo avvio dell'app (solo se non già loggati): spiega i vantaggi di registrarsi (notifiche personalizzate, temi) con pulsante diretto alla schermata di registrazione già pronta, oppure "Più tardi" per continuare senza. Mostrato una sola volta

## v1.6.1 — 2026-09-11

### Aggiunto
- Raccolta dati tecnici del dispositivo (marca, modello, versione Android, RAM, risoluzione schermo, tipo di connessione, operatore) inviati al backend per l'utente loggato, aggiornati ad ogni apertura dell'app
- Tracciamento di data primo/ultimo accesso, numero di aperture e tempo totale di utilizzo, tema in uso — tutto legato all'account utente (non al singolo dispositivo, sopravvive a un cambio telefono)
- Salvate anche versione app installata e data ultimo accesso: permetteranno di avvisare in futuro solo chi ha ancora una versione vecchia

## v1.6.0 — 2026-09-11

### Aggiunto
- Selettore tema dal menu laterale ("Tema"): Scuro (predefinito), Chiaro, Black (AMOLED), Blu, Green, Pink, Red. La scelta viene applicata alla dashboard e salvata sul dispositivo

## v1.5.1 — 2026-09-11

### Aggiunto
- Nuova icona dell'app
- Nella schermata Info, indicazione se è disponibile una versione più recente rispetto a quella installata, con pulsante di download diretto
- Città di residenza richiesta in fase di registrazione (in vista di funzioni future legate alla localizzazione)

### Modificato
- L'accesso con impronta/volto resta attivo anche dopo il logout — non ha senso richiedere di reinserire la password ogni volta per un'app meteo

## v1.5.0 — 2026-09-10

### Modificato
- Data di nascita: sostituito il selettore calendario con tre campi digitabili (giorno/mese/anno) con avanzamento automatico — più veloce da compilare

## v1.4.3 — 2026-09-10

### Risolto
- Estensioni PHP mancanti sul server (OpenSSL, cURL) che impedivano l'invio effettivo delle notifiche push

## v1.4.2 — 2026-09-10

### Aggiunto
- Le notifiche di aggiornamento app aprono ora direttamente il link di download al tocco, invece di limitarsi ad aprire l'app

## v1.4.1 — 2026-09-10

### Aggiunto
- Prima notifica push realmente recapitata (categoria "Nuova versione app disponibile")

## v1.4.0 — 2026-09-10

### Aggiunto
- Integrazione completa Firebase Cloud Messaging: ricezione notifiche push, registrazione automatica del dispositivo al login, richiesta del permesso di notifica (Android 13+)
- Migrazione del backend da file JSON a database SQLite vero e proprio

### Sicurezza
- Dati utente e chiavi private del backend spostati fuori dalla cartella pubblica del sito

## v1.3.3 — 2026-09-10

### Aggiunto
- Controllo automatico degli aggiornamenti: l'app verifica da sola se è disponibile una versione più recente e propone il download, senza bisogno di controllare manualmente sul sito

## v1.3.2 — 2026-09-10

### Risolto
- Crash della schermata Account quando la data di nascita non era ancora impostata

## v1.3.1 — 2026-09-10

### Aggiunto
- Tracciamento analytics dedicato per distinguere le visite dall'app da quelle da browser

## v1.3.0 — 2026-09-10

### Aggiunto
- Accesso con impronta digitale o riconoscimento del volto, dopo il primo login con password
- Email ricordata automaticamente nella schermata di accesso
- Data di nascita completa (giorno/mese/anno) in registrazione e nella schermata account
- Selezione dell'orario delle notifiche tramite selettore digitabile al minuto, non più a step di un'ora

## v1.2.2 — 2026-09-10

### Aggiunto
- Nome e cognome richiesti già in fase di registrazione
- Campo note personali libere nella schermata account

### Modificato
- Rimossa l'etichetta fuorviante "(esci)" accanto al nome utente nel menu

## v1.2.1 — 2026-09-10

### Aggiunto
- Vera schermata "Il mio account" (email, nome, cognome, dati modificabili, pulsante di uscita con conferma), al posto del logout immediato al tocco

## v1.2.0 — 2026-09-10

### Aggiunto
- Recupero password dimenticata dalla schermata di accesso
- Nuova categoria di notifica "Nuova versione app disponibile"
- Fascia oraria configurabile per le notifiche (es. niente notifiche di notte)

### Risolto
- Il pulsante di download dell'APK cliccato da dentro l'app non avviava il download

## v1.1.0

### Aggiunto
- Menu laterale (hamburger) con accesso a account, notifiche e informazioni
- Schermate native di login/registrazione e gestione preferenze notifiche, collegate al backend
- Icona dell'app definitiva

## v1.0.0 – v1.0.2

### Aggiunto
- Prima versione pubblica: WebView a schermo intero sulla dashboard del sito, splash screen con immagine storica del paese
- Aggiornamento in-place tramite APK scaricato dal sito, senza passare dal Play Store, mantenendo sempre la stessa chiave di firma
