package org.shuyah.concentration

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Locale

/** « 12 mars 14:30 » à partir d'un instant. */
private fun dateHeure(ms: Long) = SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE).format(ms)
private fun jourCourt(cle: String) = cle.substring(8, 10) + "/" + cle.substring(5, 7)     // « 2026-11-13 » -> « 13/11 »
private fun francs(v: Long) = String.format(Locale.FRANCE, "%,d", v).replace(' ', ' ').replace(' ', ' ') + " FCFA"

// =====================================================================================================================
// 3e onglet : CONSOMMATION d'Internet estimée entre deux vérifications du solde
// =====================================================================================================================

/**
 * Graphique de la consommation Internet. Chaque vérification du solde (bouton « Solde » ou vérification automatique) mémorise le volume restant ;
 * la consommation entre deux vérifications est la différence (voir `Consommation`). Deux graphiques : le volume restant au fil du temps (courbe) et la
 * consommation estimée par jour (barres). Les périodes de recharge (volume qui remonte) sont signalées et ne comptent pas comme consommation.
 */
@Composable
internal fun OngletConsommation(cartes: List<Reseau.CarteSim>, tickLecture: Int, maintenant: Long) {
    val c = LocalContext.current
    var jours by remember { mutableIntStateOf(30) }                              // période affichée : 7, 30 ou 90 jours
    val mesuresToutes = remember(tickLecture, maintenant, jours) { Historique.de(c).mesures(System.currentTimeMillis() - jours * 86_400_000L) }
    val emplacements = mesuresToutes.map { it.emplacement }.distinct().sorted()
    var choisi by remember { mutableIntStateOf(-1) }
    val slot = if (choisi in emplacements) choisi else (cartes.firstOrNull { it.donnees && it.emplacement in emplacements }?.emplacement ?: emplacements.firstOrNull() ?: 0)
    val mesures = mesuresToutes.filter { it.emplacement == slot }
    val intervalles = Consommation.intervalles(mesures)
    val parJour = Consommation.parJour(intervalles)
    val moyenne = Consommation.moyenneParJour(intervalles)
    val restantActuel = mesures.lastOrNull()?.restantMo
    val nomCarte = cartes.firstOrNull { it.emplacement == slot }?.operateur ?: Historique.de(c).operateurDe(slot)

    Section("Consommation d'Internet", Icons.Filled.Info) {
        // Choix de la période (liste finie : trois boutons à choix exclusif)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 30, 90).forEach { j -> FilterChip(selected = jours == j, onClick = { jours = j }, label = { Text("$j jours") }) }
        }
        // Choix de la carte SIM quand il y en a plusieurs avec des mesures
        if (emplacements.size > 1) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            emplacements.forEach { e -> FilterChip(selected = e == slot, onClick = { choisi = e }, label = { Text("SIM ${e + 1}" + (cartes.firstOrNull { it.emplacement == e }?.operateur?.let { " · $it" } ?: "")) }) }
        }
        if (mesures.size < 2) {
            Text(if (mesures.isEmpty()) "Aucune vérification du solde enregistrée pour le moment." else "Une seule vérification enregistrée.",
                fontSize = 13.sp, color = BleuNuit, modifier = Modifier.padding(top = 10.dp))
            Text("La consommation s'estime entre DEUX vérifications du solde : appuyez sur « Solde » dans l'onglet Réseau mobile (ou activez la vérification automatique), puis revenez ici après la prochaine vérification.",
                fontSize = 12.sp, color = Color(0xFF475569), modifier = Modifier.padding(top = 4.dp))
        } else {
            val total = Consommation.total(intervalles)
            Text("$nomCarte · SIM ${slot + 1}", fontSize = 13.sp, color = Color(0xFF475569), modifier = Modifier.padding(top = 10.dp))
            LigneChiffre("Volume restant", restantActuel?.let { Solde.formater(it) } ?: "—")
            LigneChiffre("Consommé sur la période (estimé)", Solde.formater(total))
            LigneChiffre("Moyenne par jour", moyenne?.let { Solde.formater(it) } ?: "—")
            Consommation.joursRestants(restantActuel ?: 0.0, moyenne)?.let { LigneChiffre("Durée estimée du volume restant", String.format(Locale.FRANCE, "%.1f jours", it)) }

            Text("Volume restant", fontSize = 14.sp, color = BleuNuit, modifier = Modifier.padding(top = 14.dp))
            CourbeRestant(mesures)
            Text("Consommation estimée par jour", fontSize = 14.sp, color = BleuNuit, modifier = Modifier.padding(top = 14.dp))
            BarresParJour(parJour)

            Text("Entre deux vérifications", fontSize = 14.sp, color = BleuNuit, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
            intervalles.takeLast(12).reversed().forEach { i ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text("${dateHeure(i.debutMs)} → ${dateHeure(i.finMs)}", Modifier.weight(1f), fontSize = 12.sp, color = Color(0xFF475569))
                    Text(if (i.recharge) "recharge" else "−" + Solde.formater(i.consoMo), fontSize = 12.sp, color = if (i.recharge) Vert else BleuNuit, textAlign = TextAlign.End)
                }
            }
            Text("Estimation : la consommation réelle entre deux vérifications peut varier ; plus les vérifications sont rapprochées, plus elle est précise.",
                fontSize = 11.sp, color = Color(0xFF64748B), modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** Une ligne « libellé … valeur ». */
@Composable
private fun LigneChiffre(libelle: String, valeur: String) =
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) { Text(libelle, Modifier.weight(1f), fontSize = 13.sp, color = Color(0xFF475569)); Text(valeur, fontSize = 13.sp, color = BleuNuit, textAlign = TextAlign.End) }

/** Courbe du volume restant au fil du temps (une mesure = un point). L'axe vertical va de 0 au plus grand volume mesuré. */
@Composable
private fun CourbeRestant(mesures: List<Consommation.Mesure>) {
    val max = (mesures.maxOf { it.restantMo }).coerceAtLeast(1.0)
    val t0 = mesures.first().ms; val t1 = mesures.last().ms.coerceAtLeast(t0 + 1)
    Canvas(Modifier.fillMaxWidth().height(140.dp).padding(top = 6.dp).background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))) {
        val l = size.width; val h = size.height
        val x = { ms: Long -> 8f + (l - 16f) * (ms - t0).toFloat() / (t1 - t0).toFloat() }
        val y = { v: Double -> 8f + (h - 16f) * (1f - (v / max).toFloat()) }
        drawLine(Color(0xFFCBD5E1), Offset(8f, h - 8f), Offset(l - 8f, h - 8f), 2f)            // axe du temps (volume 0)
        val chemin = Path()
        mesures.forEachIndexed { i, m -> if (i == 0) chemin.moveTo(x(m.ms), y(m.restantMo)) else chemin.lineTo(x(m.ms), y(m.restantMo)) }
        drawPath(chemin, Bleu, style = Stroke(width = 4f))
        mesures.forEach { drawCircle(Bleu, 6f, Offset(x(it.ms), y(it.restantMo))) }
    }
    Row(Modifier.fillMaxWidth()) {
        Text(dateHeure(mesures.first().ms), fontSize = 10.sp, color = Color(0xFF64748B), modifier = Modifier.weight(1f))
        Text("max " + Solde.formater(max), fontSize = 10.sp, color = Color(0xFF64748B))
        Text(dateHeure(mesures.last().ms), fontSize = 10.sp, color = Color(0xFF64748B), textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

/** Barres de la consommation estimée par jour ; la valeur du jour le plus fort est écrite sous le graphique. */
@Composable
private fun BarresParJour(parJour: List<Pair<String, Double>>) {
    if (parJour.isEmpty()) { Text("Aucune consommation sur la période.", fontSize = 12.sp, color = Color(0xFF475569)); return }
    val max = parJour.maxOf { it.second }.coerceAtLeast(1.0)
    Canvas(Modifier.fillMaxWidth().height(120.dp).padding(top = 6.dp).background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))) {
        val largeur = (size.width - 16f) / parJour.size
        parJour.forEachIndexed { i, (_, v) ->
            val hauteur = (size.height - 16f) * (v / max).toFloat()
            drawRect(Orange, Offset(8f + i * largeur + largeur * 0.15f, size.height - 8f - hauteur), Size(largeur * 0.7f, hauteur))
        }
    }
    val fort = parJour.maxByOrNull { it.second }!!
    Row(Modifier.fillMaxWidth()) {
        Text(jourCourt(parJour.first().first), fontSize = 10.sp, color = Color(0xFF64748B), modifier = Modifier.weight(1f))
        Text("pic : " + jourCourt(fort.first) + " · " + Solde.formater(fort.second), fontSize = 10.sp, color = Color(0xFF64748B))
        Text(jourCourt(parJour.last().first), fontSize = 10.sp, color = Color(0xFF64748B), textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

// =====================================================================================================================
// 4e onglet : PAIEMENTS lus dans les SMS
// =====================================================================================================================

/**
 * Base des paiements : les SMS de paiement (Orange Money, Moov, Telecel, banques) sont reconnus à leur ARRIVÉE, dans la notification de l'application de SMS
 * (même « Accès aux notifications » que le reste de Concentration : aucune autorisation « SMS », que Google Play Protect bloque sur une application installée
 * à la main), et rangés dans la base locale. Affiche les totaux dépenses / recettes de la période et la liste. Tout reste sur le téléphone.
 * Limite : seuls les SMS reçus APRÈS l'activation de l'accès aux notifications sont capturés (pas l'historique de la boîte de réception).
 */
@Composable
internal fun OngletPaiements(retour: Int, maintenant: Long) {
    val c = LocalContext.current
    var jours by remember { mutableIntStateOf(30) }                              // 30, 90 ou 365 jours
    val acces = remember(retour, maintenant) { accesNotifications(c) }
    val liste = remember(retour, maintenant, jours) { Historique.de(c).paiements(System.currentTimeMillis() - jours * 86_400_000L) }
    val depenses = liste.filter { it.genre == "DEPENSE" }.sumOf { it.montant }
    val recettes = liste.filter { it.genre == "RECETTE" }.sumOf { it.montant }

    Section("Paiements (SMS)", Icons.Filled.Info) {
        Autorisation("Accès aux notifications (lecture des SMS de paiement à leur arrivée)", acces) {
            ouvrir(c, android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30, 90, 365).forEach { j -> FilterChip(selected = jours == j, onClick = { jours = j }, label = { Text(if (j == 365) "1 an" else "$j jours") }) }
        }
        LigneChiffre("Dépenses", francs(depenses))
        LigneChiffre("Recettes", francs(recettes))
        LigneChiffre("Paiements enregistrés", "${liste.size} (base : ${Historique.de(c).nombrePaiements()})")
        if (liste.isEmpty()) Text(if (acces) "Aucun paiement pour le moment : chaque nouveau SMS de paiement reçu sera ajouté ici." else "Activez l'accès aux notifications pour constituer la base des paiements.",
            fontSize = 12.sp, color = Color(0xFF475569), modifier = Modifier.padding(top = 8.dp))
        liste.take(60).forEach { p ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(p.tiers.ifBlank { p.adresse }, fontSize = 14.sp, color = BleuNuit)
                    Text(dateHeure(p.recuLeMs) + " · " + p.adresse + (p.soldeApres?.let { " · solde " + francs(it) } ?: ""), fontSize = 11.sp, color = Color(0xFF64748B))
                    p.reference?.let { Text("Transaction " + it, fontSize = 11.sp, color = Color(0xFF64748B)) }
                }
                Text((if (p.genre == "RECETTE") "+ " else "− ") + francs(p.montant), fontSize = 14.sp, color = if (p.genre == "RECETTE") Vert else Rouge, textAlign = TextAlign.End)
            }
        }
        if (liste.size > 60) Text("Les 60 paiements les plus récents sont affichés.", fontSize = 11.sp, color = Color(0xFF64748B), modifier = Modifier.padding(top = 8.dp))
        Text("Seuls les SMS reconnus comme paiements sont conservés, uniquement sur ce téléphone. Seuls les SMS reçus depuis l'activation de l'accès aux notifications sont lus.", fontSize = 11.sp, color = Color(0xFF64748B), modifier = Modifier.padding(top = 8.dp))
    }
}
