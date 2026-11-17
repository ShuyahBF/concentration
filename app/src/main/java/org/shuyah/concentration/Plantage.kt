package org.shuyah.concentration

import android.content.Context
import android.os.Build

/**
 * MÉMOIRE DES PLANTAGES (version 1.41) : quand l'application plante, le détail technique (message et pile d'appels) est écrit dans un petit fichier du téléphone AVANT que
 * l'application se ferme ; au lancement suivant, l'écran principal l'affiche avec un bouton « Copier », pour pouvoir le transmettre au support. Rien n'est envoyé tout seul.
 *
 * Pour un développeur WinDev : c'est l'équivalent d'un gestionnaire d'exceptions global (« WHEN EXCEPTION » de toute l'application) qui garde la trace avant la fermeture.
 * Il couvre tous les fils d'exécution, y compris celui du pare-feu (VPN) : un plantage venu d'un service ou d'un fil de fond est donc gardé lui aussi.
 */
object Plantage {
    private const val FICHIER = "plantage"
    @Volatile private var installe = false

    /** Installe le gestionnaire (sans effet s'il l'est déjà). À appeler depuis chaque point d'entrée : écran, service, récepteur. */
    fun installer(c: Context) {
        if (installe) return
        installe = true
        val contexte = c.applicationContext
        val ancien = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { fil, erreur ->
            try {
                val pile = android.util.Log.getStackTraceString(erreur).take(6000)
                val texte = "Concentration ${Version.VERSION} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                    "Fil : ${fil.name} · ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.FRANCE).format(java.util.Date())}\n\n$pile"
                contexte.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).edit().putString("dernier", texte).commit()     // commit : écrit tout de suite, avant la fermeture
            } catch (_: Throwable) { /* ne jamais aggraver le plantage */ }
            ancien?.uncaughtException(fil, erreur)
        }
    }

    /** Détail du dernier plantage, ou null. */
    fun dernier(c: Context): String? = c.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).getString("dernier", null)
    fun effacer(c: Context) { c.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).edit().remove("dernier").apply() }
}
