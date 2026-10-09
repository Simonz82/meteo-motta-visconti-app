package it.meteomottavisconti

// Client ID OAuth di tipo "Web" generato da Firebase quando si abilita il
// provider Google in Authentication > Sign-in method (progetto "mottameteo").
// Non e' un segreto: e' lo stesso identico valore che il backend usa per
// validare l'audience del token Google (vedi google_login.php), quindi puo'
// stare qui in chiaro nel codice sorgente (anche su un repo pubblico).
object GoogleAuthConfig {
    const val WEB_CLIENT_ID = "xxxxxxxxxxxx-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.apps.googleusercontent.com"
}
