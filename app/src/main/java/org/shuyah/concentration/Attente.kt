package org.shuyah.concentration

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * « J'ATTENDS UN PAIEMENT » : l'utilisateur appuie une fois ; si aucun paiement n'est arrivé dans la base DÉLAI_MIN minutes plus tard, une notification
 * « Paiement attendu non reçu » propose de vérifier auprès de l'opérateur. L'utilisateur n'a donc pas à penser à vérifier lui-même (sans SMS de réception,
 * rien ne l'avertit qu'il manque quelque chose). Aucun envoi USSD n'est fait automatiquement : la notification ouvre simplement l'application.
 */
object Attente {
    const val DELAI_MIN = 30

    /** Vrai si un paiement RECU est arrivé depuis ce moment (une marge de 5 minutes couvre l'heure du numéro de transaction, parfois légèrement antérieure). Fonction pure. */
    fun paiementRecu(base: List<Paiements.Paiement>, depuisMs: Long) = base.any { it.genre == "RECETTE" && it.recuLeMs >= depuisMs - 5 * 60_000L }

    private fun intention(c: Context) = PendingIntent.getBroadcast(c, 4, Intent(c, RecepteurAttente::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** Démarre l'attente : mémorise l'heure et programme le contrôle dans DÉLAI_MIN minutes. */
    fun demarrer(c: Context) {
        val maintenant = System.currentTimeMillis()
        Reglages.attenteDepuis(c, maintenant); Reglages.attenteEchue(c, false)
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, maintenant + DELAI_MIN * 60_000L, intention(c))
    }

    /** Annule l'attente (paiement arrivé, ou abandon). */
    fun annuler(c: Context) {
        (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(intention(c))
        Reglages.attenteDepuis(c, 0L); Reglages.attenteEchue(c, false)
    }
}

/** Contrôle à l'échéance : paiement arrivé → attente terminée en silence ; sinon notification et indicateur affiché dans l'onglet Paiements. */
class RecepteurAttente : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val depuis = Reglages.attenteDepuis(c)
        if (depuis == 0L) return
        if (Attente.paiementRecu(Historique.de(c).tousPaiements(), depuis)) { Attente.annuler(c); return }
        Reglages.attenteEchue(c, true)
        Reseau.notifier(c, "Paiement attendu non reçu", "Aucun paiement reçu depuis ${Attente.DELAI_MIN} minutes. Ouvrez Concentration → Paiements → « Vérifier auprès de l'opérateur ».", 2300)
    }
}
