# Rapport final de la campagne ECC — GALAXAM-SIRH

Date : 26 octobre 2026 (Indian/Antananarivo)
Branche : `codex/windows-sprint-2`
Référence historique : `125a5b763f6066f2cd925a4cd2879f034776bb7e`

## A. Résumé exécutif

La campagne exploratoire et les corrections ciblées ont couvert l’embauche, les contrats, les congés, le pointage, les snapshots, la paie, les bulletins, les exports et les migrations ECC. L’état de campagne conservé reste **99 scénarios : 83 PASS, 0 FAIL, 16 BLOCKED, 0 NOT RUN**. Ce nombre décrit la matrice ECC; il ne certifie pas la conformité générale.

La demi-journée sans solde a été corrigée et validée financièrement de bout en bout dans une exécution historique dédiée : snapshot de 240 minutes, retenue 23 077,37 Ar, net 858 420 Ar et PDF persistant vérifié. Le cas de deux jours a donné 960 minutes, 92 309,47 Ar de retenue et 804 140 Ar net. Le recalcul avec une retenue diverse de 1 000 Ar a été vérifié dans les preuves Phase 9. Le recalcul répété et l’actualisation de l’audit du bulletin ont des tests ciblés.

Cette dernière intervention a exécuté les tests ciblés indiqués plus bas, contrôlé en lecture seule la clôture Phase 7 et rejoué un contrôle navigateur de cette période. La matrice n’a pas été artificiellement élargie ni ses statuts modifiés. La période `ECC-P10-2026-10` demeure ouverte avec 840 minutes de retard dont l’horaire planifié n’est pas établi; elle n’a pas été transmise ou gelée.

## B. Matrice finale et scénarios bloqués

| Statut | Début de campagne (56 scénarios) | État final (99 scénarios) |
|---|---:|---:|
| PASS | 9 | **83** |
| FAIL | 1 | **0** |
| BLOCKED | 0 | **16** |
| NOT RUN | 46 | **0** |

Les 16 BLOCKED ont été relus individuellement dans la matrice et le bilan Phase 11. Ils restent bloqués pour les motifs établis ci-dessous; cette mission n’a pas fourni de preuve suffisante pour promouvoir un statut.

