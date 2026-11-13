package org.shuyah.concentration

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Tests des statistiques de paiements : filtres (période, opérateur, sens, texte), totaux, classements, barres par jour/mois, CSV. */
class StatsPaiementsTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private fun ms(a: Int, m: Int, j: Int, h: Int = 12) = Calendar.getInstance(utc).apply { clear(); set(a, m - 1, j, h, 0, 0) }.timeInMillis
    private fun p(adresse: String, a: Int, m: Int, j: Int, genre: String, montant: Long, tiers: String, corps: String = "", ref: String? = null) =
        Paiements.Paiement(adresse, ms(a, m, j), genre, montant, tiers, null, corps, ref)

    private val maintenant = ms(2026, 11, 13)
    private val liste = listOf(
        p("OrangeMoney", 2026, 11, 13, "DEPENSE", 2150, "ALIMENTATION LA MENAGERE", ref = "MP261113.1339.1"),
        p("OrangeMoney", 2026, 11, 10, "DEPENSE", 5000, "Pharmacie Étoile"),
        p("MOOV", 2026, 11, 2, "RECETTE", 25000, "OUEDRAOGO Awa"),
        p("TELECEL", 2026, 10, 20, "DEPENSE", 10000, "pharmacie etoile"),
        p("CORIS BANK", 2026, 9, 5, "DEPENSE", 40000, "SONABEL"),
        p("AMI", 2026, 11, 12, "DEPENSE", 700, "Kiosque"))

    @Test fun operateursDevinesDepuisLExpediteur() {
        assertEquals(listOf("Orange Money", "Orange Money", "Moov Money", "Telecel Money", "Banque", "Autre"), liste.map { StatsPaiements.operateurDe(it) })
    }

    @Test fun bornesDesPeriodes() {
        val b = { per: String -> StatsPaiements.bornes(StatsPaiements.Filtre(periode = per), maintenant, utc) }
        assertEquals(ms(2026, 11, 13, 0) to ms(2026, 11, 14, 0), b("aujourdhui"))
        assertEquals(ms(2026, 11, 7, 0) to ms(2026, 11, 14, 0), b("7j"))
        assertEquals(ms(2026, 11, 1, 0) to ms(2026, 11, 14, 0), b("mois"))
        assertEquals(ms(2026, 10, 1, 0) to ms(2026, 11, 1, 0), b("mois-1"))
        assertEquals(null to null, b("tout"))
        // dates personnalisées : « au » est INCLUS (on prend la fin de ce jour)
        assertEquals(ms(2026, 11, 2, 0) to ms(2026, 11, 11, 0), StatsPaiements.bornes(StatsPaiements.Filtre("perso", ms(2026, 11, 2, 0), ms(2026, 11, 10, 0)), maintenant, utc))
    }

    @Test fun filtresCombines() {
        val f = { fi: StatsPaiements.Filtre -> StatsPaiements.filtrer(liste, fi, maintenant, utc).map { it.montant } }
        assertEquals(listOf(2150L, 700L, 5000L, 25000L, 10000L), f(StatsPaiements.Filtre("30j")))                           // 30 jours, plus récent d'abord (le 5/09 est exclu)
        assertEquals(listOf(2150L, 5000L), f(StatsPaiements.Filtre("tout", operateur = "Orange Money")))
        assertEquals(listOf(25000L), f(StatsPaiements.Filtre("tout", genre = "RECETTE")))
        assertEquals(listOf(5000L, 10000L), f(StatsPaiements.Filtre("tout", q = "PHARMACIE etoile")))                       // sans accents ni majuscules
        assertEquals(listOf(2150L), f(StatsPaiements.Filtre("tout", q = "MP261113")))                                       // numéro de transaction
        assertEquals(6, f(StatsPaiements.Filtre("tout")).size)
    }

    @Test fun totauxEtOperateurs() {
        val t = StatsPaiements.totaux(liste)
        assertEquals(6, t.nombre); assertEquals(57850L, t.depenses); assertEquals(25000L, t.recettes); assertEquals(25000L - 57850L, t.net)
        assertEquals(40000L, t.plusGrosse); assertEquals(57850L / 5, t.depenseMoyenne); assertEquals(5, t.nbDepenses)
        assertEquals(StatsPaiements.Totaux(0, 0, 0, 0, 0, 0, 0, 0), StatsPaiements.totaux(emptyList()))
        val ops = StatsPaiements.parOperateur(liste)
        assertEquals("Orange Money", ops.first().operateur); assertEquals(2, ops.first().nombre); assertEquals(7150L, ops.first().depenses)
    }

    @Test fun classementsRegroupentLesNomsSansAccents() {
        val top = StatsPaiements.topTiers(liste)
        assertEquals("SONABEL", top[0].nom); assertEquals(40000L, top[0].total)
        val pharmacie = top.first { it.nom.lowercase().contains("pharmacie") }
        assertEquals(2, pharmacie.nombre); assertEquals(15000L, pharmacie.total)                                           // « Étoile » et « etoile » regroupés
        assertEquals(listOf(40000L, 10000L, 5000L), StatsPaiements.topMontants(liste, n = 3).map { it.montant })
        val parts = StatsPaiements.parts(top, 57850L, n = 2)
        assertEquals(listOf("SONABEL", pharmacie.nom, "Autres"), parts.map { it.first }); assertEquals(57850L, parts.sumOf { it.second })
    }

    @Test fun barresParJourEtParMoisEtIntervalle() {
        val jours = StatsPaiements.parPeriode(liste, false, utc)
        assertEquals("2026-09-05", jours.first().cle); assertEquals("2026-11-13", jours.last().cle)
        val mois = StatsPaiements.parPeriode(liste, true, utc)
        assertEquals(listOf("2026-09", "2026-10", "2026-11"), mois.map { it.cle })
        assertEquals(25000L, mois.last().recettes); assertEquals(4, mois.last().nombre)
        assertEquals(ms(2026, 11, 13, 0) to ms(2026, 11, 14, 0), StatsPaiements.intervalleDe("2026-11-13", utc))
        assertEquals(ms(2026, 11, 1, 0) to ms(2026, 12, 1, 0), StatsPaiements.intervalleDe("2026-11", utc))
    }

    @Test fun csvAvecEntetesEtGuillemetsEchappes() {
        val c = StatsPaiements.csv(listOf(p("MOOV", 2026, 11, 2, "RECETTE", 25000, "Awa \"la grande\"")), utc).lines()
        assertTrue(c[0].startsWith("﻿\"Date\";\"Opérateur\""))
        assertEquals("\"02/11/2026 12:00\";\"Moov Money\";\"Recette\";\"25000\";\"Awa \"\"la grande\"\"\";\"\";\"\"", c[1])
    }
}
