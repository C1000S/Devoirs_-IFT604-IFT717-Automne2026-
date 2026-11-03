# RépareCampus — API REST (TP1a)

API REST de la clinique de réparation collaborative **RépareCampus**, bâtie avec
**Node.js**, **Express** et **MongoDB** (Mongoose). Elle couvre l'authentification,
les autorisations par rôle, le cycle de vie complet des demandes de réparation,
l'historique d'événements, les notifications persistantes, un flux temps réel
(Server-Sent Events) et un traitement planifié nocturne.

> Cours IFT604/IFT717 — Applications Internet et Mobilité — Automne 2026.

---

## 1. Prérequis

- **Node.js** 18 ou plus récent (développé et testé avec Node 22).
- **MongoDB**. Le plus simple est **Docker** (un `docker-compose.yml` est fourni à la
  racine du dépôt). Une installation locale de MongoDB ou MongoDB Atlas fonctionne aussi.

## 2. Installation

```sh
# Depuis la racine du dépôt : démarrer MongoDB
docker compose up -d

# Installer les dépendances du serveur
cd server
npm install

# Copier la configuration d'exemple
cp .env.example .env
```

## 3. Configuration (variables d'environnement)

Toute la configuration passe par le fichier `.env` (voir `.env.example`).

| Variable             | Rôle                                                        | Défaut                                     |
| -------------------- | ------------------------------------------------------------ | ------------------------------------------- |
| `PORT`             | Port HTTP du serveur                                         | `3000`                                    |
| `MONGODB_URI`      | Chaîne de connexion MongoDB                                 | `mongodb://127.0.0.1:27017/repare-campus` |
| `JWT_SECRET`       | Secret de signature des JWT (**à remplacer en prod**) | `dev-secret-...`                          |
| `JWT_EXPIRES_IN`   | Durée de validité des JWT                                  | `7d`                                      |
| `BCRYPT_ROUNDS`    | Coût du hachage bcrypt                                      | `10`                                      |
| `NIGHTLY_CRON`     | Expression cron du traitement nocturne                       | `0 23 * * *` (23 h)                       |
| `NIGHTLY_TIMEZONE` | Fuseau horaire du planificateur                              | `America/Toronto`                         |
| `DEMO_MODE`        | Active la route de déclenchement manuel du batch            | `true`                                    |
| `CORS_ORIGIN`      | Origines autorisées (CORS)                                  | `*`                                       |

> **Aucun secret réel ne doit être déposé dans Git.** Seul `.env.example` est versionné ;
> `.env` est ignoré par `.gitignore`.

## 4. Démarrage

```sh
# Charger les données de démonstration (vide puis réinsère les collections)
npm run seed

# Démarrer le serveur
npm start          # ou : npm run dev  (redémarrage auto avec --watch)
```

Le serveur écoute sur `http://localhost:3000`. Vérification : `GET /api/health`.

## 5. Comptes de démonstration

Créés par `npm run seed`. **Mot de passe commun : `password123`.**

| Rôle          | Courriel               | Compétences                |
| -------------- | ---------------------- | --------------------------- |
| Demandeur      | `alex@repare.test`   | —                          |
| Demandeuse     | `samira@repare.test` | —                          |
| Réparateur    | `noah@repare.test`   | Informatique, Électronique |
| Réparatrice   | `ines@repare.test`   | Vélos, Appareils mobiles   |
| Coordonnatrice | `mila@repare.test`   | —                          |
| Administrateur | `admin@repare.test`  | —                          |

## 6. Modèle de données (MongoDB / Mongoose)

- **User** — personne et rôle (`requester`, `repairer`, `coordinator`, `admin`),
  mot de passe haché (bcrypt), compétences (références `Category`).
- **Category** — type de réparation, `slug` stable.
- **TimeSlot** — créneau d'accueil de la clinique, éventuellement rattaché à un réparateur.
- **RepairRequest** — appareil, problème, photos, contact, priorité, état, affectation,
  créneau, diagnostic, estimation, pièces, interventions, motif de clôture, activité.
- **RepairEvent** — entrée d'historique rattachée à une demande.
- **Notification** — message persistant destiné à une personne.

### Cycle de vie d'une demande

