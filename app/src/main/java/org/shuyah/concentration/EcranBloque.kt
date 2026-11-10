package org.shuyah.concentration

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Écran affiché à la place d'une application bloquée : cadenas, heure de fin et pictogramme de retour à l'accueil. */
class EcranBloque : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent { Contenu(Reglages.minutesRestantes(this)) { accueil() } }
    }

    /** Retour à l'écran d'accueil du téléphone, puis fermeture de cet écran. */
    private fun accueil() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    @Composable
    private fun Contenu(restant: Int?, surAccueil: () -> Unit) {
        Column(Modifier.fillMaxSize().background(Color(0xFF0B1F3A)).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Filled.Lock, null, tint = Color.White, modifier = Modifier.size(72.dp))
            Text("Concentration", color = Color.White, fontSize = 26.sp, modifier = Modifier.padding(top = 16.dp))
            Text(if (restant != null) "Cette application est bloquée jusqu'à ${heureFin(restant)}." else "Cette application n'est plus bloquée.",
                color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 32.dp))
            // Règle 14 : pictogramme rond avec légende, jamais un bouton à texte
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { surAccueil() }) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Color(0xFFF6A35B)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Home, "Accueil", tint = Color(0xFF0B1F3A), modifier = Modifier.size(34.dp))
                }
                Text("Accueil", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }

    /** Heure de fin « 07:00 » à partir des minutes restantes. */
    private fun heureFin(restant: Int): String {
        val t = (System.currentTimeMillis() / 60000L + restant) * 60000L
        val c = java.util.Calendar.getInstance().apply { timeInMillis = t }
        return "%02d:%02d".format(c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
    }
}
