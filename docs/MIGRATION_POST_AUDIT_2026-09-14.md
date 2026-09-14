# Migration post-audit du SIRH GALAXAM — 14 septembre 2026

Ce journal suit les corrections réellement stabilisées. Il ne transforme pas les anciens documents en source de vérité : le code courant, les tests et le schéma actif restent prioritaires.

## Phase 0 — Sécurité P0 — TERMINÉE

### Avant

- clé JWT, clé ImgBB et identifiants DAF dotés de valeurs sensibles par défaut ;
- bootstrap administrateur actif implicitement ;
- photos RH publiques sous `/uploads/photos/**` avec cache public ;
- documents RH contournables par un handler de fichiers statiques ;
- deux politiques CORS divergentes et wildcard possible en production ;
- durée JWT signée différente de `expiresIn` annoncé au client.

### Modifications

- secrets JWT et origines CORS rendus obligatoires hors profils local/test ;
- suppression de la clé ImgBB et du stockage RH tiers ;
- bootstrap administrateur désactivé par défaut, activable explicitement et refusé sans identifiants externes ;
- suppression du handler MVC statique et de la seconde politique CORS ;
- validation au démarrage interdisant toute origine vide ou `*` avec credentials ;
- stockage local des photos et lecture par endpoint authentifié `GET /api/employes/photos/{filename}` ;
- accès photo soumis à `EMPLOYE_READ`, écriture soumise à `EMPLOYE_WRITE`, chemins normalisés, types et taille limités, cache privé ;
- téléchargement documentaire conservé derrière son endpoint autorisé et passé en cache privé sans stockage ;
- composant frontend unique `AuthenticatedImage` pour charger toute photo via le client JWT ;
- expiration communiquée par l'API dérivée de la même propriété que la signature JWT.

### Fichiers principaux

- `backend/src/main/resources/application*.properties`
- `backend/src/main/java/mg/galaxam/sirh/config/SecurityConfig.java`
- `backend/src/main/java/mg/galaxam/sirh/config/WebConfig.java` (supprimé)
- `backend/src/main/java/mg/galaxam/sirh/securite/AdminAccountSeeder.java`
- `backend/src/main/java/mg/galaxam/sirh/securite/JwtService.java`
- `backend/src/main/java/mg/galaxam/sirh/api/controller/securite/AuthController.java`
- `backend/src/main/java/mg/galaxam/sirh/api/controller/employe/EmployePhotoController.java`
- `backend/src/main/java/mg/galaxam/sirh/api/controller/employe/DocumentRhController.java`
- `frontend/src/components/ui/data-display/AuthenticatedImage.jsx`
- `frontend/src/components/ui/data-display/Avatar.jsx`

### Tests ajoutés

`Phase0SecurityIntegrationTest` couvre : bootstrap absent, refus photo anonyme, refus cross-role Pointage, upload/lecture autorisés, cache privé, chemin documentaire statique inaccessible, CORS autorisée/refusée et cohérence JWT.

### Tests exécutés et résultat

- tests ciblés sécurité : 8/8 ;
- backend complet, run isolé final : 149/149 ;
- frontend lint : OK ;
- frontend build : OK ;
- `git diff --check` : OK ;
- démarrage `prod` sans `JWT_SECRET` : refusé pendant la création du contexte ;
- recherche des anciennes valeurs sensibles dans les sources actives : 0 résultat.

Un run backend antérieur a aussi été vert. Un chevauchement accidentel entre deux processus Maven a produit un échec de fixture intermédiaire ; le run isolé suivant confirme que le code et la suite sont déterministes lorsqu'une seule suite manipule la base de test partagée.

### Dette restante

- rotation des secrets déjà exposés à réaliser dans l'environnement d'exploitation ;
- les anciennes documentations de sécurité décrivent encore les routes publiques supprimées : mise à jour différée à la phase 11 ;
- l'autorisation objet plus fine que `EMPLOYE_READ` dépend d'une politique de visibilité salarié non encore formalisée. Aucune supposition n'est codée.

### Impact estimé

- Sécurité secrets/CORS/JWT : de critique à environ 8/10 ;
- PII fichiers RH : de critique à environ 8/10 ;
- score global : gain estimé de 6 à 9 points, à confirmer au contre-audit.

### Classification documentaire

| Document | Statut | Raison | Action |
|---|---|---|---|
| `docs/AUDIT_GLOBAL_CONTRADICTOIRE_2026-09-14.md` | KEEP | photographie correcte de l'état avant correction | conserver comme baseline datée |
| `docs/cartographie_architecturale_sirh.md` | UPDATE | décrit encore les photos comme publiques | mettre à jour en phase 11 |
| `backend/docs/securite.md` | UPDATE | contient l'ancienne clé par défaut et l'ancien matcher public | mettre à jour en phase 11 |
| `backend/docs/optimisation.md` | OBSOLETE | recommande le cache public retiré | corriger ou archiver en phase 11 |

