package org.shuyah.concentration

import android.content.Context

/**
 * VÉRIFIER AUPRÈS DE L'OPÉRATEUR : quand un paiement attendu n'arrive pas par SMS (cas fréquent : aucun SMS de réception), un seul bouton envoie le code USSD
 * du « mini relevé » (celui d'auRor), lit la réponse et la RAPPROCHE de la base : les opérations du relevé qui ne sont pas dans la base apparaissent
 * « Reçu sans SMS » et s'ajoutent d'un appui.
 *
 * PRUDENCE VIS-À-VIS DE L'OPÉRATEUR (un compte d'argent n'est pas un jeu) :
 *  - jamais d'envoi automatique en boucle : seulement à l'appui sur le bouton ;
 *  - au plus MAX_PAR_JOUR envois par 24 h, et au moins ESPACEMENT_MIN_MIN minutes entre deux envois ;
 *  - le code secret est saisi à chaque fois et jamais conservé ;
 *  - si la réponse n'est pas comprise, on affiche le texte brut tel que l'opérateur l'a envoyé (jamais d'échec silencieux).
 * Logique pure testée par VerifOperateurTest ; l'envoi lui-même passe par Reseau.lancerUssd (un seul échange).
 */
object VerifOperateur {
    const val MAX_PAR_JOUR = 4
    const val ESPACEMENT_MIN_MIN = 10
    private const val JOUR_MS = 86_400_000L

    /** Pourquoi on refuse d'envoyer maintenant ; null = envoi autorisé. `horaires` = heures (ms) des derniers envois. */
    fun refus(horaires: List<Long>, maintenant: Long): String? {
        val dernieres24h = horaires.filter { maintenant - it in 0 until JOUR_MS }
        val dernier = dernieres24h.maxOrNull()
        return when {
            dernieres24h.size >= MAX_PAR_JOUR -> "Limite atteinte : $MAX_PAR_JOUR vérifications par jour au plus, pour ne pas solliciter l'opérateur à l'excès. Réessayez demain."
            dernier != null && maintenant - dernier < ESPACEMENT_MIN_MIN * 60_000L -> "Une vérification vient d'être faite : attendez ${ESPACEMENT_MIN_MIN - ((maintenant - dernier) / 60_000L).toInt()} minute(s) avant la suivante."
            else -> null
        }
    }

    /** Nombre de vérifications encore possibles aujourd'hui. */
    fun restantes(horaires: List<Long>, maintenant: Long) = (MAX_PAR_JOUR - horaires.count { maintenant - it in 0 until JOUR_MS }).coerceAtLeast(0)

    /**
     * Opérations lues dans le texte d'un relevé : une opération par ligne (ou séparée par « ; »), reconnue comme un SMS de paiement (montant + sens).
     * Une ligne qui n'est pas comprise est ignorée ici — le texte brut reste affiché à l'écran pour que rien ne soit caché.
     */
    fun analyserReleve(texte: String, maintenant: Long): List<Paiements.Paiement> =
        texte.split('\n', ';').mapNotNull { Paiements.analyser("RELEVE", normaliser(it.trim()), maintenant) }

    /** Une ligne de relevé commence souvent par « Reçu 5000 FCFA… » (sans « Vous avez ») : on la ramène à la tournure des SMS pour que le sens soit reconnu. */
    private fun normaliser(ligne: String): String =
        if (Regex("^(?:\\d+[.)]\\s*)?(?:re[cç]u|recue|dép[ôo]t|depot)\\b", RegexOption.IGNORE_CASE).containsMatchIn(ligne)) "Vous avez reçu " + ligne.replaceFirst(Regex("^(?:\\d+[.)]\\s*)?\\p{L}+\\s*", RegexOption.IGNORE_CASE), "") else ligne

    /**
     * Opérations du relevé ABSENTES de la base. Avec un numéro de transaction : comparaison exacte. Sans numéro : une opération de même sens et de même montant
     * déjà enregistrée dans les 3 derniers jours est considérée comme présente (prudence : on évite d'ajouter un doublon).
     */
    fun absents(lues: List<Paiements.Paiement>, base: List<Paiements.Paiement>, maintenant: Long): List<Paiements.Paiement> {
        val references = base.mapNotNull { it.reference }.toSet()
        return lues.filter { l ->
            if (l.reference != null) l.reference !in references
            else base.none { b -> b.genre == l.genre && b.montant == l.montant && maintenant - b.recuLeMs in 0..3 * JOUR_MS }
        }
    }

    /**
     * Envoie le code (complété du code secret si besoin) sur la carte voulue. Enregistre l'heure de l'envoi (limites), jamais le code secret.
     * `surFin` reçoit la réponse brute (texte ou erreur) au thread principal.
     */
    fun lancer(c: Context, carte: Reseau.CarteSim, code: CodesUssd.Code, pin: String?, surFin: (Reseau.ReponseUssd) -> Unit) {
        val maintenant = System.currentTimeMillis()
        val horaires = Reglages.horairesVerif(c).filter { maintenant - it in 0 until JOUR_MS }
        val motif = refus(horaires, maintenant)
        if (motif != null) { surFin(Reseau.ReponseUssd(null, motif)); return }
        if (code.demandePin && pin.isNullOrBlank()) { surFin(Reseau.ReponseUssd(null, "Code secret nécessaire pour cette vérification.")); return }
        Reglages.horairesVerif(c, horaires + maintenant)
        val aEnvoyer = if (code.demandePin) CodesUssd.completer(code.code, pin.orEmpty()) else code.code
        Reglages.derniereRequete(c, carte.emplacement, maintenant)       // un SMS reçu dans les 3 minutes sera rattaché à cette carte
        Reseau.lancerUssd(c, carte.idAbonnement, aEnvoyer, surFin)
    }
}
