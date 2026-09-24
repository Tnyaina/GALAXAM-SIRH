# Audit global de structure — Pointage, Présence et effets Paie

État du dépôt observé le 24 septembre 2026. Cet audit décrit l’arbre de travail et les migrations présentes dans le dépôt; il ne constitue pas la preuve que toutes les migrations ont été appliquées sur les bases de chaque environnement.

## 1. Synthèse

La direction de l’architecture est bonne sur quatre axes : `pointage_jour` porte les horaires et statuts saisis; `pointage_indemnite_jour` porte les indemnités effectivement accordées; la semaine est recalculée sans table permanente; `pointage_cumul_periode` et ses snapshots fournissent à Paie une version figée. Le frontend de saisie appelle un endpoint de prévisualisation et la persistance recalcule côté serveur. Le calcul métier de présence n’est plus dupliqué dans `pointageCalculations.js`, qui ne fait maintenant que comparer l’état local pour détecter les modifications.

Le domaine reste difficile à modifier parce que la structure physique et la source de vérité ne sont pas encore alignées partout. Le code générique des indemnités coexiste avec des colonnes, requêtes et formulaires REPAS/DÉPLACEMENT. Des vues (fiche employé, synthèse, anomalie, détail hebdomadaire) relisent ces anciennes rubriques en plus du journal générique. Le traitement Paie des nouveaux snapshots utilise le journal générique, mais les lectures et corrections Pointage ne sont pas encore toutes génériques.

Le risque fonctionnel majeur est `FIXE_MENSUEL` : les lignes de cette périodicité sont exclues de la grille de saisie, tandis que le snapshot de gel ne copie que les lignes présentes dans `pointage_indemnite_jour`. Aucun chemin générique visible ne valorise une indemnité fixe mensuelle dans Paie. Elle peut donc être configurée sans être versée. Une règle métier est également nécessaire pour traiter un changement de site en milieu de période.

## 2. État mesuré dans l’arbre

| Périmètre | Mesure observée |
|---|---:|
| Java sous `pointage/` | 24 fichiers, environ 10 262 lignes |
| Services applicatifs Pointage | 5 |
| Controllers Pointage | 2 |
| Moteurs Pointage/calendrier | 3 fichiers, dont un moteur temporel interne |
| DTO Pointage autonomes | 7 |
| Frontend `features/attendance/` | 21 fichiers, environ 8 368 lignes |
| Pages Pointage | 4 composants de page, dont un orchestrateur |
| Tables de la conception Pointage | 14 définitions courantes |

Les deux fichiers applicatifs les plus longs sont `PointageService.java` (~2 101 lignes) et `PointageSyntheseService.java` (~1 694). `PointageEmployeService.java` (~1 016) et `PointageAnomalieService.java` (~971) sont également des propriétaires substantiels. La réduction des fichiers de Phase 4 a donc réduit la dispersion des petits calculateurs, mais a produit un service de saisie qui dépasse le seuil de vigilance annoncé pour éviter les classes géantes.

## 3. Sources de vérité observées

| Information | Source de vérité recommandée et actuelle | Observations |
|---|---|---|
| Heures, date, statut, motif, site du jour | `pointage_jour` | La validation et le recalcul backend restent nécessaires à chaque écriture. Les horaires de préremplissage ne sont pas la vérité finale. |
| Indemnité accordée, fraction, quantité, montant journalier | `pointage_indemnite_jour` | C’est la vérité de l’octroi du jour. `montant_total_indemnites` dans `pointage_jour` est une copie dénormalisée et doit être traitée comme cache contrôlé. |
| Droit de présélection d’une indemnité | `employe_indemnite_derogation` + configuration site | Sert au préremplissage. L’agent peut modifier l’octroi journalier. Le backend doit continuer à recalculer le montant depuis la configuration datée. |
| Résultat hebdomadaire | calcul en mémoire via `AttendanceEngine` | `pointage_semaine_calcule` est absent du schéma courant et une migration forward la supprime. Ne pas la réintroduire comme cache permanent. |
| Résultat de période transmis à Paie | dernière version `GELE` de `pointage_cumul_periode` | Paie lit un snapshot versionné plutôt que les pointages ouverts. |
| Provenance des jours et qualification des temps | `pointage_snapshot_jour`, `pointage_snapshot_bucket` | Les buckets sont encore lus pour les majorations/qualifications Paie; pas de suppression sans replay. |
| Indemnités gelées | `pointage_snapshot_indemnite` pour version de contrat 2 | Nouveau contrat générique code, fiscalité, assujettissement, montant. Les anciens snapshots version 1 restent lus par les champs agrégés historiques. |

