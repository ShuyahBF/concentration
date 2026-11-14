# Concentration (Android)

Application personnelle pour se concentrer : pendant des **plages horaires** (par défaut 20:00 → 07:00, tous les jours), elle retarde les notifications de WhatsApp / WhatsApp Business et peut verrouiller des applications, en ne laissant que celles que l'on autorise (par exemple Claude).

## Version 1.20 — pictogrammes sur toutes les actions (règle 15)
- Chaque action de l'onglet Paiements porte son pictogramme (Réinitialiser, Afficher plus, Fermer, Supprimer grisé…).
- Plus d'enregistrement « deviné » : l'interrupteur d'envoi automatique indique « Enregistré automatiquement dès que vous basculez » ; adresse, identifiant et clé s'enregistrent par le bouton « Enregistrer et envoyer maintenant », dit explicitement.

## Version 1.19 — envoi des paiements à SAWALI, suppression impossible
- **Envoi à SAWALI** : nouvelle section dans l'onglet Paiements. Dans SAWALI (menu « Transactions SMS », administrateur) on ajoute le téléphone, SAWALI donne un identifiant et une clé (montrée une seule fois) ; on les saisit ici. Les paiements partent par lots de 300, une seule fois chacun, sans doublon ; envoi automatique à chaque nouveau paiement et après chaque lecture de SMS passés (désactivable), ou bouton « Enregistrer et envoyer maintenant ».
- SAWALI affiche les transactions avec les mêmes filtres que l'onglet Paiements et peut associer un numéro à une plateforme (ex. ALBARKA) pour qu'elle retrouve ses paiements sans les ressaisir.
- **Les SMS lus ne peuvent plus être supprimés** : le bouton « Supprimer » du détail est grisé.
- Les paiements déjà enregistrés sur le téléphone sont envoyés au premier envoi.

## Version 1.18 — SMS passés et statistiques interactives des paiements
- **Lecture de TOUS les SMS passés** : seuls les SMS de paiement (Orange Money, Moov, Telecel, banques) sont gardés dans la base locale, les autres sont lus puis oubliés ; aucun doublon si on relit.
- **Deux versions de l'application** (même code, même identifiant) :
  - `concentration-1.18.apk` (« standard ») : SANS l'autorisation de lire les SMS, donc sans souci avec Google Play Protect ; les SMS passés s'importent depuis un fichier de sauvegarde XML de l'application gratuite « SMS Backup & Restore » (bouton « Importer une sauvegarde de SMS (XML) »).
  - `concentration-1.18-sms.apk` : déclare l'autorisation « lire les SMS » et ajoute le bouton « Lire tous les SMS de ma boîte ». Si Android grise l'autorisation : Réglages → Applications → Concentration → ⋮ → « Autoriser les paramètres restreints ». Play Protect peut bloquer l'installation (le suspendre le temps de l'installer).
- **Onglet Paiements refait, dans l'esprit de l'Historique d'auRor** : filtres (période dont dates personnalisées, opérateur, dépenses / recettes, recherche par bénéficiaire, numéro de transaction ou texte), chiffres clés (nombre, dépenses, recettes, solde net, moyenne, plus grosse dépense), cartes par opérateur, histogramme par jour ou par mois en montant ou en nombre, Top 10 des bénéficiaires et des montants, camembert des parts, liste paginée avec le SMS complet, partage en CSV.
- **Interactif** : toucher un opérateur, une barre de l'histogramme ou un bénéficiaire du classement filtre tout l'écran sur cet élément.
- Code : `StatsPaiements.kt` (calculs purs, testés), `ImportSms.kt`, `EcransPaiements.kt`.

## Version 1.16 — lecture corrigée des SMS Orange Money
- Montant : « 2,150.00 FCFA » est lu **2 150** (et non 150) : la virgule peut séparer les milliers, le point les décimales (« 6403.0202 » = 6 403). Les paiements déjà enregistrés sont relus à la mise à jour.
- **Bénéficiaire** (le nom répété deux fois par Orange Money n'est gardé qu'une fois) et **numéro de transaction** (ex. MP261113.1339.95787510 = date AAMMJJ . heure HHMM . numéro chronologique) ; la date et l'heure du paiement sont celles du numéro de transaction.

