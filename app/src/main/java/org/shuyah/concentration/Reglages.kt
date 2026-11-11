package org.shuyah.concentration

import android.content.Context

/**
 * Réglages de l'application, gardés dans les préférences du téléphone (aucun serveur). Lus par l'écran principal
 * ET par les deux services (notifications, verrou) : ils partagent donc les mêmes valeurs.
 */
object Reglages {
    private fun p(c: Context) = c.getSharedPreferences("concentration", Context.MODE_PRIVATE)

    /** Interrupteur général. */
    fun actif(c: Context) = p(c).getBoolean("actif", true)
    fun actif(c: Context, v: Boolean) = p(c).edit().putBoolean("actif", v).apply()

    /** Niveau 1 : retarder les notifications des applications bloquées. */
    fun notifications(c: Context) = p(c).getBoolean("notifications", true)
    fun notifications(c: Context, v: Boolean) = p(c).edit().putBoolean("notifications", v).apply()

    /** Niveau 2 : verrouiller les applications bloquées. */
    fun verrou(c: Context) = p(c).getBoolean("verrou", false)
    fun verrou(c: Context, v: Boolean) = p(c).edit().putBoolean("verrou", v).apply()

    /** Niveau 3 : pare-feu (coupe l'accès Internet des applications bloquées). */
    fun pareFeu(c: Context) = p(c).getBoolean("pareFeu", false)
    fun pareFeu(c: Context, v: Boolean) = p(c).edit().putBoolean("pareFeu", v).apply()

    /** Quelles applications ? faux = celles que je choisis (liste « bloquées ») ; vrai = toutes SAUF celles que j'autorise. */
    fun toutSauf(c: Context) = p(c).getBoolean("toutSauf", false)
    fun toutSauf(c: Context, v: Boolean) = p(c).edit().putBoolean("toutSauf", v).apply()

    /** Applications à bloquer (par défaut : WhatsApp et WhatsApp Business si elles sont installées). */
    fun bloquees(c: Context): Set<String> = p(c).getStringSet("bloquees", null)
        ?: Applications.installees(c, setOf("com.whatsapp", "com.whatsapp.w4b"))
    fun bloquees(c: Context, v: Set<String>) = p(c).edit().putStringSet("bloquees", v).apply()

    /** Applications autorisées en mode « toutes sauf » (par défaut : Claude s'il est installé). */
    fun autorisees(c: Context): Set<String> = p(c).getStringSet("autorisees", null)
        ?: Applications.installees(c, setOf("com.anthropic.claude"))
    fun autorisees(c: Context, v: Set<String>) = p(c).edit().putStringSet("autorisees", v).apply()

    /** Plages horaires (par défaut : tous les jours de 20:00 à 07:00). */
    fun plages(c: Context): List<Plage> {
        val t = p(c).getString("plages", null) ?: return listOf(Plage(20 * 60, 7 * 60))
        return t.split(";").mapNotNull { Plage.depuisTexte(it) }
    }
    fun plages(c: Context, v: List<Plage>) = p(c).edit().putString("plages", v.joinToString(";") { it.versTexte() }).apply()

    /** Pause : la concentration est suspendue jusqu'à cet instant (millisecondes) ; 0 = pas de pause. */
    fun pauseJusqua(c: Context) = p(c).getLong("pauseJusqua", 0L)
    fun pauseJusqua(c: Context, v: Long) = p(c).edit().putLong("pauseJusqua", v).apply()
    fun enPause(c: Context) = pauseJusqua(c) > System.currentTimeMillis()

    /**
     * Compteurs de notifications retardées, par application, pour la plage EN COURS (remis à zéro quand une nouvelle plage commence).
     * `finMs` identifie la plage (instant de sa fin, à la minute).
     */
    fun compter(c: Context, paquet: String, finMs: Long) {
        val prefs = p(c)
        val memeplage = prefs.getLong("compteursFin", 0L) == finMs
        val m = if (memeplage) compteurs(c).toMutableMap() else mutableMapOf()
        m[paquet] = (m[paquet] ?: 0) + 1
        prefs.edit().putLong("compteursFin", finMs).putString("compteurs", m.entries.joinToString(";") { it.key + "=" + it.value }).apply()
    }
    /** Nombre de notifications retardées par application (plage en cours, ou dernière plage terminée). */
    fun compteurs(c: Context): Map<String, Int> = (p(c).getString("compteurs", "") ?: "").split(";").mapNotNull {
        val kv = it.split("="); if (kv.size == 2) kv[1].toIntOrNull()?.let { n -> kv[0] to n } else null }.toMap()

    /** Minutes restantes avant la fin de la plage en cours, ou null si la concentration n'est pas active maintenant (désactivée ou en pause). */
    fun minutesRestantes(c: Context): Int? {
        if (!actif(c) || enPause(c)) return null
        val (jour, min) = Plage.jourEtMinutes()
        return plages(c).mapNotNull { it.minutesRestantes(jour, min) }.maxOrNull()
    }
}
