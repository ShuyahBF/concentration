package org.shuyah.concentration

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.accessibility.AccessibilityNodeInfo

/**
 * LECTURE DE LA FENÊTRE « INFOS DE L'OPÉRATEUR » : le menu « renvoi de notification » d'Orange affiche, sans demander le code secret, la liste des dernières
 * opérations (numéros de transaction). Android ne rend pas ce type de menu à une application par `sendUssdRequest` : Concentration ouvre donc le code dans le
 * composeur du téléphone et LIT la fenêtre qui s'affiche, avec son service d'accessibilité.
 *
 * Pour un développeur WinDev : c'est la lecture d'un texte affiché par une autre application. Garde-fous (c'est un accès sensible) :
 *  - lecture UNIQUEMENT pendant 2 minutes après l'appui sur le bouton « Lire la liste des dernières opérations » de Concentration ;
 *  - UNIQUEMENT la fenêtre du téléphone / de l'opérateur (paquet « phone », « telecom », « dialer », « ussd » ou « stk ») ;
 *  - on n'en retient QUE les numéros de transaction (format MP261113.1339.95787510) : le reste du texte est oublié aussitôt, jamais enregistré ;
 *  - Concentration ne tape rien dans cette fenêtre et ne touche jamais au code secret ; elle ne fait que lire.
 */
object LectureMenu {
    private const val DUREE_MS = 120_000L
    private val PAQUETS = listOf("phone", "telecom", "dialer", "ussd", "stk")

    /** Démarre la lecture : ouvre le code dans le composeur (aucune autorisation) et autorise la lecture de la fenêtre pendant 2 minutes. */
    fun demarrer(c: Context, code: String) {
        Reglages.menuAttenduJusqua(c, System.currentTimeMillis() + DUREE_MS)
        try { c.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(code))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
    }

    /** Vrai si une fenêtre de l'opérateur est attendue en ce moment (test très rapide, appelé à chaque événement d'accessibilité). */
    fun attendu(c: Context) = System.currentTimeMillis() < Reglages.menuAttenduJusqua(c)

    /** Appelé par le service d'accessibilité : lit la fenêtre active si elle est attendue, en retient les numéros de transaction et prévient s'il en manque. */
    fun depuisFenetre(service: AccessibilityService) {
        if (!attendu(service)) return
        val racine = service.rootInActiveWindow ?: return
        val paquet = racine.packageName?.toString()?.lowercase().orEmpty()
        if (PAQUETS.none { paquet.contains(it) }) return                                  // pas la fenêtre de l'opérateur : rien n'est lu
        val texte = StringBuilder()
        rassembler(racine, texte, 0)
        val refs = VerifOperateur.referencesDuTexte(texte.toString())
        texte.setLength(0)                                                                // le texte de la fenêtre est oublié aussitôt
        if (refs.isEmpty()) return
        Reglages.menuLu(service, refs, System.currentTimeMillis())
        Reglages.menuAttenduJusqua(service, 0L)                                           // une seule lecture par demande
        val absentes = VerifOperateur.referencesAbsentes(refs, Historique.de(service).tousPaiements())
        if (absentes.isNotEmpty()) Reseau.notifier(service, "Opérations sans SMS détectées",
            "${absentes.size} opération(s) de la liste de l'opérateur ne sont pas dans votre base. Ouvrez Concentration → Paiements.", 2310)
    }

    /** Parcourt les éléments de la fenêtre (profondeur limitée) et ajoute leurs textes. */
    private fun rassembler(n: AccessibilityNodeInfo?, sortie: StringBuilder, profondeur: Int) {
        if (n == null || profondeur > 12 || sortie.length > 4000) return
        n.text?.let { sortie.append(it).append('\n') }
        for (i in 0 until n.childCount) rassembler(n.getChild(i), sortie, profondeur + 1)
    }
}
