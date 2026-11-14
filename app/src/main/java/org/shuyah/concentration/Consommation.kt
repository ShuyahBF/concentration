package org.shuyah.concentration

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * ESTIMATION DE LA CONSOMMATION D'INTERNET entre deux vérifications du solde (logique pure, sans Android : testée par ConsommationTest).
 *
 * Principe : à chaque vérification, le volume restant est mémorisé (`Mesure`). Entre deux mesures consécutives d'une même carte SIM,
 * la consommation = volume précédent − volume actuel. Si le volume REMONTE nettement (recharge), l'intervalle n'est pas une consommation :
 * il est signalé « recharge » et ignoré dans les totaux. La consommation d'un intervalle est ensuite répartie sur les jours qu'il couvre,
 * proportionnellement au temps, pour tracer un graphique « Mo par jour ».
 */
object Consommation {
    /** Un volume restant relevé à un instant, pour la carte SIM de cet emplacement. */
    data class Mesure(val emplacement: Int, val ms: Long, val restantMo: Double)

    /** Période entre deux mesures consécutives : `consoMo` estimée (0 si recharge), `recharge` vrai si le volume a nettement augmenté. */
    data class Intervalle(val emplacement: Int, val debutMs: Long, val finMs: Long, val consoMo: Double, val recharge: Boolean) {
        val heures: Double get() = (finMs - debutMs) / 3_600_000.0
    }

    /**
     * MESURES ABERRANTES : quand la connexion de la carte SIM est coupée (passage en Wi-Fi), l'opérateur répond parfois « 0 Mo » : le volume paraît s'effondrer, puis
     * « remonter » à la vérification suivante (fausse consommation de plusieurs Go, puis fausse recharge). Une telle mesure est un CREUX ISOLÉ et n'est pas une consommation :
     *  - le volume perd plus de la MOITIÉ du dernier bon relevé (au moins 1 Go, et plus de 15 Go à l'heure : 6,7 Go en 3 minutes est impossible à vitesse réelle) ;
     *    le creux n'est pas forcément à zéro : dans l'historique réel il restait 1,0 à 1,5 Go sur 7,8 Go ;
     *  - ET il remonte ensuite à au moins 80 % du dernier bon relevé.
     * Les mesures du creux (une ou plusieurs) sont ignorées. Un volume réellement épuisé (qui ne remonte pas, ou qui baisse peu à peu) est conservé.
     */
    fun mesuresFiables(mesures: List<Mesure>): List<Mesure> =
        mesures.groupBy { it.emplacement }.flatMap { (_, liste) -> nettoyer(liste.sortedBy { it.ms }) }.sortedBy { it.ms }

    /** Nombre de mesures ignorées comme aberrantes (pour le signaler à l'écran). */
    fun nombreIgnorees(mesures: List<Mesure>) = mesures.size - mesuresFiables(mesures).size

    private fun nettoyer(l: List<Mesure>): List<Mesure> {
        val sortie = mutableListOf<Mesure>()
        var i = 0
        while (i < l.size) {
            val bon = sortie.lastOrNull(); val m = l[i]
            if (bon != null && chuteImpossible(bon.restantMo, m.restantMo, m.ms - bon.ms)) {
                var j = i
                while (j < l.size && l[j].restantMo < bon.restantMo * SEUIL_CREUX) j++              // fin du creux : première mesure qui n'est plus « effondrée »
                val suite = l.getOrNull(j)
                if (suite != null && suite.restantMo >= bon.restantMo * 0.8) { i = j; continue }  // le volume est revenu : creux isolé, ignoré
            }
            sortie += m; i++
        }
        return sortie
    }

    private const val SEUIL_CREUX = 0.5                 // creux = moins de la moitié du dernier bon relevé
    private const val VITESSE_MAX_MO_PAR_HEURE = 15_000.0   // au-delà de 15 Go à l'heure, une baisse n'est pas une consommation

