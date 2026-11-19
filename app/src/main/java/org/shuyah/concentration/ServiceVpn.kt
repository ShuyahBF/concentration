package org.shuyah.concentration

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream

/**
 * NIVEAU 3 : PARE-FEU. Pendant la plage, les applications bloquées (WhatsApp…) sont dirigées vers un « tunnel » local qui ne mène nulle part :
 * leurs paquets sont lus puis jetés, elles n'ont donc AUCUN accès à Internet (pas de message, pas d'appel). Les autres applications ne passent pas
 * par ce tunnel et fonctionnent normalement. À la fin de la plage, le tunnel est fermé : WhatsApp se reconnecte et reçoit tout ce qui attendait.
 * Rien ne sort du téléphone : aucune donnée n'est transmise, ce n'est pas un vrai VPN.
 */
class ServiceVpn : VpnService() {
    private var tunnel: ParcelFileDescriptor? = null
    private var lecteur: Thread? = null

    /** (Re)crée le tunnel à chaque démarrage : la liste des applications peut avoir changé. */
    override fun onCreate() { super.onCreate(); Plantage.installer(this) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instance = this
        // Aucune erreur du pare-feu ne doit faire planter l'application : on la garde (écran « Niveau 3 ») et on s'arrête proprement
        try { ouvrir() } catch (e: Throwable) {
            erreur = "${e.javaClass.simpleName} : ${e.message}"; actif = false; fermerTunnel(); stopSelf()
            JournalPare.noter(this, "ERREUR à l'ouverture du tunnel : $erreur")
        }
        return START_STICKY
    }

    private fun ouvrir() {
        fermerTunnel()
        // Applications concernées : celles que la règle centrale déclare bloquées. ATTENTION : un tunnel sans aucune application autorisée
        // concernerait TOUTES les applications ; on n'ouvre donc rien si la liste est vide.
        val libres = Applications.toujoursLibres(this)
        val apps = Applications.lancables(this).map { it.paquet }.filter { Applications.estBloquee(this, it, libres) }
        if (apps.isEmpty()) { actif = false; JournalPare.noter(this, "Aucune application à couper (liste vide) : tunnel non ouvert, donc pas d'icône VPN"); stopSelf(); return }
        val b = Builder().setSession("Concentration").setBlocking(true)
            .addAddress("10.255.255.1", 32).addRoute("0.0.0.0", 0)      // IPv4 : tout le trafic de ces applications vers le tunnel
            .addAddress("fd00::1", 128).addRoute("::", 0)                // IPv6 : idem
        apps.forEach { try { b.addAllowedApplication(it) } catch (e: Exception) { /* application désinstallée entre-temps */ } }
        val t = try { b.establish() } catch (e: Exception) { JournalPare.noter(this, "Le système a refusé d'ouvrir le tunnel : ${e.javaClass.simpleName} ${e.message}"); null }
        if (t == null) {
            // Autorisation VPN retirée, ou un autre VPN a pris la place (Android n'en admet qu'un à la fois)
            actif = false; erreur = "Tunnel non ouvert : autorisation VPN absente ou autre VPN actif"
            JournalPare.noter(this, "Tunnel non ouvert : autorisation VPN absente ou autre VPN actif (réaccordez-la : Niveau 3 → Autorisation VPN)")
            stopSelf(); return
        }
        tunnel = t
        actif = true; erreur = null
        JournalPare.noter(this, "Tunnel ouvert pour ${apps.size} application(s) : l'icône VPN (clé) doit s'afficher")
        // Lecture et abandon des paquets (sinon la file du tunnel se remplit) ; l'arrêt du tunnel interrompt la lecture
        // TOUT le fil est protégé (avant, la création du flux était hors du « try » : une erreur là, ou un tunnel refermé par un redémarrage rapide du service,
        // faisait planter toute l'application, car une exception non rattrapée dans UN fil tue le processus entier)
        lecteur = Thread {
            try {
                val entree = FileInputStream(t.fileDescriptor); val tampon = ByteArray(32767)
                while (entree.read(tampon) >= 0) { /* paquet jeté */ }
            } catch (e: Throwable) { /* tunnel fermé */ }
            // Fin de la lecture alors que le service se croit encore actif : le système a fermé le tunnel (autre VPN, économiseur de batterie…)
            if (tunnel === t && actif) JournalPare.noter(this@ServiceVpn, "Le tunnel a été fermé par le système alors que le pare-feu était actif")
        }.also { it.isDaemon = true; it.start() }
    }

    private fun fermerTunnel() {
        try { tunnel?.close() } catch (e: Exception) {}
        tunnel = null; lecteur = null
    }

    /** Ferme le tunnel et arrête le service (fin de plage, pause, interrupteur éteint). */
    fun fermer() { JournalPare.noter(this, "Fermeture demandée (fin de plage, pause ou interrupteur éteint)", sansRepeter = true); fermerTunnel(); actif = false; stopSelf() }

    /** L'utilisateur coupe le VPN dans les réglages ou un autre VPN le remplace. */
    override fun onRevoke() { JournalPare.noter(this, "VPN repris par le système ou par une autre application (onRevoke) : plus d'icône VPN"); fermer(); super.onRevoke() }

    override fun onDestroy() { JournalPare.noter(this, "Service du pare-feu arrêté (onDestroy)", sansRepeter = true); fermerTunnel(); actif = false; if (instance === this) instance = null; super.onDestroy() }

    companion object {
        /** Vrai quand le tunnel est ouvert (les applications bloquées n'ont plus Internet). */
        @Volatile var actif = false
        @Volatile var instance: ServiceVpn? = null
        /** Dernière erreur du pare-feu (affichée dans l'écran « Niveau 3 ») ; null si tout va bien. */
        @Volatile var erreur: String? = null
    }
}
