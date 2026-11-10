package org.shuyah.concentration

import org.junit.Assert.*
import org.junit.Test

/** Tests de la logique des plages horaires (sans Android). */
class PlageTest {
    private val nuit = Plage(20 * 60, 7 * 60)                       // 20:00 → 07:00, tous les jours

    @Test fun soireeEtPetitMatinSontDansLaPlageDeNuit() {
        assertEquals(11 * 60, nuit.minutesRestantes(2, 20 * 60))     // 20:00 → encore 11 h
        assertEquals(60, nuit.minutesRestantes(2, 6 * 60))           // 06:00 → encore 1 h
        assertNull(nuit.minutesRestantes(2, 7 * 60))                 // 07:00 : terminé
        assertNull(nuit.minutesRestantes(2, 12 * 60))                // midi : hors plage
    }

    @Test fun plageDeJourDansLaMemeJournee() {
        val j = Plage(8 * 60, 12 * 60)
        assertTrue(j.contient(0, 8 * 60)); assertFalse(j.contient(0, 12 * 60)); assertFalse(j.contient(0, 7 * 60 + 59))
    }

    @Test fun lesJoursSontCeuxOuLaPlageCommence() {
        val vendrediSeulement = Plage(20 * 60, 7 * 60, 1 shl 4)     // commence le vendredi (jour 4)
        assertTrue(vendrediSeulement.contient(4, 22 * 60))          // vendredi soir
        assertTrue(vendrediSeulement.contient(5, 3 * 60))           // samedi 03:00 : suite de la plage du vendredi
        assertFalse(vendrediSeulement.contient(5, 22 * 60))         // samedi soir : non
        assertFalse(vendrediSeulement.contient(4, 3 * 60))          // vendredi 03:00 : suite de la plage du jeudi, non cochée
    }

    @Test fun lundiEstLeJourZeroEtLaVeilleDuLundiEstDimanche() {
        val dimancheSeulement = Plage(20 * 60, 7 * 60, 1 shl 6)
        assertTrue(dimancheSeulement.contient(0, 2 * 60))           // lundi 02:00 : suite de la plage du dimanche
    }

    @Test fun plageVideEtTexte() {
        assertNull(Plage(600, 600).minutesRestantes(0, 600))
        assertEquals(nuit, Plage.depuisTexte(nuit.versTexte()))
        assertNull(Plage.depuisTexte("abc"))
        assertEquals("20:00 → 07:00", nuit.libelle())
    }
}
