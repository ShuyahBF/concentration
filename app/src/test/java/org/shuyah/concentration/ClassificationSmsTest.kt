package org.shuyah.concentration

import org.junit.Assert.*
import org.junit.Test
import org.shuyah.concentration.ClassificationSms.Categorie.*

/** Tests du classement des SMS : promo / alerte / message / transaction. */
class ClassificationSmsTest {
    private fun cat(adresse: String, corps: String) = ClassificationSms.categorie(adresse, corps, Paiements.referenceDe(corps))

    @Test fun promoAvecUssdEtValidite() {
        assertEquals(PROMO, cat("TELECEL", "Profitez des frais de retrait a 0,5% jusqu'au 31 decembre ! Composez *808# pour en beneficier. Stop *8#"))
        assertEquals(PROMO, cat("OrangeMoney", "Orange Money: retirez 50 000 FCFA et beneficiez de 0% de frais jusqu'au 31/12. Composez *144#"))
    }

    @Test fun alerteDeBanque() {
        assertEquals(ALERTE, cat("UBA", "DEBIT Montant : XOF600,000.00 Compte : 0123 Solde : XOF1,000.00"))
        assertEquals(ALERTE, cat("CORIS BANK", "Votre compte a ete credite"))
    }

    @Test fun messageDUnePersonne() {
        assertEquals(MESSAGE, cat("+22670123456", "Salut, envoie-moi 5000 FCFA stp"))
        assertEquals(MESSAGE, cat("70123456", "Je t'ai envoye 5000 FCFA"))
    }

    @Test fun vraieTransactionResteTransaction() {
        assertEquals(TRANSACTION, cat("OrangeMoney", "Vous avez paye 15 000 FCFA a SONABEL. Trans id: MP261113.1339.95787510. Nouveau solde: 82 500 FCFA."))
        assertEquals(TRANSACTION, cat("MOOV", "Vous avez reçu 25.000 F CFA de OUEDRAOGO Awa. Solde: 40 000 F CFA"))
    }

    @Test fun horsTransactionGardeMaisHorsDecompte() {
        val promo = Paiements.analyser("OrangeMoney", "Orange Money: retirez 50 000 FCFA et beneficiez de 0% de frais jusqu'au 31/12. Composez *144#", 1L)!!
        assertEquals("PROMO", promo.categorie); assertFalse(promo.estTransaction)
        val alerte = Paiements.analyser("UBA", "DEBIT Montant : XOF600,000.00 Compte : 0123", 1L)!!
        assertEquals("ALERTE", alerte.categorie)
        val message = Paiements.analyser("+22670123456", "Vous avez reçu 5000 FCFA de ma part", 1L)!!
        assertEquals("MESSAGE", message.categorie)
        val vraie = Paiements.analyser("OrangeMoney", "Vous avez paye 15 000 FCFA a SONABEL. Trans id: MP261113.1339.95787510", 1L)!!
        assertTrue(vraie.estTransaction)
        // Les totaux ne comptent que la vraie transaction
        val t = StatsPaiements.totaux(listOf(promo, alerte, message, vraie))
        assertEquals(1, t.nombre); assertEquals(15000L, t.depenses); assertEquals(0L, t.recettes)
        assertEquals(1, StatsPaiements.parOperateur(listOf(promo, alerte, message, vraie)).sumOf { it.nombre })
    }
}
