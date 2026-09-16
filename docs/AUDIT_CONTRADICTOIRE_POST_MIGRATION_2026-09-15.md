# Audit contradictoire post-migration — GALAXAM SIRH

**Date :** 15 septembre 2026  
**Nature :** audit indépendant postérieur au rapport `CONTRE_AUDIT_GLOBAL_2026-09-15.md`.  
**Règle de preuve :** code actif, schéma réellement chargé dans `sirh_galaxam_test`, mappings HTTP et exécutions de test priment sur les rapports précédents.

## Verdict

Le précédent verdict « aucun P0/P1 structurel » et le score **76/100** ne sont pas soutenus par le code courant. Les fondations apportées par la migration sont réelles (wrapper, workflow de période, suppression des deux artefacts SQL ciblés, pagination Employés), mais plusieurs règles annoncées comme terminées ont des contre-exemples actifs.

**Score contradictoire retenu : 61/100.**

La régression principale n'est pas un échec de compilation : c'est l'écart entre les contrats annoncés et les chemins réellement utilisables en production.

## Gates observés

| Vérification | Résultat | Limite de preuve |
|---|---:|---|
| `backend/./mvnw -q test` | 192/192, 0 erreur | ne couvre pas tous les mappings frontend/backend ni les scénarios CORS/temporalité ci-dessous |
| `frontend/npm run lint` | OK | lint statique uniquement |
| `frontend/npm run build` | OK | build statique uniquement |
| Tests frontend (`*.test.*`, `*.spec.*`) | **0** | aucun test de route, interaction, contrat ou a11y frontend |
| Schéma PostgreSQL de test | inspecté en direct | `fn_jours_ouvrables` et `v_config_legale_active` absents ; 2 triggers d'intégrité restants |

## Défauts prouvés

### P1-01 — Les ajustements de paie restent un workflow état-centré et l'UI appelle la mauvaise méthode HTTP

**Preuve :**

- le frontend appelle `PATCH /paie/ajustements/{id}/statut` dans `frontend/src/api/paie/paieService.js:65` ;
- le backend ne déclare que `PUT /{id}/statut` dans `PaieAjustementController.java:183` ;
- ce contrôleur applique directement `entity.setStatut(req.getStatut().toUpperCase())` sans matrice `état courant + commande -> état cible` ;
- `PaieAjustementRequest` accepte aussi un champ client `statut` à la création ;
- la contrainte SQL n'autorise que les valeurs, pas les transitions.

**Impact :** l'action UI échoue en 405 lorsqu'elle est utilisée ; même avec `PUT`, le client continue de demander un état arbitraire. Le principe « le backend décide, le client demande une action » n'est donc pas globalement vrai.

**Correction attendue :** un unique `PayrollAdjustmentWorkflowService.execute(id, command, user, reason)` avec commandes explicites (`APPROVE`, `REJECT`, `CANCEL`), matrice testée, réponse `BusinessDecision`; retirer le champ `statut` client et retourner 410 sur l'ancienne mutation.

### P1-02 — Le dashboard reconstruit encore la période et la destination métier côté React

**Preuve :** `DashboardHeader.jsx:115-120` choisit localement une période selon `statutCode`, puis `periodes[0]` en fallback. Il compose ensuite une route à partir de `SUBMIT`, `EDIT_POINTAGE` et du rôle DAF (`DashboardHeader.jsx:132-138`).

**Impact :** une réponse backend incomplète, un nouveau statut ou une liste non triée redirige l'utilisateur vers une période ou un cockpit erroné. Cela contredit le critère « période active et workflow décidés par backend ».

**Correction attendue :** le backend doit fournir une destination/étape canonique avec le `PeriodContextView`; le frontend l'affiche sans recherche de statut ni fallback `periodes[0]`.

### P1-03 — La temporalité reste incorrecte dans des flux historiques Pointage, Carrière et STC

**Preuves :**

- `PointageSaisieService.java:180` sélectionne le premier site (`ORDER BY s.id_site LIMIT 1`) quand `idSite` manque, et impose `COALESCE(s.horaire_continu, TRUE)` ;
- `WorkflowRhService.java:482` et `:492` résolvent une affectation par le dernier identifiant, non par date d'effet ;
- `StcService.java:358` et `:365` lit CNaPS/FMFP avec `WHERE code = ... LIMIT 1`, sans `id_config_legale` ni date de départ ;
- `StcService.java:350` remplace une configuration SME absente par `262680.00`.

**Impact :** un STC recalculé après une révision de cotisation peut utiliser les taux courants ou arbitraires, et une saisie Pointage sans site peut recevoir le calendrier du mauvais site. Ces défauts violent l'exigence de date métier explicite et de configuration manquante bloquante.

**Correction attendue :** utiliser les résolveurs canoniques datés et bloquer l'absence/ambiguïté. Aucun `LIMIT 1`, dernier identifiant ou fallback légal ne doit décider une donnée historique.

### P1-04 — CORS et JWT ne satisfont pas la promesse de configuration de production stricte

**Preuves :**

- `SecurityConfig.validateCorsConfiguration()` ne rejette que la chaîne exactement égale à `"*"`, mais `setAllowedOriginPatterns(...)` accepte par exemple `https://*.example.com` avec `allowCredentials(true)` ;
- `JwtService.getSigningKey()` accepte un secret arbitrairement court puis le hache avec SHA-512. La présence de `JWT_SECRET` est exigée, mais sa qualité n'est pas validée.

