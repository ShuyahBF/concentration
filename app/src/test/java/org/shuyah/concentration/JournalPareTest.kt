package org.shuyah.concentration

import org.junit.Assert.*
import org.junit.Test

/** Journal du pare-feu (1.52) : on garde les 30 dernières lignes, la date est devant le texte, un même message n'est pas répété chaque minute. */
class JournalPareTest {
    @Test fun ne_garde_que_les_dernieres_lignes() {
        var l = emptyList<String>()
        for (i in 1..45) l = JournalPareLogique.ajouter(l, "ligne $i")
        assertEquals(JournalPareLogique.MAX_LIGNES, l.size)
        assertEquals("ligne 16", l.first()); assertEquals("ligne 45", l.last())
        assertEquals(listOf("b", "c"), JournalPareLogique.ajouter(listOf("a", "b"), "c", max = 2))
    }

    @Test fun la_ligne_porte_la_date_devant_le_texte() {
        val l = JournalPareLogique.ligne(0L, "Tunnel ouvert")
        assertTrue(l.endsWith(" Tunnel ouvert"))
        assertEquals(14, l.length - " Tunnel ouvert".length)           // « jj/MM HH:mm:ss » = 14 caractères, puis l'espace et le texte
    }

    @Test fun un_message_identique_n_est_pas_repete() {
        val l = JournalPareLogique.ligne(1_700_000_000_000L, "Autorisation VPN absente")
        assertTrue(JournalPareLogique.identique(l, "Autorisation VPN absente"))
        assertTrue(JournalPareLogique.identique(l, "  Autorisation VPN absente "))
        assertFalse(JournalPareLogique.identique(l, "Tunnel ouvert"))
        assertFalse(JournalPareLogique.identique(null, "Tunnel ouvert"))
        assertFalse(JournalPareLogique.identique("court", "court"))
    }
}
