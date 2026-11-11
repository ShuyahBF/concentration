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

    /** Tous les textes de la notification où peut figurer le nom du contact : titre, texte, conversation, expéditeurs des messages. */
    @Suppress("DEPRECATION")
    private fun textesDe(sbn: StatusBarNotification): List<String> {
        val x = sbn.notification.extras ?: return emptyList()
        val l = mutableListOf<String>()
        for (k in listOf(android.app.Notification.EXTRA_TITLE, android.app.Notification.EXTRA_TEXT, android.app.Notification.EXTRA_BIG_TEXT,
            android.app.Notification.EXTRA_CONVERSATION_TITLE, android.app.Notification.EXTRA_SUB_TEXT)) x.getCharSequence(k)?.let { l += it.toString() }
        try { x.getParcelableArray(android.app.Notification.EXTRA_MESSAGES)?.forEach { m -> (m as? android.os.Bundle)?.getCharSequence("sender")?.let { l += it.toString() } } } catch (e: Exception) {}
        return l
    }

    /** Retarde (ou, à défaut, supprime) la notification si la concentration est active et que l'application est bloquée. */
    private fun traiter(sbn: StatusBarNotification, compter: Boolean) {
        if (!Reglages.notifications(this)) return
        val restant = Reglages.minutesRestantes(this) ?: return            // hors plage : rien à faire
        if (!Applications.estBloquee(this, sbn.packageName)) return
        // Contact autorisé : la notification passe normalement (son, bannière), elle n'est ni retardée ni comptée
        if (Contacts.correspond(textesDe(sbn), Reglages.contacts(this))) return
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
