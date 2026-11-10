package org.shuyah.concentration

import java.util.Calendar

/**
 * Une plage horaire de concentration, par exemple de 20:00 à 07:00 (elle peut passer minuit).
 * `debut` et `fin` sont en minutes depuis minuit (20:00 = 1200). `jours` : un bit par jour, lundi = bit 0 … dimanche = bit 6
 * (ce sont les jours où la plage COMMENCE : une plage 20:00–07:00 du vendredi finit le samedi matin).
 * Logique pure (sans Android) : testée par PlageTest.
 */
data class Plage(val debut: Int, val fin: Int, val jours: Int = TOUS_LES_JOURS) {

    /** Vrai si l'instant (jour 0 = lundi … 6 = dimanche, minutes depuis minuit) est dans la plage. */
    fun contient(jour: Int, minutes: Int): Boolean = minutesRestantes(jour, minutes) != null

    /**
     * Minutes qui restent avant la FIN de la plage, ou null si l'instant n'est pas dans la plage.
     * Trois cas : début = fin (plage vide) ; même journée (08:00–12:00) ; plage de nuit (20:00–07:00, sur deux jours).
     */
    fun minutesRestantes(jour: Int, minutes: Int): Int? {
        fun jourActif(j: Int) = jours and (1 shl ((j % 7 + 7) % 7)) != 0
        return when {
            debut == fin -> null
            debut < fin -> if (jourActif(jour) && minutes in debut until fin) fin - minutes else null
            minutes >= debut -> if (jourActif(jour)) (1440 - minutes) + fin else null        // soirée : jusqu'à minuit puis jusqu'à `fin`
            minutes < fin -> if (jourActif(jour - 1)) fin - minutes else null                  // petit matin : la plage a commencé la veille
            else -> null
        }
    }

    /** « 20:00 → 07:00 » */
    fun libelle(): String = "%02d:%02d → %02d:%02d".format(debut / 60, debut % 60, fin / 60, fin % 60)

    /** Texte stocké dans les préférences : « 1200,420,127 ». */
    fun versTexte(): String = "$debut,$fin,$jours"

    companion object {
        const val TOUS_LES_JOURS = 127
        val JOURS = listOf("L", "M", "M", "J", "V", "S", "D")
        fun depuisTexte(t: String): Plage? {
            val p = t.split(",").mapNotNull { it.trim().toIntOrNull() }
            return if (p.size == 3 && p[0] in 0..1439 && p[1] in 0..1439) Plage(p[0], p[1], p[2]) else null
        }
        /** Jour (0 = lundi … 6 = dimanche) et minutes depuis minuit d'un instant. */
        fun jourEtMinutes(c: Calendar = Calendar.getInstance()): Pair<Int, Int> =
            Pair((c.get(Calendar.DAY_OF_WEEK) + 5) % 7, c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE))
    }
}
