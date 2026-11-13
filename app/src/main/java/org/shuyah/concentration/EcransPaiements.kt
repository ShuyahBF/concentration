package org.shuyah.concentration

import android.app.DatePickerDialog
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// =====================================================================================================================
// 4e onglet : PAIEMENTS — base des SMS de paiement, avec statistiques interactives (même esprit que l'Historique d'auRor)
// =====================================================================================================================

private val GRIS = Color(0xFF475569)
private val GRIS_CLAIR = Color(0xFF64748B)
private val FOND_GRAPHIQUE = Color(0xFFF8FAFC)
private val PALETTE = listOf(Color(0xFF0E6BA8), Color(0xFFF6A35B), Color(0xFF2E9E6B), Color(0xFFD64545), Color(0xFF7C5CC4), Color(0xFF0FA3B1), Color(0xFF94A3B8))
private const val TAILLE_PAGE = 20

private fun francs(v: Long) = String.format(Locale.FRANCE, "%,d", v).replace(' ', ' ').replace(' ', ' ') + " FCFA"
private fun dateHeure(ms: Long) = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(ms)
private fun jour(ms: Long) = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(ms)
/** Libellé court d'une barre : « 13/11 » pour un jour, « nov. 26 » pour un mois. */
private fun libelleBarre(cle: String) = if (cle.length > 7) cle.substring(8, 10) + "/" + cle.substring(5, 7) else SimpleDateFormat("MMM yy", Locale.FRANCE).format(StatsPaiements.intervalleDe(cle).first)

/**
 * ONGLET PAIEMENTS. Trois façons d'alimenter la base (sans doublon) :
 *  1. les SMS de paiement qui ARRIVENT (lus dans la notification, grâce à « l'accès aux notifications ») ;
 *  2. TOUS LES SMS PASSÉS lus directement dans la boîte de réception (version « sms » de l'application) ;
 *  3. TOUS LES SMS PASSÉS importés depuis un fichier de sauvegarde (« SMS Backup & Restore », version « standard »).
 * Ensuite on filtre (période, opérateur, sens, texte) et tout l'écran se recalcule : chiffres clés, opérateurs, histogramme, classements, camembert, liste.
 * On peut TOUCHER un opérateur, une barre de l'histogramme ou un bénéficiaire du classement pour filtrer dessus.
 */
