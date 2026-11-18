package org.shuyah.concentration

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * RÈGLE 24 du propriétaire (18/11/2026) : « Tout tableau ou liste de plus de 30 lignes défile dans son cadre au lieu d'allonger la page. »
 *
 * Pour un développeur WinDev : on entoure la liste (les lignes écrites avec forEach) de ce composant en lui donnant le NOMBRE de lignes.
 *  - 30 lignes ou moins : la liste s'affiche normalement, comme avant ;
 *  - plus de 30 lignes : la liste est enfermée dans un cadre de hauteur limitée (30 lignes) avec sa propre barre de défilement ;
 *    l'écran qui la contient reste court.
 * `hauteurLigne` est la hauteur moyenne d'une ligne (à ajuster si les lignes sont plus hautes ou plus basses).
 */
const val LIGNES_MAX_SANS_DEFILEMENT = 30

@Composable
fun ListeBornee(nbLignes: Int, hauteurLigne: Dp = 56.dp, contenu: @Composable ColumnScope.() -> Unit) {
    if (nbLignes > LIGNES_MAX_SANS_DEFILEMENT) {
        Column(Modifier.heightIn(max = hauteurLigne * LIGNES_MAX_SANS_DEFILEMENT).verticalScroll(rememberScrollState()), content = contenu)
    } else {
        Column(content = contenu)
    }
}
