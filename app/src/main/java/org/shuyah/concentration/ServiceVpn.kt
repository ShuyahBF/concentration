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
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instance = this
        ouvrir()
        return START_STICKY
    }

    private fun ouvrir() {
        fermerTunnel()
        // Applications concernées : celles que la règle centrale déclare bloquées. ATTENTION : un tunnel sans aucune application autorisée
        // concernerait TOUTES les applications ; on n'ouvre donc rien si la liste est vide.
        val libres = Applications.toujoursLibres(this)
        val apps = Applications.lancables(this).map { it.paquet }.filter { Applications.estBloquee(this, it, libres) }
        if (apps.isEmpty()) { actif = false; stopSelf(); return }
        val b = Builder().setSession("Concentration").setBlocking(true)
            .addAddress("10.255.255.1", 32).addRoute("0.0.0.0", 0)      // IPv4 : tout le trafic de ces applications vers le tunnel
            .addAddress("fd00::1", 128).addRoute("::", 0)                // IPv6 : idem
        apps.forEach { try { b.addAllowedApplication(it) } catch (e: Exception) { /* application désinstallée entre-temps */ } }
        val t = try { b.establish() } catch (e: Exception) { null }
        if (t == null) { actif = false; stopSelf(); return }              // autorisation VPN retirée
        tunnel = t
        actif = true
        // Lecture et abandon des paquets (sinon la file du tunnel se remplit) ; l'arrêt du tunnel interrompt la lecture
        lecteur = Thread {
            val entree = FileInputStream(t.fileDescriptor); val tampon = ByteArray(32767)
            try { while (entree.read(tampon) >= 0) { /* paquet jeté */ } } catch (e: Exception) { /* tunnel fermé */ }
        }.also { it.isDaemon = true; it.start() }
    }

    private fun fermerTunnel() {
        try { tunnel?.close() } catch (e: Exception) {}
        tunnel = null; lecteur = null
    }

    /** Ferme le tunnel et arrête le service (fin de plage, pause, interrupteur éteint). */
    fun fermer() { fermerTunnel(); actif = false; stopSelf() }

    /** L'utilisateur coupe le VPN dans les réglages ou un autre VPN le remplace. */
    override fun onRevoke() { fermer(); super.onRevoke() }

    override fun onDestroy() { fermerTunnel(); actif = false; if (instance === this) instance = null; super.onDestroy() }

    companion object {
        /** Vrai quand le tunnel est ouvert (les applications bloquées n'ont plus Internet). */
        @Volatile var actif = false
        @Volatile var instance: ServiceVpn? = null
    }
}
