package org.shuyah.concentration

import java.util.Locale

/**
 * LECTURE DES SMS DE PAIEMENT (Orange Money, Moov Money, Telecel Money, banques…) : logique pure, sans Android (testée par PaiementsTest).
 *
 * Un SMS est retenu comme paiement quand il contient UN MONTANT en francs CFA ET un mot qui indique le sens de l'opération
 * (dépense : payé, achat, retrait, envoi, transfert, facture, débit ; recette : reçu, dépôt, crédit, versement). Les SMS promotionnels
 * (« gagnez », « promo », « offre »…) et ceux sans montant sont ignorés. Le texte des SMS d'opérateurs n'étant pas normalisé, la lecture est
 * volontairement prudente : en cas de doute le SMS est ignoré plutôt que mal classé.
 */
object Paiements {
    /** Un paiement lu : sens (DEPENSE ou RECETTE), montant en FCFA, tiers (qui reçoit / qui envoie) et solde après opération si le SMS le donne. */
    data class Paiement(val adresse: String, val recuLeMs: Long, val genre: String, val montant: Long, val tiers: String, val soldeApres: Long?, val corps: String)

    // Montant : « 15 000 FCFA », « 15.000 F CFA », « 2500F », « XOF 1 200 » ; séparateurs de milliers : espace, point, espace insécable
    private const val NOMBRE = "(\\d{1,3}(?:[ .\\u00a0\\u202f]\\d{3})+|\\d+)(?:[.,]\\d{1,2})?"
    private val MONTANT_APRES = Regex("$NOMBRE\\s*(?:fcfa|f\\s?cfa|xof|cfa|f)(?![a-z])", RegexOption.IGNORE_CASE)
    private val MONTANT_AVANT = Regex("(?:fcfa|f\\s?cfa|xof|cfa)\\s*:?\\s*$NOMBRE", RegexOption.IGNORE_CASE)
    private val SOLDE = Regex("(?:nouveau\\s+)?solde(?:\\s+(?:actuel|disponible|est|apr[eè]s))?\\s*(?:est|:|de)?\\s*$NOMBRE", RegexOption.IGNORE_CASE)

    private val MOTS_DEPENSE = listOf("vous avez payé", "vous avez paye", "paiement", "payé", "achat", "retrait", "vous avez envoyé", "vous avez envoye",
        "envoi de", "transfert de", "transfert vers", "facture", "débit", "debit", "vous avez transféré")
    private val MOTS_RECETTE = listOf("vous avez reçu", "vous avez recu", "reçu de", "recu de", "dépôt", "depot", "crédit de", "credit de", "versement", "transfert reçu")
    private val MOTS_PROMO = listOf("gagnez", "gagner", "promo", "offre", "bonus", "félicitations", "felicitations", "tentez", "jeu concours", "cliquez")

    /** « 15 000 » ou « 15.000 » → 15000 (les centimes éventuels sont ignorés). */
    private fun entier(brut: String): Long = brut.filter { it.isDigit() }.toLongOrNull() ?: 0L

    /** Premier montant du texte en FCFA (hors celui qui suit « solde »), ou null. */
    fun montantDe(texte: String): Long? {
        val soldePos = SOLDE.find(texte)?.range
        val candidats = (MONTANT_APRES.findAll(texte) + MONTANT_AVANT.findAll(texte)).filter { m -> soldePos == null || m.range.first !in soldePos }.sortedBy { it.range.first }
        return candidats.firstOrNull()?.let { entier(it.groupValues[1]) }?.takeIf { it > 0 }
    }

    /** Solde indiqué après l'opération (« Nouveau solde : 12 500 FCFA »), ou null. */
    fun soldeDe(texte: String): Long? = SOLDE.find(texte)?.let { entier(it.groupValues[1]) }

    /** Qui reçoit (dépense) ou qui envoie (recette) : mot qui suit « à », « chez », « vers », « pour » (dépense) ou « de » (recette), tronqué avant un chiffre ou une ponctuation. */
    fun tiersDe(texte: String, genre: String): String {
        val mots = if (genre == "RECETTE") "de|du" else "à|a|au profit de|chez|vers|pour|auprès de"
        val m = Regex("(?:^|\\s)(?:$mots)\\s+([\\p{L}][\\p{L}'’ .\\-]{2,40}?)(?=\\s+(?:le|du|au|n°|num|numéro|ref|réf|frais|solde|montant|\\d)|[,.;\\n\\d]|$)", RegexOption.IGNORE_CASE).find(texte)
        return m?.groupValues?.get(1)?.trim()?.trimEnd('.', '-')?.take(40).orEmpty()
    }

    /** Analyse un SMS : le paiement qu'il contient, ou null (promotion, pas de montant, sens indéterminé). */
    fun analyser(adresse: String, corps: String, recuLeMs: Long): Paiement? {
        val t = corps.lowercase(Locale.ROOT)
        if (MOTS_PROMO.any { t.contains(it) }) return null
        val montant = montantDe(corps) ?: return null
        // Recette testée d'abord : « vous avez reçu » contient des mots ambigus (ex. « envoi » dans un libellé) mais jamais l'inverse
        val genre = when {
            MOTS_RECETTE.any { t.contains(it) } -> "RECETTE"
            MOTS_DEPENSE.any { t.contains(it) } -> "DEPENSE"
            else -> return null
        }
        return Paiement(adresse, recuLeMs, genre, montant, tiersDe(corps, genre), soldeDe(corps), corps.trim())
    }
}