    /**
     * Vrai si un nouveau relevé est une CHUTE IMPOSSIBLE par rapport au précédent : un volume d'au moins 1 Go perd plus de la moitié (au moins 500 Mo) en moins d'une heure,
     * à une vitesse supérieure à 15 Go par heure (durée comptée pour une minute au moins). Une vraie consommation, même très forte, n'atteint pas ce rythme.
     * Sert aussi à ne pas enregistrer ni signaler un tel relevé au moment de la lecture (voir Reseau.traiterReponse).
     */
    fun chuteImpossible(avantMo: Double?, restantMo: Double, dureeMs: Long): Boolean {
        if (avantMo == null || avantMo < 1000.0 || dureeMs !in 0..3_600_000L) return false
        val perte = avantMo - restantMo
        if (perte < 500.0 || restantMo >= avantMo * SEUIL_CREUX) return false
        val heures = maxOf(dureeMs, 60_000L) / 3_600_000.0
        return perte / heures > VITESSE_MAX_MO_PAR_HEURE
    }

    /** Intervalles entre mesures consécutives, par carte SIM (les mesures peuvent arriver dans le désordre). Deux mesures au même instant sont ignorées. */
    fun intervalles(mesuresBrutes: List<Mesure>): List<Intervalle> =
        mesuresFiables(mesuresBrutes).groupBy { it.emplacement }.flatMap { (slot, liste) ->
            liste.sortedBy { it.ms }.zipWithNext().filter { (a, b) -> b.ms > a.ms }.map { (a, b) ->
                val recharge = Solde.rechargeDetectee(a.restantMo, b.restantMo)
                // Baisse = consommation ; petite hausse non assimilable à une recharge (arrondi de l'opérateur) = 0
                Intervalle(slot, a.ms, b.ms, if (recharge) 0.0 else maxOf(0.0, a.restantMo - b.restantMo), recharge)
            }
        }.sortedBy { it.debutMs }

    /** Clé de jour « AAAA-MM-JJ » dans le fuseau donné. */
    private fun cle(ms: Long, fuseau: TimeZone) = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = fuseau }.format(ms)

    /**
     * Consommation estimée par jour (Mo) : chaque intervalle (hors recharge) est réparti sur les jours qu'il traverse, au prorata de la durée
     * passée dans chaque jour. Résultat trié par jour ; seuls les jours touchés figurent.
     */
    fun parJour(intervalles: List<Intervalle>, fuseau: TimeZone = TimeZone.getDefault()): List<Pair<String, Double>> {
        val total = sortedMapOf<String, Double>()
        for (i in intervalles) {
            if (i.recharge || i.consoMo <= 0.0 || i.finMs <= i.debutMs) continue
            val duree = (i.finMs - i.debutMs).toDouble()
            var courant = i.debutMs
            while (courant < i.finMs) {
                // Début du jour suivant (minuit local)
                val minuit = Calendar.getInstance(fuseau).apply { timeInMillis = courant; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
                val finTranche = minOf(minuit, i.finMs)
                total.merge(cle(courant, fuseau), i.consoMo * (finTranche - courant) / duree, Double::plus)
                courant = finTranche
            }
        }
        return total.map { it.key to it.value }
    }

    /** Consommation totale estimée (Mo) sur les intervalles hors recharge. */
    fun total(intervalles: List<Intervalle>) = intervalles.filter { !it.recharge }.sumOf { it.consoMo }

    /** Consommation moyenne par jour (Mo) : total ÷ durée des intervalles hors recharge ; null s'il n'y a pas encore de durée mesurée. */
    fun moyenneParJour(intervalles: List<Intervalle>): Double? {
        val heures = intervalles.filter { !it.recharge }.sumOf { it.heures }
        return if (heures <= 0.0) null else total(intervalles) / heures * 24.0
    }

    /** Jours restants au rythme moyen (volume restant ÷ moyenne par jour) ; null si la moyenne est inconnue ou nulle. */
    fun joursRestants(restantMo: Double, moyenneParJourMo: Double?): Double? =
        if (moyenneParJourMo == null || moyenneParJourMo <= 0.0) null else restantMo / moyenneParJourMo
}
