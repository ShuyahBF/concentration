package org.shuyah.concentration

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Lecture du solde Internet dans la réponse texte d'un code USSD, et alerte de seuil. Logique pure (sans Android) : testée par SoldeTest.
 * Les volumes sont comptés en Mo (1 Go = 1024 Mo).
 */
object Solde {
    private val VOLUME = Regex("(\\d+(?:[.,]\\d+)?)\\s*(go|gb|mo|mb|ko|kb)(?![a-z])", RegexOption.IGNORE_CASE)
    private val MOTS_CLES = listOf("reste", "restant", "solde", "disponible", "remaining", "balance")

    /** Convertit une valeur et son unité (Go, Mo, Ko ; majuscules ou minuscules ; GB, MB, KB aussi) en Mo. */
    fun enMo(valeur: Double, unite: String): Double = when (unite.lowercase(Locale.ROOT)) {
        "go", "gb" -> valeur * 1024.0
        "ko", "kb" -> valeur / 1024.0
        else -> valeur
    }

    /**
     * Volume restant, en Mo, d'après le texte de l'opérateur ; null si aucun volume n'y figure.
     * Les volumes précédés (60 caractères au plus) d'un mot comme « reste », « solde » ou « disponible » sont additionnés (plusieurs forfaits) ;
     * sans mot-clé, c'est le premier volume du texte qui est retenu. La virgule décimale est acceptée.
     */
    fun analyserVolume(texte: String): Double? {
        val trouves = VOLUME.findAll(texte).toList()
        if (trouves.isEmpty()) return null
        fun valeur(m: MatchResult) = enMo(m.groupValues[1].replace(',', '.').toDouble(), m.groupValues[2])
        val prioritaires = trouves.filter { m ->
            val avant = texte.substring(maxOf(0, m.range.first - 60), m.range.first).lowercase(Locale.ROOT)
            MOTS_CLES.any { avant.contains(it) }
        }
        return if (prioritaires.isNotEmpty()) prioritaires.sumOf { valeur(it) } else valeur(trouves.first())
    }

    /** Pourcentage du volume de départ qui reste (arrondi) ; null si le départ n'est pas connu. */
    fun pourcentRestant(restantMo: Double, departMo: Double): Int? =
        if (departMo <= 0.0) null else Math.round(restantMo / departMo * 100.0).toInt().coerceAtLeast(0)

    /** Vrai si le volume restant est au niveau du seuil (ex. 10 %) du volume de départ, ou en dessous. */
    fun seuilAtteint(restantMo: Double, departMo: Double, pourcentSeuil: Int): Boolean =
        departMo > 0.0 && restantMo <= departMo * pourcentSeuil / 100.0

    /** Une recharge est détectée quand le volume remonte nettement (plus de 20 % et au moins 50 Mo de plus) : le départ est alors remis à jour. */
    fun rechargeDetectee(avantMo: Double?, apresMo: Double): Boolean =
        avantMo != null && apresMo > avantMo * 1.2 && apresMo - avantMo >= 50.0

    // ---- Date d'expiration du forfait (souvent entre parenthèses dans la réponse de l'opérateur) ----
    private val DATE_JMA = Regex("(?<!\\d)(\\d{1,2})[/\\-.](\\d{1,2})[/\\-.](\\d{4}|\\d{2})(?!\\d)")
    private val DATE_AMJ = Regex("(?<!\\d)(\\d{4})[/\\-.](\\d{1,2})[/\\-.](\\d{1,2})(?!\\d)")
    private val HEURE_APRES = Regex("^\\s*(?:,|à|a|at|-)?\\s*(\\d{1,2})\\s*[:hH]\\s*(\\d{2})")
    private val MOTS_EXPIRATION = listOf("exp", "valab", "valid", "jusqu", "until", "fin ", "echeance", "échéance")

    /** Première date (JJ/MM/AAAA, JJ-MM-AA, AAAA-MM-JJ…) d'un texte, avec son heure si elle suit (14:30, 14h30 ; sinon 23:59), en millisecondes ; null si aucune. */
    private fun premiereDate(texte: String, fuseau: TimeZone): Long? = datesDe(texte, fuseau).firstOrNull()?.ms

    private data class DateLue(val debut: Int, val fin: Int, val ms: Long)

