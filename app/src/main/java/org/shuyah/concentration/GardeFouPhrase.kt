package org.shuyah.concentration

/**
 * GARDE-FOU DE LA PHRASE SECRÈTE (Concentration 1.56, repris de Personnel de maison) : refuse une phrase trop courte ou trop facile à deviner. La force de la sauvegarde dépend ENTIÈREMENT de la phrase
 * (le serveur ne la connaît pas, personne ne peut la retrouver) : une phrase banale (« 123456789012 », « azertyuiopqs », un numéro de téléphone…) serait devinée en quelques minutes.
 * Logique pure, testée sur ordinateur. Chaque refus donne une RAISON écrite (jamais un simple « refusé »).
 */
object GardeFouPhrase {
    class Verdict(val acceptable: Boolean, val raisons: List<String>)

    /** Suites trop connues (dans les deux sens) : si la phrase, sans espaces, en est un morceau, elle est refusée. */
    private val SUITES = listOf("abcdefghijklmnopqrstuvwxyz", "azertyuiopqsdfghjklmwxcvbn", "qwertyuiopasdfghjklzxcvbnm", "0123456789012345678901234567890")
    /** Mots et débuts de phrases trop courants (cherchés dans la phrase sans espaces ni accents). */
    private val BANALS = listOf("motdepasse", "password", "passw0rd", "azerty", "qwerty", "123456", "000000", "111111", "iloveyou", "jetaime", "personnelmaison", "sauvegarde", "secret", "burkinafaso", "ouagadougou")

    private fun sansAccents(t: String) = java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    fun verifier(phrase: String): Verdict {
        val n = CopieCrypto.normaliser(phrase)
        val serre = sansAccents(n).replace(" ", "")
        val raisons = mutableListOf<String>()
        if (n.length < CopieCrypto.LONGUEUR_MIN_PHRASE) raisons += "Encore ${CopieCrypto.LONGUEUR_MIN_PHRASE - n.length} caractère(s) au moins."
        if (serre.isNotEmpty()) {
            if (serre.toSet().size < 6 && n.length >= CopieCrypto.LONGUEUR_MIN_PHRASE) raisons += "Trop de caractères répétés : utilisez plusieurs mots différents."
            if (Regex("^(.{1,4})\\1+$").matches(serre)) raisons += "Une répétition (« abcabcabc… ») se devine tout de suite."
            if (serre.length >= 8 && SUITES.any { it.contains(serre) || it.reversed().contains(serre) }) raisons += "Une suite de lettres ou de chiffres (clavier, alphabet) se devine tout de suite."
            if (serre.all { it.isDigit() }) raisons += "Des chiffres seuls (date, numéro) se devinent trop facilement : écrivez plusieurs mots."
            BANALS.firstOrNull { serre.contains(it) }?.let { raisons += "La phrase contient « $it », beaucoup trop courant : choisissez des mots à vous." }
        }
        return Verdict(raisons.isEmpty(), raisons)
    }
}
