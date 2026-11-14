package org.shuyah.concentration

import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.net.VpnService
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope

internal val Bleu = Color(0xFF0E6BA8)
internal val BleuNuit = Color(0xFF0B1F3A)
internal val Vert = Color(0xFF2E9E6B)
internal val Rouge = Color(0xFFD64545)
internal val Orange = Color(0xFFF6A35B)

/** Écran unique : état, plages horaires, niveau 1 (notifications), niveau 2 (verrou), autorisations et conseils Xiaomi. */
class MainActivity : ComponentActivity() {
    /** Incrémenté à chaque retour sur l'écran : l'état des autorisations est alors relu (l'utilisateur revient des Paramètres). */
    private var retour by mutableIntStateOf(0)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Presence.demarrer(lifecycleScope)
        setContent { MaterialTheme { Ecran(retour) } }
    }

    override fun onResume() { super.onResume(); retour++ }
}

/** Vrai si l'accès aux notifications est accordé à Concentration. */
internal fun accesNotifications(c: Context) = Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners")?.contains(c.packageName + "/") == true

/** Vrai si le service d'accessibilité (verrou) est activé. */
internal fun accessibiliteActive(c: Context) = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.contains(c.packageName + "/") == true

/** Vrai si Concentration est exclue de l'économie de batterie (« Aucune restriction » : le système ne l'arrête plus). */
private fun batterieLibre(c: Context) = (c.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isIgnoringBatteryOptimizations(c.packageName)

/** Ouvre un écran de réglages ; si l'appareil ne l'a pas, ouvre la fiche de l'application. */
internal fun ouvrir(c: Context, i: Intent) {
    try { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {
        try { c.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e2: Exception) {}
    }
}

@Composable
private fun Ecran(retour: Int) {
    val c = LocalContext.current
    var actif by remember { mutableStateOf(Reglages.actif(c)) }
    var notif by remember { mutableStateOf(Reglages.notifications(c)) }
    var verrou by remember { mutableStateOf(Reglages.verrou(c)) }
    var pareFeu by remember { mutableStateOf(Reglages.pareFeu(c)) }
    var tickVpn by remember { mutableIntStateOf(0) }                     // relu après la fenêtre d'autorisation VPN
    val lanceurVpn = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { tickVpn++ }
    var toutSauf by remember { mutableStateOf(Reglages.toutSauf(c)) }
    var bloquees by remember { mutableStateOf(Reglages.bloquees(c)) }
    var autorisees by remember { mutableStateOf(Reglages.autorisees(c)) }
    val plages = remember { mutableStateListOf(*Reglages.plages(c).toTypedArray()) }
    var maintenant by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(20_000); maintenant = System.currentTimeMillis() } }   // état mis à jour toutes les 20 s
    var choix by remember { mutableStateOf<String?>(null) }          // « bloquees » ou « autorisees » : fenêtre de choix des applications ouverte
    val sauverPlages = { Reglages.plages(c, plages.toList()) }
    // Les états des autorisations sont relus aussi à chaque tick (toutes les 20 s) : sinon une ligne reste rouge alors que le réglage vient de passer au vert
    val okNotif = remember(retour, maintenant) { accesNotifications(c) }
    val okAcces = remember(retour, maintenant) { accessibiliteActive(c) }
    val okVpn = remember(retour, tickVpn, maintenant) { Pare.autorise(c) }
    // Tout changement de réglage met le pare-feu à jour (recréation du tunnel) et reprogramme les réveils
    LaunchedEffect(actif, pareFeu, toutSauf, bloquees, autorisees, plages.toList(), okVpn) { Pare.appliquer(c, forcer = true) }
    var batterieConfirmee by remember { mutableStateOf(Reglages.batterieConfirmee(c)) }
    val contacts = remember { mutableStateListOf(*Reglages.contacts(c).toTypedArray()) }
    var ajoutContact by remember { mutableStateOf(false) }
    var son by remember { mutableStateOf(Reglages.son(c)) }
    // ---- Réseau mobile (v1.8) ----
    var tickPerm by remember { mutableIntStateOf(0) }                    // relu après la fenêtre d'autorisations du système
    val lanceurPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tickPerm++ }
    val cartes = remember(retour, tickPerm, maintenant) { Reseau.cartes(c) }
    val okReseau = remember(retour, tickPerm, maintenant) { Reseau.lectureAutorisee(c) && Reseau.ussdAutorise(c) && Reseau.notificationsAutorisees(c) }
    var tickLecture by remember { mutableIntStateOf(0) }                 // relu après chaque vérification du solde
    var verification by remember { mutableStateOf<Int?>(null) }          // emplacement de la carte en cours de vérification
    var messageReseau by remember { mutableStateOf("") }
    val codes = remember { mutableStateMapOf<Int, String>() }
    val departs = remember { mutableStateMapOf<Int, String>() }          // volume de départ saisi, par emplacement de carte SIM
    var onglet by remember { mutableIntStateOf(0) }                      // 0 = Général, 1 = Réseau mobile
    var seuilTexte by remember { mutableStateOf(Reglages.seuilPourcent(c).toString()) }
    var alerteReseau by remember { mutableStateOf(Reglages.alerteReseau(c)) }
    var verifAuto by remember { mutableStateOf(Reglages.verifAuto(c)) }
    var rappelExpiration by remember { mutableStateOf(Reglages.rappelExpiration(c)) }
    var intervalleTexte by remember { mutableStateOf(Reglages.intervalleHeures(c).toString()) }
    var departAuto by remember { mutableStateOf(Reglages.departAuto(c)) }
    val okBatterie = remember(retour, batterieConfirmee, maintenant) { batterieLibre(c) || batterieConfirmee }
    val restant = remember(maintenant, actif, plages.toList()) { Reglages.minutesRestantes(c) }

    Column(Modifier.fillMaxSize().background(Color(0xFFF1F5F9)).statusBarsPadding().navigationBarsPadding()) {
    TabRow(selectedTabIndex = onglet, containerColor = Color.White, contentColor = Bleu) {
        Tab(selected = onglet == 0, onClick = { onglet = 0 }, text = { Text("Général") })
        Tab(selected = onglet == 1, onClick = { onglet = 1 }, text = { Text("Réseau mobile") })
        Tab(selected = onglet == 2, onClick = { onglet = 2 }, text = { Text("Consommation") })
        Tab(selected = onglet == 3, onClick = { onglet = 3 }, text = { Text("Paiements") })
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (onglet == 0) {
        // ---- État général et interrupteur ----
        Carte(if (restant != null) Vert else Color(0xFF64748B)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pastille(if (restant != null) Icons.Filled.CenterFocusStrong else Icons.Filled.Bedtime, Color.White.copy(alpha = 0.25f), Color.White)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Concentration", color = Color.White, fontSize = 20.sp)
                    Text(when {
                        !actif -> "Désactivée"
                        Reglages.enPause(c) -> "En pause (reprise à ${heureDe(Reglages.pauseJusqua(c))})"
                        restant != null -> "En cours : encore ${restant / 60} h ${"%02d".format(restant % 60)} (jusqu'à ${heureFin(restant)})"
                        plages.isEmpty() -> "Aucune plage horaire"
                        plages.none { it.actif } -> "Toutes les plages sont inactives"
                        else -> "En attente de la prochaine plage"
                    }, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                }
                Switch(actif, { actif = it; Reglages.actif(c, it) })
            }
            // Notifications retardées par application (plage en cours ou dernière plage)
            val cpt = remember(maintenant, retour) { Reglages.compteurs(c) }
            if (cpt.isNotEmpty()) Text("Notifications retardées : " + cpt.entries.joinToString(" · ") { (p, n) ->
                (try { c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(p, 0)).toString() } catch (e: Exception) { p }) + " " + n },
                color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }

        // ---- Résultats du solde Internet de chaque carte SIM (carte bleue, onglet Général) ----
        val lectures = remember(tickLecture, cartes, maintenant) { cartes.filter { Reglages.lectureHeure(c, it.emplacement) > 0L } }
        if (lectures.isNotEmpty()) Carte(Bleu) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pastille(Icons.Filled.SimCard, Color.White.copy(alpha = 0.25f), Color.White, 40)
                Text("Internet mobile", color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(start = 12.dp))
            }
            lectures.forEach { carte ->
                val restantSim = Reglages.restantMo(c, carte.emplacement); val departSim = Reglages.departMo(c, carte.emplacement)
                val pc = if (restantSim != null) Solde.pourcentRestant(restantSim, departSim) else null
                val bas = restantSim != null && departSim > 0 && Solde.seuilAtteint(restantSim, departSim, Reglages.seuilPourcent(c))
                Column(Modifier.fillMaxWidth().padding(top = 12.dp).clickable {                       // un appui actualise le solde de cette carte
                    if (verification == null) { verification = carte.emplacement; Reseau.verifier(c, carte) { verification = null; tickLecture++ } }
                }) {
                    Text("SIM ${carte.emplacement + 1} · ${carte.operateur}" + (if (carte.donnees) " · Internet actif" else ""), color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                    Text(if (verification == carte.emplacement) "Interrogation en cours…" else if (restantSim != null) "Il reste ${Solde.formater(restantSim)}" + (if (pc != null) " (${pc} % du départ)" else "") else "Pas de forfait Internet",
                        color = if (bas) Orange else Color.White, fontSize = 18.sp)
                    val expiration = Reglages.expirationMs(c, carte.emplacement)
                    if (expiration > 0L) Text(Solde.libelleExpiration(expiration, System.currentTimeMillis()),
                        color = if (expiration < System.currentTimeMillis() || Solde.joursRestants(expiration, System.currentTimeMillis()) <= 7) Orange else Color.White, fontSize = 15.sp)
                    Text("à ${heureDe(Reglages.lectureHeure(c, carte.emplacement))} · " + Reglages.lectureTexte(c, carte.emplacement).replace('\n', ' ').take(140), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
            }
            Text("Appuyez sur une carte SIM pour actualiser son solde.", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        }

        // ---- Plages horaires ----
        Section("Plages horaires", Icons.Filled.Schedule) {
            plages.forEachIndexed { i, p ->
                // Une plage EN COURS est verrouillée (horaires et jours) ; une plage future ou inactive reste modifiable
                val (jourNow, minNow) = remember(maintenant) { Plage.jourEtMinutes() }
                val verrouillee = p.enCours(jourNow, minNow)
                Column(Modifier.padding(vertical = 4.dp)) {
                    // Ligne du haut : horaires, état (actif / inactif), modifier, supprimer
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.libelle(), fontSize = 17.sp, color = if (p.actif) BleuNuit else Color(0xFF94A3B8))
                            Text(if (!p.actif) "Inactive (conservée, ne bloque rien)" else if (verrouillee) "En cours : non modifiable" else "Active",
                                fontSize = 12.sp, color = if (!p.actif) Color(0xFF94A3B8) else if (verrouillee) Orange else Vert)
                        }
                        Switch(p.actif, { plages[i] = p.copy(actif = it); sauverPlages() })
                        // Modifier : heure de début puis heure de fin, préremplies avec les valeurs actuelles (impossible pendant la plage)
                        Icon(Icons.Filled.Edit, "Modifier la plage", tint = if (verrouillee) Color(0xFFCBD5E1) else Bleu,
                            modifier = Modifier.padding(start = 8.dp).size(28.dp).clickable {
                                if (verrouillee) { Toast.makeText(c, "Plage en cours : modification impossible avant sa fin.", Toast.LENGTH_SHORT).show(); return@clickable }
                                TimePickerDialog(c, { _, h1, m1 ->
                                    TimePickerDialog(c, { _, h2, m2 -> plages[i] = p.copy(debut = h1 * 60 + m1, fin = h2 * 60 + m2); sauverPlages() }, p.fin / 60, p.fin % 60, true)
                                        .apply { setTitle("Heure de FIN") }.show()
                                }, p.debut / 60, p.debut % 60, true).apply { setTitle("Heure de DÉBUT") }.show()
                            })
                        Icon(Icons.Filled.Delete, "Supprimer la plage", tint = Rouge, modifier = Modifier.padding(start = 8.dp).size(28.dp).clickable { plages.removeAt(i); sauverPlages() })
                    }
                    // Jours : un appui sur une lettre l'ajoute ou l'enlève (jour où la plage commence) — verrouillé pendant la plage
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 6.dp)) {
                        Plage.JOURS.forEachIndexed { j, l ->
                            val on = p.jours and (1 shl j) != 0
                            Box(Modifier.size(30.dp).clip(CircleShape).background(if (on) (if (p.actif) Bleu else Color(0xFF94A3B8)) else Color(0xFFE2E8F0))
                                .clickable(enabled = !verrouillee) { plages[i] = p.copy(jours = p.jours xor (1 shl j)); sauverPlages() }, contentAlignment = Alignment.Center) {
                                Text(l, color = if (on) Color.White else Color(0xFF475569), fontSize = 13.sp)
                            }
                        }
                    }
                }
                HorizontalDivider()
            }
            // Son de changement d'état
            LigneInterrupteur("Émettre un son quand l'état change (début, fin, pause, reprise)", son) { son = it; Reglages.son(c, it) }
            // Ajout : l'heure de début puis l'heure de fin (deux sélecteurs d'heure successifs)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp).clickable {
                TimePickerDialog(c, { _, h1, m1 ->
                    TimePickerDialog(c, { _, h2, m2 -> plages.add(Plage(h1 * 60 + m1, h2 * 60 + m2)); sauverPlages() }, 7, 0, true).apply { setTitle("Heure de FIN") }.show()
                }, 20, 0, true).apply { setTitle("Heure de DÉBUT") }.show()
            }) {
                Pastille(Icons.Filled.Add, Orange, BleuNuit, 40)
                Text("Ajouter une plage", color = BleuNuit, modifier = Modifier.padding(start = 12.dp))
            }
        }

        // ---- Quelles applications ? ----
        Section("Quelles applications ?", Icons.Filled.Apps) {
            ChoixMode(!toutSauf, Icons.Filled.CheckBox, "Celles que je choisis") { toutSauf = false; Reglages.toutSauf(c, false) }
            ChoixMode(toutSauf, Icons.Filled.Shield, "Toutes, sauf celles que j'autorise") { toutSauf = true; Reglages.toutSauf(c, true) }
            Text(if (!toutSauf) "${bloquees.size} application(s) bloquée(s)" else "${autorisees.size} application(s) autorisée(s) (le téléphone, les SMS, le clavier et les réglages restent toujours libres)",
                fontSize = 13.sp, color = Color(0xFF475569), modifier = Modifier.padding(top = 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp).clickable { choix = if (toutSauf) "autorisees" else "bloquees" }) {
                Pastille(Icons.Filled.Edit, Orange, BleuNuit, 40)
                Text(if (toutSauf) "Choisir les applications autorisées" else "Choisir les applications à bloquer", color = BleuNuit, modifier = Modifier.padding(start = 12.dp))
            }
        }

        // ---- Niveau 1 ----
        Section("Niveau 1 · Notifications", Icons.Filled.NotificationsOff) {
            LigneInterrupteur("Retarder messages et appels jusqu'à la fin de la plage", notif) { notif = it; Reglages.notifications(c, it) }
            Autorisation("Accès aux notifications", okNotif) { ouvrir(c, Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }
        }

        // ---- Contacts autorisés (exceptions du niveau 1) ----
        Section("Contacts autorisés", Icons.Filled.Contacts) {
            Text("Leurs messages et appels sonnent normalement pendant la plage (niveau 1). Écrivez le nom tel qu'il s'affiche dans WhatsApp.", fontSize = 13.sp, color = Color(0xFF475569))
            contacts.forEachIndexed { i, n ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Icon(Icons.Filled.Person, null, tint = Bleu, modifier = Modifier.size(24.dp))
                    Text(n, Modifier.weight(1f).padding(horizontal = 8.dp), color = BleuNuit)
                    Icon(Icons.Filled.Delete, "Retirer ce contact", tint = Rouge, modifier = Modifier.size(26.dp).clickable { contacts.removeAt(i); Reglages.contacts(c, contacts.toList()) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp).clickable { ajoutContact = true }) {
                Pastille(Icons.Filled.PersonAdd, Orange, BleuNuit, 40)
                Text("Ajouter un contact", color = BleuNuit, modifier = Modifier.padding(start = 12.dp))
            }
            if (contacts.isNotEmpty() && (verrou || pareFeu)) Text("Attention : le verrou (niveau 2) empêche d'ouvrir WhatsApp et le pare-feu (niveau 3) coupe aussi ces contacts. Pour les laisser passer, utilisez le niveau 1 seul.",
                fontSize = 12.sp, color = Rouge, modifier = Modifier.padding(top = 8.dp))
        }

        // ---- Niveau 2 ----
        Section("Niveau 2 · Verrou des applications", Icons.Filled.Lock) {
            LigneInterrupteur("Empêcher d'ouvrir les applications bloquées", verrou) { verrou = it; Reglages.verrou(c, it) }
            Autorisation("Service d'accessibilité « Concentration (verrou) »", okAcces) { ouvrir(c, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }

        // ---- Niveau 3 : pare-feu ----
        Section("Niveau 3 · Pare-feu", Icons.Filled.WifiOff) {
            LigneInterrupteur("Couper Internet aux applications bloquées pendant la plage (plus aucun message ni appel)", pareFeu) {
                pareFeu = it; Reglages.pareFeu(c, it)
                if (it && !Pare.autorise(c)) VpnService.prepare(c)?.let { i -> lanceurVpn.launch(i) }     // fenêtre système de demande VPN
            }
            Autorisation("Autorisation VPN (pare-feu local)", okVpn) { VpnService.prepare(c)?.let { i -> lanceurVpn.launch(i) } }
            Text(if (ServiceVpn.actif) "Pare-feu en cours : les applications bloquées n'ont plus Internet." else "Aucun message ne sort ni n'entre : tout ce qui attend arrive à la fin de la plage. Ce n'est pas un vrai VPN : rien ne quitte le téléphone. Il remplace un autre VPN s'il y en a un.",
                fontSize = 12.sp, color = Color(0xFF475569), modifier = Modifier.padding(top = 6.dp))
        }

        // ---- Xiaomi : rester actif en arrière-plan ----
        Section("Xiaomi : pour que ça reste actif", Icons.Filled.PhoneAndroid) {
            // 1. Batterie : un appui ouvre la fenêtre du système « Autoriser Concentration à toujours fonctionner en arrière-plan ? » → Autoriser
            Autorisation("Batterie sans restriction", okBatterie) {
                ouvrir(c, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + c.packageName)))
            }
            // Sur Xiaomi, « Pas de restriction » est un réglage de MIUI : Android ne peut pas le lire, d'où cette confirmation manuelle
            Text("Xiaomi : dans « Fiche appli » → « Économiseur de batterie », choisissez « Pas de restriction ». Android ne peut pas lire ce réglage : confirmez-le ci-dessous.", fontSize = 12.sp, color = Color(0xFF475569), modifier = Modifier.padding(top = 4.dp, start = 34.dp))
            if (!okBatterie || batterieConfirmee) LigneInterrupteur("C'est fait : la fiche indique « Pas de restriction »", batterieConfirmee) { batterieConfirmee = it; Reglages.batterieConfirmee(c, it) }
            // 2. Démarrage automatique (non vérifiable par l'application) et fiche de l'application
            Text("Démarrage automatique : à activer pour Concentration (réglage de Xiaomi, non vérifiable ici).", fontSize = 13.sp, color = Color(0xFF475569), modifier = Modifier.padding(top = 10.dp))
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Action(Icons.Filled.PowerSettingsNew, "Démarrage auto") {
                    ouvrir(c, Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")))
                }
                Action(Icons.Filled.Info, "Fiche appli") { ouvrir(c, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.packageName))) }
            }
        }
        Text(Version.libelle(), fontSize = 12.sp, color = Color(0xFF64748B), modifier = Modifier.padding(bottom = 24.dp))
        } else if (onglet == 1) {
            // ---- Onglet « Réseau mobile » : réglages de l'interrogation des cartes SIM (le résultat s'affiche dans la carte bleue de l'onglet Général) ----
            Section("Réseau mobile", Icons.Filled.SimCard) {
                Autorisation("Téléphone, appels et notifications (lecture des cartes SIM, envoi du code, alerte)", okReseau) {
                    lanceurPerm.launch(arrayOf(android.Manifest.permission.READ_PHONE_STATE, android.Manifest.permission.CALL_PHONE, android.Manifest.permission.POST_NOTIFICATIONS))
                }
                if (cartes.isEmpty()) Text(if (okReseau) "Aucune carte SIM active détectée." else "Accordez l'autorisation pour voir vos cartes SIM.", fontSize = 13.sp, color = Color(0xFF475569), modifier = Modifier.padding(top = 8.dp))
                cartes.forEach { carte ->
                    val code = codes[carte.emplacement] ?: Reglages.codeUssd(c, carte.emplacement)
                    val depart = departs[carte.emplacement] ?: Reglages.departMo(c, carte.emplacement).let { if (it > 0) "%.1f".format(java.util.Locale.FRANCE, it / 1024.0) else "" }
                    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.SimCard, null, tint = if (carte.donnees) Vert else Color(0xFF94A3B8), modifier = Modifier.size(24.dp))
                            Text("SIM ${carte.emplacement + 1} · ${carte.operateur}", Modifier.weight(1f).padding(start = 8.dp), color = BleuNuit)
                            Text(if (carte.donnees) "Internet actif" else "", color = Vert, fontSize = 12.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            OutlinedTextField(code, { codes[carte.emplacement] = it; Reglages.codeUssd(c, carte.emplacement, it) }, singleLine = true,
                                placeholder = { Text("Code du solde Internet (ex. *123#)") }, modifier = Modifier.weight(1f))
                            Box(Modifier.padding(start = 8.dp)) {
                                Action(Icons.Filled.Refresh, if (verification == carte.emplacement) "…" else "Solde") {
                                    if (verification == null) {
                                        verification = carte.emplacement; messageReseau = "Interrogation de ${carte.operateur}…"
                                        Reseau.verifier(c, carte) { r -> verification = null; tickLecture++; messageReseau = r.erreur ?: "Réponse reçue : voir la carte bleue de l'onglet Général." }
                                    }
                                }
                            }
                        }
                        OutlinedTextField(depart, { departs[carte.emplacement] = it; it.replace(',', '.').toDoubleOrNull()?.let { go -> Reglages.departMo(c, carte.emplacement, go * 1024.0); Reglages.alerteEnvoyee(c, carte.emplacement, false) } },
                            singleLine = true, label = { Text("Volume de départ du forfait (Go)") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    }
                }
                if (messageReseau.isNotEmpty()) Text(messageReseau, fontSize = 13.sp, color = if (verification == null) Bleu else Bleu, modifier = Modifier.padding(top = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    OutlinedTextField(seuilTexte, { seuilTexte = it; it.toIntOrNull()?.let { p -> Reglages.seuilPourcent(c, p) } },
                        singleLine = true, label = { Text("Seuil d'alerte (% du volume de départ)") }, modifier = Modifier.fillMaxWidth())
                }
                LigneInterrupteur("M'alerter quand il ne reste que ce pourcentage du volume de départ", alerteReseau) { alerteReseau = it; Reglages.alerteReseau(c, it) }
                LigneInterrupteur("Me rappeler l'expiration du forfait (7 jours, 3 jours, le jour même avant l'heure)", rappelExpiration) { rappelExpiration = it; Reglages.rappelExpiration(c, it); Reseau.programmerExpirations(c) }
                LigneInterrupteur("Remettre le volume de départ à jour quand une recharge est détectée", departAuto) { departAuto = it; Reglages.departAuto(c, it) }
                LigneInterrupteur("Vérifier le solde automatiquement (carte de données active)", verifAuto) { verifAuto = it; Reglages.verifAuto(c, it); Reseau.programmer(c) }
                if (verifAuto) OutlinedTextField(intervalleTexte, { intervalleTexte = it; it.toIntOrNull()?.let { h -> Reglages.intervalleHeures(c, Solde.intervalleHeures(it)); Reseau.programmer(c) } },
                    singleLine = true, label = { Text("Toutes les … heures (minimum 1)") },
                    supportingText = { Text(if ((intervalleTexte.toIntOrNull() ?: 1) < 1) "Au moins 1 heure : 1 h sera retenue." else "Prochaine vérification dans ${Reglages.intervalleHeures(c)} h au plus tard.") },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                // Enregistrer (écriture immédiate de tous les réglages) et test du rappel d'expiration
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Action(Icons.Filled.Save, "Enregistrer") {
                        val erreurs = mutableListOf<String>()
                        cartes.forEach { carte ->
                            val n = carte.emplacement + 1
                            Reglages.codeUssd(c, carte.emplacement, codes[carte.emplacement] ?: Reglages.codeUssd(c, carte.emplacement))
                            val depart = departs[carte.emplacement]
                            if (!depart.isNullOrBlank()) {
                                val go = depart.replace(',', '.').toDoubleOrNull()
                                if (go == null) erreurs += "volume de départ de la SIM $n illisible" else Reglages.departMo(c, carte.emplacement, go * 1024.0)
                            }
                        }
                        val seuil = seuilTexte.toIntOrNull()
                        if (seuil == null) erreurs += "seuil d'alerte illisible" else Reglages.seuilPourcent(c, seuil)
                        if (verifAuto) Reglages.intervalleHeures(c, Solde.intervalleHeures(intervalleTexte))
                        val ecrit = Reglages.enregistrer(c)                                  // écriture synchrone : certitude que tout est sur le téléphone
                        Reseau.programmer(c); Reseau.programmerExpirations(c)
                        messageReseau = if (!ecrit) "⚠ Écriture impossible sur le téléphone : réessayez." else if (erreurs.isEmpty()) "✓ Réglages enregistrés à ${heureDe(Reglages.enregistreLe(c))}." else "Enregistré, sauf : ${erreurs.joinToString(", ")}."
                        Toast.makeText(c, if (ecrit) "Réglages enregistrés" else "Écriture impossible", Toast.LENGTH_SHORT).show()
                    }
                    Action(Icons.Filled.NotificationsActive, "Test rappel") {
                        messageReseau = if (Reseau.testerRappelExpiration(c)) "Notification d'essai envoyée : regardez le panneau de notifications." else "Notifications non autorisées : accordez-les avec la ligne d'autorisation ci-dessus."
                    }
                }
                Text("Le code part en arrière-plan, sans fenêtre, sur la carte choisie (même si ce n'est pas celle d'Internet). Un seul échange : si l'opérateur répond par un menu, saisissez le code complet, ex. *123*1#. Le résultat de chaque carte s'affiche dans l'onglet Général. Sans forfait Internet, l'opérateur répond par une simple phrase : elle est affichée telle quelle. Si l'opérateur répond par SMS (ex. Telecel sans forfait), la notification du SMS reçue dans les 3 minutes après le code est lue et rattachée à la carte (accès aux notifications requis).",
                    fontSize = 12.sp, color = Color(0xFF475569), modifier = Modifier.padding(top = 6.dp))
            }
            Text(Version.libelle(), fontSize = 12.sp, color = Color(0xFF64748B), modifier = Modifier.padding(bottom = 24.dp))
        } else if (onglet == 2) {
            // ---- 3e onglet « Consommation » : graphique de la consommation Internet estimée entre deux vérifications du solde ----
            OngletConsommation(cartes, tickLecture, maintenant)
            Text(Version.libelle(), fontSize = 12.sp, color = Color(0xFF64748B), modifier = Modifier.padding(bottom = 24.dp))
        } else {
            // ---- 4e onglet « Paiements » : base des SMS de paiement ----
            OngletPaiements(retour, maintenant)
            Text(Version.libelle(), fontSize = 12.sp, color = Color(0xFF64748B), modifier = Modifier.padding(bottom = 24.dp))
        }
    }
    }

    // Fenêtre d'ajout d'un contact autorisé
    if (ajoutContact) {
        var nom by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { ajoutContact = false },
            title = { Text("Ajouter un contact") },
            text = { OutlinedTextField(nom, { nom = it }, singleLine = true, placeholder = { Text("Nom dans WhatsApp") }) },
            confirmButton = { Action(Icons.Filled.Check, "Valider") { if (nom.trim().length >= 2) { contacts.add(nom.trim()); Reglages.contacts(c, contacts.toList()) }; ajoutContact = false } },
            dismissButton = { Action(Icons.Filled.Close, "Annuler") { ajoutContact = false } }
        )
    }

    // Fenêtre de choix des applications (liste cochable avec recherche)
    when (choix) {
        "bloquees" -> ChoixApplications("Applications à bloquer", bloquees, { choix = null }) { bloquees = it; Reglages.bloquees(c, it); choix = null }
        "autorisees" -> ChoixApplications("Applications autorisées", autorisees, { choix = null }) { autorisees = it; Reglages.autorisees(c, it); choix = null }
    }
}

