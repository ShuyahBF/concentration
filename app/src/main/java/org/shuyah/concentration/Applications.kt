package org.shuyah.concentration

import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager

/** Une application installée qui possède une icône dans le lanceur. */
data class Appli(val paquet: String, val nom: String)

/** Outils sur les applications installées et règle « cette application est-elle bloquée maintenant ? ». */
object Applications {
    /** Applications que l'on peut ouvrir depuis l'écran d'accueil, triées par nom (hors Concentration elle-même). */
    fun lancables(c: Context): List<Appli> {
        val pm = c.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0).map { Appli(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.paquet != c.packageName }.distinctBy { it.paquet }.sortedBy { it.nom.lowercase() }
    }

    /** Parmi ces noms de paquets, ceux qui sont réellement installés (valeurs par défaut du premier lancement). */
    fun installees(c: Context, paquets: Set<String>): Set<String> = lancables(c).map { it.paquet }.filter { it in paquets }.toSet()

    /**
     * Applications TOUJOURS libres, même en mode « tout bloquer sauf… » : cette application, le système, l'écran d'accueil, le téléphone
     * (appels normaux), les SMS, le clavier, les réglages et les écrans d'autorisation. Le but : ne jamais pouvoir bloquer le téléphone
     * lui-même ni s'enfermer dehors.
     */
    fun toujoursLibres(c: Context): Set<String> {
        val s = mutableSetOf(c.packageName, "android", "com.android.systemui", "com.android.settings", "com.android.phone", "com.android.server.telecom",
            "com.android.incallui", "com.google.android.dialer", "com.android.dialer", "com.android.emergency", "com.miui.securitycenter",
            "com.lbe.security.miui", "com.android.permissioncontroller", "com.google.android.permissioncontroller",
            "com.android.packageinstaller", "com.google.android.packageinstaller", "com.miui.packageinstaller")
        try { c.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0).forEach { s += it.activityInfo.packageName } } catch (e: Exception) {}
        try { (c.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)?.defaultDialerPackage?.let { s += it } } catch (e: Exception) {}
        try { Telephony.Sms.getDefaultSmsPackage(c)?.let { s += it } } catch (e: Exception) {}
        try { (c.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.enabledInputMethodList?.forEach { s += it.packageName } } catch (e: Exception) {}
        return s
    }

    /** Vrai si l'application a une icône dans le lanceur (les services internes du système n'en ont pas et ne sont jamais bloqués). */
    private fun lancable(c: Context, paquet: String) = c.packageManager.getLaunchIntentForPackage(paquet) != null

    /**
     * RÈGLE CENTRALE : cette application est-elle bloquée (si la plage est active) ?
     *  - jamais pour les applications toujours libres ;
     *  - mode « celles que je choisis » : seulement celles de la liste « bloquées » ;
     *  - mode « toutes sauf » : toutes les applications à icône, sauf celles de la liste « autorisées ».
     */
    fun estBloquee(c: Context, paquet: String, libres: Set<String> = toujoursLibres(c)): Boolean = when {
        paquet in libres -> false
        Reglages.toutSauf(c) -> paquet !in Reglages.autorisees(c) && lancable(c, paquet)
        else -> paquet in Reglages.bloquees(c)
    }
}