La dérogation n’est pas un verrou d’autorisation du montant final : la saisie doit rester libre de déroger, et le serveur calcule le montant applicable. En revanche, les règles de défaut et le choix explicite quotidien devraient être transmis par l’API sous forme d’actions/capacités et de valeurs proposées, pas reconstitués par des tests de codes dans React.

## 4. Backend : ce qui aide et ce qui disperse encore

### À conserver

- `PointageController` et `PointagePeriodeController` forment une frontière HTTP lisible. Les transitions de période restent groupées dans le controller et le workflow partagé avec Paie.
- `AttendanceEngine` est pur pour le calcul jour/semaine; `CalendrierOuvrableEngine` garde une responsabilité transverse autonome.
- `TemporalAttendanceEngine` regroupe les types/algorithmes temporels internes et leurs records plutôt que de maintenir chaque helper dans son propre fichier.
- `PointageExcelExportService` mérite de rester isolé : génération de classeur, styles et format de sortie sont une responsabilité différente du cycle de vie d’une journée.
- `pointage_cumul_periode` et ses snapshots restent le contrat historique permettant de rejouer et d’expliquer une paie.

### À fusionner ou clarifier

| Fichier/responsabilité | Diagnostic | Recommandation |
|---|---|---|
| `PointageEmployeService` | Assemblage de fiche et statistiques d’un employé; même domaine de lecture que les synthèses, mais déjà ~1 016 lignes | Fusionner les lectures de fiche dans une façade de lecture/synthèse seulement après extraction des requêtes et test de contrat. Ne pas déplacer aveuglément 1 000 lignes dans `PointageService`. |
| `PointageAnomalieService` | Liste, indicateurs, lecture et correction; correction réimplémente calcul et règles d’indemnités | Faire de `PointageService` l’unique propriétaire de l’écriture journalière. Garder les requêtes/liste d’anomalies avec la synthèse/lecture. Ne pas conserver deux implémentations de correction. |
| `PointageService` | 2 101 lignes, saisie + préremplissage + validation + calcul + writes jour et ledger | Réduire d’abord le SQL et le mapping répétés. Garder un propriétaire d’écriture, mais isoler les commandes quotidiennes des lectures/grilles si la classe reste >2 000 lignes. La cible de 2 services n’est pas une fin en soi. |
| `PointageSyntheseService` | Agrégation + filtres + gel + snapshots; 1 694 lignes | Responsable cohérent, mais séparer les projections SQL et le gel seulement si la navigation reste clairement sous une façade `PointageSyntheseService`. Éviter un service par mini-étape. |
| `PointageExcelExportService` | 728 lignes, formatage/classeur autonome | KEEP. |
| `TemporalAttendanceEngine` | Utilisé par la synthèse et ses types sont regroupés | KEEP tant que la qualification temporelle nuit/férié/dimanche garde sa complexité. Le réduire au moteur principal seulement si les tests de parité le rendent plus lisible. |

La cible raisonnable après le correctif des duplications est quatre propriétaires applicatifs compréhensibles (commandes journée, synthèse/gel, workflow période, export) ou trois si la lecture employé et les anomalies s’intègrent sans porter une classe monolithique. Viser « deux services » au prix de fichiers de plusieurs milliers de lignes contredirait l’objectif de retrouver rapidement une règle.

