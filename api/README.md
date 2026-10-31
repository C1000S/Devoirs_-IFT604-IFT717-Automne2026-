# RépareCampus - API REST (TP1a)

API Node.js/Express avec MongoDB (Mongoose) pour le devoir RépareCampus (IFT604/IFT717 - Automne 2026).

## Prérequis

- Node.js 18+
- Une instance MongoDB accessible (locale ou MongoDB Atlas)

## Installation

```sh
npm install
cp .env.example .env   # puis ajustez MONGODB_URI / JWT_SECRET au besoin
```

### Démarrer MongoDB localement (macOS)

```sh
brew tap mongodb/brew
brew install mongodb-community
brew services start mongodb-community
```

Alternative : utiliser une base MongoDB Atlas gratuite et coller son URI dans `MONGODB_URI` (`.env`).

## Démarrage

```sh
npm run seed   # insère les catégories et comptes de démonstration
npm run dev    # démarre le serveur avec rechargement automatique (nodemon)
```

Le serveur écoute par défaut sur `http://localhost:3000`.

## Comptes de démonstration (mot de passe : `Demo1234!`)

| Rôle | Courriel |
| --- | --- |
| Demandeur | alex@example.test |
| Demandeuse | samira@example.test |
| Réparateur (informatique/électronique) | noah@example.test |
| Réparatrice (vélos/mobile) | ines@example.test |
| Coordonnatrice | mila@example.test |
| Administrateur | admin@example.test |

## Variables d'environnement

| Variable | Rôle |
| --- | --- |
| `PORT` | Port d'écoute HTTP (3000 par défaut). |
| `MONGODB_URI` | Chaîne de connexion MongoDB. |
| `JWT_SECRET` | Clé de signature des jetons JWT. À changer en dehors du développement. |
| `JWT_EXPIRES_IN` | Durée de validité des jetons (ex: `12h`). |
| `NODE_ENV` | `development` en local ; `production` désactive la route de démonstration. |

## Authentification

- `POST /api/auth/register` : inscription publique (toujours en tant que demandeur/demandeuse).
- `POST /api/auth/login` : retourne `{ token, user }`.
- `GET /api/auth/me` : profil de la personne connectée.
- Les routes protégées attendent l'en-tête `Authorization: Bearer <token>`.
- Le flux SSE (`/api/events`) accepte aussi le jeton en paramètre de requête (`?token=...`), car `EventSource` ne permet pas d'en-têtes personnalisés.
- Seul un administrateur ou une administratrice peut créer des comptes réparateur, coordonnateur ou admin, via `POST /api/users`.

## Routes principales

| Méthode | Route | Rôles autorisés | Description |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | public | Créer un compte demandeur. |
| POST | `/api/auth/login` | public | Connexion. |
| GET | `/api/auth/me` | authentifié | Profil courant. |
| GET | `/api/users` | admin, coordination | Lister les comptes. |
| POST | `/api/users` | admin | Créer un compte (tout rôle). |
| PATCH | `/api/users/:id/role` | admin | Modifier le rôle/compétences. |
| GET | `/api/categories` | authentifié | Lister les catégories. |
| POST/PATCH | `/api/categories` | admin, coordination | Gérer les catégories. |
| GET | `/api/repair-requests` | authentifié | Lister/filtrer selon le rôle (`status`, `category`, `priority`, `assignedTo`, `requester`, `hasEstimate`, `from`, `to`). |
| GET | `/api/repair-requests/:id` | autorisé sur la demande | Détail + historique d'événements. |
| POST | `/api/repair-requests` | demandeur | Créer une demande. |
| PATCH | `/api/repair-requests/:id/assign` | coordination, admin | Affecter une personne réparatrice. |
| PATCH | `/api/repair-requests/:id/diagnosis` | réparateur assigné | Consigner un diagnostic. |
| PATCH | `/api/repair-requests/:id/estimate` | réparateur assigné | Proposer une estimation (-> état "En attente"). |
| PATCH | `/api/repair-requests/:id/estimate-response` | demandeur propriétaire | Accepter/refuser l'estimation. |
| POST | `/api/repair-requests/:id/interventions` | réparateur assigné | Consigner une intervention. |
| PATCH | `/api/repair-requests/:id/status` | réparateur, coordination, admin | Transition d'état contrôlée (voir `ALLOWED_TRANSITIONS`). |
| GET | `/api/notifications` | authentifié | Notifications de la personne connectée. |
| PATCH | `/api/notifications/:id/read` | propriétaire, admin | Marquer comme lue. |
| GET | `/api/events` | authentifié | Flux SSE temps réel filtré par rôle. |
| POST | `/api/demo/nightly-batch` | admin (hors production) | Déclenche manuellement le traitement quotidien. |
| GET | `/api/slots` | authentifié | Lister les créneaux (filtres `from`, `to`, `repairerId`). |
| POST | `/api/slots` | coordination, admin | Créer un créneau. |
| PATCH | `/api/slots/:id` | coordination, admin | Modifier un créneau. |
| PATCH | `/api/slots/:id/availability` | coordination, admin | Valider/retirer la disponibilité d'une personne réparatrice sur un créneau. |
| DELETE | `/api/slots/:id` | coordination, admin | Supprimer un créneau. |
| PATCH | `/api/repair-requests/:id/schedule` | coordination, admin | Planifier une demande sur un créneau (vérifie capacité et disponibilité du réparateur assigné). |
| GET | `/api/parts` | authentifié | Lister le catalogue de pièces. |
| POST | `/api/parts` | coordination, admin | Ajouter une pièce (nom, SKU, coût, stock). |
| PATCH | `/api/parts/:id` | coordination, admin | Modifier une pièce (stock, coût, nom). |
| DELETE | `/api/parts/:id` | admin | Retirer une pièce du catalogue. |

