# Contribution TP2 · RépareCampus

**NGUEMA Jean Bonheur Brillant (`nguj3557`) · Théotim Dumetz (`dumt4378`) · Cheikh Ahmadou Tidiane Ly (`lyzc3821`)**
IFT604/IFT717 · Université de Sherbrooke · Automne 2026

Le client Web dans `web/` couvre les quatre parcours de RépareCampus. Il utilise les mêmes ressources et permissions que le client Android du TP1. Le client fonctionne avec JavaScript natif et le serveur Web utilise les modules intégrés de Node, ce qui permet de le construire sans accès au registre npm. Cette décision a été prise après un échec de résolution de `registry.npmjs.org` dans l’environnement de travail.

## Intégration du TP1

La branche GitHub `bonheur` pointe sur `e2d03c4`, qui contient `api/` et le client Android. La branche `tidiane` pointe sur `659d291` et utilise une autre arborescence, avec `server/`. La version `bonheur` correspond au contrat utilisé. Le client a été développé sur une branche locale `tp2-web`, à partir de la copie Windows du TP1. Les fichiers distants utilisés pour préparer les modifications ont été comparés aux fichiers de l’instantané local à l’aide de leurs identifiants Git.

- Le backend ne disposait ni de modification de profil personnel ni de mise à jour générique de demande. `PATCH /api/auth/me` modifie uniquement le nom. `PATCH /api/repair-requests/:id` permet au propriétaire de compléter une demande active. Ces routes ne modifient pas les rôles, affectations, états ou estimations.
- La configuration CORS est complétée avec `DELETE`. Les routes et les applications existantes conservent leurs adresses.
- Les réparateurs consultent les demandes non assignées compatibles avec leurs compétences et les demandes qui leur sont assignées. La coordination sélectionne une personne qualifiée. L’affectation initiale passe à l’état **Assignée**. La réaffectation conserve l’état en cours.
- Le détail inclut les transitions existantes pour éviter une seconde table côté navigateur. Les références MongoDB sont résolues par identifiant, qu’elles soient peuplées ou non.
- Les photos sont des URL. Les graphiques agrègent les données déjà accessibles et ne nécessitent aucune route statistique. Les pièces sont valorisées au prix catalogue actuel, sans prétendre retrouver une dépense historique.

## Revue de l’énoncé

| Exigence | Mise en œuvre | Vérification |
| --- | --- | --- |
| Section 5 · connexion, profil et navigation | Connexion, inscription, déconnexion et navigation liée au rôle. Modification du nom affiché. | API vérifiée avec les quatre profils. Inscription, profil, 401 et 403 vérifiés. Parcours navigateur en attente. |
| Section 5 · demandes | Création, édition, recherche, filtres, liste et détail. Photos par URL. | Création, édition et contrôle d’accès vérifiés sur l’API réelle. Filtres et présentation en attente de validation navigateur. |
| Section 5 · tri, affectation, diagnostic, intervention, états et historique | Actions conditionnelles, affectation par compétence, créneaux, diagnostic, estimation et réponse, pièces, transitions autorisées et historique. | Affectation, planification, diagnostic, estimation, réponse, stock, historique et clôture testés sur MongoDB. |
| Section 5 · notifications et événements poussés | Consultation, lecture, écoute SSE et actualisation des écrans avec reconnexion. | Réception HTTP SSE et notification vérifiées. Actualisation côté navigateur en attente. |
| Section 5 · graphique | Graphiques par état, catégorie et réparateur. Montants séparés selon la nature des données. | Agrégation implémentée. Rendu navigateur en attente. |
| Section 5 · PWA et hors ligne | Manifest, service worker, cache IndexedDB par session et indicateur hors ligne. | Build vérifié. Installation, lecture hors ligne et purge à confirmer dans un navigateur. |
| Sections 2 et 7 · administration et démonstration | Comptes, rôles, catégories, pièces, créneaux, disponibilité et traitement quotidien. | Le test API vérifie le traitement manuel et la création/suppression d’une pièce. Les autres scénarios administratifs sont documentés. |
| Section 7 · dépôt GitLab | Contribution et documentation versionnées sur la branche de travail locale. | URL GitLab d’équipe et synchronisation à confirmer. La remise n’est pas vérifiée. |
| Sections 6 et 7 · requête sémantique | Composante TP3 de Cheikh. | Hors du périmètre d’implémentation et de validation TP2. |

## Exécution de la validation

Node.js 24.19.0, Git 2.47.1, npm 11.17.0 et MongoDB 9.0 ont été utilisés sous Windows. Le test d’intégration a exécuté le parcours sur une base locale réservée à la validation, directement sur Express puis via le mandataire Web. Il a testé également la modification de profil, la séparation entre demandeurs, les compétences, CORS et le traitement quotidien. Le serveur de l’aperçu a livré les ressources, le manifeste et le service worker versionné par HTTP. Le navigateur Chrome automatisé a échoué au lancement dans cet environnement et un autre navigateur n’a pas pu joindre le serveur local. Le scénario `npm.cmd run test:browser` est fourni pour valider les comportements correspondants avec Chrome ou Edge.

Le dossier Windows demandé se trouve dans OneDrive et l’écriture y a été refusée depuis cet environnement. Le code est versionné dans une copie de travail locale. La session GitHub disponible dispose du droit de lecture. Aucun accès au dépôt GitLab d’équipe n’a été établi.