**Impact :** les « origines explicites » ne sont pas garanties et un secret faible reste accepté en production, contrairement aux critères de phase 0.

**Correction attendue :** utiliser `setAllowedOrigins`, rejeter tout métacaractère de pattern, valider une clé HMAC-SHA512 de longueur/entropie minimale au démarrage et ne jamais la renforcer silencieusement par hachage.

### P1-05 — Photos RH protégées par rôle, mais pas autorisées au niveau ressource

**Preuve :** `EmployePhotoController` charge directement un nom de fichier UUID (`GET /employes/photos/{filename}`) sans employer, propriétaire documentaire ni contrôle de périmètre. L'upload ne rattache pas non plus le fichier à un `idEmploye`.

**Impact :** tout utilisateur disposant de `EMPLOYE_READ` peut lire toute photo dont il connaît l'URL. Le test de sécurité vérifie seulement l'anonyme et le rôle Pointage ; il ne vérifie pas un accès croisé entre deux ressources.

**Correction attendue :** stocker propriétaire/classification, exposer `GET /employes/{id}/photo`, vérifier l'accès à cet employé via un `DocumentAccessService` et ajouter un test IDOR inter-ressource.

### P1-06 — Le contrat d'erreur reste partiellement dépendant du texte technique

**Preuve :** `GlobalExceptionHandler.handleDataIntegrity` reconnaît toujours `"foreign key"`, `"duplicate key"`, `"unique constraint"` et `"unique"` dans le message PostgreSQL. Les contrôleurs contiennent encore 389 occurrences combinées de `catch (Exception)`, `Map.of`, `HashMap` ou exceptions génériques.

**Impact :** le contrat dépend encore du fournisseur SQL et les réponses métier ne sont pas uniformément portées par `SirhBusinessException`/`BusinessDecision`.

**Correction attendue :** traduire les contraintes par leur nom SQLSTATE/contrainte, centraliser les erreurs fonctionnelles et réduire les structures de réponse manuelles des contrôleurs par cas d'usage.

## Dette P2 confirmée

| ID | Constat | Preuve |
|---|---|---|
| P2-01 | Les services restent très concentrés | `PointageSyntheseService` 1 762 lignes, `PointageSaisieService` 1 676, `WorkflowRhService` 1 581, `PayrollService` 1 246 |
| P2-02 | Le contrat Référentiels charge encore tout côté client | `useContractReferentiels.fetchAll()` fait dix appels `Promise.all` et filtre les données localement |
| P2-03 | Aucune non-régression frontend | 0 fichier de test frontend, malgré des routes et capacités métier critiques |
| P2-04 | Tests d'architecture trop ciblés | ils ne détectent pas le fallback Dashboard ni le mismatch PATCH/PUT |
| P2-05 | Accessibilité et responsive non démontrés | lint/build seuls; pas d'axe, de test clavier, ni de test visuel/réactif |

## SQL : conclusion nuancée

Le rapport précédent est **correct** sur deux points ciblés : les objets `fn_jours_ouvrables(date,date,integer)` et `v_config_legale_active` sont bien absents de la base de test active. Les fonctions restantes sont les triggers d'immutabilité des bulletins.

Il est toutefois **incorrect** de conclure que toute décision métier SQL/temporalité a disparu : des requêtes Java SQL actives conservent des `LIMIT 1`, des fallbacks de configuration et des sélections non datées dans les flux cités en P1-03. Le problème n'est plus une fonction SQL nommée, mais une décision métier arbitraire embarquée dans des requêtes applicatives.

## Scores recalculés

| Axe | Rapport précédent | Audit contradictoire |
|---|---:|---:|
| Backend architecture | 7,3 | 6,2 |
| Backend-driven | 8,2 | 5,8 |
| SQL | 8,5 | 6,8 |
| Granularité | 7,0 | 5,8 |
| Temporalité | 8,0 | 5,2 |
| Sécurité | 8,1 | 6,0 |
| Frontend architecture | 7,2 | 6,0 |
| Frontend assistant | 8,0 | 5,4 |
| Workflow | 7,8 | 5,8 |
| UI | 7,1 | 6,8 |
| UX | 7,2 | 6,1 |
| Design system | 7,0 | 6,5 |
| Accessibilité | 7,0 | 5,0 |
| Responsive | 6,8 | 5,5 |
| Paie backend | 8,0 | 6,0 |
| Paie frontend | 7,8 | 6,0 |
| Pointage | 7,5 | 6,0 |
| Congés | 7,3 | 6,5 |
| Carrière | 7,0 | 5,8 |

**GLOBAL : 61/100.**

## Priorité de correction

1. Corriger P1-01 et ajouter le test contractuel frontend/backend correspondant.
2. Retirer les sélections/fallbacks historiques de P1-03 et tester avant/jour/après mutation.
3. Durcir P1-04 et P1-05 avec tests de démarrage, CORS pattern et IDOR.
4. Éliminer P1-02 et compléter les tests de route/capabilities frontend.
5. Ensuite seulement, poursuivre la réduction incrémentale des contrôleurs/services P2.
