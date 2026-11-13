package org.shuyah.concentration

import android.Manifest
import android.content.Context
import android.net.Uri
import android.content.pm.PackageManager

/**
 * LECTURE DES SMS du téléphone pour constituer la base des paiements (comme dans aurOR). Concentration étant une application privée, installée
 * hors du Play Store, elle peut demander l'autorisation « SMS » (lecture) sans la restriction que Google impose aux applications publiées.
 *
 * Pour un développeur WinDev : la boîte de réception est une « table » du système (`content://sms/inbox`) lue avec un curseur ; on ne relit que les
 * SMS plus récents que le dernier traité (mémorisé), et chaque SMS reconnu comme paiement est écrit dans la base locale (`Historique`).
 * Seuls les paiements sont conservés ; les autres SMS ne sont ni copiés ni envoyés ailleurs.
 */
object Sms {
    fun autorise(c: Context) = c.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    /**
     * Lit les SMS reçus depuis la dernière synchronisation (ou depuis `depuisMs` à la toute première : 90 jours par défaut) et enregistre les paiements.
     * Retourne le nombre de nouveaux paiements (0 sans autorisation). Jamais bloquant : toute erreur de lecture donne 0.
     */
    fun synchroniser(c: Context, premierePeriodeJours: Int = 90): Int {
        if (!autorise(c)) return 0
        return try {
            val dernier = Reglages.dernierSmsLu(c)
            val depuis = if (dernier > 0L) dernier else System.currentTimeMillis() - premierePeriodeJours * 86_400_000L
            val base = Historique.de(c)
            var nouveaux = 0; var plusRecent = dernier
            c.contentResolver.query(Uri.parse("content://sms/inbox"), arrayOf("address", "body", "date"), "date > ?", arrayOf(depuis.toString()), "date ASC")?.use { q ->
                while (q.moveToNext()) {
                    val date = q.getLong(2)
                    if (date > plusRecent) plusRecent = date
                    Paiements.analyser(q.getString(0).orEmpty(), q.getString(1).orEmpty(), date)?.let { if (base.ajouterPaiement(it)) nouveaux++ }
                }
            }
            Reglages.dernierSmsLu(c, plusRecent)
            nouveaux
        } catch (e: Exception) { 0 }
    }
}
