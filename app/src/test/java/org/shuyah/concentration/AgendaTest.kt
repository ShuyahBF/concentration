package org.shuyah.concentration

import org.junit.Assert.*
import org.junit.Test

/** Tests de la logique pure de l'agenda : corps JSON, dates affichées, fin de rendez-vous. */
class AgendaTest {
    @Test fun corpsJsonEchappes() {
        assertEquals("{\"gmail\":\"awa.badini@gmail.com\"}", Agenda.corpsDemande(" awa.badini@gmail.com "))
        assertEquals("{\"texte\":\"samedi 8h15 \\\"dentiste\\\"\"}", Agenda.corpsAnalyse("samedi 8h15 \"dentiste\""))
        assertEquals("{\"titre\":\"RDV\",\"debut\":\"2026-11-21T08:15:00+00:00\",\"fin\":\"2026-11-21T09:15:00+00:00\"}", Agenda.corpsCreation("RDV", "2026-11-21T08:15:00+00:00", "2026-11-21T09:15:00+00:00"))
        assertEquals("{\"id\":\"ev1\"}", Agenda.corpsSuppression("ev1"))
    }

    @Test fun adresseGoogle() {
        assertTrue(Agenda.gmailValide("awa.badini@gmail.com")); assertTrue(Agenda.gmailValide(" a@b.co "))
        assertFalse(Agenda.gmailValide("pas-un-mail")); assertFalse(Agenda.gmailValide("a@b"))
    }

    @Test fun dateAffichee() {
        assertEquals("21/11/2026 08:15", Agenda.dateAffichee("2026-11-21T08:15:00+00:00"))
        assertEquals("21/11/2026", Agenda.dateAffichee("2026-11-21"))
        assertEquals("illisible", Agenda.dateAffichee("illisible"))
    }

    @Test fun finDuRendezVous() {
        assertEquals("2026-11-21T09:15:00+00:00", Agenda.finIso("2026-11-21T08:15:00+00:00", 60))
        assertEquals("2026-12-01T00:30:00+00:00", Agenda.finIso("2026-11-30T23:45:00+00:00", 45))      // passage de mois
        assertEquals("2027-01-01T00:00:00+00:00", Agenda.finIso("2026-12-31T23:00:00+00:00", 60))      // passage d'année
    }

    @Test fun messagesLisibles() {
        assertTrue(Agenda.Reponse(409, "{\"detail\":\"Agenda non lié : reliez votre compte Google\"}").message().startsWith("Agenda non lié"))
        assertTrue(Agenda.Reponse(403, "").message().contains("administrateur"))
        assertFalse(Agenda.Reponse(500, "").ok); assertTrue(Agenda.Reponse(200, "{}").ok)
    }
}
