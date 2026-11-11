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

private val Bleu = Color(0xFF0E6BA8)
private val BleuNuit = Color(0xFF0B1F3A)
private val Vert = Color(0xFF2E9E6B)
private val Rouge = Color(0xFFD64545)
private val Orange = Color(0xFFF6A35B)

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
private fun accesNotifications(c: Context) = Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners")?.contains(c.packageName + "/") == true

/** Vrai si le service d'accessibilité (verrou) est activé. */
private fun accessibiliteActive(c: Context) = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.contains(c.packageName + "/") == true

/** Vrai si Concentration est exclue de l'économie de batterie (« Aucune restriction » : le système ne l'arrête plus). */
private fun batterieLibre(c: Context) = (c.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isIgnoringBatteryOptimizations(c.packageName)

/** Ouvre un écran de réglages ; si l'appareil ne l'a pas, ouvre la fiche de l'application. */
private fun ouvrir(c: Context, i: Intent) {
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
    val okNotif = remember(retour) { accesNotifications(c) }
    val okAcces = remember(retour) { accessibiliteActive(c) }
    val okVpn = remember(retour, tickVpn) { Pare.autorise(c) }
    // Tout changement de réglage met le pare-feu à jour (recréation du tunnel) et reprogramme les réveils
    LaunchedEffect(actif, pareFeu, toutSauf, bloquees, autorisees, plages.toList(), okVpn) { Pare.appliquer(c, forcer = true) }
    val okBatterie = remember(retour) { batterieLibre(c) }
    val restant = remember(maintenant, actif, plages.toList()) { Reglages.minutesRestantes(c) }

    Column(Modifier.fillMaxSize().background(Color(0xFFF1F5F9)).verticalScroll(rememberScrollState()).padding(16.dp).statusBarsPadding().navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

        // ---- Plages horaires ----
        Section("Plages horaires", Icons.Filled.Schedule) {
            plages.forEachIndexed { i, p ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(p.libelle(), fontSize = 17.sp, color = BleuNuit)
                        // Jours : un appui sur une lettre l'ajoute ou l'enlève (jour où la plage commence)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                            Plage.JOURS.forEachIndexed { j, l ->
                                val on = p.jours and (1 shl j) != 0
                                Box(Modifier.size(30.dp).clip(CircleShape).background(if (on) Bleu else Color(0xFFE2E8F0))
                                    .clickable { plages[i] = p.copy(jours = p.jours xor (1 shl j)); sauverPlages() }, contentAlignment = Alignment.Center) {
                                    Text(l, color = if (on) Color.White else Color(0xFF475569), fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    Icon(Icons.Filled.Delete, "Supprimer la plage", tint = Rouge, modifier = Modifier.size(28.dp).clickable { plages.removeAt(i); sauverPlages() })
                }
                HorizontalDivider()
            }
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
            Text("Si le téléphone n'affiche pas de fenêtre, ouvrez la fiche de l'application → « Économiseur de batterie » → « Aucune restriction ».", fontSize = 12.sp, color = Color(0xFF475569), modifier = Modifier.padding(top = 4.dp, start = 34.dp))
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
private fun Section(titre: String, icone: ImageVector, contenu: @Composable ColumnScope.() -> Unit) {
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
private fun Pastille(icone: ImageVector, fond: Color, couleur: Color, taille: Int = 48) =
    Box(Modifier.size(taille.dp).clip(CircleShape).background(fond), contentAlignment = Alignment.Center) { Icon(icone, null, tint = couleur, modifier = Modifier.size((taille * 0.55).dp)) }

/** Action = pictogramme rond + légende courte. */
@Composable
private fun Action(icone: ImageVector, legende: String, surAppui: () -> Unit) =
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
private fun Autorisation(texte: String, accordee: Boolean, surAppui: () -> Unit) =
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