| ID | Fonction / niveau couvert | Précondition manquante, conséquence et suite nécessaire |
|---|---|---|
| S012 | Affectation multi-site; interface/référentiel partiellement explorés | Politique métier multi-site et portée d’accès serveur non établies. Risque d’affectation/paie élevé. Il faut une règle validée et un test serveur négatif/positif. |
| S020 | Contrats chevauchants; validations structurelles testées séparément | Aucun second contrat chevauchant n’a été activé. Il faut la règle de résolution d’un chevauchement et une fixture synthétique. |
| S021 | Renouvellement CDD; règles génériques de carrière testées | Aucun workflow de renouvellement n’a été exécuté. Il faut établir l’action métier disponible et créer une situation d’échéance synthétique sans altérer l’historique. |
| S022 | Modification rétroactive de salaire; calculs ciblés de paie vérifiés | Aucun mouvement salarial rétroactif n’a été propagé jusqu’à une période ouverte avec oracle. Risque financier élevé; il faut les règles d’effet et d’audit applicables. |
| S026 | Refus par Responsable; contrôles d’autres rôles partiellement vérifiés | `ROLE_RESPONSABLE` n’était pas présent dans les rôles actifs ECC. Il faut un compte/rôle effectif et vérifier le refus côté serveur, sans contourner la permission. |
| S028 | Congé payé vs non payé; cas non payé financier validé | Aucun cycle financier comparable avec congé payé éligible et bulletin vérifié. Il faut une demande payée conforme, un snapshot et l’oracle correspondant. |
| S031 | Heures inversées vs passage de minuit; nuit partiellement observée | L’exigence ne distingue pas les heures invalides d’un quart de nuit. Il faut une règle de validation avant de qualifier le calcul. |
| S034 | Régularisation partielle; saisie de pointage persistée | Pas de régularisation partielle suivie d’un audit/snapshot. La seule période ouverte disponible porte le retard nocturne non qualifié; le corriger seulement pour permettre le gel fausserait la fixture. |
| S043 | Absence → régularisation → recalcul paie | La chaîne complète n’a pas été exécutée sur une période admissible. Elle dépend d’un pointage cohérent, d’une régularisation autorisée et d’un snapshot frais. |
| S044 | Changement de contrat → nouvelle période | Pas de changement contractuel daté suivi d’une période et d’un calcul avec oracle de proratisation. Il faut une fixture future/historique cohérente. |
| S045 | Workflow successif par deux rôles | Deux rôles ECC existent, mais le même workflow n’a pas été accompli par les deux. Il faut deux identifiants et une période ouverte compatible. |
| S048 | Contrat et congé traversant deux années | Un congé transannuel a été testé séparément; aucun contrat transannuel associé ni effet paie combiné. Il faut une fixture et une règle de découpage d’exercice. |
| S049 | Travail de nuit traversant minuit / valorisation paie | Les heures et heures de nuit sont observées, mais aucun quart nocturne n’est affecté; l’écart calculé de 840 min (14 h) n’a pas d’oracle horaire. Pas de gel ni de bulletin pour ce cas. |
| S050 | Dimanche/jour férié / valorisation paie | Les 8 h du dimanche sont persistées dans l’UI; taux applicable, snapshot et ligne de bulletin n’ont pas été contrôlés. Il faut un cycle de paie admissible et les paramètres datés. |
| S051 | Périodes passées/futures et recalcul rétroactif | Un recalcul historique est prouvé, mais pas la matrice complète des dates futures/rétroactives et paramètres datés. Il faut des scénarios et oracles propres à chaque frontière. |
| S054 | Revue finale de régression critique | Dépend de plusieurs scénarios financiers et de sécurité toujours bloqués, notamment S022, S028, S043, S049 et S050. La revue de conformité globale ne peut donc pas être clôturée. |

### Qualification des 840 minutes

