package org.shuyah.concentration

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build

/**
 * Pilotage du pare-feu (niveau 3) : démarre le tunnel au début de la plage et l'arrête à la fin, à la pause ou quand on éteint le niveau 3.
 * Un réveil (AlarmManager) est programmé à chaque changement d'état (début ou fin de plage, fin de pause) ; l'état est aussi revérifié à l'ouverture
 * de l'application, au redémarrage du téléphone et régulièrement par le service d'accessibilité.
 */
object Pare {
    /** Vrai si le pare-feu doit être actif maintenant (niveau 3 allumé et plage en cours, hors pause). */
    fun voulu(c: Context) = Reglages.pareFeu(c) && Reglages.minutesRestantes(c) != null

    /** Vrai si l'utilisateur a accepté la fenêtre système « Demande de connexion VPN ». */
    fun autorise(c: Context) = try { VpnService.prepare(c) == null } catch (e: Exception) { false }

    /** Met le tunnel dans l'état voulu. `forcer` : le recrée même s'il est déjà ouvert (liste d'applications modifiée). */
    fun synchroniser(c: Context, forcer: Boolean = false) {
        Sons.controler(c)                                        // son si l'état a changé (début, fin, pause, reprise)
        try {
            if (voulu(c) && autorise(c)) { if (forcer || !ServiceVpn.actif) c.startService(Intent(c, ServiceVpn::class.java)) }
            else ServiceVpn.instance?.fermer()
        } catch (e: Exception) { /* démarrage refusé par le système : réessayé au prochain contrôle */ }
    }

    /** Programme le réveil du prochain changement d'état (début/fin de plage ou fin de pause). */
    fun programmer(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(c, 1, Intent(c, RecepteurPlanning::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        am.cancel(pi)
        val (jour, min) = Plage.jourEtMinutes()
        var quand: Long? = Plage.minutesAvantChangement(Reglages.plages(c), jour, min)?.let { (System.currentTimeMillis() / 60000L + it) * 60000L }
        if (Reglages.enPause(c)) quand = minOf(quand ?: Long.MAX_VALUE, Reglages.pauseJusqua(c))
        if (quand == null) return
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quand, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quand, pi)
        } catch (e: SecurityException) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quand, pi) }
    }

    /** À appeler après tout changement de réglage. */
    fun appliquer(c: Context, forcer: Boolean = false) { synchroniser(c, forcer); programmer(c) }
}

/** Reçoit le réveil programmé, le redémarrage du téléphone et la mise à jour de l'application : remet le pare-feu dans le bon état. */
class RecepteurPlanning : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        // goAsync : le récepteur reste en vie le temps du son (sinon le système peut l'arrêter avant la fin de la mélodie)
        val attente = goAsync()
        Thread { try { Pare.appliquer(c); Thread.sleep(1500) } finally { attente.finish() } }.start()
    }
}