## Cycle de vie des demandes

`to_triage -> assigned -> in_diagnosis -> (waiting) -> in_repair -> ready_for_pickup -> closed`, avec sorties possibles vers `cancelled` ou `not_repairable` (voir `ALLOWED_TRANSITIONS` dans `src/constants.js`).

## Traitement quotidien

- Planifié à 23h via `node-cron` (`src/jobs/nightlyJob.js`).
- Rappelle les objets prêts à récupérer depuis plus de 7 jours, signale les demandes inactives depuis plus de 5 jours et relance les estimations sans réponse.
- Déclenchement manuel de démonstration : `POST /api/demo/nightly-batch` (jeton admin), désactivé si `NODE_ENV=production`.

## Créneaux et pièces

- Les créneaux (`Slot`) représentent les plages où la clinique accueille des réparations. La coordination crée les créneaux et y valide la disponibilité des personnes réparatrices (`PATCH /api/slots/:id/availability`).
- Une demande peut être planifiée sur un créneau (`PATCH /api/repair-requests/:id/schedule`) : la route refuse si la capacité du créneau est atteinte, ou si la personne réparatrice déjà assignée n'est pas dans la liste des disponibilités du créneau.
- Le catalogue de pièces (`Part`) est géré via `/api/parts`. Le stock est déduit automatiquement lors de l'ajout d'une intervention (`POST /api/repair-requests/:id/interventions`) si des pièces sont utilisées ; la route refuse l'opération si le stock est insuffisant.

## Temps réel

`GET /api/events` ouvre un flux SSE ; chaque connexion ne reçoit que les événements pour lesquels la personne connectée est autorisée (ses propres demandes, ses demandes assignées, ou tout si coordination/admin).

## Tester avec curl

```sh
# Connexion
TOKEN=$(curl -s -X POST http://localhost:3000/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"alex@example.test","password":"Demo1234!"}' | node -pe 'JSON.parse(require("fs").readFileSync(0)).token')

# Créer une demande
curl -X POST http://localhost:3000/api/repair-requests \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"category":"mobile","device":{"type":"téléphone","brand":"Google","model":"Pixel"},"problemDescription":"Ne se connecte plus au Wifi."}'

# Lister ses demandes
curl -H "Authorization: Bearer $TOKEN" http://localhost:3000/api/repair-requests
```

## Modèle de domaine

- `User` (`src/models/User.js`) : compte, rôle, mot de passe haché (bcrypt), compétences (pour les réparateurs).
- `Category` (`src/models/Category.js`) : type de réparation.
- `RepairRequest` (`src/models/RepairRequest.js`) : demande, cycle de vie, diagnostic, estimation, interventions, pièces utilisées.
- `RepairEvent` (`src/models/RepairEvent.js`) : historique d'événements par demande.
- `Notification` (`src/models/Notification.js`) : notification par personne, liée ou non à une demande.
- `Part` (`src/models/Part.js`) : catalogue de pièces (stock, coût), référencé dans le diagnostic et les interventions.
- `Slot` (`src/models/Slot.js`) : créneau où la clinique accueille des réparations, avec les personnes réparatrices disponibles pour ce créneau ; une demande peut y être planifiée (`RepairRequest.scheduledSlot`).

## Reste à faire (équipe)

- Statistiques agrégées et vue graphique (TP2).
- Client Web (TP2), client Android (TP1b), client sémantique `/data` (TP3).
- Éventuellement : tests automatisés (Jest/Supertest), pagination des listes.
