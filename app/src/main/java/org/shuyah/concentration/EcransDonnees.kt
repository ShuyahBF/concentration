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
    var actualisations by remember { mutableIntStateOf(0) }                      // augmente à chaque appui sur « Actualiser » : tout est relu et recalculé
    val mesuresToutes = remember(tickLecture, maintenant, jours, actualisations) { Historique.de(c).mesures(System.currentTimeMillis() - jours * 86_400_000L) }
    val emplacements = mesuresToutes.map { it.emplacement }.distinct().sorted()
    var choisi by remember { mutableIntStateOf(-1) }
    val slot = if (choisi in emplacements) choisi else (cartes.firstOrNull { it.donnees && it.emplacement in emplacements }?.emplacement ?: emplacements.firstOrNull() ?: 0)
    val mesuresBrutes = mesuresToutes.filter { it.emplacement == slot }
    val mesures = Consommation.mesuresFiables(mesuresBrutes)                      // creux isolés (connexion SIM coupée) écartés
    val ignorees = mesuresBrutes.size - mesures.size
    val intervalles = Consommation.intervalles(mesures)
    val parJour = Consommation.parJour(intervalles)
    val moyenne = Consommation.moyenneParJour(intervalles)
    val restantActuel = mesures.lastOrNull()?.restantMo
    val nomCarte = cartes.firstOrNull { it.emplacement == slot }?.operateur ?: Historique.de(c).operateurDe(slot)

    // Après un appui sur « Actualiser » : le résultat est annoncé par un toast (le toast « en cours » est affiché à l'appui)
    LaunchedEffect(actualisations) {
        if (actualisations > 0) Avis.resultat(c, "Consommation actualisée : ${mesures.size} mesure(s) prise(s) en compte" + if (ignorees > 0) ", $ignorees ignorée(s) (creux aberrants)." else ".")
    }

    Section("Consommation d'Internet", Icons.Filled.Info) {
        // Choix de la période (liste finie : trois boutons à choix exclusif)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 30, 90).forEach { j -> FilterChip(selected = jours == j, onClick = { jours = j }, label = { Text("$j jours") }) }
            // Pictogramme seul « Actualiser » (règle pictogrammes : économise la place ; l'info-bulle vocale est donnée par contentDescription) :
            // relit toutes les mesures et refait le graphique, les totaux et la liste
            FilledTonalIconButton({ Avis.enCours(c, "Actualisation de la consommation en cours…"); actualisations++ }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Actualiser la consommation")
            }
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
                    // Une consommation est toujours positive : « 95 Mo » consommés (jamais « −95 Mo ») ; « recharge » en vert
                    Text(if (i.recharge) "recharge" else Solde.formater(i.consoMo), fontSize = 12.sp, color = if (i.recharge) Vert else BleuNuit, textAlign = TextAlign.End)
                }
            }
            if (ignorees > 0) Text("ℹ $ignorees mesure(s) ignorée(s) : volume tombé à zéro puis revenu (connexion de la carte SIM coupée, par exemple en Wi-Fi). Elles ne comptent pas comme consommation.",
                fontSize = 11.sp, color = Color(0xFF64748B), modifier = Modifier.padding(top = 8.dp))
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