@Composable
internal fun OngletPaiements(retour: Int, maintenant: Long) {
    val c = LocalContext.current
    val portee = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }                                         // augmente après un import, une suppression ou « Actualiser »
    val toutes = remember(retour, version) { Historique.de(c).tousPaiements() }
    var filtre by remember { mutableStateOf(StatsPaiements.Filtre(periode = "tout")) }
    var recherche by remember { mutableStateOf("") }
    var parMois by remember { mutableStateOf(false) }                                        // histogramme : par jour (faux) ou par mois (vrai)
    var enNombre by remember { mutableStateOf(false) }                                       // histogramme : en montant (faux) ou en nombre de paiements (vrai)
    var sens by remember { mutableStateOf("DEPENSE") }                                       // sens des classements et du camembert
    var affiches by remember { mutableIntStateOf(TAILLE_PAGE) }
    var ouvert by remember { mutableStateOf<Paiements.Paiement?>(null) }                     // paiement dont on lit le détail
    var barreChoisie by remember { mutableStateOf<String?>(null) }

    // Import des SMS passés : état affiché pendant et après la lecture
    var occupe by remember { mutableStateOf(false) }
    var progres by remember { mutableStateOf("") }
    var bilan by remember { mutableStateOf<String?>(null) }
    val acces = remember(retour, maintenant) { accesNotifications(c) }

    /** Lance une lecture dans un fil d'arrière-plan et affiche la progression puis le bilan. */
    fun lancer(lecture: () -> ImportSms.Bilan?) {
        occupe = true; bilan = null; progres = "Lecture en cours…"
        portee.launch {
            val resultat = withContext(Dispatchers.IO) { try { lecture() } catch (e: Exception) { null } }
            occupe = false
            bilan = if (resultat == null) "Lecture impossible : fichier illisible ou autorisation refusée."
            else "${resultat.lus} SMS lus · ${resultat.paiements} paiements reconnus · ${resultat.ajoutes} nouveaux ajoutés" + if (resultat.paiements > resultat.ajoutes) " (les autres étaient déjà dans la base)" else ""
            version++
            if (resultat != null) EnvoiSawali.envoyerEnArrierePlan(c)                                  // les nouveaux paiements partent vers SAWALI (si configuré)
        }
    }
    val choixFichier = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lancer { ImportSms.importerFichier(c, uri) { lus, pay -> progres = "$lus SMS lus · $pay paiements reconnus…" } }
    }
    val autorisationSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) lancer { ImportSms.lireBoite(c) { lus, pay -> progres = "$lus SMS lus · $pay paiements reconnus…" } } else bilan = "Autorisation refusée : voir la remarque sur les « paramètres restreints » ci-dessous."
    }

    // Recherche : appliquée au filtre quand on valide le clavier ou qu'on touche « Appliquer »
    val filtrees = remember(toutes, filtre) { StatsPaiements.filtrer(toutes, filtre, System.currentTimeMillis()) }
    val tot = remember(filtrees) { StatsPaiements.totaux(filtrees) }
    val operateurs = remember(filtrees) { StatsPaiements.parOperateur(filtrees) }
    val barres = remember(filtrees, parMois) { StatsPaiements.parPeriode(filtrees, parMois).takeLast(if (parMois) 24 else 40) }
    val tiers = remember(filtrees, sens) { StatsPaiements.topTiers(filtrees, sens, 10) }
    val tops = remember(filtrees, sens) { StatsPaiements.topMontants(filtrees, sens, 10) }
    val totalSens = if (sens == "DEPENSE") tot.depenses else tot.recettes
    val filtreActif = filtre != StatsPaiements.Filtre(periode = "tout")

    // ------------------------------------------------------------------ 1. Alimenter la base
    Section("Récupérer mes SMS de paiement", Icons.Filled.Sms) {
        Autorisation("Accès aux notifications (lecture des nouveaux SMS de paiement)", acces) { ouvrir(c, Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        Text("Base : ${toutes.size} paiement(s) enregistré(s). Seuls les SMS de paiement sont conservés, uniquement sur ce téléphone ; les autres SMS sont lus puis oubliés.", fontSize = 12.sp, color = GRIS, modifier = Modifier.padding(top = 8.dp))
        if (ImportSms.lectureDirecteDisponible()) {
            Button({ if (ImportSms.autorisationAccordee(c)) lancer { ImportSms.lireBoite(c) { lus, pay -> progres = "$lus SMS lus · $pay paiements reconnus…" } } else autorisationSms.launch("android.permission.READ_SMS") },
                enabled = !occupe, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Icon(Icons.Filled.Inbox, null); Spacer(Modifier.width(8.dp)); Text("Lire tous les SMS de ma boîte") }
            Text("Si Android grise l'autorisation (« paramètre restreint ») : Réglages → Applications → Concentration → menu ⋮ → « Autoriser les paramètres restreints », puis recommencez.", fontSize = 11.sp, color = GRIS_CLAIR, modifier = Modifier.padding(top = 4.dp))
        }
        Button({ choixFichier.launch(arrayOf("text/xml", "application/xml", "text/plain", "*/*")) }, enabled = !occupe, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Icon(Icons.Filled.UploadFile, null); Spacer(Modifier.width(8.dp)); Text("Importer une sauvegarde de SMS (XML)")
        }
        Text("Sauvegarde : installez l'application gratuite « SMS Backup & Restore », appuyez sur « Sauvegarder maintenant » (SMS seulement, sur le téléphone), puis choisissez ici le fichier .xml créé.", fontSize = 11.sp, color = GRIS_CLAIR, modifier = Modifier.padding(top = 4.dp))
        if (occupe) { LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp)); Text(progres, fontSize = 12.sp, color = GRIS, modifier = Modifier.padding(top = 4.dp)) }
        bilan?.let { Text(it, fontSize = 13.sp, color = BleuNuit, modifier = Modifier.padding(top = 8.dp)) }
    }

    // ------------------------------------------------------------------ 1 bis. Envoi à SAWALI (menu « Transactions SMS » de SAWALI)
    var urlSawali by remember { mutableStateOf(Reglages.sawaliUrl(c)) }
    var sourceSawali by remember { mutableStateOf(Reglages.sawaliSource(c)) }
    var cleSawali by remember { mutableStateOf(Reglages.sawaliCle(c)) }
    var voirCle by remember { mutableStateOf(false) }
    var envoiAuto by remember { mutableStateOf(Reglages.sawaliAuto(c)) }
    var envoiEnCours by remember { mutableStateOf(false) }
    var messageEnvoi by remember { mutableStateOf<String?>(null) }
    val enAttente = remember(version, envoiEnCours) { Historique.de(c).nombreNonEnvoyes() }
    Section("Envoi à SAWALI", Icons.Filled.CloudUpload) {
        Text("SAWALI affiche vos transactions SMS (menu « Transactions SMS », administrateur) avec les mêmes filtres, et peut associer un numéro à une plateforme (ALBARKA…). Dans SAWALI : Transactions SMS → Téléphones source → Ajouter, puis saisissez ici l'identifiant et la clé.", fontSize = 12.sp, color = GRIS)
        OutlinedTextField(urlSawali, { urlSawali = it }, label = { Text("Adresse de SAWALI (laisser vide : adresse habituelle)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        OutlinedTextField(sourceSawali, { sourceSawali = it }, label = { Text("Identifiant du téléphone") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        OutlinedTextField(cleSawali, { cleSawali = it }, label = { Text("Clé du téléphone") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            visualTransformation = if (voirCle) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation())
        Text(if (voirCle) "Masquer la clé" else "Afficher la clé", fontSize = 13.sp, color = Bleu, modifier = Modifier.padding(top = 2.dp).clickable { voirCle = !voirCle })
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Switch(envoiAuto, { envoiAuto = it; Reglages.sawaliAuto(c, it) }); Spacer(Modifier.width(8.dp))
            Text("Envoyer automatiquement chaque nouveau paiement", fontSize = 13.sp, color = BleuNuit)
        }
        Text("$enAttente paiement(s) en attente d'envoi.", fontSize = 13.sp, color = BleuNuit, modifier = Modifier.padding(top = 6.dp))
        Button({
            Reglages.sawali(c, urlSawali, sourceSawali, cleSawali)
            envoiEnCours = true; messageEnvoi = null
            portee.launch { val b = withContext(Dispatchers.IO) { EnvoiSawali.envoyer(c) }; envoiEnCours = false; messageEnvoi = b.message + if (b.restants > 0) " ${b.restants} restant(s)." else ""; version++ }
        }, enabled = !envoiEnCours && EnvoiSawali.configure(sourceSawali, cleSawali), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Icon(Icons.Filled.CloudUpload, null); Spacer(Modifier.width(8.dp)); Text("Enregistrer et envoyer maintenant") }
        if (envoiEnCours) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        messageEnvoi?.let { Text(it, fontSize = 13.sp, color = BleuNuit, modifier = Modifier.padding(top = 6.dp)) }
    }

    if (toutes.isEmpty()) {
        Section("Statistiques", Icons.Filled.Info) { Text("Aucun paiement pour le moment : importez vos SMS passés ci-dessus, ou attendez le prochain SMS de paiement.", fontSize = 13.sp, color = GRIS) }
        return
    }

    // ------------------------------------------------------------------ 2. Filtres
    Section("Filtres", Icons.Filled.FilterList) {
        Liste("Période", filtre.periode, StatsPaiements.PERIODES) { filtre = filtre.copy(periode = it); affiches = TAILLE_PAGE }
        if (filtre.periode == "perso") {
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoutonDate("Du", filtre.duMs, Modifier.weight(1f)) { filtre = filtre.copy(duMs = it) }
                BoutonDate("Au", filtre.auMs, Modifier.weight(1f)) { filtre = filtre.copy(auMs = it) }
            }
        }
        Liste("Opérateur", filtre.operateur, listOf("" to "Tous les opérateurs") + StatsPaiements.OPERATEURS.map { it to it }) { filtre = filtre.copy(operateur = it); affiches = TAILLE_PAGE }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("TOUS" to "Tous", "DEPENSE" to "Dépenses", "RECETTE" to "Recettes").forEach { (v, n) -> FilterChip(selected = filtre.genre == v, onClick = { filtre = filtre.copy(genre = v); affiches = TAILLE_PAGE }, label = { Text(n) }) }
        }
        OutlinedTextField(recherche, { recherche = it; filtre = filtre.copy(q = it); affiches = TAILLE_PAGE }, label = { Text("Bénéficiaire, numéro de transaction, texte du SMS") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ version++ }) { Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(4.dp)); Text("Actualiser") }
            if (filtreActif) OutlinedButton({ filtre = StatsPaiements.Filtre(periode = "tout"); recherche = ""; barreChoisie = null; affiches = TAILLE_PAGE }) { Text("Réinitialiser") }
        }
    }

    // ------------------------------------------------------------------ 3. Chiffres clés
    Section("Chiffres clés", Icons.Filled.Info) {
        if (filtrees.isEmpty()) Text("Aucun paiement pour ces critères.", fontSize = 13.sp, color = GRIS)
        else {
            Chiffre("Paiements", "${tot.nombre}  (${tot.nbDepenses} dépenses · ${tot.nbRecettes} recettes)")
            Chiffre("Dépenses", francs(tot.depenses), Rouge)
            Chiffre("Recettes", francs(tot.recettes), Vert)
            Chiffre("Solde net (recettes − dépenses)", (if (tot.net >= 0) "+ " else "− ") + francs(Math.abs(tot.net)), if (tot.net >= 0) Vert else Rouge)
            Chiffre("Dépense moyenne", francs(tot.depenseMoyenne))
            Chiffre("Plus grosse dépense", francs(tot.plusGrosse))
        }
    }

    if (filtrees.isEmpty()) return

    // ------------------------------------------------------------------ 4. Une carte par opérateur (toucher = filtrer sur cet opérateur)
    Section("Par opérateur", Icons.Filled.CellTower) {
        val maxNombre = operateurs.maxOf { it.nombre }.coerceAtLeast(1)
        operateurs.forEachIndexed { i, o ->
            Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).background(if (filtre.operateur == o.operateur) Color(0xFFFFF1E0) else FOND_GRAPHIQUE)
                .clickable { filtre = filtre.copy(operateur = if (filtre.operateur == o.operateur) "" else o.operateur); affiches = TAILLE_PAGE }.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(PALETTE[i % PALETTE.size]))
                    Text(o.operateur, Modifier.weight(1f).padding(start = 8.dp), fontSize = 14.sp, color = BleuNuit)
                    Text("${o.nombre} paiement${if (o.nombre > 1) "s" else ""}", fontSize = 13.sp, color = BleuNuit, textAlign = TextAlign.End)
                }
                Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFE2E8F0))) {
                    Box(Modifier.fillMaxWidth(o.nombre.toFloat() / maxNombre).fillMaxHeight().background(PALETTE[i % PALETTE.size]))
                }
                Text("Dépenses " + francs(o.depenses) + " · Recettes " + francs(o.recettes), fontSize = 11.sp, color = GRIS, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Text("Touchez un opérateur pour ne garder que ses paiements.", fontSize = 11.sp, color = GRIS_CLAIR, modifier = Modifier.padding(top = 6.dp))
    }

    // ------------------------------------------------------------------ 5. Histogramme par jour ou par mois (toucher une barre = filtrer sur ce jour / ce mois)
    Section("Activité", Icons.Filled.BarChart) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !parMois, onClick = { parMois = false; barreChoisie = null }, label = { Text("Par jour") })
            FilterChip(selected = parMois, onClick = { parMois = true; barreChoisie = null }, label = { Text("Par mois") })
            FilterChip(selected = !enNombre, onClick = { enNombre = false }, label = { Text("Montant") })
            FilterChip(selected = enNombre, onClick = { enNombre = true }, label = { Text("Nombre") })
        }
        Histogramme(barres, enNombre, barreChoisie) { cle ->
            barreChoisie = cle
            val (du, fin) = StatsPaiements.intervalleDe(cle)
            filtre = filtre.copy(periode = "perso", duMs = du, auMs = fin - 1); affiches = TAILLE_PAGE         // « au » est inclus : la dernière milliseconde du jour / du mois
        }
        barres.firstOrNull { it.cle == barreChoisie }?.let { b ->
            Text(libelleBarre(b.cle) + " : dépenses " + francs(b.depenses) + " · recettes " + francs(b.recettes) + " · ${b.nombre} paiement${if (b.nombre > 1) "s" else ""}", fontSize = 12.sp, color = BleuNuit, modifier = Modifier.padding(top = 6.dp))
        }
        Row(Modifier.padding(top = 4.dp)) {
            Box(Modifier.size(10.dp).background(Rouge)); Text(" dépenses   ", fontSize = 11.sp, color = GRIS); Box(Modifier.size(10.dp).background(Vert)); Text(" recettes   ", fontSize = 11.sp, color = GRIS)
            Text("Touchez une barre pour filtrer sur ce " + (if (parMois) "mois." else "jour."), fontSize = 11.sp, color = GRIS_CLAIR)
        }
    }

    // ------------------------------------------------------------------ 6. Classements et camembert (sens au choix)
    Section("Classements", Icons.Filled.EmojiEvents) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = sens == "DEPENSE", onClick = { sens = "DEPENSE" }, label = { Text("Dépenses") })
            FilterChip(selected = sens == "RECETTE", onClick = { sens = "RECETTE" }, label = { Text("Recettes") })
        }
        if (tiers.isEmpty()) Text("Aucun paiement de ce sens.", fontSize = 13.sp, color = GRIS, modifier = Modifier.padding(top = 8.dp))
        else {
            Text(if (sens == "DEPENSE") "Où va l'argent (part de chaque bénéficiaire)" else "D'où vient l'argent (part de chaque expéditeur)", fontSize = 13.sp, color = BleuNuit, modifier = Modifier.padding(top = 10.dp))
            Camembert(StatsPaiements.parts(tiers, totalSens), totalSens)
            Text("Top 10 des " + (if (sens == "DEPENSE") "bénéficiaires" else "expéditeurs") + " (touchez pour filtrer)", fontSize = 13.sp, color = BleuNuit, modifier = Modifier.padding(top = 12.dp))
            tiers.forEachIndexed { i, t ->
                Row(Modifier.fillMaxWidth().clickable { recherche = t.nom; filtre = filtre.copy(q = t.nom); affiches = TAILLE_PAGE }.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                    Text("${i + 1}", Modifier.width(24.dp), fontSize = 13.sp, color = GRIS_CLAIR)
                    Column(Modifier.weight(1f)) { Text(t.nom, fontSize = 14.sp, color = BleuNuit); Text("${t.nombre} paiement${if (t.nombre > 1) "s" else ""}", fontSize = 11.sp, color = GRIS_CLAIR) }
                    Text(francs(t.total), fontSize = 14.sp, color = BleuNuit, textAlign = TextAlign.End)                  // les montants sont alignés à droite
                }
            }
            Text("Top 10 des plus gros montants", fontSize = 13.sp, color = BleuNuit, modifier = Modifier.padding(top = 14.dp))
            tops.forEachIndexed { i, p ->
                Row(Modifier.fillMaxWidth().clickable { ouvert = p }.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                    Text("${i + 1}", Modifier.width(24.dp), fontSize = 13.sp, color = GRIS_CLAIR)
                    Column(Modifier.weight(1f)) { Text(p.tiers.ifBlank { p.adresse }, fontSize = 14.sp, color = BleuNuit); Text(StatsPaiements.operateurDe(p) + " · " + jour(p.recuLeMs), fontSize = 11.sp, color = GRIS_CLAIR) }
                    Text(francs(p.montant), fontSize = 14.sp, color = BleuNuit, textAlign = TextAlign.End)
                }
            }
        }
    }

    // ------------------------------------------------------------------ 7. Liste des paiements (toucher = lire le SMS)
    Section("Paiements (${filtrees.size})", Icons.Filled.Receipt) {
        OutlinedButton({
            val envoi = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "Paiements Concentration").putExtra(Intent.EXTRA_TEXT, StatsPaiements.csv(filtrees))
            c.startActivity(Intent.createChooser(envoi, "Partager les paiements affichés (CSV)").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }) { Icon(Icons.Filled.Share, null); Spacer(Modifier.width(6.dp)); Text("Partager en CSV (Excel)") }
        filtrees.take(affiches).forEach { p ->
            Row(Modifier.fillMaxWidth().clickable { ouvert = p }.padding(top = 10.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(p.tiers.ifBlank { p.adresse }, fontSize = 14.sp, color = BleuNuit)
                    Text(dateHeure(p.recuLeMs) + " · " + StatsPaiements.operateurDe(p) + (p.soldeApres?.let { " · solde " + francs(it) } ?: ""), fontSize = 11.sp, color = GRIS_CLAIR)
                }
                Text((if (p.genre == "RECETTE") "+ " else "− ") + francs(p.montant), fontSize = 14.sp, color = if (p.genre == "RECETTE") Vert else Rouge, textAlign = TextAlign.End)
            }
        }
        if (filtrees.size > affiches) OutlinedButton({ affiches += TAILLE_PAGE }, Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("Afficher plus (${filtrees.size - affiches} restants)") }
    }

    // ------------------------------------------------------------------ Détail d'un paiement : le SMS en entier, suppression possible
    ouvert?.let { p ->
        AlertDialog(onDismissRequest = { ouvert = null }, title = { Text(p.tiers.ifBlank { p.adresse }) },
            text = {
                Column {
                    Text((if (p.genre == "RECETTE") "Recette " else "Dépense ") + francs(p.montant), fontSize = 16.sp, color = if (p.genre == "RECETTE") Vert else Rouge)
                    Text(dateHeure(p.recuLeMs) + " · " + StatsPaiements.operateurDe(p), fontSize = 12.sp, color = GRIS)
                    p.soldeApres?.let { Text("Solde après : " + francs(it), fontSize = 12.sp, color = GRIS) }
                    p.reference?.let { Text("Transaction : $it", fontSize = 12.sp, color = GRIS) }
                    Text(p.corps, fontSize = 12.sp, color = BleuNuit, modifier = Modifier.padding(top = 8.dp).background(FOND_GRAPHIQUE).padding(8.dp))
                    Text("Les SMS lus ne peuvent pas être supprimés.", fontSize = 11.sp, color = GRIS_CLAIR, modifier = Modifier.padding(top = 6.dp))
                }
            },
            confirmButton = { TextButton({ ouvert = null }) { Text("Fermer") } },
            // Un SMS lu ne peut pas être supprimé : le bouton reste visible mais grisé (demande du propriétaire du 14/11/2026)
            dismissButton = { TextButton({}, enabled = false) { Text("Supprimer (impossible)") } })
    }
}

