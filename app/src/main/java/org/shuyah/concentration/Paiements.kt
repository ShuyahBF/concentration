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
    /** Un paiement lu : sens (DEPENSE ou RECETTE), montant en FCFA, tiers, solde après opération, numéro de transaction (ex. MP261113.1339.95787510), identifiant en base (0 tant que non enregistré). */
    data class Paiement(val adresse: String, val recuLeMs: Long, val genre: String, val montant: Long, val tiers: String, val soldeApres: Long?, val corps: String, val reference: String? = null, val id: Long = 0)

    // Nombre : « 2,150.00 », « 15 000 », « 15.000 », « 6403.0202 », « 2500 » (voir `lireNombre` pour le sens des virgules et des points)
    private const val NOMBRE = "(?:\\d{1,3}(?:[ \\u00a0\\u202f]\\d{3})+|\\d+)(?:[.,]\\d+)*"
    private val MONTANT_APRES = Regex("($NOMBRE)\\s*(?:fcfa|f\\s?cfa|xof|cfa|f)(?![a-z])", RegexOption.IGNORE_CASE)
    private val MONTANT_AVANT = Regex("(?:fcfa|f\\s?cfa|xof|cfa)\\s*:?\\s*($NOMBRE)", RegexOption.IGNORE_CASE)
    private val SOLDE = Regex("(?:nouveau\\s+)?solde(?:\\s+(?:actuel|disponible|est|apr[eè]s))?(?:\\s*(?:est|:|de|=))*\\s*($NOMBRE)", RegexOption.IGNORE_CASE)
    // Numéro de transaction Orange Money : « Trans id: MP261113.1339.95787510 » = MP + AAMMJJ . HHMM . numéro chronologique
    private val TRANSACTION = Regex("trans(?:action)?\\s*id\\s*:?\\s*([A-Z]{2}\\d{6}\\.\\d{4}\\.\\d+|[A-Z0-9][A-Z0-9._-]{5,})", RegexOption.IGNORE_CASE)
    private val DATE_TRANSACTION = Regex("^[A-Z]{2}(\\d{2})(\\d{2})(\\d{2})\\.(\\d{2})(\\d{2})\\.")

    private val MOTS_DEPENSE = listOf("vous avez payé", "vous avez paye", "votre paiement", "paiement", "payé", "achat", "retrait", "vous avez envoyé", "vous avez envoye",
        "envoi de", "transfert de", "transfert vers", "facture", "débit", "debit", "vous avez transféré")
    private val MOTS_RECETTE = listOf("vous avez reçu", "vous avez recu", "reçu de", "recu de", "dépôt", "depot", "crédit de", "credit de", "versement", "transfert reçu")
    private val MOTS_PROMO = listOf("gagnez", "gagner", "promo", "offre", "bonus", "félicitations", "felicitations", "tentez", "jeu concours", "cliquez")

    /**
     * Sens des séparateurs d'un nombre écrit par un opérateur (francs entiers retournés, centimes arrondis) :
     *  - espaces = milliers ; « 2,150.00 » : le DERNIER séparateur est la décimale, l'autre sépare les milliers ;
     *  - un seul séparateur : milliers s'il est suivi d'exactement 3 chiffres et précédé de 1 à 3 chiffres (« 15.000 », « 2,150 »), sinon décimale (« 6403.0202 », « 2.5 »).
     */
    fun lireNombre(brut: String): Long? {
        val t = brut.trim().replace(Regex("[ \\u00a0\\u202f]"), "")
        if (t.isEmpty() || !t.first().isDigit()) return null
        val derniere = t.indexOfLast { it == '.' || it == ',' }
        if (derniere < 0) return t.toLongOrNull()
        val separateurs = t.filter { it == '.' || it == ',' }
        val apres = t.substring(derniere + 1)
        val avant = t.substring(0, derniere)
        val decimale = when {
            separateurs.toSet().size > 1 -> true                                          // « 2,150.00 » : l'autre séparateur est celui des milliers
            separateurs.length > 1 -> false                                              // « 1.234.567 » : que des milliers
            else -> !(apres.length == 3 && avant.length in 1..3)                          // « 15.000 » = milliers ; « 6403.0202 » = décimale
        }
        val entier = if (decimale) avant.filter { it.isDigit() } else t.filter { it.isDigit() }
        val v = entier.toLongOrNull() ?: return null
        return if (decimale && apres.firstOrNull()?.let { it >= '5' } == true) v + 1 else v
    }

    /** Premier montant du texte en FCFA (hors celui qui suit « solde »), ou null. */
    fun montantDe(texte: String): Long? {
        val soldePos = SOLDE.find(texte)?.range
        val candidats = (MONTANT_APRES.findAll(texte) + MONTANT_AVANT.findAll(texte)).filter { m -> soldePos == null || m.range.first !in soldePos }.sortedBy { it.range.first }
        return candidats.firstOrNull()?.let { lireNombre(it.groupValues[1]) }?.takeIf { it > 0 }
    }

    /** Solde indiqué après l'opération (« Votre solde est de : 6403.0202 »), ou null. */
    fun soldeDe(texte: String): Long? = SOLDE.find(texte)?.let { lireNombre(it.groupValues[1]) }

    /** Numéro de transaction (« MP261113.1339.95787510 »), ou null. */
    fun referenceDe(texte: String): String? = TRANSACTION.find(texte)?.groupValues?.get(1)

    /** Date et heure contenues dans le numéro de transaction (MP + AAMMJJ . HHMM), en millisecondes (fuseau du téléphone) ; null si le numéro n'a pas cette forme. */
    fun dateDeReference(reference: String?): Long? {
        val m = DATE_TRANSACTION.find(reference ?: return null) ?: return null
        val (a, mo, j, h, mi) = m.destructured
        return try {
            java.util.Calendar.getInstance().apply { isLenient = false; clear(); set(2000 + a.toInt(), mo.toInt() - 1, j.toInt(), h.toInt(), mi.toInt(), 0) }.timeInMillis
        } catch (e: IllegalArgumentException) { null }
    }

    /**
     * Le nom est pris la PREMIÈRE fois qu'il apparaît : si ses premiers mots sont répétés juste après (« ALIMENTATION LA MENAGERE ALIMENTATION LA MENAGERE »,
     * même suivi d'autres mots), on ne garde que la première occurrence. Sans répétition, le nom est laissé tel quel.
     */
    fun sansRepetition(nom: String): String {
        val mots = nom.trim().split(Regex("\\s+"))
        for (n in 1..mots.size / 2) {
            if (mots.take(n).map { it.lowercase() } == mots.subList(n, 2 * n).map { it.lowercase() }) return mots.take(n).joinToString(" ")
        }
        return nom.trim()
    }

    /**
     * Qui reçoit (dépense) ou qui envoie (recette). Dépense Orange Money : « … FCFA a NOM a ete effectue avec succes » (le nom s'arrête avant « a ete / effectué / avec succès /
     * Votre solde / Trans id ») ; sinon, mot qui suit « à », « chez », « vers », « pour » (dépense) ou « de » (recette), tronqué avant un chiffre ou une ponctuation.
     */
    fun tiersDe(texte: String, genre: String): String {
        if (genre == "DEPENSE") {
            val m = Regex("(?:fcfa|f\\s?cfa|xof)\\s+(?:a|à|au profit de|chez|vers|pour)\\s+(.+?)(?=\\s+(?:a|est)\\s+[ée]t[ée]\\b|\\s+effectu|\\s+avec\\s+succ|\\.\\s|\\s+votre\\s+solde|\\s+trans\\b|\\s+le\\s+\\d|$)", RegexOption.IGNORE_CASE).find(texte)
            if (m != null) return sansRepetition(m.groupValues[1]).take(60)
        }
        val mots = if (genre == "RECETTE") "de|du" else "à|a|au profit de|chez|vers|pour|auprès de"
        val m = Regex("(?:^|\\s)(?:$mots)\\s+([\\p{L}][\\p{L}'’ .\\-]{2,40}?)(?=\\s+(?:le|du|au|n°|num|numéro|ref|réf|frais|solde|montant|\\d)|[,.;\\n\\d]|$)", RegexOption.IGNORE_CASE).find(texte)
        return m?.groupValues?.get(1)?.trim()?.trimEnd('.', '-')?.take(40).orEmpty()
    }

    /** Analyse un SMS : le paiement qu'il contient, ou null (promotion, pas de montant, sens indéterminé). La date du numéro de transaction, si elle existe, remplace l'heure de réception. */
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
        val reference = referenceDe(corps)
        return Paiement(adresse, dateDeReference(reference) ?: recuLeMs, genre, montant, tiersDe(corps, genre), soldeDe(corps), corps.trim(), reference)
    }
}
