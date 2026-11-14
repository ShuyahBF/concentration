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
                    terminer(ReponseUssd(null, (if (failureCode == TelephonyManager.USSD_ERROR_SERVICE_UNAVAIL) "Service USSD indisponible sur cette carte." else "L'opérateur a refusé ou n'a pas compris le code.") +
                        " (code d'échec $failureCode : certains menus ne s'obtiennent que par le composeur du téléphone)"))
            }, principal)
        } catch (e: SecurityException) { terminer(ReponseUssd(null, "Autorisation refusée par le téléphone."))
        } catch (e: Exception) { terminer(ReponseUssd(null, "Envoi impossible : " + (e.message ?: e.javaClass.simpleName))) }
    }

    /**
     * Vérifie le solde d'une carte : envoie son code, mémorise la réponse, lit le volume restant, met à jour le volume de départ si une recharge est détectée,
     * puis envoie l'alerte (une seule fois) quand le seuil est atteint. `surFin` reçoit la réponse brute.
     */
    fun verifier(c: Context, carte: CarteSim, surFin: (ReponseUssd) -> Unit = {}) {
        Reglages.derniereRequete(c, carte.emplacement, System.currentTimeMillis())          // un SMS de résultat reçu dans les 3 minutes sera rattaché à cette carte
        lancerUssd(c, carte.idAbonnement, Reglages.codeUssd(c, carte.emplacement)) { r ->
            if (r.texte != null) traiterReponse(c, carte, r.texte)
            surFin(r)
        }
    }

    /**
     * Certains opérateurs (ex. Telecel sans forfait) répondent par un SMS dont la notification s'affiche. Appelé par l'écouteur de notifications avec le titre
     * et le texte d'une notification de l'application de SMS : si un code vient d'être envoyé à une carte (moins de 3 minutes) et que le texte ressemble à un
     * solde Internet, il est traité comme la réponse de cette carte (préfixe « SMS : »). Carte reconnue par le nom de l'opérateur dans le titre ou le texte ;
     * à défaut, la seule carte interrogée récemment. Retourne vrai si le SMS a été rattaché.
     */
    fun traiterSms(c: Context, titre: String, texte: String): Boolean {
        if (!lectureAutorisee(c) || texte.isBlank() || !Solde.ressembleAUnSoldeInternet(texte)) return false
        val maintenant = System.currentTimeMillis()
        val recentes = cartes(c).filter { maintenant - Reglages.derniereRequete(c, it.emplacement) in 0..180_000L }
        if (recentes.isEmpty()) return false
        val tout = "$titre $texte".lowercase()
        val cible = recentes.firstOrNull { tout.contains(Solde.jetonOperateur(it.operateur)) } ?: recentes.singleOrNull() ?: return false
        traiterReponse(c, cible, "SMS : $texte")
        return true
    }

    /** Notification d'essai du rappel d'expiration (avec la vraie date si une SIM en a une, sinon un exemple) ; faux si les notifications ne sont pas autorisées. */
    fun testerRappelExpiration(c: Context): Boolean {
        if (!notificationsAutorisees(c)) return false
        val slot = (0 until EMPLACEMENTS_MAX).firstOrNull { Reglages.expirationMs(c, it) > System.currentTimeMillis() }
        val expiration = if (slot != null) Reglages.expirationMs(c, slot) else System.currentTimeMillis() + 3 * 86_400_000L
        val operateur = if (slot != null) Reglages.lectureOperateur(c, slot).ifBlank { "SIM ${slot + 1}" } else "Exemple"
        val quand = Solde.libelleExpiration(expiration, 0L).substringAfter("Expire le ").substringBefore(" (")
        notifier(c, "TEST · Forfait Internet : expiration dans 3 jours", "$operateur : le forfait expire dans 3 jours, le $quand. Les vrais rappels arrivent à 7 jours, 3 jours et le jour même.", 2190)
        return true
    }

    private fun traiterReponse(c: Context, carte: CarteSim, texte: String) {
        // Sans volume (Go, Mo) dans la réponse, l'opérateur répond par une simple phrase : c'est l'absence de forfait Internet (restant = null)
        // Date d'expiration du forfait (souvent entre parenthèses) : mémorisée par carte, et les rappels J-7, J-3 et du jour sont reprogrammés
        Reglages.expirationMs(c, carte.emplacement, Solde.analyserExpiration(texte, System.currentTimeMillis()) ?: 0L)
        programmerExpirations(c)
        val restant = Solde.analyserVolume(texte)
        val avant = Reglages.restantMo(c, carte.emplacement)
        Reglages.derniereLecture(c, carte.emplacement, texte, System.currentTimeMillis(), restant, carte.operateur)
        if (restant == null) return
        // Historique des mesures : sert au graphique de consommation entre deux vérifications (3e onglet)
        try { Historique.de(c).ajouterMesure(carte.emplacement, carte.operateur, System.currentTimeMillis(), restant) } catch (e: Exception) { /* jamais bloquant */ }
        if (Reglages.departAuto(c) && Solde.rechargeDetectee(avant, restant)) { Reglages.departMo(c, carte.emplacement, restant); Reglages.alerteEnvoyee(c, carte.emplacement, false) }
        val depart = Reglages.departMo(c, carte.emplacement)
        if (depart > 0 && Solde.seuilAtteint(restant, depart, Reglages.seuilPourcent(c))) {
            if (Reglages.alerteReseau(c) && !Reglages.alerteEnvoyee(c, carte.emplacement)) {
                Reglages.alerteEnvoyee(c, carte.emplacement, true)
                val pc = Solde.pourcentRestant(restant, depart)
                notifier(c, "Internet mobile : presque épuisé", "${carte.operateur} : il reste ${Solde.formater(restant)} (${pc} % du volume de départ).")
            }
        } else Reglages.alerteEnvoyee(c, carte.emplacement, false)            // volume remonté (recharge) : la prochaine baisse sous le seuil alertera de nouveau
    }

    /** Notification d'alerte (canal « Réseau mobile »). Sans l'autorisation d'afficher des notifications, rien ne s'affiche. */
    internal fun notifier(c: Context, titre: String, texte: String, id: Int = 2001) {
        if (!notificationsAutorisees(c)) return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL, "Réseau mobile", NotificationManager.IMPORTANCE_HIGH))
        val ouvrir = PendingIntent.getActivity(c, 2, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(id, Notification.Builder(c, CANAL).setSmallIcon(android.R.drawable.stat_sys_warning).setContentTitle(titre).setContentText(texte)
            .setStyle(Notification.BigTextStyle().bigText(texte)).setContentIntent(ouvrir).setAutoCancel(true).build())
    }

    private const val EMPLACEMENTS_MAX = 4

    /** Programme (ou annule) les trois rappels d'expiration du forfait de chaque carte SIM : 7 jours avant, 3 jours avant, le jour même avant l'heure. */
    fun programmerExpirations(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val maintenant = System.currentTimeMillis()
        for (slot in 0 until EMPLACEMENTS_MAX) {
            val expiration = Reglages.expirationMs(c, slot)
            val voulus = if (Reglages.rappelExpiration(c) && expiration > maintenant) Solde.momentsRappel(expiration) else emptyList()
            listOf(7, 3, 0).forEachIndexed { idx, type ->
                val intent = Intent(c, RecepteurExpiration::class.java).putExtra("slot", slot).putExtra("type", type).putExtra("expiration", expiration)
                val pi = PendingIntent.getBroadcast(c, 100 + slot * 10 + idx, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                am.cancel(pi)
                val quand = voulus.firstOrNull { it.first == type }?.second ?: return@forEachIndexed
                if (quand <= maintenant) return@forEachIndexed          // moment déjà passé : plus de rappel pour ce palier
                try {
                    if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quand, pi)
                    else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quand, pi)
                } catch (e: SecurityException) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quand, pi) }
            }
        }
    }

    /** Vérification automatique : programme (ou annule) un réveil à l'intervalle choisi (en heures, minimum 1). À rappeler après tout changement de réglage. */
    fun programmer(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(c, 3, Intent(c, RecepteurReseau::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        am.cancel(pi)
        if (!Reglages.verifAuto(c)) return
        val intervalle = Reglages.intervalleHeures(c) * AlarmManager.INTERVAL_HOUR
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

/** Rappel d'expiration du forfait Internet : notification 7 jours avant, 3 jours avant et le jour même avant l'heure. */
class RecepteurExpiration : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val slot = i.getIntExtra("slot", -1); val type = i.getIntExtra("type", -1); val expiration = i.getLongExtra("expiration", 0L)
        // Rappel périmé (forfait rechargé : la date a changé depuis la programmation) : ignoré
        if (slot < 0 || expiration == 0L || Reglages.expirationMs(c, slot) != expiration || !Reglages.rappelExpiration(c)) return
        val operateur = Reglages.lectureOperateur(c, slot).ifBlank { "SIM ${slot + 1}" }
        val quand = Solde.libelleExpiration(expiration, 0L).substringAfter("Expire le ").substringBefore(" (")
        val delai = when (type) { 7 -> "dans 7 jours"; 3 -> "dans 3 jours"; else -> "AUJOURD'HUI" }
        Reseau.notifier(c, "Forfait Internet : expiration $delai", "$operateur (SIM ${slot + 1}) : le forfait expire $delai, le $quand. Pensez à le renouveler.", 2100 + slot * 10 + (if (type == 7) 0 else if (type == 3) 1 else 2))
    }
}
