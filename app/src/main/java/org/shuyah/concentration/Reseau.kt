package org.shuyah.concentration

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

/**
 * RÉSEAU MOBILE (téléphone à deux cartes SIM) : quelle carte porte l'Internet mobile, lecture du solde Internet par un code USSD envoyé en arrière-plan
 * sur la carte voulue, et alerte quand il ne reste plus que X % du volume de départ.
 *
 * Pour un développeur WinDev :
 *  - la carte de données active est celle que le téléphone désigne pour Internet (`getDefaultDataSubscriptionId`) ;
 *  - un code USSD (ex. *123#) est envoyé par `sendUssdRequest` : aucune fenêtre ne s'ouvre, la réponse de l'opérateur revient sous forme de texte ;
 *    il peut partir sur N'IMPORTE LAQUELLE des deux cartes (l'USSD passe par le réseau téléphonique, pas par Internet) ;
 *  - UN SEUL échange : le texte de la première réponse. Un menu à plusieurs choix ne peut pas être poursuivi ; on saisit alors le code complet (ex. *123*1#) ;
 *  - rien n'est envoyé ailleurs : la réponse reste sur le téléphone (aucun code secret n'est conservé, seul le texte de réponse du dernier solde l'est).
 */
object Reseau {
    /** Une carte SIM active : identifiant du système, emplacement (0 = SIM 1), opérateur, et vrai si c'est elle qui porte l'Internet mobile. */
    data class CarteSim(val idAbonnement: Int, val emplacement: Int, val operateur: String, val donnees: Boolean)

    /** Réponse à un code USSD : le texte de l'opérateur, ou le motif de l'échec. */
    data class ReponseUssd(val texte: String?, val erreur: String?)

    private const val CANAL = "reseau_mobile"

    fun permis(c: Context, permission: String) = c.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    fun lectureAutorisee(c: Context) = permis(c, Manifest.permission.READ_PHONE_STATE)
    fun ussdAutorise(c: Context) = permis(c, Manifest.permission.CALL_PHONE)
    fun notificationsAutorisees(c: Context) = Build.VERSION.SDK_INT < 33 || permis(c, Manifest.permission.POST_NOTIFICATIONS)

    /** Les cartes SIM actives (vide sans l'autorisation « Téléphone » ou sans carte). */
    fun cartes(c: Context): List<CarteSim> {
        if (!lectureAutorisee(c)) return emptyList()
        return try {
            val gestionnaire = c.getSystemService(SubscriptionManager::class.java)
            val donnees = SubscriptionManager.getDefaultDataSubscriptionId()
            gestionnaire.activeSubscriptionInfoList.orEmpty().map {
                CarteSim(it.subscriptionId, it.simSlotIndex, it.carrierName?.toString().orEmpty().ifBlank { "SIM ${it.simSlotIndex + 1}" }, it.subscriptionId == donnees)
            }.sortedBy { it.emplacement }
        } catch (e: SecurityException) { emptyList() }
    }

    /** Envoie le code USSD sur la carte donnée et rend la réponse (ou l'erreur) au thread principal ; délai maximal 45 secondes. */
    fun lancerUssd(c: Context, idAbonnement: Int, code: String, surFin: (ReponseUssd) -> Unit) {
        val principal = Handler(Looper.getMainLooper())
        var fini = false
        fun terminer(r: ReponseUssd) { if (!fini) { fini = true; principal.removeCallbacksAndMessages(null); surFin(r) } }
        if (code.isBlank()) { terminer(ReponseUssd(null, "Aucun code saisi pour cette carte.")); return }
        if (!ussdAutorise(c)) { terminer(ReponseUssd(null, "Autorisation « Appels » non accordée.")); return }
        principal.postDelayed({ terminer(ReponseUssd(null, "Pas de réponse de l'opérateur (délai dépassé).")) }, 45_000)
        try {
            val tm = c.getSystemService(TelephonyManager::class.java).createForSubscriptionId(idAbonnement)
            tm.sendUssdRequest(code.trim(), object : TelephonyManager.UssdResponseCallback() {
                override fun onReceiveUssdResponse(telephonyManager: TelephonyManager, request: String, response: CharSequence) =
                    terminer(ReponseUssd(response.toString(), null))
                override fun onReceiveUssdResponseFailed(telephonyManager: TelephonyManager, request: String, failureCode: Int) =
                    terminer(ReponseUssd(null, if (failureCode == TelephonyManager.USSD_ERROR_SERVICE_UNAVAIL) "Service USSD indisponible sur cette carte." else "L'opérateur a refusé ou n'a pas compris le code."))
            }, principal)
        } catch (e: SecurityException) { terminer(ReponseUssd(null, "Autorisation refusée par le téléphone."))
        } catch (e: Exception) { terminer(ReponseUssd(null, "Envoi impossible : " + (e.message ?: e.javaClass.simpleName))) }
    }

