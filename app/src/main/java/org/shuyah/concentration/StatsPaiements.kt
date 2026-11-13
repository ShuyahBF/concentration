package org.shuyah.concentration

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * STATISTIQUES DES PAIEMENTS (logique pure, sans Android, testée par StatsPaiementsTest) : filtres, totaux, répartition par opérateur, activité par jour ou par mois,
 * classements (Top 10 des montants et des bénéficiaires) et export CSV. C'est la même idée que l'« Historique » d'auRor : on choisit une période, un opérateur, un sens
 * (dépenses / recettes) et un texte à chercher, et TOUT l'écran (chiffres, graphiques, listes) se recalcule sur ces critères.
 *
 * Pour un développeur WinDev : chaque fonction reçoit la liste des paiements et rend un résultat ; aucune ne touche à la base ni à l'écran.
 */
object StatsPaiements {
    /** Périodes proposées (liste déroulante) ; « perso » utilise les dates « du » et « au » saisies. */
    val PERIODES = listOf("tout" to "Toute la période", "aujourdhui" to "Aujourd'hui", "7j" to "7 derniers jours", "30j" to "30 derniers jours",
        "mois" to "Ce mois-ci", "mois-1" to "Mois dernier", "an" to "12 derniers mois", "perso" to "Dates personnalisées…")

    /** Critères choisis à l'écran. `genre` : "TOUS", "DEPENSE" ou "RECETTE". `operateur` vide = tous. `q` = texte cherché (bénéficiaire, numéro de transaction, SMS). */
    data class Filtre(val periode: String = "30j", val duMs: Long? = null, val auMs: Long? = null, val operateur: String = "", val genre: String = "TOUS", val q: String = "")

    /** Opérateur d'un paiement, deviné d'après l'expéditeur du SMS puis son texte (liste finie : Orange Money, Moov Money, Telecel Money, Banque, Autre). */
    val OPERATEURS = listOf("Orange Money", "Moov Money", "Telecel Money", "Banque", "Autre")
    fun operateurDe(p: Paiements.Paiement): String {
        val t = (p.adresse + " " + p.corps).lowercase(Locale.ROOT)
        return when {
            t.contains("orange") -> "Orange Money"
            t.contains("moov") -> "Moov Money"
            t.contains("telecel") -> "Telecel Money"
            Regex("banque|bank|coris|ecobank|sgbf|boa\\b|bicia|uba\\b|bdm|cbao|sonibank|société générale|societe generale").containsMatchIn(t) -> "Banque"
            else -> "Autre"
        }
    }

