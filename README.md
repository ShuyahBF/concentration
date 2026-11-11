# Concentration (Android)

Application personnelle pour se concentrer : pendant des **plages horaires** (par défaut 20:00 → 07:00, tous les jours), elle retarde les notifications de WhatsApp / WhatsApp Business et peut verrouiller des applications, en ne laissant que celles que l'on autorise (par exemple Claude).

## Fonctions (version 1.4)
- **Plages horaires** multiples, passant minuit, avec choix des jours (lettres L M M J V S D).
- **Quelles applications ?** : « celles que je choisis » (par défaut WhatsApp et WhatsApp Business) ou « toutes, sauf celles que j'autorise » (par défaut Claude). Le téléphone, les SMS, le clavier, les réglages et l'écran d'accueil restent toujours libres.
- **Niveau 1 — Notifications** : les notifications (messages, appels) des applications bloquées sont retardées jusqu'à la fin de la plage (ni son ni bannière) ; les messages arrivent toujours chez WhatsApp.
- **Niveau 2 — Verrou** : une application bloquée qui s'ouvre est remplacée par l'écran « Concentration » (retour à l'accueil).
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
