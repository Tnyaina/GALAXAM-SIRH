# Rapport final — Refactorisation structurelle globale

Date : 2026-09-23

## Résultat livré

Le chantier a établi les sources de vérité et l'architecture cible, supprimé les doublons prouvés morts et remis le frontend sous lint strict. Il n'applique aucune fusion risquée de domaine ou de table : la lecture du code, des tests et des schémas montre que les séparations restantes préservent des règles fiscales, historiques et de workflow.

## Avant / après

| Mesure | Avant | Après | Évolution |
| --- | ---: | ---: | ---: |
| Fichiers Java production | 295 | 295 | 0 |
| Fichiers JS/JSX frontend | 315 | 312 | -3 |
| Tests backend | 139 | 139 | 0 |
| Fichiers de tests frontend | 12 | 12 | 0 |
| TODO/FIXME code | 3 | 3 | 0 |
| Tables des schémas canoniques | 70 | 70 | 0 |

La réduction de volume n'était pas un objectif : aucun composant métier cohésif n'a été fusionné artificiellement.

## Suppressions et simplifications

- Alias `frontend/src/services/periodePaieService.js` supprimé. Tous les consommateurs visent désormais `frontend/src/api/pointage/periodePaieService.js`.
- `frontend/src/utils/excelExportUtils.js` et `frontend/src/utils/pointageSyntheseUtils.js` supprimés après recherche de références dans le code, les tests, les routes, la configuration et la documentation.
- Imports et variables morts retirés des pages paie ; le bloc impossible à atteindre de déclarations trimestrielles a été retiré. L'écran ne fabrique plus de dossier déclaratif sans contrat backend.

## Architecture et données

Les documents associés sont :

- `REFACTOR_SPRINT_1_AUDIT.md` : cartographie et preuves ;
- `TARGET_ARCHITECTURE.md` : conventions et points d'entrée ;
- `DUPLICATION_MAP.md` : concurrents, décision et action ;
- `DATABASE_STRUCTURE_AUDIT.md` : propriété des tables et garde-fous ;
- `REFACTOR_SPRINT_2_BACKEND_DATABASE.md` : décision backend/BDD.

Les tables et objets historiques sont maintenus : bulletins et lignes, taux datés, contrats, carrière, mouvements de congés, pointages, snapshots, transitions et STC. Il n'y a ni suppression de table/colonne, ni migration, ni endpoint supprimé dans cette livraison.

## Certification

| Vérification | Résultat |
| --- | --- |
| `npm run lint` | succès |
| `npm test -- --run` | succès — 12 fichiers, 91 tests |
| `npm run build` | succès |
| `git diff --check` | succès |
| `mvn test -q` | échec sur 1 test de caractérisation préexistant |

## BUSINESS_RULE_SUSPECT

`TcongeP1TechnicalValidityLowerBoundUnitTest` attend que la description humaine contienne le code `TECHNICAL_VALIDITY_LOWER_BOUND`. L'implémentation renvoie une description française du même concept, sans le code. Le dépôt ne permet pas de déterminer si l'API doit exposer le code stable ou si le test est trop couplé à un libellé ; changer l'un ou l'autre serait une décision de contrat potentiellement externe. La règle et les données ne sont pas modifiées.

## Décision humaine restante

Décider si les routes de compatibilité (`/pointage/*`, `/paie/periods/*`, `/parametres/*`) peuvent être retirées. Les références internes sont connues, mais le dépôt ne donne aucune donnée d'usage des bookmarks, intégrations ou liens utilisateurs externes. Recommandation : instrumenter les redirections puis les supprimer après une période sans trafic.

## Vérification runtime

Le build frontend et les tests de contrat/architecture s'exécutent localement. Le démarrage complet PostgreSQL + backend + frontend et les parcours authentifiés `ROLE_ADMIN` / `ROLE_AGENT_POINTAGE` nécessitent des identifiants, une base et un environnement de runtime ; ils ne sont pas simulés par cette refactorisation.