## Phase 1 — Workflow métier backend — TERMINÉE

### Avant

- `PUT /periodes/{id}/statut` acceptait un état cible choisi par le client ;
- les transitions Pointage DAF, clôture et réouverture Paie étaient réparties entre plusieurs contrôleurs et services ;
- aucun numéro de version ne protégeait une période contre un double clic ou deux validations concurrentes ;
- la réouverture pouvait remettre en circulation des données de pointage déjà consommées ;
- le frontend disposait de plusieurs méthodes spécialisées qui contournaient un owner métier unique.

### Modifications

- création d'un unique `PayrollPeriodWorkflowService.execute(...)`, seul propriétaire des règles et effets de transition ;
- commandes explicites `OPEN`, `SUBMIT`, `APPROVE`, `REJECT`, `CLOSE` et `REOPEN` ;
- matrice état courant + commande vers état cible, permissions contrôlées côté backend et refus explicite des transitions impossibles ;
- endpoint canonique `POST /api/periodes/{id}/actions/{command}` ; l'ancienne mutation générique répond désormais `410 Gone` sans modifier la période ;
- ajout d'une version optimiste et mise à jour conditionnelle sur l'identifiant, la version et l'état initial ;
- journal append-only `periode_paie_transition` contenant commande, états, versions, auteur, motif et date ;
- gel des snapshots lors de l'approbation, clôture des données de paie uniquement après validation des bulletins et réouverture sans altérer les snapshots `CONSOMME` ;
- suppression des anciens endpoints et méthodes parallèles Pointage/Paie ;
- migration des appels React vers une seule méthode `executeCommand` ;
- retrait de la clôture du cockpit Pointage : cette action reste dans le cockpit Paie où ses prérequis peuvent être expliqués.

### Fichiers principaux

- `backend/conception/bdd/05_schema_pointage.sql`
- `backend/conception/bdd/2026-09-14_workflow_periode_paie.sql`
- `backend/src/main/java/mg/galaxam/sirh/model/paie/workflow/PayrollPeriodWorkflowService.java`
- `backend/src/main/java/mg/galaxam/sirh/model/paie/workflow/PayrollPeriodCommand.java`
- `backend/src/main/java/mg/galaxam/sirh/model/paie/workflow/PayrollPeriodDecision.java`
- `backend/src/main/java/mg/galaxam/sirh/api/controller/pointage/PeriodePaieController.java`
- `backend/src/main/java/mg/galaxam/sirh/model/paie/service/PayrollService.java`
- `frontend/src/api/pointage/periodePaieService.js`
- `frontend/src/hooks/pointage/usePeriodePaie.js`
- `frontend/src/components/paie/CockpitPaieTab.jsx`
- `frontend/src/pages/pointage/ArbitrageDafPage.jsx`

### Tests ajoutés

`PayrollPeriodWorkflowIntegrationTest` couvre : disparition effective de la mutation générique, transition et historique, double commande concurrente, refus de retour `CLOTUREE -> BROUILLON` et conservation d'un snapshot déjà consommé lors d'une réouverture.

Les tests existants Paie, Pointage, sécurité et E2E ont été migrés vers les commandes canoniques, avec version attendue explicite.

### Tests exécutés et résultat

- nouveau workflow : 5/5 ;
- sélection d'intégration migrée : 27/27 ;
- backend complet, premier run isolé : 154/154 ;
- backend complet, second run isolé : 154/154 ;
- frontend lint : OK ;
- frontend build : OK ;
- `git diff --check` : OK ;
- recherche des anciennes routes/méthodes de transition actives : 0 résultat ;
- recherche des écritures d'état de période : une seule, dans le workflow canonique.

### Dette restante

- `A_VALIDER` et l'ancien alias `TRANSMIS_DAF` coexistent encore dans le référentiel ; le workflow les traite de façon identique en attendant une migration de données justifiée ;
- les listes de périodes exposent la version, mais les capacités calculées seront généralisées avec le contrat commun de la phase 2 et le contexte backend de la phase 6 ;
- les documents décrivant les anciennes routes restent volontairement inchangés jusqu'à la phase 11.

### Impact estimé

- Workflow backend : environ 8/10 ;
- concurrence et auditabilité des périodes : environ 8/10 ;
- backend-driven sur le cycle Paie/Pointage : environ 7/10 à ce stade, avant généralisation des capacités ;
- score global : gain estimé de 5 à 7 points, à confirmer au contre-audit.

### Classification documentaire

| Document | Statut | Raison | Action |
|---|---|---|---|
| `docs/AUDIT_GLOBAL_CONTRADICTOIRE_2026-09-14.md` | KEEP | constate correctement la mutation arbitraire avant correction | conserver comme baseline datée |
| documentation API Pointage/Paie existante | UPDATE | référence les anciens endpoints spécialisés | mettre à jour en phase 11 |
