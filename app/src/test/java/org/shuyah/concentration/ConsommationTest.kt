package org.shuyah.concentration

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Tests de l'estimation de la consommation entre deux vérifications du solde. */
class ConsommationTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private fun ms(jour: Int, h: Int, min: Int = 0) = Calendar.getInstance(utc).apply { clear(); set(2026, Calendar.NOVEMBER, jour, h, min) }.timeInMillis

    @Test fun consommationEntreDeuxMesures() {
        val i = Consommation.intervalles(listOf(Consommation.Mesure(0, ms(10, 8), 1000.0), Consommation.Mesure(0, ms(10, 20), 700.0)))
        assertEquals(1, i.size); assertEquals(300.0, i[0].consoMo, 0.001); assertFalse(i[0].recharge); assertEquals(12.0, i[0].heures, 0.001)
    }

    @Test fun mesuresDansLeDesordreEtCartesSeparees() {
        val i = Consommation.intervalles(listOf(
            Consommation.Mesure(0, ms(10, 20), 700.0), Consommation.Mesure(1, ms(10, 9), 50.0),
            Consommation.Mesure(0, ms(10, 8), 1000.0), Consommation.Mesure(1, ms(10, 21), 20.0)))
        assertEquals(2, i.size)
        assertEquals(300.0, i.first { it.emplacement == 0 }.consoMo, 0.001); assertEquals(30.0, i.first { it.emplacement == 1 }.consoMo, 0.001)
    }

    @Test fun rechargeNEstPasUneConsommation() {
        val i = Consommation.intervalles(listOf(Consommation.Mesure(0, ms(10, 8), 100.0), Consommation.Mesure(0, ms(11, 8), 5000.0), Consommation.Mesure(0, ms(12, 8), 4000.0)))
        assertTrue(i[0].recharge); assertEquals(0.0, i[0].consoMo, 0.001)
        assertEquals(1000.0, Consommation.total(i), 0.001)                         // seul le 2e intervalle compte
        assertEquals(1000.0, Consommation.moyenneParJour(i)!!, 0.001)              // 1000 Mo sur 24 h
    }

    @Test fun repartitionSurLesJours() {
        // 23 h le 10 → 05 h le 11 : 6 h dont 1 h le 10 et 5 h le 11 ; 600 Mo consommés = 100 Mo le 10, 500 Mo le 11
        val i = Consommation.intervalles(listOf(Consommation.Mesure(0, ms(10, 23), 1000.0), Consommation.Mesure(0, ms(11, 5), 400.0)))
        val j = Consommation.parJour(i, utc)
        assertEquals(listOf("2026-11-10", "2026-11-11"), j.map { it.first })
        assertEquals(100.0, j[0].second, 0.01); assertEquals(500.0, j[1].second, 0.01)
    }

    @Test fun joursRestantsEtAbsenceDeDonnees() {
        assertEquals(5.0, Consommation.joursRestants(500.0, 100.0)!!, 0.001)
        assertNull(Consommation.joursRestants(500.0, null)); assertNull(Consommation.joursRestants(500.0, 0.0))
        assertNull(Consommation.moyenneParJour(emptyList()))
        assertTrue(Consommation.intervalles(listOf(Consommation.Mesure(0, ms(10, 8), 1.0))).isEmpty())
    }
}

/** Mesures aberrantes : connexion de la carte SIM coupée (Wi-Fi) → « 0 Mo » puis retour du volume. Données de l'historique réel simplifiées. */
class MesuresAberrantesTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private fun ms(jour: Int, h: Int, min: Int = 0) = Calendar.getInstance(utc).apply { clear(); set(2026, Calendar.NOVEMBER, jour, h, min) }.timeInMillis
    private fun m(h: Int, min: Int, mo: Double) = Consommation.Mesure(0, ms(14, h, min), mo)

    @Test fun creuxIsoleIgnore() {
        // 6,8 Go → 0 Mo en 2 minutes → 7,8 Go : ni consommation de 6,8 Go, ni fausse recharge
        val liste = listOf(m(17, 10, 6900.0), m(17, 14, 6800.0), m(17, 16, 0.0), m(18, 14, 7800.0))
        assertEquals(1, Consommation.nombreIgnorees(liste))
        val i = Consommation.intervalles(liste)
        assertEquals(2, i.size)
        assertTrue(i.none { it.consoMo > 500.0 })                                  // plus de fausse consommation de plusieurs Go
        assertTrue(i.none { it.recharge } || i.all { it.consoMo < 500.0 })
    }

    @Test fun plusieursMesuresACeroDeSuite() {
        val liste = listOf(m(10, 0, 6700.0), m(10, 9, 6700.0), m(10, 12, 0.0), m(10, 15, 0.0), m(10, 22, 6650.0))
        assertEquals(2, Consommation.nombreIgnorees(liste))
        assertEquals(3, Consommation.mesuresFiables(liste).size)
    }

    @Test fun creuxAUnGoPasAZero() {
        // Historique réel du propriétaire : 7,9 Go → 1,2 Go en 3 minutes → 7,8 Go ; puis 6,8 Go → 1,0 Go en 2 minutes → 7,8 Go (les creux ne sont PAS à zéro)
        val liste = listOf(m(10, 0, 8000.0), m(10, 9, 7900.0), m(10, 12, 1200.0), m(10, 22, 7800.0), m(10, 26, 7790.0),
            m(17, 14, 7850.0), m(17, 16, 1000.0), m(18, 14, 7800.0))
        assertEquals(2, Consommation.nombreIgnorees(liste))
        val i = Consommation.intervalles(liste)
        assertTrue(i.none { it.consoMo > 1000.0 })                                 // plus de fausse consommation de 6 Go
        assertTrue(i.none { it.recharge })                                         // ni de fausse recharge
    }

    @Test fun volumeReellementEpuiseConserve() {
        // Le volume tombe à zéro et NE remonte PAS : épuisement réel, rien n'est ignoré
        val liste = listOf(m(10, 0, 800.0), m(10, 30, 0.0), m(11, 0, 0.0))
        assertEquals(0, Consommation.nombreIgnorees(liste))
    }

    @Test fun baisseProgressiveConservee() {
        // Consommation normale, même importante : rien n'est ignoré
        val liste = listOf(m(8, 0, 11000.0), m(9, 0, 9000.0), m(10, 0, 4000.0), m(11, 0, 500.0))
        assertEquals(0, Consommation.nombreIgnorees(liste))
    }

    @Test fun relevesAberrantsALaLecture() {
        assertTrue(Consommation.chuteImpossible(6800.0, 0.0, 120_000L))             // 6,8 Go → 0 en 2 minutes : jamais enregistré
        assertFalse(Consommation.chuteImpossible(6800.0, 0.0, 5 * 3_600_000L))     // 5 h plus tard : possible (forfait épuisé)
        assertFalse(Consommation.chuteImpossible(6800.0, 5000.0, 60_000L))          // baisse normale
        assertFalse(Consommation.chuteImpossible(null, 0.0, 60_000L))               // premier relevé
        assertFalse(Consommation.chuteImpossible(300.0, 0.0, 60_000L))              // petit volume : on ne présume rien
        assertTrue(Consommation.chuteImpossible(7900.0, 1200.0, 180_000L))          // 7,9 Go → 1,2 Go en 3 minutes (cas réel) : aberrant
        assertFalse(Consommation.chuteImpossible(7900.0, 3000.0, 6 * 3_600_000L))   // forte consommation sur 6 h : possible
        assertFalse(Consommation.chuteImpossible(9000.0, 4000.0, 3_600_000L))       // 5 Go en 1 h : très forte mais possible
    }
}
