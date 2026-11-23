package org.shuyah.concentration

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * COPIE DE SÉCURITÉ CHIFFRÉE DE CONCENTRATION SUR LE TÉLÉPHONE (version 1.56, demande du propriétaire : « sauvegarder toutes les données de toutes les applications sur le périphérique, de manière cryptée »).
 *
 * Un FICHIER (extension .concsauv) enregistré où la personne le choisit (Documents, carte SD…), qui survit à la désinstallation. Il contient la base de données locale (paiements lus dans
 * les SMS, mesures de consommation) et tous les réglages (plages, applications, contacts autorisés, envoi à SAWALI…), chiffrés par la phrase secrète. Restauration : sur ce téléphone ou un autre.
 * Pour un développeur WinDev : c'est une « sauvegarde HFSQL » du fichier local, rangée dans une valise ZIP verrouillée par un mot de passe (la phrase).
 * Format du fichier : « CONC » + version (1) + valise chiffrée.
 */
object CopieLocale {
    private val MAGIE = "CONC".toByteArray()
    private const val BASE = "concentration.db"
    private val PREFS = listOf("concentration", "agenda_appareil")          // jeux de préférences copiés (réglages ; pas l'état de l'enregistrement d'écran)

    class Resultat(val ok: Boolean, val message: String)
    class Apercu(val entrees: Map<String, ByteArray>, val paiements: Long, val mesures: Long)

