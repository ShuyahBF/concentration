package org.shuyah.concentration

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * FENÊTRE « ÉTAT » (ouverte par le bouton d'accessibilité) : l'état de la concentration, la mise en pause (15 min ou 1 h) ou la
 * réactivation, et le nombre de notifications retardées par application bloquée.
 */
class EcranEtat : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent { Contenu() }
    }

    /** Nom lisible d'une application à partir de son paquet. */
    private fun nomAppli(c: Context, paquet: String) = try { c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(paquet, 0)).toString() } catch (e: Exception) { paquet }

    @Composable
    private fun Contenu() {
        val c = this
        var rafraichir by remember { mutableIntStateOf(0) }              // relu après chaque action
        val enPause = remember(rafraichir) { Reglages.enPause(c) }
        val restant = remember(rafraichir) { Reglages.minutesRestantes(c) }
        val actif = remember(rafraichir) { Reglages.actif(c) }
        val compteurs = remember(rafraichir) { Reglages.compteurs(c).entries.sortedByDescending { it.value } }
        Column(Modifier.background(Color.White).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Concentration", color = Color(0xFF0B1F3A), fontSize = 20.sp)
            // ---- État ----
            val (texte, couleur) = when {
                !actif -> "Désactivée" to Color(0xFF64748B)
                enPause -> "En pause jusqu'à ${heure(Reglages.pauseJusqua(c))}" to Color(0xFFE67E22)
                restant != null -> "En cours, jusqu'à ${heure((System.currentTimeMillis() / 60000L + restant) * 60000L)}" to Color(0xFF2E9E6B)
                else -> "En attente de la prochaine plage" to Color(0xFF64748B)
            }
            Text(texte, color = couleur, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
            // ---- Actions : pictogrammes ronds avec légende (règle 14) ----
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                if (enPause) Tuile(Icons.Filled.PlayArrow, "Réactiver") { Reglages.pauseJusqua(c, 0L); rafraichir++ }
                else if (actif) {
                    Tuile(Icons.Filled.Pause, "Pause 15 min") { Reglages.pauseJusqua(c, System.currentTimeMillis() + 15 * 60_000L); rafraichir++ }
                    Tuile(Icons.Filled.HourglassBottom, "Pause 1 h") { Reglages.pauseJusqua(c, System.currentTimeMillis() + 60 * 60_000L); rafraichir++ }
                } else Tuile(Icons.Filled.PlayArrow, "Activer") { Reglages.actif(c, true); rafraichir++ }
                Tuile(Icons.Filled.Settings, "Réglages") { startActivity(Intent(c, MainActivity::class.java)); finish() }
                Tuile(Icons.Filled.Close, "Fermer") { finish() }
            }
            // ---- Notifications retardées ----
            Text("Notifications retardées", color = Color(0xFF0B1F3A), fontSize = 15.sp, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))
            if (compteurs.isEmpty()) Text("Aucune pour le moment", fontSize = 13.sp, color = Color(0xFF64748B))
            compteurs.forEach { (p, n) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(nomAppli(c, p), Modifier.weight(1f), fontSize = 14.sp, color = Color(0xFF0B1F3A))
                    Text(n.toString(), fontSize = 14.sp, color = Color(0xFF0E6BA8))
                }
            }
        }
    }

    @Composable
    private fun Tuile(icone: ImageVector, legende: String, surAppui: () -> Unit) =
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { surAppui() }) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0xFFF6A35B)), contentAlignment = Alignment.Center) {
                Icon(icone, legende, tint = Color(0xFF0B1F3A), modifier = Modifier.size(28.dp))
            }
            Text(legende, fontSize = 11.sp, color = Color(0xFF0B1F3A), modifier = Modifier.padding(top = 4.dp))
        }

    /** « 07:00 » à partir d'un instant en millisecondes. */
    private fun heure(ms: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
        return "%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
    }
}
