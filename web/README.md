# RépareCampus · Client Web moderne

TP2 d’IFT604/IFT717, Université de Sherbrooke, automne 2026. Contribution de NGUEMA Jean Bonheur Brillant (`nguj3557`), Théotim Dumetz (`dumt4378`) et Cheikh Ahmadou Tidiane Ly (`lyzc3821`).

## Installation sous Windows

Prérequis recommandés : Node.js 22.12+ ou 24.x, Git, VS Code, MongoDB Community ou Atlas, Chrome ou Edge. Le client utilise JavaScript natif, HTML et CSS. Node fournit le serveur Web, le mandataire HTTP et le build. Le client Web n’a aucune dépendance npm.

Depuis la racine, configurez l’API dans un premier terminal PowerShell.

```powershell
Set-Location api
npm.cmd ci
Copy-Item .env.example .env
code .env
```

Renseignez `MONGODB_URI` et un `JWT_SECRET` local. MongoDB doit être démarré. Pour la démonstration Windows, on peut isoler la base avec ces commandes dans un terminal séparé. Adaptez la version installée dans le chemin de `mongod.exe`.

```powershell
$demoDb = Join-Path $env:LOCALAPPDATA 'RepareCampus\mongo-demo'
New-Item -ItemType Directory -Force $demoDb | Out-Null
& 'C:\Program Files\MongoDB\Server\9.0\bin\mongod.exe' --bind_ip 127.0.0.1 --port 27027 --dbpath $demoDb
```

Dans `api/.env`, utilisez alors `MONGODB_URI=mongodb://127.0.0.1:27027/reparecampus_tp2_demo`. **Le seed supprime les données des collections visées. Cette commande s’applique uniquement à une base locale dédiée et jetable.**

```powershell
# Depuis api/
npm.cmd run seed
npm.cmd start
```

Dans un deuxième terminal, depuis la racine du dépôt.

```powershell
Set-Location web
npm.cmd run build
npm.cmd run preview
```

Ouvrez [RépareCampus](http://localhost:5173). Le backend répond à [la route de santé](http://localhost:3000/api/health). Pour éditer le client sans refaire le build, lancez `npm.cmd run dev` et utilisez un rafraîchissement forcé après une mise à jour d’un fichier précaché.

| Emplacement | Variable | Valeur par défaut et rôle |
| --- | --- | --- |
| `api/.env` | `PORT` | `3000`, port Express |
| `api/.env` | `MONGODB_URI` | URI de la base choisie pour l’API |
| `api/.env` | `JWT_SECRET`, `JWT_EXPIRES_IN` | Signature et durée des sessions |
| `api/.env` | `NODE_ENV` | En production, désactive le déclenchement manuel à des fins de démonstration |
| `web/.env` | `PORT` | `5173`, port du client |
| `web/.env` | `API_ORIGIN` | `http://127.0.0.1:3000`, cible du mandataire |
| Environnement du serveur Web | `HOST` | `127.0.0.1`, adresse d’écoute |

Pour une installation sur téléphone, servez le Web en HTTPS. Le proxy de déploiement doit relayer `/api/` vers Express et laisser passer le flux SSE sans mise en tampon. `localhost` convient à la démonstration PWA sur le même ordinateur.

## Comptes de démonstration

Tous utilisent le mot de passe fictif `Demo1234!`.

| Profil | Courriel | Usage |
| --- | --- | --- |
| Demande | `alex@example.test` | Créer, compléter, suivre et répondre à une estimation |
| Demande | `samira@example.test` | Vérifier la séparation entre comptes |
| Réparation | `noah@example.test` | Informatique et électronique |
| Réparation | `ines@example.test` | Appareils mobiles et vélos |
| Coordination | `mila@example.test` | Trier, affecter, planifier et gérer les références |
| Administration | `admin@example.test` | Gérer comptes, rôles, données de référence et démonstration |

## Démonstration

1. Ouvrez deux profils de navigateur ou une fenêtre privée en parallèle. Connectez Alex et Mila.
2. Avec Alex, créez une demande **Informatique**. Indiquez le type, la marque, le problème, le contact et une URL de photo HTTP(S) joignable.
3. Avec Mila, filtrez les demandes à trier et affectez la demande à Noah. Choisissez un créneau où Noah est disponible. La fiche d’Alex se met à jour par SSE.
4. Avec Noah, ajoutez un diagnostic et une estimation. Sur l’écran d’Alex, ouvrez la notification et acceptez l’estimation. Noah peut ensuite saisir une intervention, déclarer les pièces utilisées et passer la demande à **Prête à récupérer**. Mila peut clôturer la demande en saisissant un motif.
5. Avec Admin, affichez le tableau de bord, puis déclenchez le traitement quotidien depuis **Comptes**. Avec les données fraîches du seed, il peut ne trouver aucune demande assez ancienne pour une relance.
6. Dans Chrome ou Edge, ouvrez la liste et la fiche, puis coupez le réseau avec DevTools et rechargez la page. Revenez à la liste puis à la fiche. Déconnectez le compte et vérifiez que la session n’affiche plus ces données. Le menu du navigateur propose l’installation lorsque les critères sont remplis.

## Architecture et limites

- Le client consomme les routes d’Express via `/api`. Les actions restent soumises au contrôle du serveur. Le détail expose les transitions depuis la table `ALLOWED_TRANSITIONS`.
- Le flux temps réel utilise `fetch` avec le JWT dans l’en-tête `Authorization`. À la reconnexion, les données de l’écran sont reconsultées. Les données restent en place pendant la saisie d’un formulaire.
- Le service worker conserve la structure statique et laisse transiter les routes API par le réseau. IndexedDB enregistre les lectures consultées, avec l’heure de consultation et un identifiant unique par session. Le jeton reste dans `sessionStorage` pour le rechargement de l’onglet. La déconnexion retire le jeton et efface les entrées de la session.
- Le hors ligne exige une connexion préalable et un jeton encore valide. Les écrans non consultés et les photos distantes peuvent manquer. Les formulaires ont besoin du réseau. Le cache peut contenir des informations anciennes jusqu’au retour du serveur.
- Les photos utilisent les chaînes prévues par le modèle existant. Le client accepte des liens HTTP(S) vers les images. Les documents antérieurs sans photo restent consultables et modifiables.
- Le tableau de bord agrège les demandes visibles par le rôle. Il présente les estimations, la valorisation des pièces selon leur prix actuel et l’absence de données de dépenses constatées. Le TP1 n’enregistre ni facture ni prix historique de la pièce utilisée.
- Le profil permet de modifier le nom affiché. Le rôle et les compétences sont gérés dans les comptes par une personne administratrice.

## Validation

Le test API crée des données et modifie un stock. Exécutez-le sur la base de démonstration après le seed, avec l’API en marche et les créneaux initiaux disponibles.

```powershell
npm.cmd run build
npm.cmd run test:api
```

Le scénario navigateur lance une instance Chrome de test et cible l’aperçu de production avec l’API active. Exécutez-le avec une base de démonstration. `CHROME_BIN` permet de sélectionner le navigateur Chromium et `WEB_ORIGIN` l’URL de l’aperçu.

```powershell
npm.cmd run test:browser
```

Les résultats et les limites de validation sont consignés dans le [rapport TP2](../CONTRIBUTION-TP2.md).
