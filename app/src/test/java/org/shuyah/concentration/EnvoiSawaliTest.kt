package org.shuyah.concentration

import org.junit.Assert.*
import org.junit.Test

/** Tests de l'envoi à SAWALI : JSON correctement échappé, réglages, messages (logique pure). */
class EnvoiSawaliTest {
    @Test fun echappementJson() {
        assertEquals("a\\\"b\\\\c\\nd\\t\\u0001", EnvoiSawali.echapper("a\"b\\c\nd\t\u0001"))
        assertEquals("Café — étage", EnvoiSawali.echapper("Café — étage"))                       // accents conservés
    }

    @Test fun corpsDuContratV1() {
        val p = Paiements.Paiement("OrangeMoney", 1794573540000L, "DEPENSE", 2150, "ALIMENTATION \"X\"", 6403, "Trans id: MP1\nSolde", "MP261113.1339.1", 7)
        val sans = Paiements.Paiement("MOOV", 5L, "RECETTE", 25000, "Awa", null, "reçu", null)
        assertEquals("{\"paiements\":[{\"adresse\":\"OrangeMoney\",\"recu_le_ms\":1794573540000,\"genre\":\"DEPENSE\",\"montant\":2150,\"tiers\":\"ALIMENTATION \\\"X\\\"\"," +
            "\"solde\":6403,\"reference\":\"MP261113.1339.1\",\"corps\":\"Trans id: MP1\\nSolde\"},{\"adresse\":\"MOOV\",\"recu_le_ms\":5,\"genre\":\"RECETTE\",\"montant\":25000,\"tiers\":\"Awa\"," +
            "\"solde\":null,\"reference\":null,\"corps\":\"reçu\"}]}", EnvoiSawali.corpsJson(listOf(p, sans)))
        assertEquals("{\"paiements\":[]}", EnvoiSawali.corpsJson(emptyList()))
    }

    @Test fun reglagesEtAdresse() {
        assertTrue(EnvoiSawali.configure("a1b2c3d4", "x".repeat(20))); assertFalse(EnvoiSawali.configure("", "x".repeat(20))); assertFalse(EnvoiSawali.configure("a1b2c3d4", "court"))
        assertEquals("https://api.sawalismartsystems.com", EnvoiSawali.adresse("  ")); assertEquals("https://x.bf", EnvoiSawali.adresse(" https://x.bf/ "))
        assertTrue(EnvoiSawali.messageHttp(401).contains("clé")); assertEquals("envoi réussi", EnvoiSawali.messageHttp(200))
    }
}