/** « 07:00 » à partir d'un instant en millisecondes. */
private fun heureDe(ms: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    return "%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
}

/** Heure de fin « 07:00 » à partir des minutes restantes. */
private fun heureFin(restant: Int): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = (System.currentTimeMillis() / 60000L + restant) * 60000L }
    return "%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
}

// ======================= Petits éléments d'interface (règle 14 : pictogrammes) =======================

/** Carte colorée (état général). */
@Composable
private fun Carte(fond: Color, contenu: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(fond).padding(16.dp), content = contenu)

/** Section blanche avec un titre et son pictogramme. */
@Composable
internal fun Section(titre: String, icone: ImageVector, contenu: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            Icon(icone, null, tint = Bleu, modifier = Modifier.size(24.dp))
            Text(titre, color = BleuNuit, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
        }
        contenu()
    }
}

/** Pictogramme rond. */
@Composable
internal fun Pastille(icone: ImageVector, fond: Color, couleur: Color, taille: Int = 48) =
    Box(Modifier.size(taille.dp).clip(CircleShape).background(fond), contentAlignment = Alignment.Center) { Icon(icone, null, tint = couleur, modifier = Modifier.size((taille * 0.55).dp)) }

/** Action = pictogramme rond + légende courte. */
@Composable
internal fun Action(icone: ImageVector, legende: String, surAppui: () -> Unit) =
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { surAppui() }) {
        Pastille(icone, Orange, BleuNuit, 52); Text(legende, fontSize = 12.sp, color = BleuNuit, modifier = Modifier.padding(top = 4.dp))
    }

