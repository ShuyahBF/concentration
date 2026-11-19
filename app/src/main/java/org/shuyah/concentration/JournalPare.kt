package org.shuyah.concentration

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * JOURNAL DU PARE-FEU (niveau 3, version 1.52, propriétaire 19/11/2026 : « l'icône VPN ne s'affiche plus alors que les blocages sont déjà en cours »).
 *
 * Pour un développeur WinDev : un petit fichier texte (30 lignes au plus) dans les préférences de l'application, où le pare-feu écrit CE QU'IL FAIT :
 * démarrage demandé, tunnel ouvert (pour combien d'applications), refus du système, autorisation VPN absente, VPN repris par une autre application,
 * service arrêté… Avant, plusieurs refus étaient avalés sans trace ; quand l'icône « clé » (le VPN) manquait, rien ne disait pourquoi.
 * Le journal s'affiche dans l'écran principal (Niveau 3) avec « Actualiser » et « Copier ». Aucune donnée personnelle : seulement des événements du pare-feu.
 */
object JournalPareLogique {
    const val MAX_LIGNES = 30

    /** Ajoute une ligne et ne garde que les `max` dernières. */
    fun ajouter(lignes: List<String>, nouvelle: String, max: Int = MAX_LIGNES): List<String> = (lignes + nouvelle).takeLast(max)

    /** « 19/11 14:05:09 texte » : la date et l'heure sont écrites AVANT le texte. */
    fun ligne(instantMs: Long, texte: String): String = SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE).format(Date(instantMs)) + " " + texte

    /** Vrai si la dernière ligne porte déjà ce texte (la date et l'heure, au début, sont ignorées) : sert à ne pas répéter le même message chaque minute. */
    fun identique(derniere: String?, texte: String): Boolean = derniere != null && derniere.length > 15 && derniere.substring(15).trim() == texte.trim()
}

object JournalPare {
    private const val FICHIER = "journal_pare"

    private fun p(c: Context) = c.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)

    /** Les lignes du journal, de la plus ancienne à la plus récente. */
    fun lignes(c: Context): List<String> = try { p(c).getString("lignes", "")!!.split('\n').filter { it.isNotBlank() } } catch (e: Throwable) { emptyList() }

    /** Écrit un événement. `sansRepeter` : rien n'est écrit si le dernier événement est le même (message d'un contrôle qui revient chaque minute). Ne lève jamais d'exception. */
    fun noter(c: Context, texte: String, sansRepeter: Boolean = false) {
        try {
            val avant = lignes(c)
            if (sansRepeter && JournalPareLogique.identique(avant.lastOrNull(), texte)) return
            val apres = JournalPareLogique.ajouter(avant, JournalPareLogique.ligne(System.currentTimeMillis(), texte.replace('\n', ' ')))
            p(c).edit().putString("lignes", apres.joinToString("\n")).apply()
        } catch (e: Throwable) { /* un journal ne doit jamais faire échouer le pare-feu */ }
    }

    fun effacer(c: Context) { try { p(c).edit().clear().apply() } catch (e: Throwable) { } }
}
