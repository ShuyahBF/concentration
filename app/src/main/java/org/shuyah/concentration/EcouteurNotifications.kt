package org.shuyah.concentration

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * NIVEAU 1 : pendant la plage, les notifications (messages, appels) des applications bloquées sont RETARDÉES jusqu'à la fin de la plage :
 * ni son, ni vibration, ni bannière ; elles réapparaissent à l'heure de fin. Les messages arrivent toujours chez WhatsApp, ils ne sont
 * simplement pas annoncés. Nécessite l'autorisation « Accès aux notifications » (réglage système, à activer une fois).
 */
class EcouteurNotifications : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) = traiter(sbn, true)

    /** Au démarrage du service : traite aussi les notifications déjà affichées. */
    override fun onListenerConnected() {
        try { activeNotifications?.forEach { traiter(it, false) } } catch (e: Exception) { /* service pas prêt : sans conséquence */ }
    }

    /** Retarde (ou, à défaut, supprime) la notification si la concentration est active et que l'application est bloquée. */
    private fun traiter(sbn: StatusBarNotification, compter: Boolean) {
        if (!Reglages.notifications(this)) return
        val restant = Reglages.minutesRestantes(this) ?: return            // hors plage : rien à faire
        if (!Applications.estBloquee(this, sbn.packageName)) return
        // Comptage (nouvelles notifications seulement, ni les résumés de groupe ni les notifications permanentes)
        if (compter && sbn.notification.flags and (android.app.Notification.FLAG_GROUP_SUMMARY or android.app.Notification.FLAG_ONGOING_EVENT) == 0) {
            Reglages.compter(this, sbn.packageName, (System.currentTimeMillis() / 60000L + restant) * 60000L)
        }
        try {
            snoozeNotification(sbn.key, restant * 60_000L + 30_000L)       // réapparaît juste après la fin de la plage
        } catch (e: Exception) {
            try { cancelNotification(sbn.key) } catch (e2: Exception) { /* notification permanente : impossible à retirer */ }
        }
    }
}
