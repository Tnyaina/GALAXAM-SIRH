# Sprint 4 — Complexity & Navigability Refactor

## Décision structurante

L'organisation actuelle est **domain-first dans chaque couche** : `pages/paie`, `components/paie`, `hooks/paie`, `api/paie` (même convention pour pointage et congés). Une migration intégrale vers `features/` déplacerait plus de 300 fichiers et changerait la plupart des imports sans réduire une frontière métier ni un saut runtime. Elle rendrait en outre plus difficile la découverte des composants qui servent plusieurs pages d'un domaine.

Décision : **ne pas effectuer de migration feature-first mécanique**. Les domaines sont déjà le premier terme des chemins ; les règles d'emplacement de `TARGET_ARCHITECTURE.md` deviennent la convention de navigation : route → `pages/<domaine>` → `components|hooks|api/<domaine>` → backend `model/<domaine>`.

## Parcours de navigation

| Workflow | Fichiers avant | Fichiers après | Sauts avant | Sauts après |
| --- | ---: | ---: | ---: | ---: |
| Créer employé | 6 | 6 | 5 | 5 |
| Modifier contrat | 7 | 7 | 6 | 6 |
| Demander / valider congé | 7 | 7 | 6 | 6 |
| Saisir / valider pointage | 7 | 7 | 6 | 6 |
| Calculer / contrôler / valider paie | 8 | 8 | 7 | 7 |
| Générer bulletin | 7 | 7 | 6 | 6 |
| Calculer IRSA | 5 | 5 | 4 | 4 |
| Calculer STC | 6 | 6 | 5 | 5 |

Ces comptes désignent les frontières nécessaires (UI, API, contrôleur, orchestration, moteur et persistance), non les fichiers importés par transitivité. Aucun saut de ces workflows n'est une délégation vide prouvée.

Repères canoniques : IRSA `model/paie/engine/IrsaCalculator`; STC `model/paie/service/StcService`; pointage `pages/pointage/GestionPointagesPage` et `model/pointage`; congés `pages/conges` et `model/conge`; employés/contrats `pages/employe`, `model/employe`, `model/contrat`.

## MICRO_FILES_ANALYSIS

| Taille | Frontend | Backend Java | Décision |
| --- | ---: | ---: | --- |
| 1–20 lignes | 14 | 47 | KEEP : types, enums, DTOs ou primitives à invariants distincts |
| 21–50 lignes | 23 | 41 | KEEP sauf alias prouvé |
| 51–100 lignes | 72 | 37 | inspection ciblée, pas de fusion mécanique |
| 100–300 lignes | 97 | 115 | cœur de cas d'usage |
| 300+ lignes | 69 | 55 | candidats à découpage futur par responsabilité, pas dans ce sprint sans règle claire |

Simplification effectuée : `SpecialDayCollisionPolicy` était un enum déprécié convertissant à l'identique vers `SpecialPremiumCollisionPolicy`. Sans consommateur de production, il a été supprimé avec ses trois surcharges de compatibilité ; les tests utilisent désormais le type canonique.

## PASS_THROUGH_ANALYSIS et SERVICES_ANALYSIS

Les services inspectés apportent soit transaction (`TransactionRunner`), orchestration de workflow, contrôles de droits/état, résolution temporelle, ou persistance JDBC. Les appels directs de modèles JDBC observés sont des lectures terminales, non une couche intermédiaire Service → Manager → Processor. Aucun service ne satisfait la preuve de suppression sans modifier une frontière HTTP ou transactionnelle.

Convention confirmée : `Service` orchestre/transactionne ; `Engine` ou `Calculator` calcule de façon déterministe ; `Resolver` choisit une valeur datée ; `Validator` contrôle un invariant ; `Mapper` transforme ; les modèles JDBC portent la persistance.

## HOOKS_ANALYSIS et COMPONENTS_ANALYSIS

Les hooks de domaine portent des effets, état serveur ou formulaires (par exemple `usePayrollCampaign`, `useDemandeCongeForm`) et sont conservés. Les composants UI sous `components/ui` sont transverses ; les composants de domaine restent sous `components/<domaine>`. Aucun singleton trivial confirmé n'a été réintégré : le gain de lecture serait inférieur au coût de mélanger présentation et orchestration de page.

## DTO_ANALYSIS

Les petits DTO analysés sont des contrats HTTP ou des valeurs de workflow. Request et response restent séparés lorsqu'ils ont des cycles/API distincts. Aucun alias ou DTO identique certain n'a été supprimé.

## Contrat TCONGE rouge

Le test attendait le code stable `TECHNICAL_VALIDITY_LOWER_BOUND` dans la description du paramètre. Le seed canonique ne le contenait pas, alors que le test et son libellé le définissaient comme contrat. Mise à jour du seed et migration idempotente `26-09-23-sp19-tconge-technical-validity-label.sql`, appliquée à PostgreSQL de test : test ciblé vert. La durée (`12`) et la sémantique métier sont inchangées.

## Runtime

PostgreSQL Docker est sain. Backend Spring et frontend Vite étaient déjà démarrés sur 8080 et 5173 ; la page frontend répond 200 et le login administrateur local répond 200 avec le compte configuré. Le compte `pointage@galaxam.com` existe mais son mot de passe n'est présent dans aucun fichier de configuration ou seed ; l'authentification avec le mot de passe administrateur retourne correctement 401. Son mot de passe n'a pas été réinitialisé, car cela modifierait une donnée d'accès réelle hors du refactoring.

## Vérification

- `mvn -Dtest=OvertimePayCalculatorTest,Phase1cBMonetaryValuationTest,TcongeP1TechnicalValidityLowerBoundUnitTest test` : succès.
- Migration TCONGE appliquée sur `sirh_galaxam_test` : `UPDATE 1`.
- Les vérifications frontend de la phase précédente restent vertes : lint, 91 tests, build et `git diff --check`.