    /** Début (inclus) et fin (exclue) de la période choisie, en millisecondes, d'après le jour courant du fuseau donné ; null = sans borne. */
    fun bornes(f: Filtre, maintenantMs: Long, zone: TimeZone = TimeZone.getDefault()): Pair<Long?, Long?> {
        fun debutDuJour(ms: Long) = Calendar.getInstance(zone).apply { timeInMillis = ms; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val auj = debutDuJour(maintenantMs)
        val demain = (auj.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
        fun jours(n: Int) = (auj.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -n) }.timeInMillis
        return when (f.periode) {
            "aujourdhui" -> auj.timeInMillis to demain
            "7j" -> jours(6) to demain
            "30j" -> jours(29) to demain
            "mois" -> (auj.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis to demain
            "mois-1" -> {
                val debutMois = (auj.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
                (debutMois.clone() as Calendar).apply { add(Calendar.MONTH, -1) }.timeInMillis to debutMois.timeInMillis
            }
            "an" -> (auj.clone() as Calendar).apply { add(Calendar.MONTH, -12) }.timeInMillis to demain
            "perso" -> f.duMs to f.auMs?.let { debutDuJour(it).apply { add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis }    // « au » inclus : on prend la fin de ce jour
            else -> null to null
        }
    }

    /** Sans accents ni majuscules : « Pharmacie Étoile » se retrouve en tapant « pharmacie etoile ». */
    fun normaliser(s: String): String = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).trim()

    /** Paiements correspondant aux critères (période, opérateur, sens, texte), du plus récent au plus ancien. Chaque mot tapé doit se trouver dans le bénéficiaire, la référence, l'expéditeur ou le SMS. */
    fun filtrer(liste: List<Paiements.Paiement>, f: Filtre, maintenantMs: Long, zone: TimeZone = TimeZone.getDefault()): List<Paiements.Paiement> {
        val (du, au) = bornes(f, maintenantMs, zone)
        val mots = normaliser(f.q).split(Regex("\\s+")).filter { it.isNotEmpty() }
        return liste.filter { p ->
            (du == null || p.recuLeMs >= du) && (au == null || p.recuLeMs < au) &&
                (f.genre == "TOUS" || p.genre == f.genre) && (f.operateur.isBlank() || operateurDe(p) == f.operateur) &&
                (mots.isEmpty() || normaliser(p.tiers + " " + (p.reference ?: "") + " " + p.adresse + " " + p.corps).let { botte -> mots.all { it in botte } })
        }.sortedByDescending { it.recuLeMs }
    }

    /** Chiffres clés : nombre, dépenses, recettes, solde net (recettes − dépenses), dépense moyenne, plus grosse dépense. */
    data class Totaux(val nombre: Int, val depenses: Long, val recettes: Long, val net: Long, val depenseMoyenne: Long, val plusGrosse: Long, val nbDepenses: Int, val nbRecettes: Int)
    fun totaux(liste: List<Paiements.Paiement>): Totaux {
        val dep = liste.filter { it.genre == "DEPENSE" }; val rec = liste.filter { it.genre == "RECETTE" }
        val d = dep.sumOf { it.montant }; val r = rec.sumOf { it.montant }
        return Totaux(liste.size, d, r, r - d, if (dep.isEmpty()) 0 else d / dep.size, dep.maxOfOrNull { it.montant } ?: 0, dep.size, rec.size)
    }

    /** Une carte par opérateur : nombre de paiements, dépenses et recettes. Classées par nombre de paiements décroissant. */
    data class ParOperateur(val operateur: String, val nombre: Int, val depenses: Long, val recettes: Long)
    fun parOperateur(liste: List<Paiements.Paiement>): List<ParOperateur> =
        liste.groupBy { operateurDe(it) }.map { (op, l) -> ParOperateur(op, l.size, l.filter { it.genre == "DEPENSE" }.sumOf { it.montant }, l.filter { it.genre == "RECETTE" }.sumOf { it.montant }) }
            .sortedByDescending { it.nombre }

    /** Une barre de l'histogramme : `cle` = « AAAA-MM-JJ » (par jour) ou « AAAA-MM » (par mois), avec dépenses, recettes et nombre. */
    data class Barre(val cle: String, val depenses: Long, val recettes: Long, val nombre: Int)
    /** Activité par jour ou par mois, du plus ancien au plus récent (seules les périodes qui ont des paiements). */
    fun parPeriode(liste: List<Paiements.Paiement>, parMois: Boolean, zone: TimeZone = TimeZone.getDefault()): List<Barre> =
        liste.groupBy { cleDe(it.recuLeMs, parMois, zone) }.map { (k, l) -> Barre(k, l.filter { it.genre == "DEPENSE" }.sumOf { it.montant }, l.filter { it.genre == "RECETTE" }.sumOf { it.montant }, l.size) }.sortedBy { it.cle }

    /** « 2026-11-13 » (jour) ou « 2026-11 » (mois) d'un instant. */
    fun cleDe(ms: Long, parMois: Boolean, zone: TimeZone = TimeZone.getDefault()): String {
        val c = Calendar.getInstance(zone).apply { timeInMillis = ms }
        val base = String.format(Locale.ROOT, "%04d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
        return if (parMois) base else String.format(Locale.ROOT, "%s-%02d", base, c.get(Calendar.DAY_OF_MONTH))
    }

    /** Début et fin (exclue) du jour ou du mois désigné par une clé de barre : sert à filtrer l'écran quand on touche une barre. */
    fun intervalleDe(cle: String, zone: TimeZone = TimeZone.getDefault()): Pair<Long, Long> {
        val p = cle.split("-").map { it.toInt() }
        val debut = Calendar.getInstance(zone).apply { clear(); set(p[0], p[1] - 1, if (p.size > 2) p[2] else 1, 0, 0, 0) }
        val fin = (debut.clone() as Calendar).apply { if (p.size > 2) add(Calendar.DAY_OF_MONTH, 1) else add(Calendar.MONTH, 1) }
        return debut.timeInMillis to fin.timeInMillis
    }

    /** Top des plus gros paiements du sens choisi (dépenses par défaut). */
    fun topMontants(liste: List<Paiements.Paiement>, genre: String = "DEPENSE", n: Int = 10): List<Paiements.Paiement> =
        liste.filter { it.genre == genre }.sortedByDescending { it.montant }.take(n)

    /** Un bénéficiaire (ou expéditeur) : nom affiché, nombre de paiements et total. Les noms sont regroupés sans tenir compte des accents ni des majuscules. */
    data class Tiers(val nom: String, val nombre: Int, val total: Long)
    fun topTiers(liste: List<Paiements.Paiement>, genre: String = "DEPENSE", n: Int = 10): List<Tiers> =
        liste.filter { it.genre == genre }.groupBy { normaliser(it.tiers.ifBlank { "(sans nom)" }) }
            .map { (_, l) -> Tiers(l.first().tiers.ifBlank { "(sans nom)" }, l.size, l.sumOf { it.montant }) }
            .sortedByDescending { it.total }.take(n)

    /** Parts du camembert : les `n` premiers bénéficiaires puis « Autres » (le reste), avec leur part du total. */
    fun parts(tiers: List<Tiers>, total: Long, n: Int = 6): List<Pair<String, Long>> {
        val tete = tiers.take(n).map { it.nom to it.total }
        val reste = total - tete.sumOf { it.second }
        return if (reste > 0) tete + ("Autres" to reste) else tete
    }

    /** Export CSV (séparateur « ; », accents conservés) des paiements affichés ; à ouvrir directement dans Excel. */
    fun csv(liste: List<Paiements.Paiement>, zone: TimeZone = TimeZone.getDefault()): String {
        fun e(v: Any?) = "\"" + (v?.toString() ?: "").replace("\"", "\"\"") + "\""
        val fmt = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).apply { timeZone = zone }
        val tete = listOf("Date", "Opérateur", "Sens", "Montant (FCFA)", "Bénéficiaire / expéditeur", "Solde après", "Transaction").joinToString(";") { e(it) }
        return "﻿" + tete + "\n" + liste.joinToString("\n") { p ->
            listOf(fmt.format(p.recuLeMs), operateurDe(p), if (p.genre == "RECETTE") "Recette" else "Dépense", p.montant, p.tiers, p.soldeApres ?: "", p.reference ?: "").joinToString(";") { e(it) }
        }
    }
}
