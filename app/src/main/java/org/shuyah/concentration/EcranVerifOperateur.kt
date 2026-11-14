package org.shuyah.concentration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * SECTION « VÉRIFIER AUPRÈS DE L'OPÉRATEUR » de l'onglet Paiements (logique : VerifOperateur, CodesUssd, Attente).
 * Pour un développeur WinDev : un bloc d'écran avec (1) « J'attends un paiement » (rappel automatique), (2) un bouton « Vérifier » par carte SIM, qui envoie
 * le code USSD du mini relevé pris dans la base d'auRor, (3) la réponse de l'opérateur et les opérations « reçues sans SMS » à ajouter à la base d'un appui.
 * `surChangement` demande à l'onglet de recalculer ses chiffres quand la base change.
 */
@Composable
internal fun SectionVerifOperateur(maintenant: Long, surChangement: () -> Unit) {
    val c = LocalContext.current
    val portee = rememberCoroutineScope()
    var copie by remember { mutableStateOf(CodesUssd.copie(c)) }
    var horsLigne by remember { mutableStateOf(false) }
    var attenteDepuis by remember { mutableLongStateOf(Reglages.attenteDepuis(c)) }
    var attenteEchue by remember { mutableStateOf(Reglages.attenteEchue(c)) }
    var enCours by remember { mutableStateOf(false) }
    var reponse by remember { mutableStateOf<String?>(null) }
    var reponseEnErreur by remember { mutableStateOf(false) }                                  // la réponse affichée est un échec (pas un texte de l'opérateur)
    var codeEnvoye by remember { mutableStateOf("") }                                          // dernier code envoyé, SANS le code secret (le mot PIN reste écrit tel quel)
    var absents by remember { mutableStateOf<List<Paiements.Paiement>>(emptyList()) }
    var cible by remember { mutableStateOf<Pair<Reseau.CarteSim, CodesUssd.Code>?>(null) }     // vérification en attente du code secret
    var pin by remember { mutableStateOf("") }
    val cartes = remember(maintenant) { Reseau.cartes(c) }
    val restantes = VerifOperateur.restantes(Reglages.horairesVerif(c), System.currentTimeMillis())

    var actualisation by remember { mutableStateOf(false) }                                  // relecture des codes d'auRor en cours
    var messageCodes by remember { mutableStateOf<String?>(null) }                           // résultat de la dernière relecture demandée

    /** Relit les codes d'auRor (sans quitter ni relancer l'application) ; sans Internet, la copie déjà enregistrée reste utilisée. */
    fun actualiserCodes(manuel: Boolean) {
        if (actualisation) return
        actualisation = true; if (manuel) { messageCodes = null; Avis.enCours(c, "Actualisation des codes d'auRor en cours…") }
        portee.launch {
            val reussi = withContext(Dispatchers.IO) { CodesUssd.actualiser(c) }
            horsLigne = !reussi
            copie = CodesUssd.copie(c)
            actualisation = false
            if (manuel) messageCodes = if (reussi) "Codes d'auRor relus : ${copie?.codes?.size ?: 0} code(s) à jour." else "auRor est injoignable (pas d'Internet ?) : la copie déjà enregistrée reste utilisée."
            if (manuel) messageCodes?.let { Avis.resultat(c, it) }
        }
    }

    // Mise à jour de la copie locale des codes dès l'ouverture
    LaunchedEffect(Unit) { actualiserCodes(false) }

    /** Envoie le code (avec le code secret si besoin) puis range la réponse et les opérations absentes de la base. */
    fun envoyer(carte: Reseau.CarteSim, code: CodesUssd.Code, codeSecret: String?) {
        enCours = true; reponse = null; absents = emptyList(); codeEnvoye = code.code     // le code de la base d'auRor : le mot PIN n'est jamais remplacé à l'écran
        VerifOperateur.lancer(c, carte, code, codeSecret) { r ->
            enCours = false
            reponse = r.texte ?: r.erreur; reponseEnErreur = r.texte == null
            if (r.texte != null && code.usage == CodesUssd.USAGE_RELEVE) {
                val maintenantMs = System.currentTimeMillis()
                absents = VerifOperateur.absents(VerifOperateur.analyserReleve(r.texte, maintenantMs), Historique.de(c).tousPaiements(), maintenantMs)
            }
        }
    }

    Section("Vérifier auprès de l'opérateur", Icons.Filled.FactCheck) {
        // Alerte : l'attente d'un paiement est arrivée à échéance sans rien recevoir
        if (attenteEchue) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Orange.copy(alpha = 0.25f)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.NotificationImportant, null, tint = BleuNuit); Spacer(Modifier.width(8.dp))
                Text("Paiement attendu non reçu : vérifiez auprès de l'opérateur ci-dessous.", color = BleuNuit, fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
        }

        // 1. J'attends un paiement (rappel dans 30 minutes si rien n'arrive)
        if (attenteDepuis == 0L) {
            OutlinedButton({ Attente.demarrer(c); attenteDepuis = Reglages.attenteDepuis(c); attenteEchue = false }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.HourglassTop, null); Spacer(Modifier.width(8.dp)); Text("J'attends un paiement")
            }
            Text("Si rien n'arrive dans ${Attente.DELAI_MIN} minutes, Concentration vous prévient.", fontSize = 11.sp, color = Color(0xFF64748B), modifier = Modifier.padding(top = 4.dp))
        } else {
            Text("En attente d'un paiement depuis ${SimpleDateFormat("HH:mm", Locale.FRANCE).format(attenteDepuis)} (contrôle ${Attente.DELAI_MIN} minutes plus tard).", fontSize = 13.sp, color = BleuNuit)
            OutlinedButton({ Attente.annuler(c); attenteDepuis = 0L; attenteEchue = false }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Icon(Icons.Filled.Close, null); Spacer(Modifier.width(8.dp)); Text("Annuler l'attente")
            }
        }

        // 2. Un bouton par carte SIM (le code vient d'auRor)
        Spacer(Modifier.height(10.dp))
        if (!Reseau.lectureAutorisee(c) || !Reseau.ussdAutorise(c)) {
            Text("Autorisation « Téléphone / Appels » nécessaire pour envoyer le code à l'opérateur : voir l'onglet Réseau.", fontSize = 12.sp, color = Rouge)
        } else if (cartes.isEmpty()) {
            Text("Aucune carte SIM détectée.", fontSize = 12.sp, color = Rouge)
        } else cartes.forEach { carte ->
            val releve = CodesUssd.trouver(copie, carte.operateur, CodesUssd.USAGE_RELEVE)
            val notif = CodesUssd.trouver(copie, carte.operateur, CodesUssd.USAGE_NOTIFICATION)
            if (releve == null) {
                Text("${carte.operateur} : aucun code « mini relevé » dans auRor" + if (copie == null) " (Internet n'a jamais répondu : connectez-vous une fois)." else " (à ajouter dans auRor → Codes USSD, puis appuyez sur « Actualiser les codes d'auRor »).", fontSize = 12.sp, color = Rouge, modifier = Modifier.padding(top = 4.dp))
            } else {
                Button({ if (releve.demandePin) { pin = ""; cible = carte to releve } else envoyer(carte, releve, null) }, enabled = !enCours, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Icon(Icons.Filled.FactCheck, null); Spacer(Modifier.width(8.dp)); Column { Text("Vérifier ${carte.operateur} : ${releve.titre}"); Text(releve.code, fontSize = 11.sp) }
                }
            }
            if (notif != null && notif.demandePin) {
                // Code complet avec le code secret (ex. renvoi du SMS d'une opération) : envoi direct, code secret saisi masqué
                OutlinedButton({ pin = ""; cible = carte to notif }, enabled = !enCours, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Icon(Icons.Filled.Sms, null); Spacer(Modifier.width(8.dp)); Column { Text("${carte.operateur} : ${notif.titre}"); Text(notif.code, fontSize = 11.sp) }
                }
            } else if (notif != null) {
                // Menu « dernières opérations » (sans code secret) : Android ne le rend pas à une application ; on l'ouvre dans le composeur et Concentration LIT la fenêtre
                OutlinedButton({
                    val maintenantMs = System.currentTimeMillis()
                    val motif = VerifOperateur.refus(Reglages.horairesVerif(c), maintenantMs)
                    if (motif != null) { reponse = motif; reponseEnErreur = true; codeEnvoye = notif.code }
                    else { Reglages.horairesVerif(c, Reglages.horairesVerif(c).filter { maintenantMs - it in 0 until 86_400_000L } + maintenantMs); reponse = null; LectureMenu.demarrer(c, notif.code) }
                }, enabled = !enCours, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Icon(Icons.Filled.Sms, null); Spacer(Modifier.width(8.dp)); Column { Text("${carte.operateur} : Lire la liste des dernières opérations"); Text(notif.code, fontSize = 11.sp) }
                }
                if (!accessibiliteActive(c)) Autorisation("Service d'accessibilité « Concentration (verrou) » (pour lire la liste)", false) { c.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            }
        }
        Text("$restantes vérification(s) possible(s) aujourd'hui (au plus ${VerifOperateur.MAX_PAR_JOUR} par jour, pour ne pas solliciter l'opérateur à l'excès).", fontSize = 11.sp, color = Color(0xFF64748B), modifier = Modifier.padding(top = 6.dp))
        val le = Reglages.codesLe(c)
        Text(
            when {
                copie == null -> "Codes d'auRor : pas encore reçus (Internet nécessaire une première fois)."
                horsLigne -> "Codes d'auRor : copie du ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(le)} (hors ligne : elle reste utilisable)."
                else -> "Codes d'auRor à jour (version ${copie?.version})."
            }, fontSize = 11.sp, color = Color(0xFF64748B))

        // Bouton « Actualiser les codes » : relit les codes d'auRor à la demande (sans quitter ni relancer Concentration)
        // Pictogramme seul : relit les codes d'auRor ; l'état de la lecture reste écrit à côté
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            FilledTonalIconButton({ actualiserCodes(true) }, enabled = !actualisation) {
                Icon(Icons.Filled.Refresh, contentDescription = "Actualiser les codes d'auRor")
            }
            Text(if (actualisation) "Lecture des codes d'auRor…" else "Actualiser les codes d'auRor", fontSize = 12.sp, color = Color(0xFF64748B), modifier = Modifier.padding(start = 8.dp))
        }
        messageCodes?.let { Text(it, fontSize = 12.sp, color = BleuNuit, modifier = Modifier.padding(top = 4.dp)) }

        // Dernière liste lue dans la fenêtre de l'opérateur : numéros de transaction absents de la base (paiements reçus sans SMS)
        val refsLues = remember(maintenant) { Reglages.menuReferences(c) }
        if (refsLues.isNotEmpty()) {
            val absentesLues = remember(maintenant, refsLues) { VerifOperateur.referencesAbsentes(refsLues, Historique.de(c).tousPaiements()) }
            Column(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF1F5F9)).padding(10.dp)) {
                Text("Dernière liste lue à ${SimpleDateFormat("HH:mm", Locale.FRANCE).format(Reglages.menuLuLe(c))} : ${refsLues.size} opération(s), ${absentesLues.size} absente(s) de votre base", fontSize = 13.sp, color = BleuNuit)
                absentesLues.forEach { ref ->
                    val quand = Paiements.dateDeReference(ref)?.let { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(it) } ?: "date inconnue"
                    Text("• $quand · $ref", fontSize = 12.sp, color = Orange)
                }
                if (absentesLues.isNotEmpty()) Text("Pour obtenir le SMS (montant et nom), choisissez le numéro dans le menu de l'opérateur : le code secret est demandé.", fontSize = 11.sp, color = Color(0xFF64748B), modifier = Modifier.padding(top = 4.dp))
            }
        }

        // 3. Réponse de l'opérateur et rapprochement avec la base
        if (enCours) { LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp)); Text("En attente de l'opérateur…", fontSize = 12.sp, color = BleuNuit) }
        reponse?.let {
            Column(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF1F5F9)).padding(10.dp)) {
                Text("Code (sans le code secret) : $codeEnvoye", fontSize = 12.sp, color = BleuNuit)
                Text("Réponse de l'opérateur", fontSize = 12.sp, color = Bleu, modifier = Modifier.padding(top = 4.dp))
                Text(it, fontSize = 13.sp, color = BleuNuit)
            }
            // Échec de l'envoi automatique (menu qui exige le composeur, code refusé…) : on propose d'ouvrir le même code dans le composeur du téléphone,
            // où le menu de l'opérateur s'affiche normalement. Impossible si le code contient le mot PIN (il apparaîtrait en clair dans le composeur).
            if (reponseEnErreur && codeEnvoye.isNotBlank() && !codeEnvoye.contains("PIN", ignoreCase = true) && !codeEnvoye.contains("*PIN")) {
                OutlinedButton({
                    try { c.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + android.net.Uri.encode(codeEnvoye)))) } catch (e: Exception) { }
                }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Icon(Icons.Filled.Phone, null); Spacer(Modifier.width(8.dp)); Text("Ouvrir ce code dans le composeur") }
            }
            if (absents.isNotEmpty()) {
                Text("${absents.size} opération(s) du relevé absente(s) de votre base (reçues sans SMS) :", fontSize = 13.sp, color = BleuNuit, modifier = Modifier.padding(top = 10.dp))
                absents.forEach { p ->
                    Text("• ${if (p.genre == "RECETTE") "Reçu" else "Payé"} ${p.montant} FCFA${if (p.tiers.isNotBlank()) " · ${p.tiers}" else ""}${p.reference?.let { r -> " · $r" } ?: ""}", fontSize = 12.sp, color = if (p.genre == "RECETTE") Vert else Rouge)
                }
                Button({
                    portee.launch {
                        withContext(Dispatchers.IO) { Historique.de(c).ajouterPaiements(absents) }
                        absents = emptyList(); Attente.annuler(c); attenteDepuis = 0L; attenteEchue = false
                        surChangement(); EnvoiSawali.envoyerEnArrierePlan(c)
                    }
                }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Icon(Icons.Filled.AddCircle, null); Spacer(Modifier.width(8.dp)); Text("Ajouter à ma base") }
            } else if (cible == null && !enCours && !reponseEnErreur) {
                Text("Aucune opération absente reconnue dans cette réponse. Si une opération manque, comparez avec le texte ci-dessus : sa mise en forme n'est peut-être pas encore comprise.", fontSize = 11.sp, color = Color(0xFF64748B), modifier = Modifier.padding(top = 6.dp))
            }
        }
    }

    // Saisie du code secret : masquée, utilisée pour CET envoi seulement, jamais enregistrée
    cible?.let { (carte, code) ->
        AlertDialog(
            onDismissRequest = { cible = null; pin = "" },
            icon = { Icon(Icons.Filled.Lock, null) },
            title = { Text("Code secret ${carte.operateur}") },
            text = {
                Column {
                    Text("Saisissez votre code secret Mobile Money. Il est utilisé pour cet envoi puis oublié : il n'est jamais enregistré.", fontSize = 13.sp)
                    OutlinedTextField(pin, { pin = it.filter { ch -> ch.isDigit() }.take(8) }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            },
            confirmButton = { Button({ val secret = pin; pin = ""; cible = null; envoyer(carte, code, secret) }, enabled = pin.length >= 4) { Icon(Icons.Filled.Send, null); Spacer(Modifier.width(6.dp)); Text("Envoyer") } },
            dismissButton = { OutlinedButton({ cible = null; pin = "" }) { Icon(Icons.Filled.Close, null); Spacer(Modifier.width(6.dp)); Text("Annuler") } },
        )
    }
}
