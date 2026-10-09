# Mantiene informazioni utili per leggere gli stack trace dei crash
# con il mapping.txt che ora viene generato e va caricato su Play Console
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature
-renamesourcefileattribute SourceFile

# Ponte JS tra WebView e app nativa (classe WebAppBridge, dentro
# MainActivity.kt, registrata come window.AndroidBridge). La pagina web
# chiama questi metodi per NOME via reflection (es. AndroidBridge.getToken()):
# se R8 li rinomina o li rimuove perche' apparentemente "mai chiamati" dal
# codice Kotlin/Java, la chiamata JS fallisce silenziosamente (nessun crash,
# solo "undefined"/eccezione ignorata) - esattamente la stessa classe di bug
# gia' vista con Google Sign-In/Credential Manager qui sotto. Il commento
# precedente ("nessun addJavascriptInterface in uso") era ormai falso da
# quando questo bridge e' stato aggiunto (2026-09-16) e non era mai stato
# aggiornato - si contava solo sulla keep-rule di default di AGP per
# @JavascriptInterface, mai verificata concretamente in questo progetto.
# Regola esplicita aggiunta il 2026-09-19 per togliere ogni dubbio.
-keep class it.meteomottavisconti.MainActivity$WebAppBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Firebase Messaging include le proprie consumer-rules nell'AAR: nessuna
# regola aggiuntiva richiesta per FCM.

# Google Sign-In (Credential Manager) aggiunto il 2026-09-18: l'app funzionava
# sempre con lo stesso errore "TYPE_NO_CREDENTIAL" su piu' telefoni diversi e
# aggiornati, con configurazione Google Cloud verificata corretta - sospetto
# che R8 stia eliminando silenziosamente le classi reflection-based che fanno
# da ponte tra Credential Manager e Google Play Services (nessun crash, solo
# "nessuna credenziale disponibile", sintomo tipico di questo problema noto).
-keep class androidx.credentials.playservices.** { *; }
-keep class com.google.android.gms.auth.api.identity.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class androidx.credentials.** { *; }

# Google Sign-In metodo classico (rete di sicurezza, vc47).
-keep class com.google.android.gms.auth.api.signin.** { *; }
-keep class com.google.android.gms.common.api.ApiException { *; }
-keep class com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption { *; }