La période `ECC-P10-2026-10` (#5, du 26/09 au 25/10/2026) reste `EN_COURS`. Les 840 min proviennent du pointage synthétique #33 du 13/10 : 22:00–02:00 puis 02:00–06:00, 8 h travaillées dont 7 h de nuit. Le contrat et l’affectation désignent le site TNR, dont les horaires diurnes affichés sont 08:00–12:30 et 13:30–17:00; le site porte aussi `horaire_continu=true`, tandis que la ligne de pointage indique `horaire_continu_jour=false`. Aucun horaire de quart nocturne propre au salarié n’a été retrouvé dans les schémas examinés. Le code compare 22:00 à 08:00 et calcule 840 min. **Le calcul est expliqué, mais l’oracle attendu manque** : aucun défaut applicatif n’est confirmé. Le pointage, le calendrier et les règles n’ont pas été modifiés. Aucun gel, snapshot ou bulletin n’existe pour cette période.

## C. Corrections et anomalies

Les résultats historiques des phases restent les preuves des workflows alors exécutés; les tests finaux de cette mission sont distingués dans la section D.

| Anomalie | Cause et correction documentées | Preuve / résultat et limite |
|---|---|---|
| ECC-001 / S018 | Paramètre date CDD lié à une soustraction d’intervalle PostgreSQL avec type ambigu; paramètre typé explicitement en `date`. | 4 tests CDD ciblés PASS historiquement et dans le rejeu final; le parcours Chrome a créé un brouillon HTTP 201 persistant après rechargement. Le parcours d’embauche complet n’est pas certifié ici. |
| ECC-002 | Le service était sélectionnable après choix du département; l’accessibilité de certaines options était incohérente, mais le HTTP 500 était ECC-001. | Non confirmé comme défaut métier; S095 avait prouvé la sélection UI. Aucun nouveau scénario d’embauche lancé pendant cette clôture. |
| ECC-003 / S064 | Des dates futures planifiées entraient dans les heures présentées comme réalisées et dans certains totaux d’attendu. Filtrage des dates futures dans la synthèse. | Preuve Chrome Phase 3 : 0 h réalisées pour une période entièrement future, jours encore planifiés. Le calcul paie sur cette période n’a pas été établi. La suite avait également qualifié une incohérence de reporting planifié/réalisé; elle n’est pas assimilée à une anomalie financière. |
| ECC-004 / S067 | Les champs de saisie frontend ne passaient pas en lecture seule pour les dates futures; verrouillage UI ajouté. | Quatre champs désactivés et alerte observés. Contrôle serveur HTTP 409 vérifié séparément (S068); le contrôle serveur a empêché la persistance. L’explication UI du 409 reste limitée. |
| ECC-005 | Classification d’un congé sans solde affichée comme payé; propagation UI/API corrigée, puis calcul financier contrôlé. | Cas E2E historique Phase 7 : 2 jours, snapshot 960 min, retenue 92 309,47 Ar, net 804 140 Ar; témoin sans absence 876 500 Ar. Portée limitée aux règles/fixtures du scénario. |
| ECC-006 | La demi-journée approuvée ne diminuait pas le seuil d’heures attendu dans la synthèse; le gel héritait donc de 480 min au lieu de 240. Le seuil utilise maintenant la fraction approuvée et l’horaire applicable. | Test JUnit de non-régression, test financier PostgreSQL jetable et preuve E2E historique concordent : snapshot 240 min, retenue 23 077,37 Ar, net 858 420 Ar, PDF persistant. Le témoin deux jours reste conforme. |
| ECC-007 | Le seed ECC local omettait l’étape `RETENUE_NET` de `RETENUE_DIVERSE`; la migration ECC idempotente a aligné la configuration. Le moteur ne nécessitait pas de changement. | Chrome Phase 9 : retenue 1 000 Ar une seule fois, net du salarié concerné 875 500 Ar au lieu de 876 500 Ar; les deux autres bulletins sont restés à 876 500 Ar. Les rubriques actives ont été vérifiées à froid en Phase 12. Migration et Compose restent dans le dossier local ECC ignoré; pas intégrés au dépôt. |
| ECC-008 (défaut connexe) | L’UPDATE du bulletin ne persistait pas `date_calcul` et `calcule_par`. Ces métadonnées sont désormais écrites lors du recalcul. | Test de persistance JUnit final PASS; Phase 9 avait aussi vérifié le nouvel horodatage après recalcul UI. L’export du bulletin recalculé de Phase 9 n’a pas été rejoué. |

## D. Vérification technique finale

### Tests exécutés

| Vérification | Résultat |
|---|---|
| JUnit ciblé : `mvn -f backend/pom.xml -Dtest=CarriereTransitionValidatorCddTest,CongeLotValorisationEngineTest,PointageLeaveStatusTest,BulletinPaieUpdateAuditMetadataTest test` | 24 exécutions : 23 PASS, 0 échec, 1 ignoré (intégration DB faute d’URL dédiée dans ce premier lancement). |
| Test financier isolé : `Phase5UnpaidLeaveFinancialIntegrationTest` sur PostgreSQL dédié `galaxam_ecc_phase5_finance` | 1/1 PASS. Oracle indépendant : témoin 0 min/0 Ar/876 500 Ar; demi-journée 240 min/23 077,37 Ar/858 420 Ar; 2 jours 960 min/92 309,47 Ar/804 140 Ar. Brut, cotisations, IRSA et arrondis comparés. |
| JUnit Pointage avec `GALAXAM_FIX_TEST_DB_URL` sur la base jetable ECC recette, test configuré en lecture seule | `PointageLeaveStatusTest`: 8/8 PASS; aucun statut ou donnée métier modifié. |
| Frontend : `npm run test:api` | 62 PASS. Deux avertissements non bloquants indiquaient que le port HMR 24678 était déjà utilisé; processus laissé intact. |
| Frontend : `npm run build` | Vite 8.2.1, build réussi (2 177 modules). |
| Playwright historique `persisted-financial-workflow.spec.js` | Échec d’automatisation sur un texte exact obsolète (`POINTAGE SCELLÉ & FIGÉ — PRÊT POUR CALCUL PAIE`) avant d’atteindre les assertions suivantes. Il ne constitue pas un échec métier. |
| Playwright Chrome final, lecture seule, ECC Phase 7 | 1/1 PASS avec assertions adaptées à l’état actuel : période clôturée/immuable, message de scellement, 504 h, trois montants de bulletins visibles. Capture temporaire conservée sous `/tmp/ecc-phase12-final-output/closed-period-payroll.png`. Aucun clic de mutation, téléchargement ou changement d’état. |
| Migration à froid Phase 12 | Preuve réutilisée : instance ECC isolée, volumes/réseau distincts; ordre d’init puis migrations 001/002, rejouées sans erreur et mapping des rubriques conforme. Aucun nouveau démarrage complet pendant la mission finale. |
| `git diff --check` | PASS avant création des commits ciblés. |

La période ECC Phase 7 contrôlée en lecture seule reste `ECC-P7-2026-08` (#3), statut `CLOTUREE`. Trois bulletins sont `CLOTURE`; valeurs nettes relues : 804 140 Ar, 858 420 Ar, 876 500 Ar. Les snapshots version 1 sont `CONSOMME`, avec respectivement 16 h, 4 h et 0 h d’absence retenue. La base testée est `galaxam_ecc_phase7_e2e`; l’interface Chrome était `http://127.0.0.1:18198`. Cette relecture n’a pas recalculé ces bulletins.

La suite complète des 99 scénarios n’a pas été relancée. Les permissions serveur critiques, la clôture post-calcul Phase 9 et certains exports de recalcul n’ont pas tous été retestés pendant cette intervention. La non-régression finale porte sur les tests listés ci-dessus.

### Installation et migrations

Les preuves de Phase 12 montrent que les scripts SQL canoniques de seed n’ont pas été modifiés lors de cette dernière phase, et que les migrations ECC 001/002 sont idempotentes et rejouées dans l’ordre sur une base ECC neuve isolée. Les ressources de développement, notamment les volumes historiques `galaxam_local_pg_data` et `galaxam_pg_data`, n’ont pas été ciblées. La migration de configuration des rubriques agit dans l’environnement ECC; aucune extrapolation aux bases de développement ou de production n’est faite.

La procédure Compose locale de démarrage à froid référence des valeurs d’identifiants synthétiques dans le fichier ignoré. Pour cette raison, le Compose n’a pas été forcé dans Git. Les migrations et l’automatisation ECC ne sont donc **pas encore livrées comme installation reproductible depuis le dépôt seul**; leur preuve reste dans les artefacts privés ECC. Il faut extraire une configuration sans secrets avant toute intégration plus large.

## E. Intégration Git et provenance

### État

- HEAD initial et référence historique : `125a5b763f6066f2cd925a4cd2879f034776bb7e`.
- Branche : `codex/windows-sprint-2`, suivie de `origin/codex/windows-sprint-2`.
- HEAD après les quatre commits de code/tests et avant le présent commit documentaire : `c783f700038583abd937cbc65fb62aab041684bc`.
- HEAD final de cette mission : le commit documentaire qui porte ce rapport, après cinq commits locaux au total devant l’origine; aucun push.
- Aucun commit historique réécrit, aucune fusion, aucun changement de branche.

### Commits ciblés créés

1. `745c4e7459d531c3b912f5135c0e9ca2d49abe59` — `fix(pointage): preserve unpaid leave fractions in period totals`.
2. `609aedebf36065fc86b53aedd1cabe74a3ae1d03` — `fix(payroll): persist recalculation audit metadata`.
3. `d888d5903d9a81962a1d36c3f984b33e2fa0d85c` — `test(payroll): verify unpaid leave amounts against oracle`.
4. `c783f700038583abd937cbc65fb62aab041684bc` — `fix(contracts): type CDD rehire date parameter`.

Ces ensembles sont associés à des rapports de phase et tests précis. Aucun fichier de test ne contient d’identifiants ni de données personnelles.

Le présent rapport est livré dans un commit documentaire séparé après ces quatre commits de correction; le HEAD final contient les cinq commits locaux.

### Modifications volontairement laissées hors commits

Le worktree conserve des changements non indexés et non commités dans `.gitignore`, le schéma de conception Congés, des fichiers backend de Congés/Pointage, de la documentation Boky et plusieurs composants React. L’historique Phase 2 indiquait déjà que des changements locaux préexistants étaient mélangés; l’origine individuelle de tous ces écarts n’est pas démontrable. Les changements Congés et le schéma de conception sont donc restés intacts et exclus. Les fichiers ECC sous `.ecc/` sont ignorés par Git et contiennent des rapports, fixtures, journaux, comptes synthétiques et une configuration Compose locale; aucune commande `git add .` / `git add -A` et aucune inclusion forcée de ces artefacts n’a été faite.

Le fichier `backend/conception/bdd/04_schema_conges.sql` présente une différence locale ajoutant `date_origine_lot` et une contrainte. Il n’a pas été modifié par cette intervention finale ni commité; il doit être revu avant intégration, car il relève du schéma SQL de conception. Le seed canonique paie n’a pas été touché dans les commits de cette mission.

## F. Risques résiduels

- Les 16 scénarios listés en B ne sont pas validés; plusieurs sont critiques pour paie, horaires, permissions ou rétroactivité.
- Le profil nocturne du salarié synthétique n’est pas associé à un horaire prévu. Les 840 min sont conservées, sans correction artificielle ni gel de `ECC-P10-2026-10`.
- La preuve financière des congés et retenues est circonscrite aux fixtures, paramètres légaux et périodes décrits dans les rapports Phase 5–9.
- Le recalcul partiel par salarié est documenté comme intentionnel; l’API HTTP 200 doit être interprétée par résultats individuels et garde de complétude. Tous les scénarios d’erreur artificielle n’ont pas été rejoués pendant la clôture finale.
- Les migrations à froid sont prouvées sur l’environnement ECC jetable, mais restent dans l’automatisation privée ignorée. La nouvelle installation n’est pas reproductible depuis le dépôt seul sans extraction sécurisée de la configuration.
- Le test Playwright initial est obsolète par rapport au libellé et aux données actuelles; un test de lecture seule actualisé passe. Le test source obsolète n’a pas été modifié afin de préserver le corpus existant.
- Le dépôt reste partiellement modifié après les quatre commits, avec des changements d’origine incertaine laissés non commités.

## G. Conclusion et décision

**Terminé :** corrections ciblées de contrat CDD, de synthèse des fractions d’absence et de persistance de l’audit de recalcul Paie intégrées dans des commits locaux; test financier indépendant et non-régression ciblée exécutés; état clôturé Phase 7 et migrations ECC vérifiés par preuves existantes; rapport final produit.

**Incomplet :** 16 scénarios restent BLOCKED, la période nocturne d’octobre n’est pas validée, la valorisation dimanche/nuit n’a pas de bulletin, et l’automatisation ECC à froid n’est pas intégrée dans Git. Des changements locaux doivent encore être attribués et revus.

**État de préparation :** les corrections ciblées sont prêtes pour un audit d’intégration local sur la branche actuelle. Le projet n’est pas déclaré globalement conforme ni prêt au déploiement ou à la recette utilisateur finale. Avant une étape de déploiement, il faut clarifier l’horaire nocturne, intégrer les migrations sans secrets, revoir les modifications locales restantes, puis valider les scénarios critiques de permissions et de calcul encore bloqués.

## H. Ressources temporaires et conservation

Aucune donnée, preuve, volume Docker ou rapport n’a été supprimé; aucun service de développement n’a été arrêté. Les ressources ECC de Phase 5/7/12 et le volume de l’instance de démarrage à froid restent disponibles pour inspection. Les sorties temporaires de Playwright et la capture finale se trouvent sous `/tmp`; elles peuvent être supprimées ultérieurement après archivage souhaité. Aucun nettoyage Docker ni Git n’a été effectué.