## 5. Frontend : pages, composants et navigation

Le routage utilisateur converge déjà vers `/pointages`, avec des alias historiques redirigés vers les onglets saisie/anomalies/validation. `GestionPointagesPage` orchestre `SaisieQuotidiennePage`, `SuiviAnomaliesPage` et `ArbitrageDafPage`. C’est une bonne séparation de parcours; la réduction utile porte sur les doublons d’édition, pas sur l’élimination arbitraire des onglets.

| Élément | Décision recommandée | Motif |
|---|---|---|
| `GestionPointagesPage` | KEEP comme route/coquille unique | Point d’entrée simple; préserver des onglets car rôles et tâches diffèrent. |
| `SaisieQuotidiennePage` + `PointageGridTable` | KEEP | Parcours opérationnel principal. Garder le calcul et les permissions serveur-driven. |
| `SuiviAnomaliesPage` | KEEP, mais afficher les résolutions génériques | Elle répond au besoin de correction; son chemin d’écriture doit partager la commande journalière. |
| `ArbitrageDafPage` | KEEP si l’arbitrage réel DAF reste distinct de la saisie agent | Retirer uniquement les vues ou boutons qui n’ont pas d’action métier. Les droits doivent venir des capabilities serveur. |
| `AnomalieRegularisationModal` / `DafAjustementPointageModal` | Fusionner le formulaire commun, conserver des modes/permissions | Les deux rééditent une journée et des rubriques d’indemnité; l’unification doit supporter les indemnités dynamiques et l’audit, pas masquer les permissions DAF. |
| `PointageDetailHebdoModal` | Simplifier après remplacement des libellés repas/déplacement par code catalogue | Affiche encore des KPI/filtres issus des champs legacy. La semaine est un calcul, pas une étape de validation. |
| `PointageRecapModal` | KEEP si elle résume avant sauvegarde; sinon inline dans la grille | Le rôle est UX uniquement; ne doit pas recalculer des règles métier. |
| `PointageFichePrintModal` / export employé | KEEP un seul contrat de données générique | Vérifier les colonnes de toutes indemnités sur l’écran employé, l’impression et Excel. Ne pas maintenir trois mappings de codes. |
| `PointageGanttView` | OPTIONNEL | Conserver si la DAF l’utilise réellement pour repérer des trous/conflits. Sinon déplacer ce diagnostic dans la liste d’anomalies; le Gantt n’est pas une source métier. |
| `pointageCalculations.js` | KEEP temporairement pour `isRowDataModified` uniquement | La logique métier a disparu du fichier; renommer en `attendanceRowDiff.js` ou l’inliner si c’est le seul consommateur. |

Les grands composants (Suivi anomalies ~933 lignes, modale anomalie ~955, Arbitrage ~1 001) nuisent à la recherche. À court terme, extraire uniquement les sections visuelles stables (table, panneau de filtre, sections formulaire) en composants internes nommés dans le même dossier feature; éviter de reconstituer une constellation de microfichiers. Le nommage doit porter la tâche utilisateur, pas une ancienne étape technique.

## 6. Indemnités : défauts encore visibles dans le code

La grille de saisie charge les indemnités depuis le catalogue et les tarifs datés de site, avec le code, la fractionnabilité et la périodicité. Au batch, le choix utilisateur est conservé et le montant est recalculé côté serveur; le reset des anciennes lignes avant resynchronisation réduit le risque de ledger périmé. Le nouveau snapshot version 2 permet à Paie de lire n’importe quel code sans ajouter de branche par indemnité.

Des chemins antérieurs restent cependant branchés :

