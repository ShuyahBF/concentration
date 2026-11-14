# Règles du dépôt (propriétaire ShuyahBF)

Les règles permanentes du propriétaire (commits, version, commentaires, secrets, comptes rendus par e-mail…) s'appliquent ici comme sur ses autres plateformes (voir CLAUDE.md des dépôts SAWALI / ALBARKA).

## Pictogrammes sur toutes les actions et enregistrement jamais « deviné » (règle 15, 14/11/2026 : « C'est une règle importante »)
- **Toute action possible** dans un bloc, une page ou une fenêtre (enregistrer, modifier, supprimer, annuler, fermer, ouvrir, actualiser, ajouter, choisir, utiliser, exporter…)
  porte un **PICTOGRAMME** accompagné d'une légende courte. Jamais de bouton sans pictogramme, ni d'action cachée dans un simple texte souligné. Dans une ligne de tableau
  répétée, le pictogramme seul est admis s'il a une info-bulle et un libellé d'accessibilité.
- **Ne JAMAIS laisser l'utilisateur deviner** qu'en changeant de champ (sortie du champ, bascule, liste) les données sont enregistrées. Deux seules façons admises :
  1. **bouton « Enregistrer » avec pictogramme** (✓ / disquette), actif seulement quand la valeur a changé, avec la mention « modifié, non enregistré » ; ou
  2. si l'enregistrement est AUTOMATIQUE par choix, une indication visible à côté du champ (pictogramme + « enregistré automatiquement ») et une confirmation après
     chaque enregistrement (toast, coche) — jamais un enregistrement silencieux.
- Mise en œuvre : web = `lucide-react` ; Android Compose = `Icons` + libellé ; WinForms = image sur le bouton. Valable pour toute plateforme, existante ou à venir.

## Écrans colorés, conviviaux et guidés (règle 16, 14/11/2026 : « ce qui retient l'attention de l'utilisateur, ce sont des écrans riches et pas monotones »)
- **Couleurs sobres et différentes** pour mettre en valeur ce qui est important : champs clés, montants, états, échéances, références, boutons d'action. **Jamais un tableau tout en noir** :
  par exemple identifiant / référence en bleu, montants en vert (reçu) ou rouge (dû), échéances et retards en orange / rouge, action principale en bouton de couleur pleine avec son pictogramme.
- Couleurs **modestes**, juste pour attirer l'œil sur l'essentiel (pas d'arc-en-ciel) ; contraste lisible (≥ 4,5:1) et **jamais la couleur seule** : toujours aussi un pictogramme ou un texte.
- Des interfaces **plus conviviales, intuitives et marquantes, sans ajouter de complexité** : les actions importantes (imprimer, enregistrer, payer…) sont **visibles dans le tableau** (colonne d'action
  avec pictogramme coloré), jamais cachées dans un détail ou un menu replié ; l'utilisateur n'a ni à chercher ni à deviner.
- Pensée **support** : chaque action importante doit pouvoir être décrite par sa couleur et son pictogramme (« dans la colonne du bouton bleu en forme d'imprimante, cliquez dessus »).
- Cette règle complète les règles « Tableaux » (survol bleu clair, sélection orange) et « Pictogrammes » (règle 15).

## Boutons « Actualiser » et toasts (règle 17, 14/11/2026 : « C'est une règle. Ne suppose pas »)
- Après TOUTE correction ou nouveauté qui change des RÉSULTATS présentés (graphique, totaux, liste, statut, calcul), l'écran concerné a un bouton « Actualiser » (avec pictogramme,
  règle 15) que l'utilisateur peut actionner : on ne suppose jamais qu'il verra la correction apparaître tout seul.
- TOUTE actualisation affiche un TOAST « … en cours » dès l'appui, puis un toast de résultat (ce qui a été relu, nombre d'éléments, erreurs éventuelles). Jamais d'actualisation silencieuse.
- On ne suppose pas non plus ce que montre l'écran de l'utilisateur : si un résultat ne change pas après une correction, demander la capture et la version affichée, et vérifier avec les valeurs réelles.
- Mise en œuvre : Concentration = `Avis.enCours` / `Avis.resultat` ; web = `toast` de l'application ; WinForms = infobulle ou message d'état.
