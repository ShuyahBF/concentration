package org.shuyah.concentration

import java.util.Locale

/**
 * Lecture du solde Internet dans la réponse texte d'un code USSD, et alerte de seuil. Logique pure (sans Android) : testée par SoldeTest.
 * Les volumes sont comptés en Mo (1 Go = 1024 Mo).
 */
object Solde {
    private val VOLUME = Regex("(\\d+(?:[.,]\\d+)?)\\s*(go|gb|mo|mb|ko|kb)(?![a-z])", RegexOption.IGNORE_CASE)
    private val MOTS_CLES = listOf("reste", "restant", "solde", "disponible", "remaining", "balance")

    /** Convertit une valeur et son unité (Go, Mo, Ko ; majuscules ou minuscules ; GB, MB, KB aussi) en Mo. */
    fun enMo(valeur: Double, unite: String): Double = when (unite.lowercase(Locale.ROOT)) {
        "go", "gb" -> valeur * 1024.0
        "ko", "kb" -> valeur / 1024.0
        else -> valeur
    }

    /**
     * Volume restant, en Mo, d'après le texte de l'opérateur ; null si aucun volume n'y figure.
     * Les volumes précédés (60 caractères au plus) d'un mot comme « reste », « solde » ou « disponible » sont additionnés (plusieurs forfaits) ;
     * sans mot-clé, c'est le premier volume du texte qui est retenu. La virgule décimale est acceptée.
     */
    fun analyserVolume(texte: String): Double? {
        val trouves = VOLUME.findAll(texte).toList()
        if (trouves.isEmpty()) return null
        fun valeur(m: MatchResult) = enMo(m.groupValues[1].replace(',', '.').toDouble(), m.groupValues[2])
        val prioritaires = trouves.filter { m ->
            val avant = texte.substring(maxOf(0, m.range.first - 60), m.range.first).lowercase(Locale.ROOT)
            MOTS_CLES.any { avant.contains(it) }
        }
        return if (prioritaires.isNotEmpty()) prioritaires.sumOf { valeur(it) } else valeur(trouves.first())
    }

    /** Pourcentage du volume de départ qui reste (arrondi) ; null si le départ n'est pas connu. */
    fun pourcentRestant(restantMo: Double, departMo: Double): Int? =
        if (departMo <= 0.0) null else Math.round(restantMo / departMo * 100.0).toInt().coerceAtLeast(0)

    /** Vrai si le volume restant est au niveau du seuil (ex. 10 %) du volume de départ, ou en dessous. */
    fun seuilAtteint(restantMo: Double, departMo: Double, pourcentSeuil: Int): Boolean =
        departMo > 0.0 && restantMo <= departMo * pourcentSeuil / 100.0

    /** Une recharge est détectée quand le volume remonte nettement (plus de 20 % et au moins 50 Mo de plus) : le départ est alors remis à jour. */
    fun rechargeDetectee(avantMo: Double?, apresMo: Double): Boolean =
        avantMo != null && apresMo > avantMo * 1.2 && apresMo - avantMo >= 50.0

    /** « 2,3 Go » ou « 450 Mo » (virgule française). */
    fun formater(mo: Double): String =
        if (mo >= 1024.0) String.format(Locale.FRANCE, "%.1f Go", mo / 1024.0) else String.format(Locale.FRANCE, "%.0f Mo", mo)
}
