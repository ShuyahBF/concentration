package org.shuyah.concentration

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.text.Normalizer
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * CHIFFREMENT DE LA COPIE DE SÉCURITÉ de Concentration (version 1.56) — même principe que Personnel de maison : tout est chiffré SUR LE TÉLÉPHONE avec une clé tirée de la PHRASE
 * SECRÈTE (PBKDF2, 200 000 tours : lent exprès contre les essais en série) puis AES-256-GCM. Concentration n'a ni compte ni connexion (décision du propriétaire) : la phrase est donc
 * la seule clé du fichier ; sans elle, personne ne peut le lire. Logique pure, sans Android : testée sur ordinateur.
 */
object CopieCrypto {
    /** Sel fixe et public (il doit être retrouvé à partir de la seule phrase après une réinstallation). */
    private val SEL = "concentration/copie/v1".toByteArray()
    private const val TOURS = 200_000
    const val LONGUEUR_MIN_PHRASE = 12

    /** Phrase « normalisée » : espaces superflus retirés, minuscules, accents composés de façon unique (« Été » et « été » donnent la même clé). */
    fun normaliser(phrase: String): String = Normalizer.normalize(phrase.trim().replace(Regex("\\s+"), " "), Normalizer.Form.NFC).lowercase()

    /** Clé AES-256 tirée de la phrase. */
    fun cle(phrase: String): ByteArray = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(normaliser(phrase).toCharArray(), SEL, TOURS, 256)).encoded

    /** Chiffre : octet de version (1) + vecteur aléatoire (12 octets) + texte chiffré avec sa marque d'authentification (AES-256-GCM). */
    fun chiffrer(cle: ByteArray, clair: ByteArray): ByteArray {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(cle, "AES"), GCMParameterSpec(128, iv)); updateAAD(byteArrayOf(1)) }
        return byteArrayOf(1) + iv + c.doFinal(clair)
    }

    /** Déchiffre ; lève `IllegalArgumentException` si la phrase est fausse ou si le contenu a été modifié. */
    fun dechiffrer(cle: ByteArray, chiffre: ByteArray): ByteArray {
        require(chiffre.size > 29 && chiffre[0] == 1.toByte()) { "Fichier illisible ou d'un format inconnu" }
        try {
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(cle, "AES"), GCMParameterSpec(128, chiffre.copyOfRange(1, 13))); updateAAD(byteArrayOf(1)) }
            return c.doFinal(chiffre, 13, chiffre.size - 13)
        } catch (e: java.security.GeneralSecurityException) { throw IllegalArgumentException("Phrase secrète inexacte ou fichier abîmé") }
    }

    /** Archive : un fichier ZIP en mémoire (nom → contenu). */
    fun archiver(entrees: Map<String, ByteArray>): ByteArray {
        val sortie = ByteArrayOutputStream()
        ZipOutputStream(sortie).use { z -> entrees.forEach { (nom, octets) -> z.putNextEntry(ZipEntry(nom)); z.write(octets); z.closeEntry() } }
        return sortie.toByteArray()
    }
    fun ouvrir(zip: ByteArray): Map<String, ByteArray> {
        val r = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zip)).use { z -> while (true) { val e = z.nextEntry ?: break; if (!e.isDirectory) r[e.name] = z.readBytes() } }
        return r
    }

    // ---- Préférences : (clé → valeur) ⇄ octets, en gardant le TYPE de chaque valeur (texte, entier, booléen…) ----
    fun prefsVersOctets(valeurs: Map<String, Any?>): ByteArray {
        val o = org.json.JSONObject()
        valeurs.forEach { (k, v) ->
            val e = org.json.JSONObject()
            when (v) {
                is String -> e.put("t", "s").put("v", v); is Int -> e.put("t", "i").put("v", v); is Long -> e.put("t", "l").put("v", v)
                is Float -> e.put("t", "f").put("v", v.toDouble()); is Boolean -> e.put("t", "b").put("v", v)
                is Set<*> -> e.put("t", "e").put("v", org.json.JSONArray(v.map { it.toString() }))
                else -> return@forEach
            }
            o.put(k, e)
        }
        return o.toString().toByteArray()
    }
    fun octetsVersPrefs(octets: ByteArray): Map<String, Any> {
        val o = org.json.JSONObject(String(octets)); val r = LinkedHashMap<String, Any>()
        for (k in o.keys()) {
            val e = o.getJSONObject(k)
            r[k] = when (e.getString("t")) {
                "s" -> e.getString("v"); "i" -> e.getInt("v"); "l" -> e.getLong("v"); "f" -> e.getDouble("v").toFloat(); "b" -> e.getBoolean("v")
                else -> { val a = e.getJSONArray("v"); (0 until a.length()).map { a.getString(it) }.toSet() }
            }
        }
        return r
    }
}
