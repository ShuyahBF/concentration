package org.shuyah.concentration

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * AGENDA GOOGLE dans Concentration (version 1.32) : le propriétaire du téléphone lie SON agenda, voit ses prochains rendez-vous et en crée,
 * y compris par note vocale (« samedi 8h15 rdv chez le dentiste »).
 *
 * Pour un développeur WinDev : Concentration ne parle JAMAIS à Google. Il appelle la passerelle « Agenda » de SAWALI (lot 403) avec un identifiant et une clé
 * d'appareil que l'application GÉNÈRE ELLE-MÊME au premier usage (X-Appareil / X-Cle-Appareil) : rien à saisir, aucun téléphone source à créer. SAWALI enregistre l'appareil
 * à la « demande d'accès » (compte Google déclaré + nom de l'appareil) et ne donne accès qu'à l'agenda lié à CET appareil, une fois la demande acceptée par l'administrateur.
 * Aucun jeton Google n'est stocké sur le téléphone.
 *
 * Étapes : 1) « demander » l'accès avec son compte Google ; 2) l'administrateur de SAWALI l'accepte ; 3) « lier » (page Google, une seule fois) ;
 * 4) consulter, analyser une note, créer (après relecture et confirmation), supprimer.
 * Les parties « corps JSON » et « date affichée » sont des logiques pures testées (AgendaTest) ; les appels réseau sont bloquants (hors écran).
 */
object Agenda {
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    data class Reponse(val code: Int, val corps: String) {
        val ok get() = code == 200
        /** Message lisible : le « detail » renvoyé par SAWALI s'il existe, sinon un texte selon le code HTTP. */
        fun message(): String {
            // Lecture du « detail » sans bibliothèque JSON (testable sur ordinateur) : texte entre guillemets, séquences \" \\ \n décodées
            val brut = Regex("\"detail\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(corps)?.groupValues?.get(1) ?: return messageHttp(code)
            return brut.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\").ifBlank { messageHttp(code) }
        }
    }
    data class Etat(val lie: Boolean, val email: String, val demande: String, val gmail: String, val approbationRequise: Boolean)
    data class Evenement(val id: String, val titre: String, val debut: String, val lieu: String, val toutLaJournee: Boolean)
    data class Proposition(val titre: String, val debut: String, val libelle: String, val manque: List<String>, val duree: Int)

    fun messageHttp(code: Int) = when (code) {
        401 -> "appareil refusé par SAWALI : désinstallez puis réinstallez Concentration, ou contactez l'administrateur"
        403 -> "accès à l'agenda pas encore accepté par l'administrateur"
        409 -> "agenda non lié : reliez votre compte Google"
        in 500..599 -> "serveur SAWALI momentanément indisponible"
        else -> "réponse inattendue ($code)"
    }

    // ---- Corps JSON (logique pure, testée) ----
    fun corpsDemande(gmail: String, nomAppareil: String = "") =
        "{\"gmail\":\"${EnvoiSawali.echapper(gmail.trim())}\",\"nom\":\"${EnvoiSawali.echapper(nomAppareil.trim().take(80))}\"}"

    /** Identifiant (24 caractères) et clé (32 caractères) aléatoires, lettres et chiffres : générés une seule fois, gardés dans les préférences privées de l'application. */
    fun aleatoire(longueur: Int): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"; val hasard = java.security.SecureRandom()
        return (1..longueur).map { alphabet[hasard.nextInt(alphabet.length)] }.joinToString("")
    }
    private fun appareil(c: Context): Pair<String, String> {
        val p = c.getSharedPreferences("agenda_appareil", Context.MODE_PRIVATE)
        var id = p.getString("id", null); var cle = p.getString("cle", null)
        if (id == null || cle == null) { id = aleatoire(24); cle = aleatoire(32); p.edit().putString("id", id).putString("cle", cle).apply() }
        return Pair(id, cle)
    }
    fun corpsAnalyse(texte: String) = "{\"texte\":\"${EnvoiSawali.echapper(texte.trim().take(500))}\"}"
    fun corpsCreation(titre: String, debut: String, fin: String) =
        "{\"titre\":\"${EnvoiSawali.echapper(titre.trim().take(200))}\",\"debut\":\"${EnvoiSawali.echapper(debut)}\",\"fin\":\"${EnvoiSawali.echapper(fin)}\"}"
    fun corpsSuppression(id: String) = "{\"id\":\"${EnvoiSawali.echapper(id)}\"}"