@Composable
private fun LigneInterrupteur(texte: String, valeur: Boolean, surChangement: (Boolean) -> Unit) =
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(texte, Modifier.weight(1f), color = BleuNuit, fontSize = 14.sp); Switch(valeur, surChangement)
    }

/** Ligne d'autorisation : coche verte si accordée, croix rouge sinon ; un appui ouvre le réglage du système. */
@Composable
internal fun Autorisation(texte: String, accordee: Boolean, surAppui: () -> Unit) =
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable { surAppui() }) {
        Icon(if (accordee) Icons.Filled.CheckCircle else Icons.Filled.Cancel, null, tint = if (accordee) Vert else Rouge, modifier = Modifier.size(26.dp))
        Text(texte + if (accordee) " : activé" else " : à activer (appuyez ici)", fontSize = 13.sp, color = BleuNuit, modifier = Modifier.padding(start = 8.dp))
    }

/** Choix exclusif (rond plein = choisi). */
@Composable
private fun ChoixMode(choisi: Boolean, icone: ImageVector, texte: String, surAppui: () -> Unit) =
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { surAppui() }) {
        Icon(if (choisi) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked, null, tint = Bleu, modifier = Modifier.size(24.dp))
        Text(texte, color = BleuNuit, modifier = Modifier.padding(start = 8.dp))
    }

