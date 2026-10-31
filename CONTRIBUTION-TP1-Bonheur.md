# TP1 — Comment j'ai réalisé l'API et le client Android (RépareCampus)

**Auteur : NGUEMA Jean Bonheur (CIP nguj3557)**
Cours IFT604/IFT717 — Applications Internet et Mobilité — Automne 2026

---

## 1. Ce que j'ai réalisé

J'ai conçu et développé **les deux composantes du TP1** :

- **TP1a — l'API REST** (Node.js / Express / MongoDB) : le serveur qui gère les comptes, les demandes de réparation, leur cycle de vie, les notifications et le temps réel.
- **TP1b — le client Android natif** (Kotlin / Jetpack Compose) : l'application mobile qui consomme cette API, avec un mode **hors ligne** complet.

---

## 2. Architecture générale

```
┌──────────────────────────┐        HTTP REST + JWT          ┌───────────────────────┐
│  Client Android (TP1b)   │  ───────────────────────────▶   │   API Express (TP1a)  │
│  Kotlin · Compose · Room │  ◀───────  flux SSE (temps réel) │   Node.js             │
└──────────────────────────┘                                 └───────────┬───────────┘
                                                                          │ Mongoose
                                                                          ▼
                                                                   ┌─────────────┐
                                                                   │   MongoDB   │
                                                                   └─────────────┘
```

Le client ne parle qu'à l'API (jamais directement à la base). L'API protège chaque route selon le **rôle** de la personne connectée.

---

## 3. TP1a — L'API REST

### Pile technique
Node.js · Express · MongoDB via Mongoose · authentification JWT (jsonwebtoken) · mots de passe hachés (bcrypt) · tâches planifiées (node-cron).

### Modèle de données (7 modèles Mongoose)
`User` (rôle + compétences), `Category`, `RepairRequest` (demande + cycle de vie + diagnostic + estimation + interventions + pièces), `RepairEvent` (historique), `Notification`, `Part` (catalogue de pièces + stock), `Slot` (créneaux de la clinique).

### Authentification & autorisation
- Inscription publique (toujours en « demandeur »), connexion → **jeton JWT** + profil.
- Chaque route sensible vérifie **l'identité ET le rôle** côté serveur (jamais seulement l'interface).
- Seul un admin peut créer des comptes réparateur/coordination/admin.

### Routes principales
Auth, utilisateurs, catégories, **demandes de réparation** (créer, lister/filtrer, affecter, diagnostic, estimation, réponse à l'estimation, interventions, transitions d'état), notifications, créneaux, pièces, et le flux temps réel `/api/events`.

### Cycle de vie d'une demande
`à trier → assignée → en diagnostic → (en attente) → en réparation → prête à récupérer → fermée`, avec sorties `annulée` / `non réparable`. Les transitions autorisées sont contrôlées côté serveur (`ALLOWED_TRANSITIONS`).

### Temps réel & notifications
- **SSE** (`/api/events`) : chaque client ne reçoit que les événements qui le concernent (ses demandes, ou tout pour la coordination/admin).
- Des notifications sont créées à l'affectation, au changement d'état, à l'arrivée d'une estimation, etc.

### Tâche quotidienne
Un `node-cron` tourne chaque soir à 23 h : il relance les demandes inactives, les estimations sans réponse et les objets prêts à récupérer depuis trop longtemps. Un déclenchement manuel existe pour la démo.

### Choix techniques
- Rôles et contrôle d'accès **centralisés côté serveur**.
- Historique d'événements séparé (`RepairEvent`) pour tracer chaque transition.
- Données de démonstration via `npm run seed` (comptes + catégories).

---

## 4. TP1b — Le client Android

### Pile technique
Kotlin · **Jetpack Compose + Material 3** · Navigation Compose · **Retrofit + OkHttp** (API) · kotlinx.serialization · **Room** (base locale hors ligne) · **DataStore** (session) · **WorkManager** (synchro en arrière-plan) · OkHttp SSE (temps réel) · Coroutines/Flow · architecture **MVVM**.

