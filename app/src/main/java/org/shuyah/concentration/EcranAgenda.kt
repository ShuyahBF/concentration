package org.shuyah.concentration

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * ONGLET « AGENDA » : lier son Google Agenda (demande d'accès acceptée par l'administrateur de SAWALI, puis page Google), voir ses prochains rendez-vous
 * (tableau triable), en créer par note vocale ou texte avec RELECTURE et CONFIRMATION avant création, supprimer, délier. Voir Agenda.kt.
 * Chaque action porte un pictogramme et une légende ; chaque actualisation annonce « en cours » puis son résultat (règles du propriétaire).
 */
@Composable
internal fun OngletAgenda() {
    val c = LocalContext.current
    val portee = rememberCoroutineScope()
    var etat by remember { mutableStateOf<Agenda.Etat?>(null) }
    var evenements by remember { mutableStateOf<List<Agenda.Evenement>>(emptyList()) }
    var occupe by remember { mutableStateOf(false) }
    var gmail by remember { mutableStateOf("") }
    var texte by remember { mutableStateOf("") }
    var prop by remember { mutableStateOf<Agenda.Proposition?>(null) }
    var titre by remember { mutableStateOf("") }; var jour by remember { mutableStateOf("") }; var heure by remember { mutableStateOf("") }; var minutes by remember { mutableIntStateOf(60) }
    var offset by remember { mutableStateOf("+00:00") }
    var tri by remember { mutableStateOf<Pair<String, Boolean>?>(Pair("quand", true)) }     // (colonne, croissant) ; null = ordre d'origine

    /** Appel réseau hors écran, puis retour sur l'écran avec la réponse. */
    fun appel(chemin: String, corps: String = "{}", suite: (Agenda.Reponse) -> Unit) {
        occupe = true
        portee.launch { val r = withContext(Dispatchers.IO) { Agenda.appeler(c, chemin, corps) }; occupe = false; suite(r) }
    }
    fun actualiser(annonce: Boolean) {
        if (annonce) Avis.enCours(c, "Actualisation de l'agenda en cours…")
        appel("etat") { r ->
            val e = Agenda.etat(r)
            if (e == null) { Avis.resultat(c, "Agenda indisponible : " + r.message()); return@appel }
            etat = e
            if (e.lie) appel("evenements") { re -> evenements = Agenda.evenements(re); if (annonce) Avis.resultat(c, "Agenda actualisé : ${evenements.size} rendez-vous à venir.") }
            else { evenements = emptyList(); if (annonce) Avis.resultat(c, "Agenda non lié.") }
        }
    }
    LaunchedEffect(Unit) { actualiser(false) }

    // Dictée : reconnaissance vocale du téléphone (fenêtre système), texte ajouté à la note
    val dictee = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val dit = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!dit.isNullOrBlank()) { texte = if (texte.isBlank()) dit else "$texte $dit"; Avis.resultat(c, "Note dictée : relisez-la puis touchez « Comprendre ».") }
    }

    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Section("Mon agenda Google", Icons.Filled.CalendarMonth) {
            val e = etat
            when {
                e == null -> Text("Lecture de l'état…", fontSize = 13.sp)
                !e.lie -> {
                    Text("Votre agenda Google n'est pas encore lié. Vous seul voyez votre agenda.", fontSize = 13.sp)
                    if (e.approbationRequise && e.demande != "accepte") {
                        // Étapes 1 et 2 : demande d'accès avec le compte Google, acceptée par l'administrateur de SAWALI
                        if (e.demande == "attente") Text("Demande envoyée pour ${e.gmail} : en attente de l'administrateur. Touchez « Actualiser » pour voir la réponse.", fontSize = 13.sp, color = Orange)
                        if (e.demande == "refuse") Text("Demande refusée pour ${e.gmail}. Vous pouvez en envoyer une autre avec un autre compte.", fontSize = 13.sp, color = Rouge)
                        OutlinedTextField(gmail, { gmail = it }, label = { Text("Votre compte Google (Gmail)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                        Button({
                            if (!Agenda.gmailValide(gmail)) { Avis.resultat(c, "Indiquez l'adresse e-mail de votre compte Google."); return@Button }
                            Avis.enCours(c, "Envoi de votre demande en cours…")
                            appel("demander", Agenda.corpsDemande(gmail, "Concentration · " + android.os.Build.MODEL)) { r -> Avis.resultat(c, if (r.ok) "Demande envoyée : l'administrateur doit l'accepter." else "Demande impossible : " + r.message()); actualiser(false) }
                        }, enabled = !occupe) { Icon(Icons.Filled.Send, null); Spacer(Modifier.width(6.dp)); Text("Demander l'accès") }
                        Text("L'administrateur vérifie votre compte avant que vous puissiez lier votre agenda : vous êtes responsable de l'adresse indiquée.", fontSize = 11.sp, color = Color(0xFF64748B))
                    } else {
                        // Consigne AVANT la liaison : sur l'écran Google, la case « Agenda » est décochée par défaut tant que Google n'a pas validé l'application
                        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small) {
                            Text("⚠️ Avant de continuer : une page Google va s'ouvrir. Vous y verrez la case « Afficher et modifier les événements de tous vos agendas ». COCHEZ cette case, puis touchez « Continuer ». Sans elle, votre agenda ne pourra pas être lié.",
                                fontSize = 13.sp, modifier = Modifier.padding(10.dp))
                        }
                        Button({
                            Avis.enCours(c, "Ouverture de la page d'autorisation Google…")
                            appel("lier") { r ->
                                val url = Agenda.urlLiaison(r)
                                if (url == null) Avis.resultat(c, "Liaison impossible : " + r.message())
                                else { c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); Avis.resultat(c, "Acceptez l'accès sur la page Google, puis revenez ici et touchez « Actualiser ».") }
                            }
                        }, enabled = !occupe) { Icon(Icons.Filled.Link, null); Spacer(Modifier.width(6.dp)); Text("Lier mon Google Agenda" + (if (e.gmail.isNotBlank()) " (${e.gmail})" else "")) }
                    }
                    OutlinedButton({ actualiser(true) }, enabled = !occupe) { Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Actualiser") }
                }
                else -> {
                    Text("Agenda lié : ${e.email}", fontSize = 14.sp, color = Vert)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ actualiser(true) }, enabled = !occupe) { Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Actualiser") }
                        OutlinedButton({
                            Avis.enCours(c, "Retrait de la liaison en cours…")
                            appel("delier") { r -> Avis.resultat(c, if (r.ok) "Agenda délié." else "Retrait impossible : " + r.message()); actualiser(false) }
                        }, enabled = !occupe) { Icon(Icons.Filled.LinkOff, null); Spacer(Modifier.width(6.dp)); Text("Délier") }
                    }
                }
            }
        }

        // Politique de confidentialité propre à Concentration (page publique de SAWALI, citée dans la validation Google)
        TextButton({ c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sawalismartsystems.com/privacy/concentration")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) {
            Icon(Icons.Filled.PrivacyTip, null); Spacer(Modifier.width(6.dp)); Text("Politique de confidentialité de Concentration")
        }

        if (etat?.lie == true) {
            Section("Nouveau rendez-vous", Icons.Filled.Mic) {
                OutlinedTextField(texte, { texte = it }, label = { Text("Ex. : samedi 8h15 rdv chez le dentiste") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({
                        try {
                            dictee.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR").putExtra(RecognizerIntent.EXTRA_PROMPT, "Dites votre rendez-vous"))
                        } catch (x: ActivityNotFoundException) { Avis.resultat(c, "Reconnaissance vocale indisponible sur ce téléphone : tapez votre note.") }
                    }) { Icon(Icons.Filled.Mic, null); Spacer(Modifier.width(6.dp)); Text("Dicter") }
                    Button({
                        Avis.enCours(c, "Analyse de la note en cours…")
                        appel("analyser", Agenda.corpsAnalyse(texte)) { r ->
                            val p = Agenda.proposition(r)
                            if (p == null) Avis.resultat(c, "Analyse impossible : " + r.message())
                            else {
                                prop = p; titre = p.titre; jour = p.debut.take(10); heure = p.debut.substring(11, 16); minutes = p.duree; offset = p.debut.substring(19)
                                Avis.resultat(c, if (p.manque.isEmpty()) "Relisez le rendez-vous puis enregistrez-le." else "À compléter : ${p.manque.joinToString(", ")}.")
                            }
                        }
                    }, enabled = texte.isNotBlank() && !occupe) { Icon(Icons.Filled.AutoAwesome, null); Spacer(Modifier.width(6.dp)); Text("Comprendre") }
                }
                val p = prop
                if (p != null) {
                    // Rien n'est créé sans relecture : l'utilisateur voit le jour, l'heure et le titre, les corrige, puis confirme
                    HorizontalDivider()
                    Text("📌 " + Agenda.dateAffichee("${jour}T$heure:00"), fontSize = 15.sp, color = Bleu)
                    if (p.manque.isNotEmpty()) Text("⚠️ À compléter : ${p.manque.joinToString(", ")}.", fontSize = 13.sp, color = Orange)
                    OutlinedTextField(titre, { titre = it }, label = { Text("Titre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({
                            val cal = Calendar.getInstance()
                            DatePickerDialog(c, { _, a, m, j -> jour = String.format("%04d-%02d-%02d", a, m + 1, j); prop = p.copy(manque = prop!!.manque - "date") }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                        }) { Icon(Icons.Filled.CalendarMonth, null); Spacer(Modifier.width(4.dp)); Text(jour) }
                        OutlinedButton({
                            TimePickerDialog(c, { _, h, m -> heure = String.format("%02d:%02d", h, m); prop = p.copy(manque = prop!!.manque - "heure") }, heure.take(2).toIntOrNull() ?: 9, heure.drop(3).take(2).toIntOrNull() ?: 0, true).show()
                        }) { Icon(Icons.Filled.Schedule, null); Spacer(Modifier.width(4.dp)); Text(heure) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(30, 60, 90, 120).forEach { m -> FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text("$m min") }) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({
                            val debut = "${jour}T$heure:00$offset"
                            Avis.enCours(c, "Enregistrement dans votre agenda en cours…")
                            appel("creer", Agenda.corpsCreation(titre, debut, Agenda.finIso(debut, minutes))) { r ->
                                Avis.resultat(c, if (r.ok) "Rendez-vous enregistré : $titre" else "Enregistrement impossible : " + r.message())
                                if (r.ok) { prop = null; texte = ""; actualiser(false) }
                            }
                        }, enabled = titre.isNotBlank() && p.manque.isEmpty() && !occupe) { Icon(Icons.Filled.Check, null); Spacer(Modifier.width(6.dp)); Text("Enregistrer") }
                        OutlinedButton({ prop = null }) { Icon(Icons.Filled.Close, null); Spacer(Modifier.width(6.dp)); Text("Annuler") }
                    }
                }
            }

            Section("Prochains rendez-vous (14 jours)", Icons.Filled.Event) {
                if (evenements.isEmpty()) Text("Aucun rendez-vous à venir.", fontSize = 13.sp)
                else {
                    // Règle 19 : colonnes triables (1er clic croissant, 2e décroissant, 3e ordre d'origine) ; valeurs vides en dernier
                    fun bascule(col: String) { tri = when { tri?.first != col -> Pair(col, true); tri!!.second -> Pair(col, false); else -> null } }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf("quand" to "Quand", "titre" to "Titre", "lieu" to "Lieu").forEach { (col, nom) ->
                            val actif = tri?.first == col
                            Text(nom + if (actif) (if (tri!!.second) " ▲" else " ▼") else " ⇅", fontSize = 12.sp, color = if (actif) Orange else Bleu, modifier = Modifier.clickable { bascule(col) })
                        }
                    }
                    val val_ = { ev: Agenda.Evenement -> when (tri?.first) { "titre" -> ev.titre.lowercase(); "lieu" -> ev.lieu.lowercase(); else -> ev.debut } }
                    val triees = tri.let { t -> if (t == null) evenements else evenements.filter { val_(it).isNotEmpty() }.let { l -> if (t.second) l.sortedBy(val_) else l.sortedByDescending(val_) } + evenements.filter { val_(it).isEmpty() } }
                    // Règle 24 : plus de 30 rendez-vous → la liste défile dans son cadre
                    ListeBornee(triees.size) {
                        triees.forEach { ev ->
                            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(ev.titre, fontSize = 14.sp, color = BleuNuit)
                                    Text(Agenda.dateAffichee(ev.debut) + if (ev.lieu.isNotBlank()) " · ${ev.lieu}" else "", fontSize = 11.sp, color = Bleu)
                                }
                                IconButton({
                                    Avis.enCours(c, "Suppression en cours…")
                                    appel("supprimer", Agenda.corpsSuppression(ev.id)) { r -> Avis.resultat(c, if (r.ok) "Rendez-vous supprimé." else "Suppression impossible : " + r.message()); actualiser(false) }
                                }, enabled = !occupe) { Icon(Icons.Filled.Delete, contentDescription = "Supprimer ${ev.titre}", tint = Rouge) }
                            }
                        }
                    }
                }
            }
        }
    }
}
