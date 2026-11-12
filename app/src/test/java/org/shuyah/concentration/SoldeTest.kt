package org.shuyah.concentration

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

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

    @Test fun intervalleDeVerificationLibreAvecUnMinimumDUneHeure() {
        assertEquals(6, Solde.intervalleHeures(null))            // non renseigné : 6 h par défaut
        assertEquals(6, Solde.intervalleHeures("abc"))
        assertEquals(1, Solde.intervalleHeures("1"))
        assertEquals(1, Solde.intervalleHeures("0"))             // jamais moins d'une heure
        assertEquals(1, Solde.intervalleHeures("-4"))
        assertEquals(12, Solde.intervalleHeures(" 12 "))
        assertEquals(48, Solde.intervalleHeures("48"))
    }

    private val UTC = TimeZone.getTimeZone("UTC")
    private fun ms(an: Int, mois: Int, jour: Int, h: Int = 0, m: Int = 0): Long =
        Calendar.getInstance(UTC).apply { clear(); set(an, mois - 1, jour, h, m, 0) }.timeInMillis
    private val MAINTENANT = ms(2026, 11, 12, 10, 0)

    @Test fun dateDExpirationEntreParenthesesOuApresUnMotCle() {
        assertEquals(ms(2026, 11, 20, 23, 59), Solde.analyserExpiration("Internet: 2.5 Go (exp. 20/11/2026)", MAINTENANT, UTC))
        assertEquals(ms(2026, 11, 20, 14, 30), Solde.analyserExpiration("Il vous reste 1,2 Go (valable jusqu'au 20/11/26 à 14:30)", MAINTENANT, UTC))
        assertEquals(ms(2026, 11, 20, 8, 15), Solde.analyserExpiration("Solde 500 Mo (20-11-2026 08h15)", MAINTENANT, UTC))
        assertEquals(ms(2026, 12, 3, 23, 59), Solde.analyserExpiration("Data 3 Go expire le 2026-12-03", MAINTENANT, UTC))
    }

    @Test fun uneDateDeRechargeNEstPasUneExpiration() {
        assertNull(Solde.analyserExpiration("Derniere recharge le 01/11/2026. Solde 500 Mo", MAINTENANT, UTC))
        assertNull(Solde.analyserExpiration("Vous n'avez pas de forfait Internet actif.", MAINTENANT, UTC))
        assertNull(Solde.analyserExpiration("Forfait (31/02/2026)", MAINTENANT, UTC))              // date impossible : ignorée
    }

    @Test fun plusieursForfaitsLaPlusProcheAVenirEstRetenue() {
        val t = "5 Go (exp 15/12/2026) ; 1 Go (exp 18/11/2026) ; 2 Go (exp 01/11/2026)"
        assertEquals(ms(2026, 11, 18, 23, 59), Solde.analyserExpiration(t, MAINTENANT, UTC))
        assertEquals(ms(2026, 11, 1, 23, 59), Solde.analyserExpiration("Forfait (exp 01/11/2026)", MAINTENANT, UTC))      // seulement du passé : la plus récente
    }

    @Test fun rappelsASeptJoursTroisJoursEtLeJourMemeAvantLHeure() {
        val exp = ms(2026, 11, 20, 23, 59)
        assertEquals(listOf(7 to ms(2026, 11, 13, 9, 0), 3 to ms(2026, 11, 17, 9, 0), 0 to ms(2026, 11, 20, 8, 0)), Solde.momentsRappel(exp, UTC))
        // expiration tôt le matin : le dernier rappel part 2 h avant l'heure
        assertEquals(ms(2026, 11, 20, 6, 15), Solde.momentsRappel(ms(2026, 11, 20, 8, 15), UTC).last().second)
    }

    @Test fun joursRestantsEtLibelle() {
        assertEquals(8, Solde.joursRestants(ms(2026, 11, 20, 23, 59), MAINTENANT, UTC))
        assertEquals(0, Solde.joursRestants(ms(2026, 11, 12, 23, 59), MAINTENANT, UTC))
        assertEquals("Expire le 20/11/2026 à 23:59 (dans 8 jours)", Solde.libelleExpiration(ms(2026, 11, 20, 23, 59), MAINTENANT, UTC))
        assertEquals("Expire le 12/11/2026 à 23:59 (aujourd'hui)", Solde.libelleExpiration(ms(2026, 11, 12, 23, 59), MAINTENANT, UTC))
        assertEquals("Expire le 13/11/2026 à 23:59 (demain)", Solde.libelleExpiration(ms(2026, 11, 13, 23, 59), MAINTENANT, UTC))
        assertTrue(Solde.libelleExpiration(ms(2026, 11, 1, 23, 59), MAINTENANT, UTC).startsWith("Expiré le 01/11/2026"))
    }
}