1. `PointageService` calcule encore des projections/agrégats legacy REPAS et DEPLACEMENT/TRANSPORT dans la grille, la lecture des lignes existantes et les transitions de saisie.
2. `PointageAnomalieService`, `PointageEmployeService` et `PointageSyntheseService` joignent encore les rubriques par code. Ces jointures peuvent multiplier une journée quand une indemnité fractionnable possède plusieurs lignes/fractions; elles peuvent aussi ramener un choix arbitraire via `LIMIT 1`.
3. La régularisation DAF/anomalie n’a toujours que des champs historiques repas/déplacement; `saveRegularizedDay` écrit spécifiquement ces codes. Elle ne partage donc pas le contrat dynamique de la saisie quotidienne.
4. La synthèse hebdomadaire présente des totaux repas/déplacement, alors que le gel Paie version 2 additionne toutes les indemnités du ledger. Les totaux de l’écran ne sont pas encore une projection cohérente de la donnée consommée par Paie.
5. `PointageService.enregistrerPointagesBatch` charge et résout des paramètres liés au repas même si aucune règle site ne les demande. Un paramètre désactivé doit rester distinct d’une valeur manquante; les paramètres facultatifs ne doivent être résolus qu’en cas de besoin.
6. `syncRegularizedAllowances` absorbait les erreurs d’écriture du ledger; ce catch silencieux a été retiré. Une erreur doit maintenant annuler la transaction, mais la routine demeure code-spécifique et doit être remplacée par la commande ledger commune.

**Recommandation :** l’API retourne une collection canonique par `idIndemnite`/`code`, avec `suggestedGranted`, `fractionable`, `periodicity`, `displayAmount`, `availableActions` et `configurationState`. Le front renvoie uniquement l’intention quotidienne (`accorde`, fraction); le backend vérifie tarif/date et persiste le résultat. La fiche, les anomalies, l’impression, l’export et Paie lisent ensuite le même journal ou snapshot. Aucun `if (code == ...)` n’est nécessaire à l’ajout d’une indemnité ordinaire.

## 7. Types de périodicité et impact Paie

| Périodicité | Contrat souhaité | État observé |
|---|---|---|
| `JOURNALIER` | montant par jour accordé, multiplié par quantité/fraction | Passe par `pointage_indemnite_jour` et snapshot générique. Vérifier la cohérence des fractions au journal. |
| `MENSUEL_PRORATISE` (et alias actuel `MENSUEL`) | tarif mensuel distribué selon une règle de jours/calendrier datée, puis somme figée | Le moteur journalier calcule un montant journalier. Le code traite encore `MENSUEL` comme alias. Clarifier le code canonique et le dénominateur (jours ouvrables de la période vs présence). |
| `FIXE_MENSUEL` | montant mensuel indépendant du pointage, mais attaché au site/contrat applicable | **Trou critique :** exclu de la grille; le snapshot indemnité copie le ledger quotidien uniquement; aucun chargement Paie générique observé. La valeur configurée peut ne jamais arriver au bulletin. Ajouter une ligne de snapshot dédiée (ou une source snapshot indemnité fusionnée) au gel. Règle en attente pour changement de site en cours de période. |

Paie version 2 lit maintenant `pointage_snapshot_indemnite`; version 1 continue d’utiliser `montant_repas`/`montant_deplacement`. Cela protège la compatibilité, mais `contrat_indemnites_version` est une version technique du contrat, pas une version de règles tarifaires. Pour les historiques, le snapshot doit figer l’identifiant, le code rubrique, le libellé, le montant, l’imposabilité IRSA et le traitement social. Toute modification au pointage après gel doit passer par une réouverture suivie d’un nouveau gel; Paie doit garder la version consommée et ne pas relire les jours mutables.

## 8. Impôt et cotisations