```
to_triage ──▶ assigned ──▶ in_diagnosis ──▶ waiting ──▶ in_repair ──▶ ready_for_pickup ──▶ closed
     │            │             │              │            │
     └▶ cancelled │             └▶ not_repairable ◀─────────┘
                  └▶ to_triage (réaffectation)
```

Les transitions sont validées côté serveur (voir `src/constants.js` → `STATUS_TRANSITIONS`).
Une transition interdite renvoie `409 Conflict`.

## 7. Routes de l'API

Base : `http://localhost:3000`. Les routes protégées exigent l'en-tête
`Authorization: Bearer <token>`.

### Authentification — `/api/auth`

| Méthode | Route         | Accès       | Description                             |
| -------- | ------------- | ------------ | --------------------------------------- |
| POST     | `/register` | public       | Inscription (demandeur ou réparateur). |
| POST     | `/login`    | public       | Connexion, renvoie un JWT.              |
| GET      | `/me`       | authentifié | Profil courant.                         |

### Utilisateurs — `/api/users`

| Méthode | Route    | Accès              | Description                               |
| -------- | -------- | ------------------- | ----------------------------------------- |
| GET      | `/`    | coordination, admin | Liste (filtres `role`, `categoryId`). |
| POST     | `/`    | admin               | Créer un compte (tout rôle).            |
| PATCH    | `/:id` | admin               | Modifier rôle, état, compétences.      |

### Catégories — `/api/categories`

| Méthode | Route    | Accès                         |
| -------- | -------- | ------------------------------ |
| GET      | `/`    | authentifié                   |
| POST     | `/`    | coordination, admin            |
| PATCH    | `/:id` | coordination, admin            |
| DELETE   | `/:id` | admin (désactivation logique) |

### Créneaux — `/api/slots`

| Méthode              | Route            | Accès                           |
| --------------------- | ---------------- | -------------------------------- |
| GET                   | `/`            | réparateur, coordination, admin |
| POST / PATCH / DELETE | `/` , `/:id` | coordination, admin              |

### Demandes de réparation — `/api/repair-requests`

| Méthode | Route                      | Accès                                         | Description                                             |
| -------- | -------------------------- | ---------------------------------------------- | ------------------------------------------------------- |
| GET      | `/`                      | authentifié (portée par rôle)               | Liste, recherche et filtres (voir ci-dessous).          |
| POST     | `/`                      | demandeur (ou personnel)                       | Créer une demande.                                     |
| GET      | `/:id`                   | personnes autorisées                          | Détail + historique.                                   |
| PATCH    | `/:id`                   | demandeur (sa demande) / personnel             | Modifier les champs descriptifs.                        |
| GET      | `/:id/events`            | personnes autorisées                          | Historique seul.                                        |
| POST     | `/:id/assign`            | coordination, admin                            | Affecter à un réparateur.                             |
| POST     | `/:id/claim`             | réparateur                                    | Prendre en charge une demande compatible non assignée. |
| POST     | `/:id/status`            | personnel assigné (ou demandeur pour annuler) | Changer l'état.                                        |
| POST     | `/:id/diagnosis`         | réparateur assigné                           | Consigner un diagnostic.                                |
| POST     | `/:id/estimate`          | réparateur assigné                           | Soumettre une estimation.                               |
| POST     | `/:id/estimate/response` | demandeur                                      | Accepter / refuser l'estimation.                        |
| POST     | `/:id/interventions`     | réparateur assigné                           | Consigner une intervention.                             |
| POST     | `/:id/parts`             | réparateur assigné                           | Ajouter une pièce.                                     |

**Filtres de `GET /api/repair-requests`** : `status`, `categoryId`, `priority`,
`assignedTo`, `requesterId`, `hasEstimate` (`true`/`false`), `q` (recherche texte),
`createdFrom`, `createdTo`, `page`, `limit`, `sort`.

### Notifications — `/api/notifications`

| Méthode | Route         | Description                                      |
| -------- | ------------- | ------------------------------------------------ |
| GET      | `/`         | Notifications de la personne (`?unread=true`). |
| PATCH    | `/:id/read` | Marquer comme lue.                               |
| POST     | `/read-all` | Tout marquer comme lu.                           |

### Statistiques — `/api/stats`

