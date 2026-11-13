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