Une indemnité imposable à l’IRSA est un gain versé qui entre dans l’assiette IRSA; ce n’est pas l’indemnité elle-même qui est retirée. L’assujettissement aux cotisations sociales est une décision différente et ne doit pas être inféré du drapeau IRSA. Le Code des impôts malgache de 2025 définit le champ des rémunérations/indemnités imposables et des exemptions; la qualification de chaque indemnité dépend de sa nature et du texte applicable. Source officielle : [Code des impôts 2025 — DGI](https://portal.impots.mg/textes/textes/2025-02-06--N-2025-MEF.SG.DGI-Ref-HIH9EC-3OPLT3.pdf).

Le catalogue reçoit désormais un champ distinct `soumis_cotisation`, le snapshot le fige et le calcul ajuste l’assiette sociale indépendamment du drapeau IRSA. Les lignes anciennes REPAS/DÉPLACEMENT gardent leur traitement historique non soumis; les indemnités non qualifiées sont bloquées au gel. Pour éviter toute classification silencieuse, l’administrateur doit renseigner chaque indemnité et le propriétaire Paie doit valider la règle. Si le droit distingue plusieurs assiettes (CNaPS, santé, FMFP), un seul booléen peut être trop grossier : faire confirmer le niveau de granularité avant d’étendre le modèle.

## 9. Base : KEEP, fusion et candidats suppression

| Table | Décision | Argument / condition |
|---|---|---|
| `pointage_jour` | KEEP | Fait journalier et version modifiable jusqu’au gel. |
| `pointage_indemnite_jour` | KEEP | Détail d’octroi par fraction, source d’indemnité utile. |
| `periode_paie` | KEEP | Cycle commun Pointage/Paie et dates. |
| `periode_paie_transition` | KEEP | Historique des commandes, version et motif; utile audit. |
| `ref_statut_periode_paie`, `ref_statut_pointage_jour` | KEEP provisoire | Référentiels de statut; vérifier qu’ils n’offrent pas des statuts jamais utilisés. Ils ne se fusionnent pas : cycle période et état jour diffèrent. |
| `ref_indemnite` | KEEP | Catalogue, code rubrique, fiscalité, fractionnement; compléter les qualifications requises. |
| `site_indemnite_config` | KEEP | Tarif et périodicité scope site/effective-dated. Contraintes nécessaires contre chevauchements. |
| `employe_indemnite_derogation` | KEEP provisoire | A une valeur de préremplissage indépendante du fait journalier; auditer overlaps et règles de priorité. |
| `ref_tolerance_arrivee_pointage` | MERGE candidate dans `ref_site` si aucun historique/scope plus fin n’est requis | Une ligne unique/site (`UNIQUE(id_site)`), non effective-dated. La règle est désormais activable ; si elle est active, l’heure minimale est appliquée au calcul tout en conservant l’heure brute. La table séparée reste à justifier par un futur historique daté. |
| `pointage_cumul_periode` | KEEP | Contrat versionné Paie et provenance de gel. |
| `pointage_snapshot_jour` | KEEP | Prouve quelles versions/journées ont servi au gel. |
| `pointage_snapshot_bucket` | KEEP | Lu par Paie pour les majorations et qualités temporelles. |
| `pointage_snapshot_indemnite` | KEEP | Contrat indemnités générique du gel; inclure les fixes mensuels au même niveau conceptuel. |
| `pointage_semaine_calcule` | DROP déjà conçu | La semaine est recalculée en mémoire; la migration forward existe. Tester tous les anciens consommateurs en environnement migré. |
| anciens `pointage_cumul_indemnite`, `pointage_snapshot_semaine` | DROP déjà documenté | Confirmés sans lecteur/runtime par les audits précédents; migration forward dédiée présente. |
| `pointage_jour.montant_total_indemnites` | DROP candidate après remplacement de tous ses lecteurs par `SUM(pointage_indemnite_jour)` | Colonne dérivée redondante; aujourd’hui encore lue dans des KPI. Vérifier coût/performance et parité avant suppression. |
| champs legacy repas/déplacement dans `pointage_cumul_periode` | DROP différé | Nécessaires aux snapshots contrat version 1 tant que Paie peut encore lire ces lignes. Backfill/versionnage et expiration historique requis. |

Le schéma de conception `05_schema_pointage.sql` commence par des `DROP TABLE ... CASCADE` sur des référentiels/périodes. Il doit être traité comme script de reconstruction locale et ne doit pas devenir le mécanisme de mise à jour d’une base contenant des données. Les changements d’environnement doivent passer par des migrations forward numérotées.

## 10. Workflow Pointage → Paie et isolation des modifications

Le code visible bloque le batch journalier lorsque l’état de période est `A_VALIDER`, `TRANSMIS_DAF`, `VALIDEE`, `PAIE_VALIDEE` ou `CLOTUREE`. L’approbation du workflow appelle `PointageSyntheseService.gelerPeriode`; Paie choisit la dernière version de cumul `GELE` ou `CONSOMME`. Une modification ordinaire de `pointage_jour` ne doit donc pas changer un bulletin déjà calculé depuis son snapshot consommé. Une réouverture de période Paie dégèle/relance le workflow et doit conduire à une nouvelle version gelée avant recalcul.

Le garde de période se trouve aujourd’hui dans `PointageService` batch et dans le service du workflow Paie, au lieu d’une politique d’écriture unique. Contrôler que **tous** les chemins (correction anomalie, ajustement DAF, intégration congé, imports et éventuelles commandes internes) utilisent ce même garde. Tant que cette vérification d’intégration n’est pas faite, la garantie « les modifications n’affectent pas Paie » est établie pour le chemin batch testé, pas démontrée pour chaque mutation.

Les snapshots sont le mécanisme juste : on conserve une version utilisée par Paie, puis une correction après gel crée une nouvelle version après réouverture. Ne pas recalculer silencieusement un bulletin déjà validé à partir du Pointage courant.

## 11. Orphelins et coûts de structure

- L’ancien `pointageCalculations.js` n’est plus un calculateur : un seul consommateur l’utilise pour `isRowDataModified`; renommer ou inline après vérification import.
- Alias de routes `/pointage/*` et `/pointages/*`, plus les alias d’onglets, restent utiles au maintien des URL historiques mais doivent être inventoriés puis documentés. Ne pas supprimer sans recherche dans les favoris/liens internes.
- Les deux modales d’édition sont une duplication fonctionnelle et de règles par code; priorité haute à leur formulaire commun piloté par le contrat d’indemnité dynamique.
- Les KPI de détail hebdomadaire et les badges de synthèse reconstituent encore les anciens concepts repas/déplacement. Réutiliser une seule projection backend par code.
- Les DTO de compatibilité contenant simultanément champs legacy (`indemniteRepasRecue`, `deplacementMatin`) et `dynamicIndemnites` prolongent le double modèle. Retirer uniquement après migration de tous les consommateurs et vérification des anciennes URLs/API.
- Les fichiers de changements affichés supprimés (`PointageSaisieService`, anciens controllers/calculators, clients API) ont déjà été remplacés dans l’arbre courant; ne pas les resupprimer ou réécrire les migrations historiques.

## 12. Cible de navigation recommandée

```text
frontend/features/attendance/
  pages/                 # cockpit, saisie, anomalies, arbitrage
  components/            # grille, résumé et fiches d’affichage
  components/day-edit/   # formulaire partagé agent/DAF, capacités distinctes
  api/attendanceApi.js   # contrat Pointage unique
  hooks/                 # chargement/état de parcours, pas règles de calcul

backend/pointage/
  api/PointageController.java
  api/PointagePeriodeController.java
  application/PointageService.java         # seules commandes jour et ledger
  application/PointageSyntheseService.java # projections, agrégat et gel
  application/PointageExcelExportService.java
  domain/AttendanceEngine.java
  domain/CalendrierOuvrableEngine.java
  domain/TemporalAttendanceEngine.java     # garder si utile pour Paie/snapshot
```

Navigation d’une modification : saisie/correction → `PointageService`; calcul jour/semaine → `AttendanceEngine`; lecture synthèse/gel → `PointageSyntheseService`; transitions de période → contrôleur/workflow période; configuration site/catalogue → `paieconfig`; consommation paie → snapshot, pas SQL Pointage direct.

## 13. Ordre de correction conseillé

1. **Terminer l’alignement indemnités** : régularisation DAF/anomalie vers la même mutation ledger que la saisie; retirer jointures REPAS/DEPLACEMENT des lecteurs et projeter tous les codes; éliminer les calculs legacy non requis par compatibilité.
2. **Ajouter le vrai snapshot mensuel fixe** et valider la règle de changement de site en cours de période. Clarifier si `MENSUEL` est un alias à supprimer au profit de `MENSUEL_PRORATISE`.
3. **Unifier la politique de période éditable** pour batch, régularisation, DAF et imports; tester concurrent update contre APPROVE/REJECT et vérifier le snapshot consommé par Paie.
4. **Centraliser les capacités** (`EDIT`, `REGULARIZE`, `SUBMIT`, `APPROVE`, etc.) et les choix de préremplissage dans les réponses serveur; supprimer les décisions de rôle/statut reconstruites côté React.
5. **Réduire les composants volumineux par sections utilisateur** et fusionner le formulaire d’édition commun. Mesurer compréhension/recherche avant toute suppression de page.
6. **Supprimer les données dérivées** seulement après remplacement des lecteurs et test de parité : `montant_total_indemnites`, colonnes legacy cumul, tolerance table si propriété site confirmée. Ne pas créer de table semaine/cache.
7. **Après chaque lot**, rejouer périodes paie version 1 et 2, indemnités simples et fractionnables, exonérées/imposables, socialement assujetties ou non, absence/congé, demi-journée, puis comparer les lignes de bulletin et export.

## 14. Questions métier / décisions à consigner

- `FIXE_MENSUEL` : décision confirmée : traiter le changement de site par segments ancien/nouveau site, proratiser chaque tarif par jours calendaires couverts dans la période, puis additionner.
- `MENSUEL_PRORATISE` : décision confirmée : tarif de période divisé par les jours ouvrables applicables ; acquisition selon les présences effectives. Le total acquis peut être inférieur au tarif de référence.
- Imposabilité IRSA et assujettissement social : à qualifier par nature d’indemnité et textes validés par Paie. Si CNaPS/santé/FMFP n’ont pas la même base, définir des flags distincts par assiette.
- Une régularisation d’absence est-elle une demande Congés normale, ou une dérogation réservée à l’admin/DAF ? Les contrôles, le solde, le pool, le préavis et les validations doivent rester définis par `CongeService`.
- Tolérance arrivée : un seuil unique par site suffit-il et doit-il s’appliquer au calcul des minutes de retard, à l’affichage, ou aux deux ?
- Gantt : confirmer son usage DAF. Sans décision d’arbitrage qu’il facilite, préférer la détection d’anomalie en liste.

## 15. Vérifications effectuées sur l’arbre courant

- Backend `mvn -q test` : réussi après application de la migration SP24 à la base locale `sirh_galaxam_test` (la suite échouait auparavant car cette base ne possédait pas `code_parametre_exception`).
- Frontend `npm test -- --run` : 1 fichier, 4 tests réussis.
- Frontend `npm run lint` : réussi.
- Frontend `npm run build` : réussi.
- `git diff --check` : réussi.

Ces tests prouvent compilation et scénarios couverts; ils ne couvrent pas encore le replay Paie des indemnités dynamiques dans tous les types de périodicité, les corrections DAF dynamiques, ni toutes les mutations concurrentes après gel. La base test a reçu la migration; la base applicative principale n’a pas été migrée par cet audit.