/** Icône d'une application, dessinée dans une image (chargée une seule fois). */
@Composable
private fun IconeAppli(paquet: String) {
    val c = LocalContext.current
    val image = remember(paquet) {
        try {
            val d = c.packageManager.getApplicationIcon(paquet)
            val b = Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, 72, 72); d.draw(Canvas(b)); b.asImageBitmap()
        } catch (e: Exception) { null }
    }
    if (image != null) Image(image, null, Modifier.size(36.dp)) else Box(Modifier.size(36.dp))
}

/** Fenêtre de choix des applications : recherche, liste cochable, validation. */
@Composable
private fun ChoixApplications(titre: String, selection: Set<String>, surFermeture: () -> Unit, surValidation: (Set<String>) -> Unit) {
    val c = LocalContext.current
    val applis = remember { Applications.lancables(c) }
    var coches by remember { mutableStateOf(selection) }
    var recherche by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = surFermeture,
        title = { Text(titre) },
        text = {
            Column {
                OutlinedTextField(recherche, { recherche = it }, singleLine = true, placeholder = { Text("Rechercher") }, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(applis.filter { it.nom.contains(recherche, ignoreCase = true) }, key = { it.paquet }) { a ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { coches = if (a.paquet in coches) coches - a.paquet else coches + a.paquet }.padding(vertical = 6.dp)) {
                            IconeAppli(a.paquet)
                            Text(a.nom, Modifier.weight(1f).padding(horizontal = 10.dp), fontSize = 15.sp)
                            Checkbox(a.paquet in coches, { coches = if (it) coches + a.paquet else coches - a.paquet })
                        }
                    }
                }
            }
        },
        confirmButton = { Action(Icons.Filled.Check, "Valider") { surValidation(coches) } },
        dismissButton = { Action(Icons.Filled.Close, "Annuler") { surFermeture() } }
    )
}
