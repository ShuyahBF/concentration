package org.shuyah.concentration

import org.junit.Assert.*
import org.junit.Test

/** Tests de la lecture du solde Internet (réponse USSD) et de l'alerte de seuil (sans Android). */
class SoldeTest {
    @Test fun volumeSimpleEnGoOuEnMo() {
        assertEquals(2560.0, Solde.analyserVolume("Solde Internet: 2.5 Go valable jusqu'au 20/12")!!, 0.01)
        assertEquals(450.0, Solde.analyserVolume("Il vous reste 450 Mo")!!, 0.01)
        assertEquals(1536.0, Solde.analyserVolume("Data: 1,5GB")!!, 0.01)           // virgule décimale, unité collée, GB
        assertEquals(1.0, Solde.analyserVolume("Reste 1024 Ko")!!, 0.01)
    }

    @Test fun plusieursForfaitsSontAdditionnesQuandIlsSontIntroduitsParUnMotCle() {
        assertEquals(1.2 * 1024 + 500, Solde.analyserVolume("Il vous reste 1,2 Go et 500 Mo bonus")!!, 0.5)
        // Le volume de départ du forfait (sans mot-clé) n'est pas confondu avec ce qui reste
        assertEquals(800.0, Solde.analyserVolume("Forfait 5 Go. Reste 800 Mo")!!, 0.01)
    }

    @Test fun sansVolumeOnNeDevinePas() {
        assertNull(Solde.analyserVolume("Votre solde est de 1500 FCFA"))
        assertNull(Solde.analyserVolume(""))
    }

    @Test fun alerteAuSeuilDeDixPourCent() {
        assertTrue(Solde.seuilAtteint(500.0, 5000.0, 10))          // pile 10 %
        assertTrue(Solde.seuilAtteint(100.0, 5000.0, 10))
        assertFalse(Solde.seuilAtteint(501.0, 5000.0, 10))
        assertFalse(Solde.seuilAtteint(10.0, 0.0, 10))             // départ inconnu : jamais d'alerte
        assertEquals(10, Solde.pourcentRestant(500.0, 5000.0))
        assertNull(Solde.pourcentRestant(500.0, 0.0))
    }

    @Test fun rechargeDetecteeEtFormat() {
        assertTrue(Solde.rechargeDetectee(200.0, 5000.0))
        assertFalse(Solde.rechargeDetectee(2000.0, 1900.0))
        assertFalse(Solde.rechargeDetectee(100.0, 120.0))          // hausse trop faible
        assertFalse(Solde.rechargeDetectee(null, 5000.0))
        assertEquals("2,5 Go", Solde.formater(2560.0))
        assertEquals("450 Mo", Solde.formater(450.0))
    }

    @Test fun sansForfaitLOperateurRepondUnePhraseSansVolume() {
        // Aucun volume dans la réponse = pas de forfait Internet : analyserVolume rend null (l'application l'affiche « Pas de forfait Internet » avec le texte brut)
        assertNull(Solde.analyserVolume("Vous n'avez pas de forfait Internet actif. Tapez *123*1# pour souscrire."))
        assertNull(Solde.analyserVolume("Aucun bundle data actif sur votre compte."))
    }
}
