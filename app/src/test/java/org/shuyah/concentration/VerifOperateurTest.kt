package org.shuyah.concentration

import org.junit.Assert.*
import org.junit.Test

/** Tests de la vérification auprès de l'opérateur (limites d'envoi, relevé, rapprochement, codes d'auRor). Données FICTIVES. */
class VerifOperateurTest {
    private val H = 3_600_000L

    @Test fun limitesDEnvoi() {
        val t = 100 * H
        assertNull(VerifOperateur.refus(emptyList(), t))                                                 // premier envoi : autorisé
        assertNotNull(VerifOperateur.refus(listOf(t - 5 * 60_000L), t))                                  // il y a 5 minutes : trop tôt
        assertNull(VerifOperateur.refus(listOf(t - 11 * 60_000L), t))                                    // il y a 11 minutes : autorisé
        val quatre = listOf(t - 20 * H, t - 15 * H, t - 10 * H, t - 5 * H)
        assertTrue(VerifOperateur.refus(quatre, t)!!.contains("Limite"))                                 // 4 dans les 24 h : refusé
        assertNull(VerifOperateur.refus(listOf(t - 30 * H, t - 29 * H, t - 28 * H, t - 27 * H), t))      // anciens (plus de 24 h) : ne comptent plus
        assertEquals(2, VerifOperateur.restantes(listOf(t - 2 * H, t - 3 * H), t))
    }

    @Test fun relevePuisRapprochement() {
        val t = 1_000_000_000_000L
        val texte = "Reçu 5000 FCFA de AWA TEST Trans id: MP261113.1339.11111111\nPaiement 2500 FCFA à BOUTIQUE TEST Trans id: MP261113.1400.22222222\nligne illisible"
        val lues = VerifOperateur.analyserReleve(texte, t)
        assertEquals(2, lues.size)
        assertEquals("RECETTE", lues[0].genre); assertEquals(5000L, lues[0].montant)
        // La base contient déjà la première opération (même numéro de transaction) : seule la seconde est « absente »
        val base = listOf(lues[0])
        val abs = VerifOperateur.absents(lues, base, t)
        assertEquals(1, abs.size); assertEquals(2500L, abs[0].montant)
        // Sans numéro de transaction : même sens et même montant dans les 3 jours = déjà présent
        val sansRef = Paiements.Paiement("RELEVE", t, "RECETTE", 7000L, "", null, "x")
        assertTrue(VerifOperateur.absents(listOf(sansRef), listOf(sansRef.copy(adresse = "SMS", recuLeMs = t - 86_400_000L)), t).isEmpty())
        assertEquals(1, VerifOperateur.absents(listOf(sansRef), emptyList(), t).size)
    }

    @Test fun codeSecretEtChoixDuCode() {
        assertEquals("*123*4*0000#", CodesUssd.completer("*123*4*PIN#", " 0000 "))
        assertEquals("*123#", CodesUssd.completer("*123#", "0000"))
        val copie = CodesUssd.Copie("v1", listOf(
            CodesUssd.Code("1", "Orange", "Mini relevé", "*111*7*PIN#", "releve", true),
            CodesUssd.Code("2", "Moov", "Relevé", "*222#", "releve", false)))
        assertEquals("1", CodesUssd.trouver(copie, "Orange BF", "releve")?.id)                           // « Orange BF » (nom de la SIM) retrouve « Orange »
        assertEquals("2", CodesUssd.trouver(copie, "Moov Africa", "releve")?.id)
        assertNull(CodesUssd.trouver(copie, "Telecel Faso", "releve"))
        assertNull(CodesUssd.trouver(copie, "Orange", "renvoi_notification"))
        assertNull(CodesUssd.trouver(null, "Orange", "releve"))
    }

    @Test fun attenteDePaiement() {
        val t = 1_000_000_000_000L
        val recu = Paiements.Paiement("OM", t + 60_000L, "RECETTE", 1000L, "", null, "x")
        val depense = Paiements.Paiement("OM", t + 60_000L, "DEPENSE", 1000L, "", null, "x")
        assertTrue(Attente.paiementRecu(listOf(recu), t))
        assertFalse(Attente.paiementRecu(listOf(depense), t))                                            // une dépense n'est pas le paiement attendu
        assertFalse(Attente.paiementRecu(listOf(recu.copy(recuLeMs = t - 10 * 60_000L)), t))             // arrivé bien avant l'attente
    }
}

