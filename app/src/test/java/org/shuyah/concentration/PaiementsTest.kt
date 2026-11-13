package org.shuyah.concentration

import org.junit.Assert.*
import org.junit.Test

/** Tests de la lecture des SMS de paiement. */
class PaiementsTest {
    @Test fun depenseOrangeMoney() {
        val p = Paiements.analyser("OrangeMoney", "Vous avez payé 15 000 FCFA à SONABEL le 12/11/2026. Frais: 0 FCFA. Nouveau solde: 82 500 FCFA.", 1L)!!
        assertEquals("DEPENSE", p.genre); assertEquals(15000L, p.montant); assertEquals(82500L, p.soldeApres); assertEquals("SONABEL", p.tiers)
    }

    @Test fun recette() {
        val p = Paiements.analyser("MOOV", "Vous avez reçu 25.000 F CFA de OUEDRAOGO Awa. Solde: 40 000 F CFA", 1L)!!
        assertEquals("RECETTE", p.genre); assertEquals(25000L, p.montant); assertEquals(40000L, p.soldeApres); assertEquals("OUEDRAOGO Awa", p.tiers)
    }

    @Test fun retraitSansTiers() {
        val p = Paiements.analyser("TELECEL", "Retrait de 10000 FCFA effectué. Solde 5 000 FCFA", 1L)!!
        assertEquals("DEPENSE", p.genre); assertEquals(10000L, p.montant); assertEquals(5000L, p.soldeApres)
    }

    @Test fun lesSmsSansPaiementSontIgnores() {
        assertNull(Paiements.analyser("ORANGE", "Gagnez 500000 FCFA avec notre jeu concours, cliquez ici", 1L))     // promotion
        assertNull(Paiements.analyser("ORANGE", "Votre forfait internet expire le 15/11/2026", 1L))                 // pas de montant
        assertNull(Paiements.analyser("AMI", "Salut, on se voit à 18 h ?", 1L))
        assertNull(Paiements.analyser("BANQUE", "Montant 5000 FCFA disponible", 1L))                                 // sens indéterminé
    }

    @Test fun montantEtSoldeNeSeConfondentPas() {
        assertEquals(2500L, Paiements.montantDe("Solde: 90 000 FCFA. Achat de 2500 FCFA"))
        assertEquals(90000L, Paiements.soldeDe("Achat 2500F. Nouveau solde : 90 000 FCFA"))
        assertEquals(1200L, Paiements.montantDe("paiement XOF 1 200"))
    }
}
