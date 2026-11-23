package org.shuyah.concentration

import org.junit.Assert.*
import org.junit.Test

/** Copie de sécurité chiffrée de Concentration : chiffrement, archive, préférences, garde-fou de phrase. */
class CopieCryptoTest {
    @Test fun allerRetourAvecLaBonnePhrase() {
        val cle = CopieCrypto.cle("le soleil se lève derrière le baobab")
        val clair = "paiements et réglages".toByteArray()
        assertArrayEquals(clair, CopieCrypto.dechiffrer(cle, CopieCrypto.chiffrer(cle, clair)))
        assertArrayEquals(cle, CopieCrypto.cle("  LE soleil se lève   derrière le baobab "))              // mêmes mots saisis autrement : même clé
    }

    @Test fun mauvaisePhraseOuFichierModifieRefuse() {
        val chiffre = CopieCrypto.chiffrer(CopieCrypto.cle("le soleil se lève derrière le baobab"), "secret".toByteArray())
        assertThrows(IllegalArgumentException::class.java) { CopieCrypto.dechiffrer(CopieCrypto.cle("une autre phrase tout aussi longue"), chiffre) }
        val abime = chiffre.copyOf(); abime[abime.size - 1] = (abime[abime.size - 1] + 1).toByte()
        assertThrows(IllegalArgumentException::class.java) { CopieCrypto.dechiffrer(CopieCrypto.cle("le soleil se lève derrière le baobab"), abime) }
    }

    @Test fun archiveEtPreferencesGardentLeursTypes() {
        val a = CopieCrypto.ouvrir(CopieCrypto.archiver(mapOf("x" to byteArrayOf(1, 2, 3), "y" to "texte".toByteArray())))
        assertArrayEquals(byteArrayOf(1, 2, 3), a["x"]); assertEquals("texte", String(a["y"]!!))
        val p = CopieCrypto.prefsVersOctets(mapOf("s" to "abc", "i" to 5, "l" to 7_000_000_000L, "f" to 1.5f, "b" to true, "e" to setOf("a", "b")))
        val r = CopieCrypto.octetsVersPrefs(p)
        assertEquals("abc", r["s"]); assertEquals(5, r["i"]); assertEquals(7_000_000_000L, r["l"]); assertEquals(1.5f, r["f"]); assertEquals(true, r["b"]); assertEquals(setOf("a", "b"), r["e"])
    }

    @Test fun gardeFouRefuseLesPhrasesFaibles() {
        assertFalse(GardeFouPhrase.verifier("trop courte").acceptable)
        assertFalse(GardeFouPhrase.verifier("123456789012").acceptable)
        assertFalse(GardeFouPhrase.verifier("mon mot de passe 2026").acceptable)
        assertTrue(GardeFouPhrase.verifier("le soleil se lève derrière le baobab").acceptable)
    }
}
