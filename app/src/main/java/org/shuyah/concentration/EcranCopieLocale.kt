package org.shuyah.concentration

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Champ de phrase secrète avec l'ŒIL pour afficher ou masquer la saisie (règle 22). */
@Composable
private fun ChampPhrase(libelle: String, valeur: String, surChangement: (String) -> Unit) {
    var voir by remember { mutableStateOf(false) }
    OutlinedTextField(value = valeur, onValueChange = surChangement, label = { Text(libelle) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (voir) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { IconButton({ voir = !voir }) { Icon(if (voir) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (voir) "Masquer la saisie" else "Afficher la saisie") } })
}

/**
 * SECTION « COPIE DE SÉCURITÉ CHIFFRÉE » (Concentration 1.56) : enregistre toutes les données dans un fichier chiffré par une phrase secrète, à l'endroit choisi (Documents, carte SD…),
 * et les restaure depuis un fichier. Chaque action annonce « en cours » puis son résultat (règle du propriétaire : jamais silencieux).
 */
@Composable
internal fun SectionCopieLocale() {
    val c = LocalContext.current
    val portee = rememberCoroutineScope()
    var dialogue by remember { mutableStateOf<String?>(null) }          // "creer" | "lire"
    var phrase by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var occupe by remember { mutableStateOf(false) }
    var octetsCopie by remember { mutableStateOf<ByteArray?>(null) }
    var octetsLus by remember { mutableStateOf<ByteArray?>(null) }
    var apercu by remember { mutableStateOf<CopieLocale.Apercu?>(null) }
    var derniere by remember { mutableLongStateOf(CopieLocale.derniereCopie(c)) }
    var message by remember { mutableStateOf<String?>(null) }

    fun annoncer(t: String) { message = t; Avis.resultat(c, t) }

    // Choix de l'emplacement du fichier puis écriture : l'application n'a besoin d'AUCUNE autorisation de stockage
    val creerFichier = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val octets = octetsCopie
        if (uri != null && octets != null) portee.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { c.contentResolver.openOutputStream(uri)?.use { it.write(octets) } ?: throw java.io.IOException("emplacement illisible") }
                CopieLocale.copieFaite(c); derniere = CopieLocale.derniereCopie(c)
                annoncer("Copie de sécurité enregistrée (${octets.size / 1024} Ko, chiffrée). Gardez ce fichier et votre phrase en lieu sûr.")
            } catch (e: Exception) { annoncer("Enregistrement du fichier impossible : ${e.message ?: e.javaClass.simpleName}") }
            octetsCopie = null
        } else { octetsCopie = null; if (uri == null) annoncer("Enregistrement annulé : aucune copie n'a été faite") }
    }
    val ouvrirFichier = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) portee.launch {
            try { octetsLus = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { c.contentResolver.openInputStream(uri)?.use { it.readBytes() } }; phrase = ""; dialogue = "lire" }
            catch (e: Exception) { annoncer("Lecture du fichier impossible : ${e.message ?: e.javaClass.simpleName}") }
        }
    }

    Section("Copie de sécurité chiffrée", Icons.Filled.Save) {
        Text("Enregistre TOUTES les données (paiements lus dans les SMS, mesures de consommation, réglages) dans un fichier chiffré par une phrase secrète, à l'endroit de votre choix (Documents, carte SD…). Il reste sur le téléphone même si l'application est désinstallée. Sans la phrase, personne ne peut le lire.",
            fontSize = 13.sp, color = BleuNuit)
        Text("Dernière copie : " + if (derniere <= 0) "jamais" else SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(derniere)), fontSize = 13.sp, color = Color(0xFF475569), modifier = Modifier.padding(top = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            // Les DEUX boutons ont exactement la même largeur (chacun la moitié de la ligne) et la même hauteur ; le texte passe sur 2 lignes si besoin
            Button(enabled = !occupe, modifier = Modifier.weight(1f).height(56.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                onClick = { phrase = ""; confirmation = ""; dialogue = "creer" }) { Icon(Icons.Filled.Save, null); Text(" Enregistrer une copie", maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 14.sp) }
            Button(enabled = !occupe, modifier = Modifier.weight(1f).height(56.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                onClick = { ouvrirFichier.launch(arrayOf("*/*")) }) { Icon(Icons.Filled.FolderOpen, null); Text(" Restaurer une copie", maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 14.sp) }
        }
        if (occupe) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) { CircularProgressIndicator(Modifier.size(20.dp)); Text("  Opération en cours…", fontSize = 13.sp, color = Bleu) }
        message?.let { Text(it, fontSize = 13.sp, color = BleuNuit, modifier = Modifier.padding(top = 8.dp)) }
    }

    when (dialogue) {
        "creer" -> AlertDialog(onDismissRequest = { dialogue = null }, title = { Text("Copie de sécurité chiffrée") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choisissez une phrase secrète et retenez-la : sans elle, le fichier est illisible pour tout le monde.", fontSize = 14.sp)
                ChampPhrase("Phrase secrète", phrase) { phrase = it }
                ChampPhrase("Confirmez la phrase", confirmation) { confirmation = it }
                if (phrase.isNotEmpty()) GardeFouPhrase.verifier(phrase).raisons.forEach { Text("• $it", fontSize = 13.sp, color = Color(0xFFB91C1C)) }
                if (confirmation.isNotEmpty() && CopieCrypto.normaliser(confirmation) != CopieCrypto.normaliser(phrase)) Text("Les deux phrases ne sont pas identiques.", fontSize = 13.sp, color = Color(0xFFB91C1C))
            } },
            confirmButton = { Button(enabled = !occupe && GardeFouPhrase.verifier(phrase).acceptable && CopieCrypto.normaliser(confirmation) == CopieCrypto.normaliser(phrase), onClick = {
                val p = phrase; dialogue = null; occupe = true; Avis.enCours(c, "Préparation de la copie (vérification des données)…")
                portee.launch {
                    val (octets, r) = CopieLocale.preparer(c, p); occupe = false; annoncer(r.message)
                    if (octets != null) { octetsCopie = octets; phrase = ""; confirmation = ""; creerFichier.launch(CopieLocale.nomFichier()) }
                }
            }) { Icon(Icons.Filled.Save, null); Text("  Créer") } },
            dismissButton = { OutlinedButton({ dialogue = null }) { Icon(Icons.Filled.Close, null); Text("  Annuler") } })
        "lire" -> AlertDialog(onDismissRequest = { dialogue = null; octetsLus = null }, title = { Text("Lire la copie de sécurité") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Saisissez la phrase secrète utilisée pour créer la copie.", fontSize = 14.sp)
                ChampPhrase("Phrase secrète", phrase) { phrase = it }
            } },
            confirmButton = { Button(enabled = !occupe && CopieCrypto.normaliser(phrase).length >= CopieCrypto.LONGUEUR_MIN_PHRASE, onClick = {
                val o = octetsLus; val p = phrase; dialogue = null; octetsLus = null
                if (o != null) { occupe = true; Avis.enCours(c, "Lecture et vérification en cours…")
                    portee.launch { val (ap, r) = CopieLocale.apercu(c, o, p); occupe = false; if (ap != null) apercu = ap else annoncer(r.message); phrase = "" } }
            }) { Icon(Icons.Filled.Search, null); Text("  Vérifier") } },
            dismissButton = { OutlinedButton({ dialogue = null; octetsLus = null }) { Icon(Icons.Filled.Close, null); Text("  Annuler") } })
    }

    // Contenu VÉRIFIÉ de la copie : l'utilisateur le voit avant de remplacer les données du téléphone
    apercu?.let { ap ->
        AlertDialog(onDismissRequest = { apercu = null }, title = { Text("Remplacer les données de ce téléphone ?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("La copie contient :", fontSize = 14.sp)
                Text("${ap.paiements} paiement(s), ${ap.mesures} mesure(s) et les réglages", fontSize = 16.sp, color = BleuNuit)
                Text("Vos données actuelles seront remplacées (l'ancienne base est gardée à côté du fichier de l'application), puis l'application redémarrera.", fontSize = 13.sp, color = Color(0xFF475569))
            } },
            confirmButton = { Button(onClick = {
                apercu = null; occupe = true; Avis.enCours(c, "Restauration en cours (ne fermez pas l'application)…")
                portee.launch { val r = CopieLocale.restaurer(c, ap); occupe = false; annoncer(r.message); if (r.ok) CopieLocale.relancer(c) }
            }) { Icon(Icons.Filled.Restore, null); Text("  Remplacer") } },
            dismissButton = { OutlinedButton({ apercu = null }) { Icon(Icons.Filled.Close, null); Text("  Annuler") } })
    }
}
