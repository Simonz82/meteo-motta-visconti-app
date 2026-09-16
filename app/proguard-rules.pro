# Mantiene informazioni utili per leggere gli stack trace dei crash
# con il mapping.txt che ora viene generato e va caricato su Play Console
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature
-renamesourcefileattribute SourceFile

# App WebView-based: nessun addJavascriptInterface in uso, quindi nessuna
# regola -keep speciale necessaria per un ponte JS (verificato nel codice).
# Le Activity/Service dichiarati in AndroidManifest.xml sono già protetti
# in automatico dalle regole di default di Android (non serve duplicarli).

# Firebase Messaging include le proprie consumer-rules nell'AAR: nessuna
# regola aggiuntiva richiesta per FCM.