    /**
     * Vérifie le solde d'une carte : envoie son code, mémorise la réponse, lit le volume restant, met à jour le volume de départ si une recharge est détectée,
     * puis envoie l'alerte (une seule fois) quand le seuil est atteint. `surFin` reçoit la réponse brute.
     */
    fun verifier(c: Context, carte: CarteSim, surFin: (ReponseUssd) -> Unit = {}) {
        lancerUssd(c, carte.idAbonnement, Reglages.codeUssd(c, carte.emplacement)) { r ->
            if (r.texte != null) traiterReponse(c, carte, r.texte)
            surFin(r)
        }
    }

    private fun traiterReponse(c: Context, carte: CarteSim, texte: String) {
        val restant = Solde.analyserVolume(texte)
        val avant = Reglages.restantMo(c)
        Reglages.derniereLecture(c, texte, System.currentTimeMillis(), restant, carte.operateur)
        if (restant == null) return
        if (Reglages.departAuto(c) && Solde.rechargeDetectee(avant, restant)) { Reglages.departMo(c, restant); Reglages.alerteEnvoyee(c, false) }
        val depart = Reglages.departMo(c)
        if (depart > 0 && Solde.seuilAtteint(restant, depart, Reglages.seuilPourcent(c))) {
            if (Reglages.alerteReseau(c) && !Reglages.alerteEnvoyee(c)) {
                Reglages.alerteEnvoyee(c, true)
                val pc = Solde.pourcentRestant(restant, depart)
                notifier(c, "Internet mobile : presque épuisé", "${carte.operateur} : il reste ${Solde.formater(restant)} (${pc} % du volume de départ).")
            }
        } else Reglages.alerteEnvoyee(c, false)            // volume remonté (recharge) : la prochaine baisse sous le seuil alertera de nouveau
    }

    /** Notification d'alerte (canal « Réseau mobile »). Sans l'autorisation d'afficher des notifications, rien ne s'affiche. */
    private fun notifier(c: Context, titre: String, texte: String) {
        if (!notificationsAutorisees(c)) return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL, "Réseau mobile", NotificationManager.IMPORTANCE_HIGH))
        val ouvrir = PendingIntent.getActivity(c, 2, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(2001, Notification.Builder(c, CANAL).setSmallIcon(android.R.drawable.stat_sys_warning).setContentTitle(titre).setContentText(texte)
            .setStyle(Notification.BigTextStyle().bigText(texte)).setContentIntent(ouvrir).setAutoCancel(true).build())
    }

    /** Vérification automatique : programme (ou annule) un réveil toutes les 6 heures. À rappeler après tout changement de réglage. */
    fun programmer(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(c, 3, Intent(c, RecepteurReseau::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        am.cancel(pi)
        if (!Reglages.verifAuto(c)) return
        val intervalle = 6 * AlarmManager.INTERVAL_HOUR
        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + intervalle, intervalle, pi)
    }
}

/** Réveil de la vérification automatique : vérifie le solde de la carte qui porte l'Internet mobile à ce moment-là. */
class RecepteurReseau : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val attente = goAsync()
        val carte = Reseau.cartes(c).firstOrNull { it.donnees }
        if (carte == null || !Reseau.ussdAutorise(c)) { attente.finish(); return }
        // Le système limite la durée d'un récepteur : on rend la main après 9 s au plus (la réponse est traitée si elle arrive avant)
        Handler(Looper.getMainLooper()).postDelayed({ try { attente.finish() } catch (e: Exception) { } }, 9_000)
        Reseau.verifier(c, carte) { try { attente.finish() } catch (e: Exception) { } }
    }
}
