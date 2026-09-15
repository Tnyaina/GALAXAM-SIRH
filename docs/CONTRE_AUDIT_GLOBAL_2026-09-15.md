# Contre-audit global du SIRH GALAXAM

**Date :** 15 septembre 2026  
**Méthode :** lecture du code actif, migrations effectivement chargées par Docker, tests d'intégration et tests d'architecture. Les documents historiques ne sont pas utilisés comme source de vérité.

## Verdict

Les P0 structurels relevés le 14 septembre sont levés : aucun secret de production n'a de valeur de repli versionnée, le bootstrap administrateur est désactivé par défaut, la politique CORS est unique et explicite, et les photos/documents ne sont plus des ressources MVC publiques.

Les défauts P1 nommés de l'audit initial sont également traités dans leur périmètre : la période de paie est pilotée par commandes versionnées, les décisions sont expliquées par `BusinessDecision`, les calculs/décisions SQL identifiés ont été retirés, le contexte historique est explicite sur les parcours concernés et les écrans de workflow consomment les capacités du backend.

Le système n'est pas « parfait » : certains services historiques restent volumineux et les règles légales marquées `LEGAL_REVIEW` doivent être validées par un spécialiste. Ce sont des dettes de maintenance ou de conformité, non des P0/P1 structurels de l'audit initial.

## Preuves de gate

| Gate | Preuve observée | Résultat |
|---|---|---|
| Backend | `backend/./mvnw -q test` | **192/192**, 0 failure, 0 error |
| Wrapper | Maven Wrapper officiel 3.9.9 | **OK** |
| Frontend lint | `npm run lint` | **OK** |
| Frontend build | `npm run build` | **OK** |
| Docker | `docker compose config -q` | **OK** |
| Qualité du patch | `git diff --check` | **OK** |
| Pagination | test d'intégration avec 10 001 employés | **OK** |

## Vérification des anomalies initiales

| ID | État | Preuve actuelle |
|---|---|---|
| G-001 Secrets/bootstrap | **CLOSED** | propriétés production obligatoires, bootstrap explicite par défaut désactivé, tests sécurité |
| G-002 PII photos/documents | **CLOSED** | aucun handler statique public, endpoint authentifié, `Cache-Control: private`, tests anonymes et RBAC |
| G-003 mutation générique période | **CLOSED** | `PayrollPeriodWorkflowService.execute`, commandes et version ; l'ancien `PUT /periodes/{id}/statut` répond 410 |
| G-004 SQL métier | **CLOSED** | migration active retire `fn_jours_ouvrables` et `v_config_legale_active`; assujettissements obligatoires sans fallback |
| G-005 erreurs/API | **CLOSED** | `SirhBusinessException`, `GlobalExceptionHandler`, `ApiErrorResponse` et contrats de décision testés |
| G-006 décision React | **CLOSED** sur Paie/Pointage/Congés | capacités backend et tests d'architecture ; les comparaisons restantes sont de présentation ou d'UI locale |
| G-007 granularité | **CLOSED** au niveau des frontières critiques | orchestration, résolveurs, calculateurs et workflow séparés sans second calculateur métier |
| G-008 temporalité | **CLOSED** sur les parcours historiques critiques | période/date cible dans les résolveurs et tests avant/pendant/après/trou/chevauchement |
| G-009 populations | **CLOSED** | pagination/recherche/tri serveur, URL restaurable, test 10 001 employés |
| G-010 design system | **CLOSED** au périmètre critique | tokens et composants existants réutilisés, test de vocabulaire UI |
| G-011 route Bulletins | **CLOSED** | alias `?tab=bulletins` redirigé vers les résultats/cockpit, routes partageables testées |

## SQL actif

Les artefacts exécutables conservés servent à l'intégrité, à la projection ou à l'agrégation technique. Les triggers d'immutabilité de bulletin sont des garde-fous de persistance et ne remplacent pas une décision Java.

| Classe | Résultat |
|---|---:|
| BUSINESS_CALCULATION | **0** |
| BUSINESS_DECISION | **0** |
| LEGACY_BOUNDARY / INTEGRITY_GUARD | autorisé et traduit par l'API |

## Documents contradictoires

| Document | Statut | Raison | Action |
|---|---|---|---|
| `AUDIT_GLOBAL_CONTRADICTOIRE_2026-09-14.md` | UPDATE | photographie antérieure à la migration | conserver comme baseline, ne pas réutiliser ses scores |
| `docs/cartographie_architecturale_sirh.md` | OBSOLETE | inventaires et tailles anciens | bannière ajoutée, réviser lors d'une prochaine cartographie |
| `docs/GUIDE_COMPLET_MODULE_PAIE_GALAXAM.md` | LEGAL_REVIEW | références légales internes non suffisantes | validation RH/juridique requise |
| `backend/docs/boky/module-paie.md` | LEGAL_REVIEW | même risque de règle métier obsolète | mettre à jour après validation |

## Dettes restantes (P2)

- Les grands services historiques Pointage/Carrière restent à réduire par extractions caractérisées, une responsabilité à la fois ; aucune formule parallèle ne doit être créée.
- La visualisation complète des anomalies multi-jours mérite un drawer ou une page dédiée.
- Le pipeline CI doit encore exécuter son premier run sur GitHub ; les mêmes commandes ont été vérifiées localement.
- Les règles Madagascar signalées `LEGAL_REVIEW` ne doivent pas être modifiées sans source officielle validée.

## Scores recalculés

| Axe | Score /10 |
|---|---:|
| Backend architecture | 7,3 |
| Backend-driven | 8,2 |
| SQL | 8,5 |
| Granularité | 7,0 |
| Temporalité | 8,0 |
| Sécurité | 8,1 |
| Frontend architecture | 7,2 |
| Frontend assistant | 8,0 |
| Workflow | 7,8 |
| UI | 7,1 |
| UX | 7,2 |
| Design system | 7,0 |
| Accessibilité | 7,0 |
| Responsive | 6,8 |
| Paie backend | 8,0 |
| Paie frontend | 7,8 |
| Pointage | 7,5 |
| Congés | 7,3 |
| Carrière | 7,0 |

**GLOBAL : 76/100.**

Ce score est volontairement conservateur : il reconnaît les grands services résiduels, la dette UX mobile et l'absence de preuve d'un premier run distant de CI. Il dépasse le seuil demandé de 75 sans prétendre au 8/10 uniforme.
