package org.shuyah.concentration

import android.content.Context
import android.widget.Toast

/**
 * AVIS À L'UTILISATEUR (règle du propriétaire, 14/11/2026) : toute ACTUALISATION annonce par un toast que l'opération est EN COURS, puis donne son résultat.
 * Jamais d'actualisation silencieuse : on ne suppose pas que l'utilisateur voit qu'il s'est passé quelque chose.
 */
object Avis {
    /** « Actualisation en cours… » : affiché dès l'appui sur le bouton. */
    fun enCours(c: Context, texte: String) = Toast.makeText(c.applicationContext, texte, Toast.LENGTH_SHORT).show()
    /** Résultat de l'opération (affiché plus longtemps, pour être lu). */
    fun resultat(c: Context, texte: String) = Toast.makeText(c.applicationContext, texte, Toast.LENGTH_LONG).show()
}
