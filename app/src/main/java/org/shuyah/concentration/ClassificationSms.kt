package org.shuyah.concentration

import java.text.Normalizer

/**
 * CLASSEMENT DES SMS (promo / alerte / message / transaction) — logique pure, sans Android (testée par ClassificationSmsTest).
 *
 * Même règle que SAWALI (Transactions SMS, lot 398), pour que le téléphone et le serveur classent pareil. Seul un SMS de TRANSACTION du
 * portefeuille mobile est gardé : les autres sont lus puis oubliés, ils n'entrent jamais dans la base ni dans les totaux.
 *   - PROMO       : message commercial (« frais de retrait à 0,5 % jusqu'au 31 décembre ! Composez *808# ») : montants d'exemple, période de validité, codes USSD ;
 *   - ALERTE      : alerte de la BANQUE (« DEBIT Montant : XOF600,000.00 Compte : … ») : ne concerne pas le portefeuille mobile ;
 *   - MESSAGE     : SMS d'une PERSONNE (l'expéditeur est un numéro de téléphone) : pas un SMS d'opérateur ;
 *   - TRANSACTION : tout le reste (paiement, retrait, dépôt… de l'opérateur).
 * Pour un développeur WinDev : on additionne des POINTS. Chaque signe commercial (code USSD, « composez », validité, pourcentage, verbes d'offre) en ajoute ;
 * chaque signe de vraie transaction (numéro de transaction, « Débit Montant : », « Vous avez reçu… ») en retire. À 4 points ou plus : promotion.
 */
object ClassificationSms {
    enum class Categorie { TRANSACTION, PROMO, ALERTE, MESSAGE }

    /** Nombre de points à partir duquel un SMS est une promotion. */
    const val SEUIL_PROMO = 4

    private val USSD = Regex("\\*\\d{2,4}(\\*[\\w#*]*)?#")
    private val APPEL = Regex("stop \\*|envoyez stop|composez|tapez")
    private val VALIDITE = Regex("jusqu|valable|a partir de")
    private val POURCENT = Regex("[0-9]\\s?%")
    private val OFFRE = Regex("beneficiez|profitez|gagnez|retirez|reglez|rechargez|abonnez|obtenez|offre|promo|bonus|cadeau|vous accompagne|decouvrez")
    private val VRAIE_FORME = Regex("(debit|credit)\\s*montant\\s*:")
    private val VOUS_AVEZ = Regex("vous avez (recu|envoye|paye|transfere|effectu)")
    private val BANQUE = Regex("ubabf|\\buba\\b|banque|bank|coris|ecobank|sgbf|\\bboa\\b|bicia|bdm|cbao|sonibank|societe generale")
    private val NUMERO_EXPEDITEUR = Regex("^\\+?\\d{7,}$")

    /** Minuscules sans accents (« Bénéficiez » → « beneficiez »), comme SAWALI. */
    private fun normaliser(t: String): String =
        Normalizer.normalize(t, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

    /** Points « promotion » d'un SMS (voir la description de l'objet). `reference` = numéro de transaction trouvé dans le SMS, ou null. */
    fun score(corps: String, reference: String?): Int {
        val t = normaliser(corps)
        var pts = 0
        if (USSD.containsMatchIn(t)) pts += 3
        if (APPEL.containsMatchIn(t)) pts += 2
        if (VALIDITE.containsMatchIn(t)) pts += 2
        if (POURCENT.containsMatchIn(t)) pts += 2
        if (OFFRE.containsMatchIn(t)) pts += 2
        if (reference != null) pts -= 5
        if (VRAIE_FORME.containsMatchIn(t)) pts -= 5
        if (VOUS_AVEZ.containsMatchIn(t)) pts -= 3
        return pts
    }

    /** Catégorie d'un SMS : alerte (banque) → message (expéditeur = numéro de téléphone) → promo (score ≥ 4) → sinon transaction. */
    fun categorie(adresse: String, corps: String, reference: String?): Categorie {
        val a = adresse.trim()
        if (VRAIE_FORME.containsMatchIn(normaliser(corps)) || BANQUE.containsMatchIn(normaliser(a))) return Categorie.ALERTE
        if (NUMERO_EXPEDITEUR.matches(a.replace(" ", ""))) return Categorie.MESSAGE
        if (score(corps, reference) >= SEUIL_PROMO) return Categorie.PROMO
        return Categorie.TRANSACTION
    }
}
