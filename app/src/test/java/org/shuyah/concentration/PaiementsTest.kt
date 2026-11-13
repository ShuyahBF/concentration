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

    /** SMS réel Orange Money (capture du 13/11/2026) : « 2,150.00 FCFA » ne doit pas être lu 150 ; nom répété ; numéro de transaction et solde décimal. */
    @Test fun smsReelOrangeMoney() {
        val sms = "Votre paiement de 2,150.00 FCFA a ALIMENTATION LA MENAGERE ALIMENTATION LA MENAGERE a ete effectue avec succes. Votre solde est de : 6403.0202 Trans id: MP261113.1339.95787510."
        val p = Paiements.analyser("OrangeMoney", sms, 1L)!!
        assertEquals("DEPENSE", p.genre); assertEquals(2150L, p.montant); assertEquals(6403L, p.soldeApres)
        assertEquals("ALIMENTATION LA MENAGERE", p.tiers); assertEquals("MP261113.1339.95787510", p.reference)
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = p.recuLeMs }
        assertEquals(listOf(2026, 10, 13, 13, 39), listOf(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH), cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE)))
    }

    @Test fun lectureDesNombres() {
        assertEquals(2150L, Paiements.lireNombre("2,150.00")); assertEquals(2150L, Paiements.lireNombre("2.150,00")); assertEquals(15000L, Paiements.lireNombre("15 000"))
        assertEquals(15000L, Paiements.lireNombre("15.000")); assertEquals(2150L, Paiements.lireNombre("2,150")); assertEquals(6403L, Paiements.lireNombre("6403.0202"))
        assertEquals(1234567L, Paiements.lireNombre("1.234.567")); assertEquals(2500L, Paiements.lireNombre("2500")); assertEquals(3L, Paiements.lireNombre("2.5"))
        assertNull(Paiements.lireNombre("abc"))
    }

    @Test fun nomRepeteEtReferenceAbsente() {
        assertEquals("PHARMACIE DU PROGRES", Paiements.sansRepetition("PHARMACIE DU PROGRES PHARMACIE DU PROGRES"))
        assertEquals("AWA OUEDRAOGO", Paiements.sansRepetition("AWA OUEDRAOGO"))
        assertNull(Paiements.analyser("OM", "Retrait de 10000 FCFA effectué.", 5L)!!.reference)
        assertEquals(5L, Paiements.analyser("OM", "Retrait de 10000 FCFA effectué.", 5L)!!.recuLeMs)       // sans numéro de transaction : heure de réception
    }
}