    /** Toutes les dates valides d'un texte, dans l'ordre de leur position (une date impossible comme 31/02 est ignorée). */
    private fun datesDe(texte: String, fuseau: TimeZone): List<DateLue> {
        data class Brut(val debut: Int, val fin: Int, val an: Int, val mois: Int, val jour: Int)
        val trouves = mutableListOf<Brut>()
        DATE_JMA.findAll(texte).forEach { m ->
            val an = m.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
            trouves += Brut(m.range.first, m.range.last + 1, an, m.groupValues[2].toInt(), m.groupValues[1].toInt())
        }
        DATE_AMJ.findAll(texte).forEach { m -> trouves += Brut(m.range.first, m.range.last + 1, m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }
        return trouves.sortedBy { it.debut }.mapNotNull { c ->
            val h = HEURE_APRES.find(texte.substring(c.fin, minOf(texte.length, c.fin + 14)))
            val heure = h?.groupValues?.get(1)?.toInt()?.takeIf { it in 0..23 }; val minute = h?.groupValues?.get(2)?.toInt()?.takeIf { it in 0..59 }
            val cal = Calendar.getInstance(fuseau).apply { isLenient = false; clear() }
            try {
                cal.set(c.an, c.mois - 1, c.jour, if (heure != null && minute != null) heure else 23, if (heure != null && minute != null) minute else 59, 0)
                DateLue(c.debut, c.fin, cal.timeInMillis)
            } catch (e: IllegalArgumentException) { null }
        }
    }

    private val PARENTHESE_APRES = Regex("^\\s*\\(([^)]*)\\)")

    /**
     * Date d'expiration du forfait Internet lue dans la réponse de l'opérateur, en millisecondes ; null si le texte n'en donne pas (ou s'il n'y a plus de volume).
     *
     * Format des opérateurs : chaque volume est suivi, entre parenthèses, de SA date d'expiration, ou de « - » quand il n'en a pas, par exemple
     * « 621Mo (18/11/26) / 0Mo (-) / 0Mo (15/11/26) ». Seuls les volumes ENCORE POSITIFS comptent (un volume à 0 n'a plus rien à perdre) : c'est la date
     * la plus proche à venir parmi eux. Tout à 0 : pas d'expiration à rappeler.
     * Sans ce format, repli : une date ENTRE PARENTHÈSES ou précédée d'un mot comme « exp », « valable », « jusqu'au » (une date de recharge n'est pas
     * confondue avec l'expiration) ; plusieurs dates : la plus proche à venir (sinon la plus récente, déjà passée).
     */
    fun analyserExpiration(texte: String, maintenantMs: Long, fuseau: TimeZone = TimeZone.getDefault()): Long? {
        // 1) volumes suivis de leur date entre parenthèses
        val paires = VOLUME.findAll(texte).mapNotNull { m ->
            val parenthese = PARENTHESE_APRES.find(texte.substring(m.range.last + 1)) ?: return@mapNotNull null
            enMo(m.groupValues[1].replace(',', '.').toDouble(), m.groupValues[2]) to premiereDate(parenthese.groupValues[1], fuseau)
        }.toList()
        if (paires.isNotEmpty()) {
            val dates = paires.filter { it.first > 0.0 && it.second != null }.map { it.second!! }
            if (dates.isEmpty()) return null
            return dates.filter { it >= maintenantMs }.minOrNull() ?: dates.maxOrNull()
        }
        // 2) repli : date entre parenthèses ou après un mot d'expiration
        val dates = datesDe(texte, fuseau).mapNotNull { c ->
            val avant = texte.substring(maxOf(0, c.debut - 45), c.debut).lowercase(Locale.ROOT)
            val ouvrante = texte.lastIndexOf('(', c.debut); val fermante = texte.lastIndexOf(')', c.debut)
            val entreParentheses = ouvrante > fermante && c.debut - ouvrante < 60
            if (entreParentheses || MOTS_EXPIRATION.any { avant.contains(it) }) c.ms else null
        }
        if (dates.isEmpty()) return null
        return dates.filter { it >= maintenantMs }.minOrNull() ?: dates.maxOrNull()
    }

    /** Les trois rappels d'expiration : (7, J-7 à 09:00), (3, J-3 à 09:00) et (0, le jour même à 08:00 — ou 2 h avant si l'expiration est avant 10:00). */
    fun momentsRappel(expirationMs: Long, fuseau: TimeZone = TimeZone.getDefault()): List<Pair<Int, Long>> {
        val exp = Calendar.getInstance(fuseau).apply { timeInMillis = expirationMs }
        fun jourA(joursAvant: Int, h: Int, m: Int): Long = (exp.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -joursAvant); set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val dernier = if (exp.get(Calendar.HOUR_OF_DAY) < 10) expirationMs - 2 * 3600_000L else jourA(0, 8, 0)
        return listOf(7 to jourA(7, 9, 0), 3 to jourA(3, 9, 0), 0 to dernier)
    }

    /** Jours calendaires entre maintenant et le jour d'expiration (0 = aujourd'hui, négatif = déjà expiré). */
    fun joursRestants(expirationMs: Long, maintenantMs: Long, fuseau: TimeZone = TimeZone.getDefault()): Int {
        fun debutDeJour(ms: Long) = Calendar.getInstance(fuseau).apply { timeInMillis = ms; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        return Math.round((debutDeJour(expirationMs) - debutDeJour(maintenantMs)) / 86_400_000.0).toInt()
    }

    /** « Expire le 20/11/2026 à 23:59 (dans 5 jours) » ; « (aujourd'hui) », « (demain) » ; « Expiré le … » si la date est passée. */
    fun libelleExpiration(expirationMs: Long, maintenantMs: Long, fuseau: TimeZone = TimeZone.getDefault()): String {
        val date = SimpleDateFormat("dd/MM/yyyy 'à' HH:mm", Locale.FRANCE).apply { timeZone = fuseau }.format(expirationMs)
        if (expirationMs < maintenantMs) return "Expiré le $date"
        return "Expire le $date " + when (val j = joursRestants(expirationMs, maintenantMs, fuseau)) { 0 -> "(aujourd'hui)"; 1 -> "(demain)"; else -> "(dans $j jours)" }
    }

    /** Intervalle de la vérification automatique, en heures : saisie libre, jamais moins d'une heure (texte vide ou illisible = 6 h). */
    const val INTERVALLE_DEFAUT_HEURES = 6
    fun intervalleHeures(saisie: String?): Int =
        (saisie?.trim()?.toIntOrNull() ?: INTERVALLE_DEFAUT_HEURES).coerceIn(1, 8760)

    /** « 2,3 Go » ou « 450 Mo » (virgule française). */
    fun formater(mo: Double): String =
        if (mo >= 1024.0) String.format(Locale.FRANCE, "%.1f Go", mo / 1024.0) else String.format(Locale.FRANCE, "%.0f Mo", mo)
}
