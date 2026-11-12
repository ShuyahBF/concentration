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

    // ---- Réseau mobile (v1.8) : solde Internet par code USSD et alerte de seuil ----
    /** Code USSD du solde Internet de la carte SIM de cet emplacement (0 = SIM 1) ; vide si non saisi. */
    fun codeUssd(c: Context, emplacement: Int) = p(c).getString("ussd_$emplacement", "") ?: ""
    fun codeUssd(c: Context, emplacement: Int, v: String) = p(c).edit().putString("ussd_$emplacement", v.trim()).apply()
    /** Volume de départ du forfait de cette carte SIM, en Mo (0 = inconnu). Repli : l'ancien réglage commun de la version 1.8. */
    fun departMo(c: Context, emplacement: Int): Double =
        p(c).getFloat("departMo_$emplacement", p(c).getFloat("departMo", 0f)).toDouble()
    fun departMo(c: Context, emplacement: Int, v: Double) = p(c).edit().putFloat("departMo_$emplacement", v.toFloat()).apply()
    /** Seuil d'alerte en pourcentage du volume de départ (10 par défaut). */
    fun seuilPourcent(c: Context) = p(c).getInt("seuilPourcent", 10)
    fun seuilPourcent(c: Context, v: Int) = p(c).edit().putInt("seuilPourcent", v.coerceIn(1, 90)).apply()
    fun alerteReseau(c: Context) = p(c).getBoolean("alerteReseau", true)
    fun alerteReseau(c: Context, v: Boolean) = p(c).edit().putBoolean("alerteReseau", v).apply()
    /** Vérifier le solde automatiquement toutes les 6 heures. */
    fun verifAuto(c: Context) = p(c).getBoolean("verifAuto", false)
    fun verifAuto(c: Context, v: Boolean) = p(c).edit().putBoolean("verifAuto", v).apply()
    /** Date d'expiration du forfait de cette carte SIM (millisecondes, 0 = inconnue), lue dans la réponse de l'opérateur. */
    fun expirationMs(c: Context, emplacement: Int) = p(c).getLong("expiration_$emplacement", 0L)
    fun expirationMs(c: Context, emplacement: Int, v: Long) = p(c).edit().putLong("expiration_$emplacement", v).apply()
    /** Rappeler l'expiration du forfait (7 jours, 3 jours, le jour même). */
    fun rappelExpiration(c: Context) = p(c).getBoolean("rappelExpiration", true)
    fun rappelExpiration(c: Context, v: Boolean) = p(c).edit().putBoolean("rappelExpiration", v).apply()
    /** Intervalle de la vérification automatique, en heures (minimum 1, 6 par défaut). */
    fun intervalleHeures(c: Context) = p(c).getInt("intervalleHeures", Solde.INTERVALLE_DEFAUT_HEURES).coerceAtLeast(1)
    fun intervalleHeures(c: Context, v: Int) = p(c).edit().putInt("intervalleHeures", v.coerceAtLeast(1)).apply()
    /** Remettre le volume de départ à jour quand une recharge est détectée. */
    fun departAuto(c: Context) = p(c).getBoolean("departAuto", true)
    fun departAuto(c: Context, v: Boolean) = p(c).edit().putBoolean("departAuto", v).apply()
    /** Alerte déjà envoyée pour le forfait de cette carte SIM (une seule notification par passage sous le seuil). */
    fun alerteEnvoyee(c: Context, emplacement: Int) = p(c).getBoolean("alerteEnvoyee_$emplacement", false)
    fun alerteEnvoyee(c: Context, emplacement: Int, v: Boolean) = p(c).edit().putBoolean("alerteEnvoyee_$emplacement", v).apply()
    /** Dernière lecture du solde de cette carte SIM : texte de l'opérateur, heure, volume restant en Mo (négatif = aucun volume dans la réponse) et opérateur. */
    fun derniereLecture(c: Context, emplacement: Int, texte: String, heure: Long, restantMo: Double?, operateur: String) =
        p(c).edit().putString("lectureTexte_$emplacement", texte).putLong("lectureHeure_$emplacement", heure)
            .putFloat("restantMo_$emplacement", (restantMo ?: -1.0).toFloat()).putString("lectureOperateur_$emplacement", operateur).apply()
    fun lectureTexte(c: Context, emplacement: Int) = p(c).getString("lectureTexte_$emplacement", "") ?: ""
    fun lectureHeure(c: Context, emplacement: Int) = p(c).getLong("lectureHeure_$emplacement", 0L)
    fun lectureOperateur(c: Context, emplacement: Int) = p(c).getString("lectureOperateur_$emplacement", "") ?: ""
    /** Volume restant lu la dernière fois sur cette carte, en Mo ; null si aucune lecture ou aucun volume dans la réponse (pas de forfait). */
    fun restantMo(c: Context, emplacement: Int): Double? = p(c).getFloat("restantMo_$emplacement", -1f).let { if (it < 0f) null else it.toDouble() }

    /** Niveau 1 : retarder les notifications des applications bloquées. */
    fun notifications(c: Context) = p(c).getBoolean("notifications", true)
    fun notifications(c: Context, v: Boolean) = p(c).edit().putBoolean("notifications", v).apply()

    /** Niveau 2 : verrouiller les applications bloquées. */
    fun verrou(c: Context) = p(c).getBoolean("verrou", false)
    fun verrou(c: Context, v: Boolean) = p(c).edit().putBoolean("verrou", v).apply()

    /** Contacts autorisés : leurs notifications ne sont pas retardées (un nom par ligne, tel qu'il s'affiche dans WhatsApp). */
    fun contacts(c: Context): List<String> = (p(c).getString("contacts", "") ?: "").split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    fun contacts(c: Context, v: List<String>) = p(c).edit().putString("contacts", v.joinToString("\n")).apply()

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

    /** Son émis à chaque changement d'état de la concentration (début, fin, pause, reprise). */
    fun son(c: Context) = p(c).getBoolean("son", true)
    fun son(c: Context, v: Boolean) = p(c).edit().putBoolean("son", v).apply()

    /** Dernier état connu (« libre », « active », « pause ») : sert à détecter un changement pour émettre le son. */
    fun dernierEtat(c: Context): String? = p(c).getString("dernierEtat", null)
    fun dernierEtat(c: Context, v: String) = p(c).edit().putString("dernierEtat", v).apply()

    /**
     * Xiaomi : le réglage « Pas de restriction » de la fiche de l'application est propre à MIUI et ne peut pas être lu par Android ;
     * l'utilisateur le confirme lui-même (la ligne « Batterie » passe alors au vert).
     */
    fun batterieConfirmee(c: Context) = p(c).getBoolean("batterieConfirmee", false)
    fun batterieConfirmee(c: Context, v: Boolean) = p(c).edit().putBoolean("batterieConfirmee", v).apply()

    /** Minutes restantes avant la fin de la plage en cours, sans tenir compte de la pause (null si hors plage ou désactivée). */
    fun minutesRestantesPlage(c: Context): Int? {
        if (!actif(c)) return null
        val (jour, min) = Plage.jourEtMinutes()
        return plages(c).mapNotNull { it.minutesRestantes(jour, min) }.maxOrNull()
    }

    /** Minutes restantes avant la fin de la plage en cours, ou null si la concentration n'est pas active maintenant (désactivée ou en pause). */
    fun minutesRestantes(c: Context): Int? = if (enPause(c)) null else minutesRestantesPlage(c)
}
