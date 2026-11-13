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

    /** Intervalles entre mesures consécutives, par carte SIM (les mesures peuvent arriver dans le désordre). Deux mesures au même instant sont ignorées. */
    fun intervalles(mesures: List<Mesure>): List<Intervalle> =
        mesures.groupBy { it.emplacement }.flatMap { (slot, liste) ->
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