| Méthode | Route         | Accès              | Description                                                                                                                     |
| -------- | ------------- | ------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| GET      | `/overview` | coordination, admin | Agrégats pour visualisation (par état, catégorie, priorité, jour, appareils fréquents, taux de réparation, délai moyen). |

### Temps réel — `/api/events` (SSE)

Flux Server-Sent Events authentifié. EventSource ne permettant pas d'en-tête,
le jeton passe en paramètre : `GET /api/events?token=<JWT>`. Chaque client ne
reçoit que les événements auxquels il a droit (`repair-request`, `repair-event`,
`notification`).

### Démonstration — `/api/demo`

| Méthode | Route              | Accès                                 | Description                                       |
| -------- | ------------------ | -------------------------------------- | ------------------------------------------------- |
| POST     | `/nightly-batch` | coordination, admin (si `DEMO_MODE`) | Déclenche immédiatement le traitement nocturne. |

## 8. Traitement nocturne

Planifié chaque soir à 23 h (`node-cron`, voir `src/services/scheduler.js`). Il repère :

- les objets **prêts à récupérer** depuis plus de 7 jours → rappel au demandeur ;
- les **estimations sans réponse** depuis plus de 3 jours → relance du demandeur ;
- les **demandes inactives** depuis plus de 5 jours → alerte à la personne assignée.

En mode démonstration, `POST /api/demo/nightly-batch` l'exécute sur-le-champ.

## 9. Scénarios de test

### Avec curl

```sh
# 1. Connexion
TOKEN=$(curl -s -X POST http://localhost:3000/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"alex@repare.test","password":"password123"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")

# 2. Lister mes demandes
curl -s http://localhost:3000/api/repair-requests -H "Authorization: Bearer $TOKEN"

# 3. Créer une demande
curl -s -X POST http://localhost:3000/api/repair-requests \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"categoryId":"<ID_CATEGORIE>","device":{"type":"téléphone","brand":"Google"},"problemDescription":"Batterie qui gonfle."}'

# 4. Écouter le flux temps réel
curl -N "http://localhost:3000/api/events?token=$TOKEN"
```

### Avec un client REST / Postman

Un fichier **`requests.http`** (compatible VS Code REST Client et importable dans
Postman) est fourni dans `server/` ; il couvre un parcours complet :
connexion → création → affectation → diagnostic → estimation → réponse → états →
batch nocturne → statistiques.

### Parcours de démonstration recommandé

1. Connexion des quatre rôles (demandeur, réparateur, coordination, admin).
2. Un demandeur crée une demande (`to_triage`).
3. La coordination l'affecte à un réparateur compétent (`assigned`).
4. Le réparateur pose un diagnostic (`in_diagnosis`) puis une estimation (`waiting`).
5. Le demandeur accepte l'estimation (`in_repair`) — une notification part au réparateur.
6. Le réparateur consigne une intervention puis passe à `ready_for_pickup`.
7. Montrer la **notification** reçue et un **événement SSE** poussé sans rechargement.
8. Déclencher le **batch nocturne** en mode démo et consulter les rappels générés.
9. Afficher la page **statistiques** (`/api/stats/overview`).

## 10. Structure du projet

```
server/
├── src/
│   ├── app.js              Assemblage Express (middlewares, routes)
│   ├── server.js           Point d'entrée (DB, HTTP, planificateur)
│   ├── constants.js        Rôles, états, transitions, types d'événements
│   ├── config/             env, connexion MongoDB
│   ├── models/             Schémas Mongoose
│   ├── middleware/         auth, validation (Zod), gestion d'erreurs
│   ├── services/           logique métier (cycle de vie, notifications, temps réel, batch, scheduler, accès)
│   ├── routes/             routes REST par ressource
│   └── seed/               données de démonstration
└── README.md
```

## 11. Choix techniques

- **Authentification** : courriel + mot de passe, hachage **bcrypt**, session **JWT**.
- **Autorisation** : contrôle **côté serveur** par rôle (middleware `authorize`) et par
  ressource (`services/access.js`) — une personne n'accède qu'aux demandes autorisées.
- **Validation** : schémas **Zod** par route ; erreurs JSON uniformes (`middleware/error.js`).
- **Temps réel** : **SSE** via un bus d'événements interne, filtré par droits.
- **Planification** : **node-cron** + déclencheur manuel pour la démonstration.
