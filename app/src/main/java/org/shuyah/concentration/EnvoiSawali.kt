package org.shuyah.concentration

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * ENVOI DES PAIEMENTS À SAWALI : les paiements de la base du téléphone sont envoyés par lots à SAWALI (menu « Transactions SMS », administrateur), qui les affiche avec
 * les mêmes filtres que l'historique d'auRor et peut les associer à une plateforme (ALBARKA…). Chaque paiement n'est envoyé qu'une fois (colonne `envoye`) ; SAWALI refuse
 * de toute façon les doublons.
 *
 * Contrat d'échange v1 (SAWALI lot 389) :
 *   POST <adresse>/api/transactions-sms/v1/envoi    en-têtes X-Source: <identifiant du téléphone>, X-Cle: <clé>
 *   corps {"paiements":[{"adresse","recu_le_ms","genre","montant","tiers","solde","reference","corps"}, …]}   (300 au plus)
 * Les trois réglages (adresse, identifiant, clé) sont donnés par SAWALI quand on ajoute le téléphone ; la clé n'est montrée qu'une fois.
 * La partie « corps JSON » est une logique pure testée (EnvoiSawaliTest) ; l'envoi réseau est en arrière-plan, jamais sur l'écran.
 */
object EnvoiSawali {
    const val URL_PAR_DEFAUT = "https://api.sawalismartsystems.com"
    const val LOT = 300                                    // plafond du contrat v1
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    /** Bilan d'un envoi : paiements envoyés pendant cet appel, restant à envoyer, message lisible. */
    data class Bilan(val envoyes: Int, val restants: Int, val message: String, val reussi: Boolean)

    /** Texte JSON correctement échappé (guillemets, barres obliques inverses, retours à la ligne, caractères de contrôle). */
    fun echapper(s: String): String = buildString {
        for (ch in s) when {
            ch == '"' -> append("\\\""); ch == '\\' -> append("\\\\"); ch == '\n' -> append("\\n"); ch == '\r' -> append("\\r"); ch == '\t' -> append("\\t")
            ch < ' ' -> append("\\u%04x".format(ch.code)); else -> append(ch)
        }
    }

    /** Corps JSON du contrat v1 pour ces paiements. */
    fun corpsJson(liste: List<Paiements.Paiement>): String = "{\"paiements\":[" + liste.joinToString(",") { p ->
        "{\"adresse\":\"${echapper(p.adresse)}\",\"recu_le_ms\":${p.recuLeMs},\"genre\":\"${p.genre}\",\"montant\":${p.montant},\"tiers\":\"${echapper(p.tiers)}\"," +
            "\"solde\":${p.soldeApres ?: "null"},\"reference\":${p.reference?.let { "\"${echapper(it)}\"" } ?: "null"},\"corps\":\"${echapper(p.corps)}\"}"
    } + "]}"

    /** Adresse du serveur nettoyée (sans barre finale) ; vide = adresse par défaut. */
    fun adresse(brut: String) = brut.trim().trimEnd('/').ifBlank { URL_PAR_DEFAUT }

    /** Les réglages sont-ils complets ? (adresse facultative) */
    fun configure(source: String, cle: String) = source.trim().length >= 4 && cle.trim().length >= 8

    /** Message lisible d'un code HTTP renvoyé par SAWALI. */
    fun messageHttp(code: Int) = when (code) {
        200 -> "envoi réussi"
        401 -> "identifiant ou clé refusés par SAWALI : vérifiez-les (la clé n'est montrée qu'une fois ; sinon retirez le téléphone dans SAWALI et ajoutez-le de nouveau)"
        422 -> "envoi mal formé"
        in 500..599 -> "serveur SAWALI momentanément indisponible"
        else -> "réponse inattendue ($code)"
    }

    /** Envoie TOUS les paiements en attente, par lots de 300 ; s'arrête au premier échec (le reste partira au prochain essai). Bloquant : à appeler hors de l'écran. */
    @Synchronized fun envoyer(c: Context): Bilan {
        val source = Reglages.sawaliSource(c); val cle = Reglages.sawaliCle(c)
        val base = Historique.de(c)
        if (!configure(source, cle)) return Bilan(0, base.nombreNonEnvoyes(), "Envoi non configuré : saisissez l'identifiant et la clé donnés par SAWALI.", false)
        var total = 0
        while (true) {
            val lot = base.nonEnvoyes(LOT)
            if (lot.isEmpty()) return Bilan(total, 0, if (total == 0) "Rien à envoyer : tout est déjà dans SAWALI." else "$total paiement(s) envoyé(s) à SAWALI.", true)
            val code = try {
                val requete = Request.Builder().url(adresse(Reglages.sawaliUrl(c)) + "/api/transactions-sms/v1/envoi").header("X-Source", source).header("X-Cle", cle)
                    .post(corpsJson(lot).toRequestBody(JSON)).build()
                http.newCall(requete).execute().use { it.code }
            } catch (e: Exception) { return Bilan(total, base.nombreNonEnvoyes(), "réseau indisponible : " + (e.message ?: e.javaClass.simpleName), false) }
            if (code != 200) return Bilan(total, base.nombreNonEnvoyes(), messageHttp(code), false)
            base.marquerEnvoyes(lot.map { it.id }); total += lot.size
        }
    }

    /** Envoi en arrière-plan (nouveau paiement reçu, lecture de SMS passés terminée) : seulement si c'est configuré et activé ; ne bloque jamais l'appelant. */
    fun envoyerEnArrierePlan(c: Context) {
        val app = c.applicationContext
        if (!Reglages.sawaliAuto(app) || !configure(Reglages.sawaliSource(app), Reglages.sawaliCle(app))) return
        Thread { try { envoyer(app) } catch (e: Exception) { /* réessayé au prochain paiement ou au bouton « Envoyer maintenant » */ } }.start()
    }
}