### Écrans
Connexion / inscription · liste des demandes (recherche + filtres, adaptée selon le rôle) · détail d'une demande (suivi, **historique**, notifications liées) · création d'une demande · actions selon le rôle (répondre à une estimation, diagnostic, estimation, intervention, changement d'état, affectation).

### Le mode hors ligne (le cœur du TP1b)
- **Room est la source de vérité** : l'interface n'observe que la base locale ; le réseau ne fait que la mettre à jour.
- **Cache** : les demandes récentes (+ historique), catégories, notifications, date de dernière synchro.
- **Brouillons** : une demande partiellement remplie est sauvegardée automatiquement.
- **File d'actions** : sans réseau, une action est appliquée localement, marquée « non synchronisée » et mise en file.
- **Synchronisation** : au retour du réseau (WorkManager, même app fermée), la file part automatiquement ; en cas de conflit, l'état du serveur l'emporte et l'action est marquée en échec avec le message du serveur.

### Notifications
- Au premier plan : mise à jour en direct via **SSE**, sans notification système.
- En arrière-plan : notification système Android ; un worker vérifie régulièrement (car Android gèle les apps en cache).

### Architecture du code
Découpage clair : `data/remote` (Retrofit, DTO, JWT), `data/local` (Room), `data/repository` (source de vérité), `data/sync` (file + WorkManager), `data/realtime` (SSE), `ui` (écrans Compose + ViewModels MVVM).

---

## 5. Comment les deux communiquent
Le client s'authentifie (`/api/auth/login`) → reçoit un **JWT** qu'il joint à chaque requête (`Authorization: Bearer …`). Il lit/écrit les demandes via les routes REST, et ouvre le flux **SSE** (`/api/events?token=…`) pour le temps réel. L'URL de l'API est configurable en un seul endroit (`gradle.properties`).

---

## 6. Difficultés rencontrées et comment je les ai résolues

1. **Bug `canView` (403 sur ses propres demandes).** `GET /api/repair-requests/:id` comparait un document *peuplé* à un identifiant → comparaison toujours fausse → demandeur et réparateur recevaient un **403** sur le détail de leurs propres demandes. **Solution :** comparer les `_id` (fonction `refId`).
2. **Format incohérent `requester` / `assignedTo`.** Ils reviennent en *objets* dans les listes mais en simples *ObjectId* après POST/PATCH. **Solution :** le client gère les deux formats.
3. **Hors ligne fiable.** Faire de **Room la source de vérité** + une **file d'actions** synchronisée au retour du réseau, plutôt que de dépendre du réseau à chaque écran.
4. **Notifications quand l'app est gelée.** Depuis Android 14, une app en cache est gelée et le SSE s'arrête → ajout d'un **worker** qui vérifie périodiquement.

---

## 7. Correspondance avec l'énoncé

**§4.1 (TP1a) :** modèles ✅ · auth + rôles ✅ · CRUD + recherche + transitions ✅ · historique + notifications ✅ · tâche quotidienne + déclenchement démo ✅ · README + comptes de test ✅.

**§4.2 (TP1b) :** compte + connexion + rôle dans l'UI ✅ · lister/rechercher/filtrer ✅ · créer + détail (historique + notifications) ✅ · répondre à une estimation ✅ · réparateur : demandes assignées + états ✅ · rafraîchir (ouverture, bouton, tirer, SSE) ✅ · notifications Android en arrière-plan ✅ · **Room hors ligne + file de synchro** ✅.

---

## 8. Comment je le démontre
1. `npm run seed` puis `npm run dev` (API).
2. Connexion avec les comptes de démo (demandeur, réparateur, coordination, admin).
3. Une demande qui traverse plusieurs états + une notification + le temps réel.
4. **Démo hors ligne** : mode avion → l'app reste consultable (liste, détail, historique) ; une action est mise en file ; retour du réseau → synchro automatique.

---

## 9. Ce que je peux expliquer (points clés)
- Le contrôle d'accès par rôle **côté serveur** et pourquoi « cacher un bouton » ne suffit pas.
- Le cycle de vie des demandes et les transitions contrôlées.
- Pourquoi **Room est la source de vérité** et comment la **file d'actions** gère le hors ligne et les conflits.
- Le temps réel (SSE) et la stratégie de notifications sous les contraintes d'Android récent.