    /** Adresse e-mail plausible (la vraie vérification est faite par l'administrateur et par Google). */
    fun gmailValide(s: String) = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$").matches(s.trim())

    /** « 2026-11-21T08:15:00+00:00 » → « 21/11/2026 08:15 » ; une date seule (toute la journée) → « 21/11/2026 ». Texte inchangé s'il est illisible. */
    fun dateAffichee(iso: String): String {
        val m = Regex("^(\\d{4})-(\\d{2})-(\\d{2})(?:T(\\d{2}):(\\d{2}))?").find(iso) ?: return iso
        val (a, mo, j, h, mi) = m.destructured
        return "$j/$mo/$a" + if (h.isNotEmpty()) " $h:$mi" else ""
    }

    /** Date de fin ISO = début + durée (minutes), dans le même fuseau « +00:00 » (Burkina Faso). Arithmétique simple sur la date/heure : exacte, sans fuseau local du téléphone. */
    fun finIso(debutIso: String, minutes: Int): String {
        val m = Regex("^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2})").find(debutIso) ?: return debutIso
        val (a, mo, j, h, mi) = m.destructured
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear(); set(a.toInt(), mo.toInt() - 1, j.toInt(), h.toInt(), mi.toInt()); add(java.util.Calendar.MINUTE, minutes)
        }
        return String.format(java.util.Locale.ROOT, "%04d-%02d-%02dT%02d:%02d:00", cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH), cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE)) + debutIso.substring(19)
    }

    // ---- Réseau (bloquant : à appeler hors de l'écran) ----
    fun appeler(c: Context, chemin: String, corps: String = "{}"): Reponse {
        val (id, cle) = appareil(c)
        return try {
            val requete = Request.Builder().url(EnvoiSawali.adresse(Reglages.sawaliUrl(c)) + "/api/webhook/agenda/" + chemin).header("X-Appareil", id).header("X-Cle-Appareil", cle)
                .post(corps.toRequestBody(JSON)).build()
            http.newCall(requete).execute().use { Reponse(it.code, it.body?.string() ?: "") }
        } catch (e: Exception) { Reponse(503, "{\"detail\":\"réseau indisponible : ${EnvoiSawali.echapper(e.message ?: e.javaClass.simpleName)}\"}") }
    }

    fun etat(r: Reponse): Etat? = try {
        val o = JSONObject(r.corps); Etat(o.optBoolean("lie"), o.optString("email"), o.optString("demande"), o.optString("gmail"), o.optBoolean("approbation_requise", true))
    } catch (e: Exception) { null }

    fun evenements(r: Reponse): List<Evenement> = try {
        val t = JSONObject(r.corps).getJSONArray("evenements")
        (0 until t.length()).map { i -> t.getJSONObject(i).let { Evenement(it.optString("id"), it.optString("titre"), it.optString("debut"), it.optString("lieu"), it.optBoolean("toute_la_journee")) } }
    } catch (e: Exception) { emptyList() }

    fun proposition(r: Reponse): Proposition? = try {
        val o = JSONObject(r.corps); val m = o.optJSONArray("manque")
        Proposition(o.optString("titre"), o.getString("debut"), o.optString("libelle"), (0 until (m?.length() ?: 0)).map { m!!.getString(it) }, o.optInt("duree_minutes", 60))
    } catch (e: Exception) { null }

    fun urlLiaison(r: Reponse): String? = try { JSONObject(r.corps).getString("url") } catch (e: Exception) { null }
}