/** Une ligne « libellé … valeur » des chiffres clés (la valeur est alignée à droite). */
@Composable
private fun Chiffre(libelle: String, valeur: String, couleur: Color = BleuNuit) =
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) { Text(libelle, Modifier.weight(1f), fontSize = 13.sp, color = GRIS); Text(valeur, fontSize = 14.sp, color = couleur, textAlign = TextAlign.End) }

/** Liste déroulante : le choix actuel est affiché ; un appui ouvre les options (liste finie = liste déroulante). */
@Composable
private fun Liste(titre: String, valeur: String, options: List<Pair<String, String>>, surChoix: (String) -> Unit) {
    var ouverte by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(titre, fontSize = 11.sp, color = GRIS_CLAIR)
        Box {
            OutlinedButton({ ouverte = true }, Modifier.fillMaxWidth()) { Text(options.firstOrNull { it.first == valeur }?.second ?: valeur, Modifier.weight(1f)); Icon(Icons.Filled.ArrowDropDown, null) }
            DropdownMenu(ouverte, { ouverte = false }) { options.forEach { (v, n) -> DropdownMenuItem(text = { Text(n) }, onClick = { ouverte = false; surChoix(v) }) } }
        }
    }
}

/** Bouton qui ouvre le sélecteur de date du système ; affiche la date choisie (ou le libellé si aucune). */
@Composable
private fun BoutonDate(libelle: String, ms: Long?, modifier: Modifier, surChoix: (Long) -> Unit) {
    val c = LocalContext.current
    OutlinedButton({
        val cal = Calendar.getInstance().apply { if (ms != null) timeInMillis = ms }
        DatePickerDialog(c, { _, a, m, j -> surChoix(Calendar.getInstance().apply { clear(); set(a, m, j, 12, 0, 0) }.timeInMillis) }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }, modifier) { Text(if (ms == null) libelle else "$libelle ${jour(ms)}", fontSize = 13.sp) }
}

/** Histogramme : pour chaque jour (ou mois) une barre rouge (dépenses) et une barre verte (recettes), ou une barre bleue du nombre de paiements. Toucher une barre la choisit. */
@Composable
private fun Histogramme(barres: List<StatsPaiements.Barre>, enNombre: Boolean, choisie: String?, surChoix: (String) -> Unit) {
    if (barres.isEmpty()) return
    val max = (if (enNombre) barres.maxOf { it.nombre }.toLong() else barres.maxOf { maxOf(it.depenses, it.recettes) }).coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(150.dp).padding(top = 8.dp).background(FOND_GRAPHIQUE, RoundedCornerShape(8.dp))
        .pointerInput(barres) { detectTapGestures { p -> val i = ((p.x - 8f) / ((size.width - 16f) / barres.size)).toInt().coerceIn(0, barres.size - 1); surChoix(barres[i].cle) } }) {
        val largeur = (size.width - 16f) / barres.size
        val haut = size.height - 16f
        barres.forEachIndexed { i, b ->
            val x = 8f + i * largeur
            if (b.cle == choisie) drawRect(Color(0xFFFFE4C7), Offset(x, 0f), Size(largeur, size.height))                    // la barre choisie est surlignée
            if (enNombre) { val h = haut * b.nombre / max; drawRect(Bleu, Offset(x + largeur * 0.15f, size.height - 8f - h), Size(largeur * 0.7f, h)) }
            else {
                val hd = haut * b.depenses / max; val hr = haut * b.recettes / max
                drawRect(Rouge, Offset(x + largeur * 0.08f, size.height - 8f - hd), Size(largeur * 0.4f, hd))
                drawRect(Vert, Offset(x + largeur * 0.52f, size.height - 8f - hr), Size(largeur * 0.4f, hr))
            }
        }
        drawLine(Color(0xFFCBD5E1), Offset(8f, size.height - 8f), Offset(size.width - 8f, size.height - 8f), 2f)
    }
    Row(Modifier.fillMaxWidth()) {
        Text(libelleBarre(barres.first().cle), fontSize = 10.sp, color = GRIS_CLAIR, modifier = Modifier.weight(1f))
        Text(if (enNombre) "max $max" else "max " + francs(max), fontSize = 10.sp, color = GRIS_CLAIR)
        Text(libelleBarre(barres.last().cle), fontSize = 10.sp, color = GRIS_CLAIR, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

/** Camembert (anneau) des parts, avec la légende : nom, montant et pourcentage de chaque part. */
@Composable
private fun Camembert(parts: List<Pair<String, Long>>, total: Long) {
    if (total <= 0) return
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(120.dp)) {
            var debut = -90f
            parts.forEachIndexed { i, (_, v) ->
                val balayage = 360f * v / total
                drawArc(PALETTE[i % PALETTE.size], debut, balayage, false, Offset(14f, 14f), Size(size.width - 28f, size.height - 28f), style = Stroke(width = 34f))
                debut += balayage
            }
        }
        Column(Modifier.padding(start = 12.dp)) {
            parts.forEachIndexed { i, (nom, v) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(PALETTE[i % PALETTE.size]))
                    Text(" $nom · ${(100.0 * v / total).toInt()} %", fontSize = 12.sp, color = BleuNuit, maxLines = 1)
                }
            }
        }
    }
}