    private fun reglages(c: Context) = c.getSharedPreferences("copie_locale", Context.MODE_PRIVATE)
    fun derniereCopie(c: Context): Long = reglages(c).getLong("derniere", 0L)
    fun copieFaite(c: Context) { reglages(c).edit().putLong("derniere", System.currentTimeMillis()).apply() }
    fun nomFichier(): String = "concentration-" + java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.FRANCE).format(java.util.Date()) + ".concsauv"

    /** Compte les lignes de la base contenue dans `octets` (copie temporaire, rouverte en lecture) : montré AVANT de remplacer quoi que ce soit. */
    private fun compter(c: Context, octets: ByteArray): Pair<Long, Long> {
        val t = File.createTempFile("copie", ".db", c.cacheDir)
        try {
            t.writeBytes(octets)
            val bd = android.database.sqlite.SQLiteDatabase.openDatabase(t.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
            try {
                fun n(table: String) = try { android.database.DatabaseUtils.queryNumEntries(bd, table) } catch (e: Exception) { 0L }
                return Pair(n("paiements"), n("mesures"))
            } finally { bd.close() }
        } finally { t.delete() }
    }

    /** Photographie de toutes les données (base + réglages), chiffrée avec la phrase : octets du fichier à enregistrer. Vérifiée en rouvrant la copie de la base. */
    suspend fun preparer(c: Context, phrase: String): Pair<ByteArray?, Resultat> = try {
        withContext(Dispatchers.IO) {
            // Le journal de la base est vidé dans le fichier principal : la copie est complète
            try { Historique.de(c).writableDatabase.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() } } catch (e: Exception) { /* base sans journal : sans effet */ }
            val fichier = c.getDatabasePath(BASE)
            val base = if (fichier.exists()) fichier.readBytes() else ByteArray(0)
            val (paiements, mesures) = if (base.isNotEmpty()) compter(c, base) else Pair(0L, 0L)
            val (vivantsP, vivantsM) = Pair(
                try { android.database.DatabaseUtils.queryNumEntries(Historique.de(c).readableDatabase, "paiements") } catch (e: Exception) { 0L },
                try { android.database.DatabaseUtils.queryNumEntries(Historique.de(c).readableDatabase, "mesures") } catch (e: Exception) { 0L })
            if (paiements != vivantsP || mesures != vivantsM)
                throw IllegalStateException("la copie contient $paiements paiement(s) et $mesures mesure(s) alors que le téléphone en contient $vivantsP et $vivantsM : fermez puis rouvrez l'application et réessayez")
            val entrees = LinkedHashMap<String, ByteArray>()
            entrees["manifeste"] = "concentration;${Version.VERSION};${System.currentTimeMillis()}".toByteArray()
            if (base.isNotEmpty()) entrees["base/$BASE"] = base
            PREFS.forEach { nom -> entrees["prefs/$nom"] = CopieCrypto.prefsVersOctets(c.getSharedPreferences(nom, Context.MODE_PRIVATE).all) }
            val valise = CopieCrypto.chiffrer(CopieCrypto.cle(phrase), CopieCrypto.archiver(entrees))
            Pair(MAGIE + byteArrayOf(1) + valise, Resultat(true, "Copie prête : $paiements paiement(s), $mesures mesure(s) et vos réglages (${valise.size / 1024} Ko, chiffrée)"))
        }
    } catch (e: Exception) { Pair(null, Resultat(false, "Copie impossible : ${e.message ?: e.javaClass.simpleName}")) }

    /** Lit un fichier de copie : déchiffre avec la phrase et VÉRIFIE, sans rien modifier. */
    suspend fun apercu(c: Context, octets: ByteArray, phrase: String): Pair<Apercu?, Resultat> = try {
        withContext(Dispatchers.IO) {
            if (octets.size < 6 || !octets.copyOfRange(0, 4).contentEquals(MAGIE)) throw IllegalArgumentException("Ce fichier n'est pas une copie de sécurité de Concentration")
            val entrees = CopieCrypto.ouvrir(CopieCrypto.dechiffrer(CopieCrypto.cle(phrase), octets.copyOfRange(5, octets.size)))
            if (entrees["manifeste"]?.let { String(it).startsWith("concentration;") } != true) throw IllegalArgumentException("Cette copie n'est pas celle de Concentration")
            val (p, m) = entrees["base/$BASE"]?.let { compter(c, it) } ?: Pair(0L, 0L)
            Pair(Apercu(entrees, p, m), Resultat(true, ""))
        }
    } catch (e: IllegalArgumentException) { Pair(null, Resultat(false, e.message ?: "Lecture impossible")) }
      catch (e: Exception) { Pair(null, Resultat(false, "Lecture impossible : ${e.message ?: e.javaClass.simpleName}")) }

    /** Remplace la base et les réglages par ceux de l'aperçu. L'ancienne base est gardée à côté (« .avant_restauration ») ; l'application doit ensuite être relancée. */
    suspend fun restaurer(c: Context, a: Apercu): Resultat = try {
        withContext(Dispatchers.IO) {
            val fichier = c.getDatabasePath(BASE)
            Historique.fermer()                                                        // la base n'est plus tenue ouverte par l'application
            for (suffixe in listOf("-wal", "-shm", "-journal")) File(fichier.path + suffixe).delete()
            if (fichier.exists()) fichier.copyTo(File(fichier.path + ".avant_restauration"), overwrite = true)
            a.entrees["base/$BASE"]?.let { fichier.parentFile?.mkdirs(); fichier.writeBytes(it) }
            PREFS.forEach { nom ->
                val valeurs = a.entrees["prefs/$nom"]?.let { CopieCrypto.octetsVersPrefs(it) } ?: return@forEach
                val p = c.getSharedPreferences(nom, Context.MODE_PRIVATE); val e = p.edit().clear()
                @Suppress("UNCHECKED_CAST")
                valeurs.forEach { (k, v) -> when (v) { is String -> e.putString(k, v); is Int -> e.putInt(k, v); is Long -> e.putLong(k, v); is Float -> e.putFloat(k, v); is Boolean -> e.putBoolean(k, v); is Set<*> -> e.putStringSet(k, v as Set<String>) } }
                e.commit()
            }
            Resultat(true, "Données restaurées : ${a.paiements} paiement(s), ${a.mesures} mesure(s) et vos réglages")
        }
    } catch (e: Exception) { Resultat(false, "Restauration impossible : ${e.message ?: e.javaClass.simpleName}") }

    /** Relance l'application (les écrans ouverts tenaient l'ancienne base). */
    fun relancer(c: Context) {
        val i = c.packageManager.getLaunchIntentForPackage(c.packageName)!!.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        c.startActivity(i); Runtime.getRuntime().exit(0)
    }
}
