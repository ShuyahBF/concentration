package org.shuyah.concentration

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * BANDEAU « NOUVELLE VERSION DISPONIBLE » (v1.57), affiché en haut des onglets quand ShuyahStore publie une version plus récente.
 * « Mettre à jour » ouvre ShuyahStore ; la croix = « Plus tard » (la version est mémorisée : le bandeau ne revient que pour une version encore plus récente).
 * Compact et clair (pas de bloc noir qui masque l'écran).
 */
@Composable
fun BandeauMiseAJour() {
    val c = LocalContext.current
    var nouvelle by remember { mutableStateOf<MiseAJourStore.Nouvelle?>(null) }
    LaunchedEffect(Unit) { nouvelle = MiseAJourStore.verifier(c) }          // réseau lu en arrière-plan ; rien ne s'affiche si hors connexion
    val n = nouvelle ?: return
    Row(Modifier.fillMaxWidth().background(Color(0xFFE8F3FB)).border(BorderStroke(1.dp, Bleu)).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.SystemUpdate, null, tint = Bleu, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text("Nouvelle version ${n.nom} disponible", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = BleuNuit)
            Text("Vous avez la ${Version.VERSION}.", fontSize = 12.sp, color = Color(0xFF475569))
        }
        Button({ MiseAJourStore.ouvrirStore(c) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
            Icon(Icons.Outlined.SystemUpdate, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Mettre à jour", fontSize = 13.sp)
        }
        IconButton({ MiseAJourStore.ignorer(c, n.code); nouvelle = null }) { Icon(Icons.Filled.Close, "Plus tard", tint = Color(0xFF475569)) }
    }
}
