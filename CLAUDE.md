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

## Menus et sous-menus : majuscule à chaque mot (règle 18, 14/11/2026 : « chaque 1ère lettre des mots est en majuscule sauf si le contraire est précisé. Valable pour toutes les plateformes »)
- Dans TOUS les menus et sous-menus (barre latérale, groupes dépliables, onglets de navigation, menus d'application), **chaque mot du libellé commence par une majuscule** :
  « Gestion Des Contacts », « Demandes À SAWALI », « Contacts Clients ». Seule exception : quand le propriétaire précise le contraire pour un libellé.
- Seule la première lettre de chaque mot est forcée en majuscule : les sigles et noms déjà en majuscules (SAWALI, DG, OHADA, PI-SPI) ne sont jamais abaissés ; un mot qui suit une apostrophe garde sa casse (« L'ordre »).
- Mise en œuvre : UNE fonction par plateforme, appliquée au dessin des libellés (web : `titreMenu` ; WinForms / Android : équivalent), avec une option « casse libre » pour une exception. On n'écrit pas les majuscules à la main libellé par libellé.
- Valable pour toutes les plateformes, existantes ou à venir.

## Tableaux : toutes les colonnes triables (règle 19, 15/11/2026 : « pour tout tableau présentant des données, rends toutes les colonnes triables si les types de champs le permettent »)
- Sur TOUT tableau qui présente des données (écran, application, état), **chaque colonne est triable** d'un clic sur son en-tête dès que le type de champ le permet (texte, nombre, montant, date) :
  1er clic = croissant (▲), 2e clic = décroissant (▼), 3e clic = ordre d'origine. L'en-tête de la colonne triée est mis en évidence (orange) avec son pictogramme ▲ / ▼ ; les autres colonnes portent un pictogramme ⇅ discret.
- Les valeurs VIDES restent toujours en dernier, quel que soit le sens. Les textes se comparent sans accents ni majuscules, les nombres et montants numériquement, les dates chronologiquement (jamais comme du texte).
- Tableau PAGINÉ ou chargé par morceaux : le tri se fait côté SERVEUR, sur toutes les lignes filtrées, AVANT la pagination (trier seulement les lignes affichées donnerait un faux classement). Petit tableau entièrement chargé : tri dans le navigateur.
- Seules les colonnes d'ACTIONS (boutons) et les colonnes sans valeur comparable (case à cocher de sélection, pictogramme seul) ne sont pas triables.
- Mise en œuvre : SAWALI = `frontend/src/lib/tri.js` (`useTri`, `trierLignes`) et `frontend/src/components/ThTri.jsx` (en-tête triable), tri serveur de Transactions SMS dans `backend/routes/transactions_sms.py` (`trier`) ; e-Kol = `triTableaux.js` ; WinForms = `DataGridView` avec `SortMode = Automatic` ; Android Compose = en-tête cliquable. Même règle sur toute plateforme, existante ou à venir.
- Cette règle complète la règle 3 « Tableaux » (survol, sélection, nombres à droite).


## APK livré à CHAQUE livraison (règle du propriétaire, 16/11/2026 : « Tu ne m'envoies plus les APK »)
- Après toute livraison qui touche cette application (même quand le changement vit dans le site affiché par l'application), construire un APK **release** (non débogable, jamais « debug » dans le nom) et l'ENVOYER au propriétaire avec `SendUserFile`, dans le chat, sans attendre qu'il le demande.
- Numéro de version +1 à chaque APK ; nom du fichier livré sans « debug » ni « release » (ex. `concentration-1.38.apk`, `personnel-maison-1.33.apk`, `auror-direct-2.12.0.apk`).
- Build : option Gradle `-PsignerAvecCleLocale=true` (clé locale de l'environnement, aucune clé dans le dépôt). Vérifier que le fichier contient bien la nouvelle version avant de l'envoyer.
- Mentionner dans le compte rendu que Android refuse parfois la mise à jour (signature différente) et que la désinstallation efface les données locales.


## Pictogramme œil sur toute saisie secrète (règle 22, 16/11/2026 : « pour toute saisie d'un mot de passe, pin ou passphrase mettre un pictogramme œil pour afficher ou masquer. Applique cela à tous nos projets, web comme Android »)
- **Toute saisie d'un mot de passe, d'un code PIN, d'une phrase secrète, d'un jeton ou d'une clé secrète** (connexion, création, confirmation, changement, ouverture d'un coffre, fenêtre de sécurité d'un paiement, paramètres…) porte un **pictogramme œil** : œil ouvert = saisie masquée (un clic l'affiche), œil barré / singe = saisie visible (un clic la masque). **Masquée par défaut** à chaque ouverture.
- Le pictogramme est **dans le champ** (à droite), avec une description d'accessibilité (« Afficher la saisie » / « Masquer la saisie ») et une zone tactile d'au moins 44 px.
- **Un seul composant par plateforme**, utilisé partout (jamais un `<input type="password">` nu) : web = `ChampMotDePasse` / `MotDePasse` / `PasswordInput` (ou le champ de base de l'interface qui l'intègre quand `type="password"`) ; Android Compose = champ avec `trailingIcon` (`Visibility` / `VisibilityOff`) ; Android à vues = `FrameLayout` + œil ; WinForms = case ou bouton œil qui bascule `UseSystemPasswordChar`.
- Un nouvel écran, un nouveau projet : le champ secret reçoit l'œil dès la première version. Exception : la saisie d'un code à usage unique (code reçu par e-mail ou SMS, code de l'application d'authentification), qui n'est pas un secret durable.

## Publication dans ShuyahStore à CHAQUE livraison d'APK (règle du propriétaire, 17/11/2026 : « Publier désormais sur ShuyahStore sauf indications contraires »)
- Après chaque APK release livré, le DÉPOSER aussi dans ShuyahStore (`POST https://shuyahstore-api.onrender.com/api/dev/depot`, champ `fichier`, jeton de dépôt injecté par le secret réseau de l'environnement : ne jamais le demander ni l'afficher). Signature : clé PERMANENTE (variables ANDROID_KEYSTORE_*) ; une application déjà publiée refuse une autre clé.
- La description de la fiche doit annoncer les autorisations sensibles (SMS, liste des applications installées…), sinon une alerte bloque la publication directe.

## Plantages (1.41, 17/11/2026)
- `Plantage.kt` garde le détail de tout plantage (pile d'appels, modèle, Android) et l'écran principal l'affiche avec « Copier » ; le démarrage du pare-feu (VPN) ne peut plus faire planter l'application (erreur gardée dans `ServiceVpn.erreur`, fil de lecture entièrement protégé).

## Plantage du VPN : cause trouvée (1.42, 17/11/2026)
- Message réel relevé sur le téléphone du propriétaire : « IllegalArgumentException : Can only use lower 16 bits for requestCode ». Cause : `androidx.biometric:biometric:1.1.0` ramène `androidx.fragment:fragment:1.2.5`, dont `FragmentActivity` refuse les codes de demande (> 16 bits) des fenêtres système modernes ; TOUT `rememberLauncherForActivityResult` (VPN, autorisations, fichier, dictée) plantait. Correctif : `androidx.fragment:fragment-ktx:1.8.2` dans `app/build.gradle.kts`. À vérifier après toute mise à jour de dépendances : `gradle :app:dependencies | grep androidx.fragment:fragment:` doit montrer une version ≥ 1.3.

- Enregistrement d'écran de débogage (1.43, 17/11/2026) : petite icône discrète (rond translucide en bas à gauche, posée au-dessus de tout, même sous le verrou d'ouverture) qui filme l'écran (10 min au plus, sans son, accord d'Android à chaque fois, notification pendant l'enregistrement) ; la vidéo reste sur le téléphone, partagée par l'utilisateur, effacée après 7 jours. Code : EnregistreurEcran.kt (+ EnregistreurEcranLogique.kt testé). Retrait à la compilation : -PsansEnregistrement=true.

## Listes de plus de 30 lignes : défilement dans leur cadre (règle 24, 18/11/2026 : « C'est une règle à considérer partout sur toutes les plateformes »)
- Toute liste ou tableau de plus de 30 lignes défile dans son cadre (30 lignes de hauteur) au lieu d'allonger l'écran. Composant : `ListeBornee(nbLignes) { … }` (`ListeBornee.kt`) autour des lignes écrites avec `forEach` ; une `LazyColumn` à hauteur bornée convient aussi.

## Compte rendu par e-mail AUTOMATIQUE après toute livraison (règle du propriétaire, 19/11/2026 : « L'envoi de l'e-mail doit être automatique »)
- L'e-mail HTML à jfrancois.ouoba@gmail.com (outil Gmail, voir règle 12) part TOUT SEUL à la fin de CHAQUE livraison, sans que le
  propriétaire ait à le demander ni à le rappeler, et sans demander confirmation avant l'envoi.
- « Livraison » = tout ce qui atteint le propriétaire ou les utilisateurs, même sans fusion ni déploiement Render : dépôt d'un APK ou d'une
  nouvelle version dans ShuyahStore, publication d'une version, correction, changement de libellé, simple règle ajoutée au CLAUDE.md, fichier
  envoyé au propriétaire. Aucune exception de taille.
- Ne pas conclure « la règle ne s'applique pas, rien n'a été déployé sur Render » : en cas de doute, envoyer l'e-mail.
- Contenu : date et heure, identification (dépôt, branche, commit, version, lot), périmètre, comment vérifier, à activer, points
  d'attention, reste à faire. Jamais de secret (clé, mot de passe, chaîne de connexion) dans l'e-mail.
- Charger l'outil Gmail avec ToolSearch AVANT de dire qu'il manque ; s'il est vraiment indisponible, le dire dans le chat et ne pas clore la livraison.


## Journal du pare-feu (1.52, 19/11/2026)
- `JournalPare.kt` : tout événement du pare-feu (démarrage, tunnel ouvert ou non et pourquoi, refus du système, VPN repris par un autre, arrêt) y est noté (30 lignes), affiché au Niveau 3 avec « Actualiser » et « Copier ». Un refus du pare-feu ne doit JAMAIS être avalé en silence : il passe par `JournalPare.noter` et `ServiceVpn.erreur`.

## Liste des applications à bloquer et journal du pare-feu (1.53, propriétaire, 19/11/2026)
- Fenêtre de choix des applications : les applications COCHÉES (bloquées, ou autorisées en mode « toutes sauf ») sont EN HAUT sous « Bloquées (n) », les autres EN DESSOUS sous « Non bloquées (n) » ; cocher une application la fait remonter, la décocher la fait redescendre. Page principale : le nombre d'applications de la liste est affiché en bleu et en gras à côté de « Choisir les applications à bloquer ».
- Journal du pare-feu : « fermé par le système » n'est écrit que pour une vraie fermeture ; un remplacement voulu du tunnel (liste modifiée, redémarrage du service) n'en écrit pas (`ServiceVpn.fermerTunnel` oublie le tunnel AVANT de le fermer). Au redémarrage du téléphone, le service d'accessibilité rouvre le tunnel en 5 s (journal du 19/11/2026).

## Signature et publication des APK : clé permanente seulement, publication directe dans ShuyahStore (règle du propriétaire, 19/11/2026)
- Plus JAMAIS la clé de test (`-PsignerAvecCleLocale=true`) sauf demande EXPRESSE ; APK signé avec la clé PERMANENTE de ShuyahStore (variables `ANDROID_KEYSTORE_*`), certificat vérifié avec `apksigner verify --print-certs` (« CN=ShuyahStore »). Fichier de clé absent : ne construire aucun APK de remplacement, le dire au propriétaire.
- Publication DIRECTE dans ShuyahStore à chaque livraison (`POST /api/dev/depot`, jeton injecté par le secret réseau), saveur « standard » ; le compte rendu dit si elle est publiée ou en validation.

## Copie de sécurité chiffrée sur le téléphone (1.56, propriétaire 23/11/2026 : « sauvegarder toutes les données de toutes les applications sur le périphérique, de manière cryptée »)
- Général → « Copie de sécurité chiffrée » (`EcranCopieLocale.kt`, `CopieLocale.kt`, `CopieCrypto.kt`) : un fichier `.concsauv` (« CONC » + version + valise chiffrée AES-256-GCM, clé = phrase par PBKDF2 200 000 tours) contenant la base SQLite (paiements lus dans les SMS, mesures) et les réglages (`concentration`, `agenda_appareil`), enregistré où l'utilisateur le choisit (aucune autorisation de stockage).
  Pas de connexion ni d'identifiant dans Concentration (décision du propriétaire) : la phrase (garde-fou `GardeFouPhrase`) est la seule clé. Restauration : aperçu vérifié (nombre de paiements et de mesures), base actuelle gardée en « .avant_restauration », puis redémarrage. À essayer sur téléphone.
