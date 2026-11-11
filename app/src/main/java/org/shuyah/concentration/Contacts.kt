package org.shuyah.concentration

import java.text.Normalizer

/**
 * CONTACTS AUTORISÉS (niveau 1) : leurs messages et appels ne sont PAS retardés pendant la plage.
 * WhatsApp indique le contact (ou le groupe, ou l'expéditeur dans un groupe) dans le texte de la notification ; on compare ce texte aux noms
 * de la liste, sans tenir compte des majuscules ni des accents. Logique pure (sans Android) : testée par PlageTest.
 */
object Contacts {
    /** Met un nom en minuscules, sans accents, avec des espaces simples. */
    fun normaliser(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase().replace(Regex("\\s+"), " ").trim()

    /** Vrai si l'un des textes de la notification contient le nom d'un contact autorisé (noms de moins de 2 lettres ignorés). */
    fun correspond(textes: List<String>, autorises: Collection<String>): Boolean {
        val noms = autorises.map { normaliser(it) }.filter { it.length >= 2 }
        if (noms.isEmpty()) return false
        return textes.map { normaliser(it) }.any { t -> noms.any { n -> t.contains(n) } }
    }
}
