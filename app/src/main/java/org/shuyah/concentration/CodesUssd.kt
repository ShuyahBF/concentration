package org.shuyah.concentration

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * CODES USSD CENTRALISÉS PAR auRor : la base des codes de tous les logiciels du propriétaire est tenue dans auRor (page « Codes USSD »).
 * Concentration ne contient AUCUN code écrit en dur : il lit la base d'auRor, en garde une COPIE LOCALE et s'en sert même sans Internet.
 * Si Orange ou Moov change un menu, l'administrateur corrige le code dans auRor ; ici, la copie se met à jour toute seule au prochain accès à Internet.
 *
 * Pour un développeur WinDev :
 *  - GET <auRor>/api/android/ussd-codes renvoie {"version", "codes":[{id, operateur, titre, code, usage, demande_pin, …}]} ;
 *  - l'en-tête ETag (= version) est renvoyé à l'appel suivant (If-None-Match) : réponse 304 sans corps s'il n'y a rien de nouveau ;
 *  - connexion perdue ou serveur indisponible : on garde la copie locale (jamais d'erreur bloquante) ;
 *  - un code qui contient le mot PIN (ex. *123*4*PIN#) reçoit le code secret saisi par l'utilisateur au moment de l'appel ;
 *    ce code secret n'est JAMAIS enregistré (ni sur le téléphone, ni envoyé à auRor).
 */
object CodesUssd {
    const val URL_PAR_DEFAUT = "https://api.auror.space"
    /** Rôles fonctionnels d'un code (mêmes valeurs que dans auRor). */
    const val USAGE_RELEVE = "releve"
    const val USAGE_NOTIFICATION = "renvoi_notification"

    data class Code(val id: String, val operateur: String, val titre: String, val code: String, val usage: String, val demandePin: Boolean)
    data class Copie(val version: String, val codes: List<Code>)

    private val http = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()

    /** Lit le JSON d'auRor ; null si le texte est illisible (la copie précédente est alors conservée). */
    fun lireJson(json: String): Copie? = try {
        val racine = JSONObject(json)
        val tableau = racine.getJSONArray("codes")
        Copie(racine.optString("version"), (0 until tableau.length()).map { i ->
            val o = tableau.getJSONObject(i)
            Code(o.optString("id"), o.optString("operateur"), o.optString("titre"), o.optString("code"), o.optString("usage"), o.optBoolean("demande_pin"))
        })
    } catch (e: Exception) { null }

    /** Remplace le mot PIN du code par le code secret saisi (ex. *123*4*PIN# → *123*4*0000#). Fonction pure. */
    fun completer(code: String, pin: String): String = code.replace(Regex("PIN", RegexOption.IGNORE_CASE), pin.trim())

    /** Le code d'un opérateur pour un usage donné : « Orange BF » (nom de la carte SIM) retrouve le code « Orange ». Null si la base n'en a pas. */
    fun trouver(copie: Copie?, nomCarte: String, usage: String): Code? {
        val jeton = Solde.jetonOperateur(nomCarte)
        return copie?.codes?.firstOrNull { it.usage == usage && (jeton.startsWith(it.operateur.lowercase(Locale.ROOT)) || it.operateur.lowercase(Locale.ROOT).startsWith(jeton)) }
    }

    /** Adresse d'auRor nettoyée (sans barre finale) ; vide = adresse par défaut. */
    fun adresse(brut: String) = brut.trim().trimEnd('/').ifBlank { URL_PAR_DEFAUT }

    /** La copie locale (vide si Internet n'a jamais répondu). */
    fun copie(c: Context): Copie? = Reglages.codesCorps(c).takeIf { it.isNotBlank() }?.let { lireJson(it) }

    /**
     * Met la copie locale à jour. Bloquant : à appeler hors de l'écran. Rend vrai si auRor a répondu (nouveauté ou 304), faux si Internet ou le serveur
     * est indisponible — la copie locale reste alors utilisable telle quelle.
     */
    fun actualiser(c: Context): Boolean = try {
        val etag = Reglages.codesEtag(c)
        val demande = Request.Builder().url(adresse(Reglages.aurorUrl(c)) + "/api/android/ussd-codes").apply { if (etag.isNotBlank()) header("If-None-Match", etag) }.build()
        http.newCall(demande).execute().use { r ->
            when {
                r.code == 304 -> true
                r.code == 200 -> {
                    val corps = r.body?.string().orEmpty()
                    if (lireJson(corps) != null) { Reglages.codes(c, corps, r.header("ETag").orEmpty()); true } else false
                }
                else -> false
            }
        }
    } catch (e: Exception) { false }
}
