# RépareCampus — Client Android (TP1b)

Client Android natif du projet RépareCampus (IFT604/IFT717 — Automne 2026).
Il consomme l'API REST du TP1a (`RepareCampus-server`).

## Équipe

| Nom | CIP |
| --- | --- |
| NGUEMA Jean Bonheur | nguj3557 |
| Théotim Dumetz | dumt4378 |
| Cheikh Ahmadou Tidiane Ly | lyzc3821 |

## Stack

Kotlin · Jetpack Compose + Material 3 · Navigation Compose · Retrofit + OkHttp + kotlinx.serialization ·
DataStore (session) · Room (cache hors ligne, brouillons, file de synchro) · WorkManager · OkHttp SSE · Coroutines/Flow · architecture MVVM.

## Prérequis

- Android Studio (ou seulement l'Android SDK : platform 35, build-tools 35, platform-tools)
- JDK 17 (celui d'Android Studio, ou `brew install openjdk@17`)
- L'API TP1a démarrée (`npm run seed` puis `npm run dev` dans `RepareCampus-server`)
- Un appareil Android 8.0+ (API 26), testé sur Samsung Galaxy Tab S8 Ultra, ou un émulateur

## Configuration de l'URL de l'API

Un seul endroit : la propriété `apiBaseUrl` dans [`gradle.properties`](gradle.properties).

| Cible | `apiBaseUrl` | Remarque |
| --- | --- | --- |
| Appareil USB (par défaut) | `http://localhost:3000/` | Lancer `adb reverse tcp:3000 tcp:3000` après avoir branché l'appareil |
| Appareil en Wi-Fi | `http://<IP du Mac>:3000/` | Même réseau ; autoriser Node dans le pare-feu macOS |
| Émulateur | `http://10.0.2.2:3000/` | |

Surcharge ponctuelle : `./gradlew installDebug -PapiBaseUrl=http://10.0.2.2:3000/`.
L'URL utilisée est affichée au bas de l'écran de connexion.

## Démarrage (appareil USB)

1. Sur la tablette : activer les **Options pour développeurs** puis le **Débogage USB**, brancher le câble et accepter l'empreinte RSA.
2. Dans le dossier du projet :

```sh
adb devices                      # l'appareil doit apparaître comme « device »
adb reverse tcp:3000 tcp:3000    # à refaire à chaque rebranchement
./gradlew installDebug           # compile et installe l'application
```

Ou bien ouvrir le dossier dans Android Studio et lancer la configuration `app`.

## Comptes de démonstration (issus du seed de l'API, mot de passe `Demo1234!`)

| Rôle | Courriel |
| --- | --- |
| Demandeur | alex@example.test |
| Demandeuse | samira@example.test |
| Réparateur | noah@example.test |
| Réparatrice | ines@example.test |
| Coordonnatrice | mila@example.test |
| Administrateur | admin@example.test |

L'inscription publique crée toujours un compte demandeur.

## Fonctionnalités (énoncé §4.2)

| Exigence | Réalisation |
| --- | --- |
| S'inscrire, se connecter, se déconnecter | Écrans de connexion et d'inscription ; jeton JWT et rôle conservés (DataStore) ; un `401` ramène à la connexion |
| L'UI reflète le rôle | Titre, portée de la liste, bouton de création et actions du détail varient selon le rôle |
| Lister / rechercher / filtrer | Grille adaptée à la tablette, recherche sans accents, filtres état/catégorie/priorité, « masquer terminées » ; réparateur : *Assignées à moi / Non assignées / Toutes* |
| Créer une demande, détail | Formulaire validé ; détail avec suivi technique, **historique** et **notifications liées** |
| Demandeur : répondre à une estimation | Accepter / refuser (avec confirmation) |
| Réparateur : demandes assignées, états autorisés | Diagnostic, estimation, intervention, changement d'état limité aux transitions permises (`ALLOWED_TRANSITIONS`) |
| Coordination | Affectation (personnes compétentes pour la catégorie en premier), changement d'état |
| Rafraîchir | À l'ouverture, au retour au premier plan, bouton, tirer-pour-rafraîchir, et en direct (flux SSE) |
| Notifications Android en arrière-plan | Notification système ; toucher ouvre la demande |
| ⭐ Room hors ligne | Cache des demandes récentes (+ historique), catégories, notifications, brouillons ; file d'actions synchronisée au retour du réseau |

## Mode hors ligne (Room)

- **Room est la source de vérité** : l'interface n'observe que la base locale ; le réseau ne fait que la mettre à jour.
- **Cache** : 200 demandes les plus récentes (document JSON complet), historique préchargé pour les 20 plus récentes, catégories, notifications, date de dernière synchronisation.
- **Brouillons** : enregistrés automatiquement pendant la saisie ; un envoi qui échoue conserve le brouillon.
- **File d'actions** : sans réseau, une action (estimation, état, diagnostic, intervention, affectation, création, lecture d'une notification) est appliquée localement, signalée « non synchronisée » et mise en file.
- **Synchronisation** : WorkManager (contrainte réseau, fonctionne même app fermée), au retour du réseau, avant chaque rafraîchissement, et par une sonde de l'API toutes les 15 s quand elle est injoignable.
- **Conflits** : si le serveur refuse une action (ex. transition devenue invalide), elle est marquée en échec avec le message du serveur (icône de synchronisation → panneau) et l'état du serveur l'emporte. Une création refusée redevient un brouillon.

### Scénario de démo hors ligne

1. Se connecter (ex. `alex@example.test`), laisser la liste se charger, ouvrir une demande.
2. Couper le réseau : mode avion sur l'appareil. En USB avec `adb reverse`, le câble transporte aussi l'API : retirer en plus le tunnel avec `adb reverse --remove-all`.
3. Fermer complètement l'application puis la rouvrir : le bandeau « Hors ligne » s'affiche avec la date de dernière synchronisation ; la liste, le détail et l'historique restent consultables.
4. Créer une demande : elle apparaît « En attente d'envoi ». Une action sur une demande apparaît « Non synchronisée ».
5. Rétablir le réseau (`adb reverse tcp:3000 tcp:3000`) : la file part automatiquement en quelques secondes.

## Notifications

- **Temps réel** : flux SSE `/api/events` ouvert tant que la session est active. Au premier plan, l'affichage se met à jour en direct, sans notification système.
- **Arrière-plan** : chaque nouvelle notification de l'API produit une notification système (canal « Suivi des demandes »).
- **Application gelée ou tuée** : depuis Android 14, une app en cache est gelée et le flux SSE s'arrête. Un worker WorkManager vérifie donc les nouvelles notifications environ toutes les minutes tant que l'app est en arrière-plan, plus une vérification toutes les 15 min en filet de sécurité.
- Toucher une notification ouvre le détail de la demande. La permission est demandée après la connexion (Android 13+).
- Pour la démo : mettre l'app en arrière-plan, puis changer l'état d'une demande depuis un autre compte (ou via curl).

## Architecture

```
app/src/main/java/ca/udes/reparecampus/
├── AppContainer.kt          # injection de dépendances manuelle, déclencheurs de synchro
├── RepareCampusApp.kt       # suivi premier plan / arrière-plan
├── data/
│   ├── remote/              # Retrofit (RepareCampusApi), DTO, intercepteur JWT, erreurs
│   ├── local/               # Room : brouillons, cache, file d'actions
│   ├── repository/          # AuthRepository, RequestRepository (source de vérité Room), DraftRepository
│   ├── sync/                # SyncOp, NetworkMonitor, SyncScheduler/SyncWorker (WorkManager)
│   ├── realtime/            # client SSE
│   └── session/             # SessionStore (DataStore) : jeton + rôle
├── notifications/           # AppNotifier, NotificationPollWorker
├── domain/                  # Role, Session
└── ui/                      # écrans Compose + ViewModels (MVVM), navigation typée, thème
```

## Dépendance à l'API (TP1a)

Le client a révélé un bogue dans l'API, corrigé dans `RepareCampus-server/src/routes/repairRequests.routes.js` (`canView`). `GET /api/repair-requests/:id` fait un `populate` de `requester` et `assignedTo`, puis compare `ref.toString()` à l'identifiant de la personne connectée. Avec un document peuplé, cette comparaison échoue toujours : **demandeur et réparateur recevaient un 403 sur le détail de leurs propres demandes**. Le correctif compare les `_id` (fonction `refId`). Il doit être reporté dans le dépôt de l'API.

## Limites connues

- Les messages de notification générés par l'API contiennent les codes d'état bruts (« Votre demande est maintenant : in_repair. ») : c'est le texte du serveur.
- Les notifications en arrière-plan reposent sur le SSE et l'interrogation périodique. Il n'y a pas de push FCM, car l'API n'en propose pas. Le délai peut donc atteindre environ une minute quand l'app est gelée, et davantage en mode Doze.
- Une déconnexion efface le cache et la file d'actions non envoyées (une confirmation est demandée s'il en reste).
- Pas de migrations Room écrites : un changement de schéma recrée la base.
