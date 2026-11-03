# RépareCampus

Clinique de réparation collaborative — devoirs du cours **IFT604/IFT717**
(Applications Internet et Mobilité), Automne 2026.

## Membres de l'équipe

| Nom | CIP |
| --- | --- |
| _à compléter_ | _à compléter_ |

## Composantes

| Dossier | Composante | Remise | État |
| --- | --- | --- | --- |
| [`server/`](server/) | **TP1a** — API REST Node.js / MongoDB | 05 nov. 2026 | ✅ Implémenté |
| `android/` | **TP1b** — Client Android natif (Kotlin / Room) | 05 nov. 2026 | ⏳ À venir |
| `web/` | **TP2** — Client Web moderne (PWA) | 25 nov. 2026 | ⏳ À venir |
| (dans `server/`) | **TP3** — Web sémantique (`/data`) | 06 déc. 2026 | ⏳ À venir |

## Démarrage rapide

```sh
# 1. Lancer MongoDB
docker compose up -d

# 2. Lancer l'API
cd server
npm install
cp .env.example .env
npm run seed      # données + comptes de démonstration
npm start         # http://localhost:3000
```

Détails, routes, comptes de démonstration et scénarios : voir [`server/README.md`](server/README.md).

## Sécurité

Aucun secret réel (clé privée, mot de passe, secret OAuth) n'est déposé dans le dépôt.
Seuls les fichiers `*.env.example` sont versionnés ; les `.env` sont ignorés.
