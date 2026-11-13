package org.shuyah.concentration

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStream

/**
 * LECTURE DES SMS PASSÉS : ne garde que les SMS de PAIEMENT (Orange Money, Moov, Telecel, banques) et les range dans la base locale. Tous les autres SMS sont lus puis
 * oubliés aussitôt : rien d'autre n'est conservé, rien ne quitte le téléphone.
 *
 * Deux sources, qui donnent le même résultat :
 *  1. LA BOÎTE DE RÉCEPTION du téléphone, lue directement (autorisation « SMS » : seulement dans la version « SMS » de l'application, voir README) ;
 *  2. UN FICHIER DE SAUVEGARDE XML, celui que produit l'application gratuite « SMS Backup & Restore » (version sans autorisation « SMS » : fonctionne avec Google Play Protect).
 *
 * Un même SMS lu deux fois n'est enregistré qu'une fois (la base refuse les doublons : même expéditeur, même instant, même texte, ou même numéro de transaction).
 * La logique de lecture du XML est « pure » (testée par ImportSmsTest) ; la lecture de la boîte de réception utilise Android.
 */
object ImportSms {
    /** Bilan d'une lecture : SMS parcourus, SMS reconnus comme paiements, nouveaux paiements réellement ajoutés (les autres étaient déjà là). */
    data class Bilan(val lus: Int, val paiements: Int, val ajoutes: Int)

    /** Un SMS lu : expéditeur, instant de réception (ms), texte. */
    data class Sms(val adresse: String, val dateMs: Long, val corps: String)

    // ---- Fichier de sauvegarde XML (« SMS Backup & Restore ») ----
    private val ATTRIBUT = Regex("([A-Za-z_:][\\w:.-]*)=\"([^\"]*)\"")

    /** Défait les entités XML : &amp; &lt; &gt; &quot; &apos; et les codes &#10; / &#x1F600;. */
    fun decoder(s: String): String {
        if (!s.contains('&')) return s
        return Regex("&(#x[0-9A-Fa-f]+|#\\d+|amp|lt|gt|quot|apos);").replace(s) { m ->
            when (val e = m.groupValues[1]) {
                "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"
                else -> try { String(Character.toChars(if (e[1] == 'x') e.substring(2).toInt(16) else e.substring(1).toInt())) } catch (x: Exception) { m.value }
            }
        }
    }

    /**
     * Lit un fichier de sauvegarde : une balise <sms address="…" date="…" type="1" body="…" /> par SMS (parfois sur plusieurs lignes). Seuls les SMS REÇUS (type 1) sont
     * retenus. `pourChaque` est appelé pour chacun, au fur et à mesure (le fichier n'est jamais chargé en entier : il peut peser des centaines de Mo).
     */
    fun lireXml(entree: InputStream, pourChaque: (Sms) -> Unit) {
        val lecteur = BufferedReader(entree.reader(Charsets.UTF_8), 1 shl 16)
        val balise = StringBuilder()
        var dedans = false
        for (ligne in lecteur.lineSequence()) {
            var reste = ligne
            if (!dedans) {
                val debut = reste.indexOf("<sms ")
                if (debut < 0) continue
                reste = reste.substring(debut); dedans = true; balise.setLength(0)
            }
            balise.append(reste).append(' ')
            if (reste.contains("/>") || reste.contains("</sms>")) {                       // la balise est complète
                dedans = false
                val attributs = ATTRIBUT.findAll(balise).associate { it.groupValues[1] to it.groupValues[2] }
                if (attributs["type"] == "1") {
                    val ms = attributs["date"]?.toLongOrNull()
                    val corps = attributs["body"]
                    if (ms != null && corps != null) pourChaque(Sms(decoder(attributs["address"] ?: ""), ms, decoder(corps)))
                }
            }
        }
    }

    /** Range dans la base les SMS qui sont des paiements ; rend le nombre de SMS lus, de paiements reconnus et de nouveaux paiements ajoutés. */
    fun enregistrer(c: Context, source: ((Sms) -> Unit) -> Unit, progression: (Int, Int) -> Unit = { _, _ -> }): Bilan {
        val base = Historique.de(c)
        var lus = 0; var paiements = 0; var ajoutes = 0
        val lot = ArrayList<Paiements.Paiement>()
        fun vider() { if (lot.isNotEmpty()) { ajoutes += base.ajouterPaiements(lot); lot.clear() } }
        source { sms ->
            lus++
            Paiements.analyser(sms.adresse, sms.corps, sms.dateMs)?.let { lot += it; paiements++ }
            if (lot.size >= 200) vider()
            if (lus % 500 == 0) progression(lus, paiements)
        }
        vider(); progression(lus, paiements)
        return Bilan(lus, paiements, ajoutes)
    }

    /** Importe un fichier XML choisi par l'utilisateur (sélecteur de fichiers du système : aucune autorisation requise). */
    fun importerFichier(c: Context, fichier: Uri, progression: (Int, Int) -> Unit): Bilan =
        c.contentResolver.openInputStream(fichier)!!.use { flux -> enregistrer(c, { chaque -> lireXml(flux, chaque) }, progression) }

    // ---- Boîte de réception du téléphone (version « SMS ») ----
    /** Vrai dans la version « SMS » de l'application (celle qui déclare l'autorisation de lire les SMS). */
    fun lectureDirecteDisponible(): Boolean = BuildConfig.SMS_DIRECT

    /** Autorisation de lire les SMS accordée par l'utilisateur ? */
    fun autorisationAccordee(c: Context) = c.checkSelfPermission("android.permission.READ_SMS") == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Lit TOUS les SMS reçus de la boîte de réception (du plus récent au plus ancien) et range les paiements. Rend null si l'autorisation manque. */
    fun lireBoite(c: Context, progression: (Int, Int) -> Unit): Bilan? {
        if (!autorisationAccordee(c)) return null
        return enregistrer(c, { chaque ->
            c.contentResolver.query(Uri.parse("content://sms/inbox"), arrayOf("address", "date", "body"), null, null, "date DESC")?.use { q ->
                while (q.moveToNext()) chaque(Sms(q.getString(0) ?: "", q.getLong(1), q.getString(2) ?: ""))
            }
        }, progression)
    }
}
