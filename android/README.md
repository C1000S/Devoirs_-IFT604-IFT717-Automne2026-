# RépareCampus — Client Android (TP1b)

Client Android natif (Kotlin + Jetpack Compose) de la clinique de réparation
collaborative **RépareCampus**. Il consomme l'API REST du serveur (TP1a).

## Pile technique

- **Kotlin 2.0** + **Jetpack Compose** (Material 3), `minSdk 26`, `targetSdk 34`.
- **Architecture** : MVVM + couche données (dépôts). Injection manuelle via un
  conteneur (`AppContainer`) exposé par l'`Application` — pas de bibliothèque de DI.
- **Réseau** : Retrofit + OkHttp (intercepteur `Authorization: Bearer`), Gson.
- **Persistance locale** : Room (demandes, catégories, notifications, **brouillons**)
  → source de vérité observée par l'UI ; fonctionne **hors ligne**.
- **Session** : DataStore (jeton JWT + identité + URL du serveur).
- **Temps réel / rappels** : WorkManager interroge les notifications en arrière-plan
  et émet une **notification Android** pour chaque nouvelle non-lue.

## Lancer

1. Démarrer le serveur TP1a (`../server`, MongoDB via `docker compose`, `npm run seed`).
2. Ouvrir le dossier `android/` dans **Android Studio** (Giraffe+), laisser Gradle
   synchroniser (crée `local.properties` avec le SDK).
3. Lancer sur un **émulateur** : l'URL par défaut `http://10.0.2.2:3000/` pointe vers
   l'hôte. Sur un appareil physique, saisir l'IP locale du serveur à l'écran de connexion.
4. Se connecter avec un compte de démonstration (voir le seed du serveur), p. ex.
   `coordinator@repare.test` / `password123`, ou créer un compte.

## Parcours couverts (grille TP1b)

- **2.1 Connexion/session + écrans adaptés au rôle** : connexion/inscription, choix du
  serveur ; navigation par rôle (demandeur·euse / réparateur·rice / coordination / admin).
- **2.2 Demandes** : liste, **recherche** plein-texte, **filtres** (état, priorité),
  détail, **historique** des événements, **création**.
- **2.3 Parcours mobile** : prise en charge, **affectation** (coordination),
  **diagnostic**, **estimation** (et réponse du demandeur), **intervention**, **pièces**,
  **changements d'état** validés par le graphe du cycle de vie.
- **2.4 Actualisation manuelle, notifications, notification Android en arrière-plan**
  (WorkManager + canal de notification).
- **2.5 Room/SQLite** : cache des demandes/catégories/notifications, **brouillons**
  créés hors ligne et renvoyés ensuite, **démonstration hors ligne** (bannière + cache).
- **2.6 Qualité** : architecture en couches, appels asynchrones (coroutines/Flow),
  états de chargement/erreur/vide, ergonomie Material 3.

## Structure

```
app/src/main/java/ca/usherbrooke/reparecampus/
├─ RepareCampusApp.kt        Application + planification WorkManager
├─ MainActivity.kt           Hôte Compose + permission notifications
├─ data/                     Retrofit, Room, DataStore, dépôts (couche données)
├─ util/                     Constantes, libellés FR, connectivité, erreurs
├─ work/                     WorkManager + aide aux notifications
└─ ui/
   ├─ theme/                 Thème Material 3
   ├─ components/            Cartes, puces d'état, états Loading/Error/Offline
   ├─ navigation/            AppRoot + barre de navigation par rôle
   ├─ auth/                  Connexion / inscription
   ├─ requests/              Liste, détail, création, brouillons
   ├─ notifications/         Notifications persistantes
   └─ profile/               Profil + déconnexion
```
