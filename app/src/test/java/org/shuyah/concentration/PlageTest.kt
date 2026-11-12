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

    @Test fun prochainChangementDEtat() {
        assertEquals(60, Plage.minutesAvantChangement(listOf(nuit), 2, 19 * 60))            // 19:00 → entrée à 20:00
        assertEquals(11 * 60, Plage.minutesAvantChangement(listOf(nuit), 2, 20 * 60))        // 20:00 → sortie à 07:00
        assertNull(Plage.minutesAvantChangement(emptyList(), 2, 600))                         // aucune plage : jamais de changement
    }

    @Test fun sonsDesChangementsDEtat() {
        assertEquals(Sons.Signal.DEBUT, Sons.signalDe("libre", "active"))
        assertEquals(Sons.Signal.FIN, Sons.signalDe("active", "libre"))
        assertEquals(Sons.Signal.PAUSE, Sons.signalDe("active", "pause"))
        assertEquals(Sons.Signal.REPRISE, Sons.signalDe("pause", "active"))
        assertEquals(Sons.Signal.FIN, Sons.signalDe("pause", "libre"))
        assertNull(Sons.signalDe("libre", "pause"))
    }

    @Test fun contactsAutorises() {
        assertTrue(Contacts.correspond(listOf("Liluvine", "Bonjour"), listOf("liluvine")))
        assertTrue(Contacts.correspond(listOf("Groupe famille", "Éléonore : coucou"), listOf("eleonore")))      // accents et casse ignorés
        assertFalse(Contacts.correspond(listOf("Client X", "Besoin d'aide"), listOf("Liluvine")))
        assertFalse(Contacts.correspond(listOf("Liluvine"), emptyList()))
        assertFalse(Contacts.correspond(listOf("a b c"), listOf("a")))                                          // nom trop court ignoré
    }

    @Test fun plageInactiveNeBloqueJamaisEtSeRelitAvecSonStatut() {
        val inactive = nuit.copy(actif = false)
        assertNull(inactive.minutesRestantes(2, 22 * 60))
        assertFalse(inactive.contient(2, 22 * 60))
        assertEquals(inactive, Plage.depuisTexte(inactive.versTexte()))
        assertEquals("1200,420,127,0", inactive.versTexte())
        assertEquals(nuit, Plage.depuisTexte("1200,420,127"))          // ancien format (3 nombres) : plage active
        assertNull(Plage.minutesAvantChangement(listOf(inactive), 2, 19 * 60))      // une plage inactive ne change jamais l'état
    }

    @Test fun plageEnCoursEstVerrouilleeMaisPasUnePlageFuture() {
        assertTrue(nuit.enCours(2, 22 * 60))                            // 22:00 : en cours → verrouillée
        assertFalse(nuit.enCours(2, 12 * 60))                           // midi : pas commencée → modifiable
        assertFalse(nuit.copy(actif = false).enCours(2, 22 * 60))       // inactive : modifiable
    }
}
