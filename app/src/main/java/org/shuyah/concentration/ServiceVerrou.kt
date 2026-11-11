package org.shuyah.concentration

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * NIVEAU 2 : verrou des applications. Pendant la plage, quand une application bloquée passe au premier plan, on revient à l'écran d'accueil
 * et on affiche l'écran « Concentration ». Le service ne reçoit que le NOM de l'application affichée (aucun contenu d'écran).
 * Nécessite l'activation du service dans Paramètres → Accessibilité (à faire une fois).
 */
class ServiceVerrou : AccessibilityService() {
    private var libres: Set<String> = emptySet()
    private var libresLe = 0L
    private var dernierBlocage = 0L
    private var dernierControlePareFeu = 0L

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val paquet = e.packageName?.toString() ?: return
        // Contrôle du pare-feu (niveau 3) au plus une fois par minute
        if (System.currentTimeMillis() - dernierControlePareFeu > 60_000L) { dernierControlePareFeu = System.currentTimeMillis(); Pare.synchroniser(this) }
        if (!Reglages.verrou(this) || Reglages.minutesRestantes(this) == null) return        // verrou éteint ou hors plage
        // La liste des applications toujours libres est recalculée toutes les 5 minutes (le lanceur ou le clavier peuvent changer)
        val maintenant = System.currentTimeMillis()
        if (maintenant - libresLe > 300_000L) { libres = Applications.toujoursLibres(this); libresLe = maintenant }
        if (!Applications.estBloquee(this, paquet, libres)) return
        if (maintenant - dernierBlocage < 800) return                                          // évite les répétitions rapides
        dernierBlocage = maintenant
        performGlobalAction(GLOBAL_ACTION_HOME)                                                // retour à l'accueil
        startActivity(Intent(this, EcranBloque::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
    }

    /** Le bouton d'accessibilité (Xiaomi : bouton rond sur le bord de l'écran) ouvre la fenêtre « État » de Concentration. */
    override fun onServiceConnected() {
        try {
            accessibilityButtonController.registerAccessibilityButtonCallback(object : android.accessibilityservice.AccessibilityButtonController.AccessibilityButtonCallback() {
                override fun onClicked(controller: android.accessibilityservice.AccessibilityButtonController) {
                    startActivity(Intent(this@ServiceVerrou, EcranEtat::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                }
            })
        } catch (e: Exception) { /* appareil sans bouton d'accessibilité */ }
    }

    override fun onInterrupt() {}
}