## Version 1.14 — Consommation Internet et base des paiements (SMS)
- **3e onglet « Consommation »** : à chaque vérification du solde (bouton « Solde » ou vérification automatique), le volume restant est mémorisé par carte SIM. La consommation entre deux vérifications est estimée (volume précédent − volume actuel) et tracée : **courbe du volume restant** et **barres de la consommation par jour** (périodes 7 / 30 / 90 jours), moyenne par jour, durée estimée du volume restant. Une recharge (volume qui remonte) est signalée et ne compte pas comme consommation.
- **4e onglet « Paiements »** : les SMS de paiement (Orange Money, Moov, Telecel, banques) sont reconnus (dépense ou recette, montant, tiers, solde) et rangés dans une **base locale** SQLite. Totaux dépenses / recettes et liste. **Version 1.15** : la lecture se fait dans la **notification** du SMS à son arrivée (accès aux notifications déjà utilisé), sans autorisation « SMS » que Google Play Protect bloque sur une application installée à la main ; seuls les SMS reçus après l'activation sont capturés. Seuls les paiements sont conservés, uniquement sur le téléphone.

## Fonctions (version 1.6)
- **Plages horaires** multiples, passant minuit, avec choix des jours (lettres L M M J V S D).
- **Quelles applications ?** : « celles que je choisis » (par défaut WhatsApp et WhatsApp Business) ou « toutes, sauf celles que j'autorise » (par défaut Claude). Le téléphone, les SMS, le clavier, les réglages et l'écran d'accueil restent toujours libres.
- **Niveau 1 — Notifications** : les notifications (messages, appels) des applications bloquées sont retardées jusqu'à la fin de la plage (ni son ni bannière) ; les messages arrivent toujours chez WhatsApp.
- **Niveau 2 — Verrou** : une application bloquée qui s'ouvre est remplacée par l'écran « Concentration » (retour à l'accueil).
- **Contacts autorisés** (version 1.5) : leurs messages et appels ne sont pas retardés par le niveau 1 (comparaison du nom affiché par WhatsApp, sans accents ni majuscules). Incompatible avec le verrou et le pare-feu, qui coupent l'application entière.
- **Sons** (version 1.4) : un son différent à chaque changement d'état — début (3 notes descendantes), fin (4 notes montantes), pause (2 notes identiques), reprise (2 notes montantes rapides). Interrupteur « Son » dans les plages horaires ; suit le volume des notifications.
- **Batterie Xiaomi** : le réglage « Pas de restriction » de MIUI ne peut pas être lu par Android ; la ligne passe au vert si Android confirme OU si l'utilisateur le confirme lui-même.
- **Niveau 3 — Pare-feu** (version 1.3) : pendant la plage, les applications bloquées sont dirigées vers un tunnel local (VPN interne) qui jette leurs paquets : **aucun message ni appel** n'entre ni ne sort ; tout ce qui attendait arrive à la fin de la plage. Les autres applications ne sont pas touchées. Rien ne quitte le téléphone. Le tunnel démarre et s'arrête aux heures des plages (réveils exacts, reprise au redémarrage), et à la pause. Nécessite l'autorisation VPN (fenêtre système, une fois) ; remplace un autre VPN actif.
- **Bouton d'accessibilité** (version 1.1) : ouvre une fenêtre « État » : état de la concentration, pause 15 min / 1 h, réactivation, nombre de notifications retardées par application.
- **Batterie** : un appui sur « Batterie sans restriction » ouvre la fenêtre du système qui exclut Concentration de l'économie de batterie (état affiché en vert / rouge).
- Signal de présence à SAWALI (règle 4) ; aucune donnée personnelle.

## Autorisations (à activer une fois, depuis l'écran principal)
1. **Accès aux notifications** (niveau 1).
2. **Service d'accessibilité « Concentration (verrou) »** (niveau 2) : il ne reçoit que le nom de l'application affichée, aucun contenu d'écran.
3. Xiaomi : démarrage automatique + batterie « Aucune restriction ».

## Limites
- Sans le niveau 3, les messages arrivent quand même chez WhatsApp : ils ne sont pas annoncés pendant la plage. Avec le niveau 3, WhatsApp n'a plus Internet (les messages attendent chez WhatsApp et arrivent après).
- Un seul VPN peut être actif à la fois : le niveau 3 remplace un autre VPN pendant la plage.
- Le blocage peut être levé par vous-même (interrupteur général) : c'est voulu.

## Compiler
Ouvrir le dossier dans Android Studio, ou `gradle assembleDebug`. Tests : `gradle testDebugUnitTest` (logique des plages).


## Vérifier auprès de l'opérateur (version 1.21)

Cas visé : un paiement est reçu **sans aucun SMS** de l'opérateur. Dans l'onglet **Paiements**, la section « Vérifier auprès de l'opérateur » propose :
- **J'attends un paiement** : un appui ; si rien n'est arrivé dans la base 30 minutes plus tard, une notification « Paiement attendu non reçu » prévient l'utilisateur ;
- **Vérifier (une carte SIM, un bouton)** : envoie le code USSD du *mini relevé*, lit la réponse, et propose d'**ajouter à la base** les opérations « reçues sans SMS » ;
- le texte brut de la réponse de l'opérateur est toujours affiché (rien n'est caché si la mise en forme n'est pas comprise).

**Les codes ne sont pas dans l'application** : ils viennent de la base centrale d'**auRor** (menu « Codes USSD » d'auRor, adresse publique `/api/android/ussd-codes`), avec **copie locale** (utilisable sans Internet) et mise à jour automatique (ETag) ou à la demande avec le bouton « Actualiser les codes d'auRor ». Si un opérateur change son menu, on corrige le code dans auRor.
Il faut y ajouter, pour chaque opérateur, un code d'usage « Mini relevé des dernières opérations » (et, si voulu, « Renvoi de la notification »). Si le code demande le code secret, écrire le mot `PIN` à sa place (ex. `*123*4*PIN#`) : il est saisi à chaque envoi et jamais conservé.

Prudence vis-à-vis de l'opérateur : jamais d'envoi automatique, 4 vérifications au plus par 24 h, 10 minutes minimum entre deux.
Limite connue : un seul échange USSD est possible (pas de navigation dans un menu) : le code doit être une séquence complète.

### Lecture de la liste « dernières opérations » (version 1.25)

Le menu « renvoi de notification » d'Orange (ex. `*400*9*6#`) affiche, **sans code secret**, la liste des dernières opérations (numéros de transaction). Android ne rend pas ce type de menu à une application :
Concentration ouvre donc le code dans le composeur et **lit la fenêtre « Infos de l'opérateur »** avec son service d'accessibilité (à activer une fois : Paramètres → Accessibilité → Concentration). Les numéros absents de la base signalent un paiement reçu sans SMS (date et heure lues dans le numéro).

Garde-fous : lecture **seulement pendant 2 minutes** après l'appui sur « Lire la liste des dernières opérations » ; seulement la fenêtre du téléphone / de l'opérateur ; **seuls les numéros de transaction sont retenus**, le reste du texte est oublié aussitôt ; Concentration ne saisit rien et ne touche jamais au code secret.
Le service d'accessibilité était auparavant limité au nom de l'application affichée (`canRetrieveWindowContent` à faux) : il peut désormais lire le contenu de la fenêtre active, mais le code ne le fait que dans les conditions ci-dessus.
Pour obtenir le SMS d'une opération (montant, nom) : choisir son numéro dans le menu (le code secret est demandé par l'opérateur) ; le SMS reçu est lu comme les autres.

### Consommation : mesures aberrantes (version 1.26)

Quand la connexion de la carte SIM est coupée (passage en Wi-Fi), l'opérateur répond parfois « 0 Mo » : le volume paraissait s'effondrer (fausse consommation de plusieurs Go) puis « remonter » (fausse recharge).
- **À la lecture** : un relevé qui fait disparaître plus de 90 % d'un volume d'au moins 500 Mo en moins d'une heure est **ignoré** (ni enregistré, ni fausse alerte « presque épuisé », la date d'expiration n'est pas effacée).
- **Dans l'historique** (y compris les mesures déjà enregistrées) : un « creux » isolé — le volume tombe à moins de 10 % puis remonte à au moins 80 % du dernier bon relevé — est écarté du graphique et des totaux ; l'écran signale « N mesure(s) ignorée(s) ». Un volume réellement épuisé (qui ne remonte pas) est conservé.
- **Affichage** : une consommation est toujours positive (« 95 Mo », jamais « −95 Mo »).

### Version compacte (fichier léger)

`gradle -Pcompact=true assembleSmsRelease` (ou `assembleStandardRelease`) produit un APK **réduit par R8** : environ **1,4 Mo au lieu de 16 Mo** (code et icônes inutilisés retirés). Il est signé avec la clé de debug, donc il **s'installe par-dessus** les versions de test déjà installées.
Utile quand le téléchargement du gros fichier échoue ou dépasse une limite d'envoi (Google Drive : 10 Mo). Par défaut (sans `-Pcompact=true`) les compilations ne changent pas.
Limite : la réduction du code peut, en théorie, retirer une classe appelée seulement par réflexion ; si un écran se comporte autrement qu'avec la version normale, revenir à celle-ci et le signaler.
