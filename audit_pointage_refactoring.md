# Contre-audit du module Pointage — 04/10/2026

## Périmètre et principe

Audit du code courant, suivi d'une première tranche de corrections temporelles et de workflow. Les fichiers de tests, les anciennes migrations archivées et la documentation historique ne sont pas des composants de production à supprimer automatiquement. Le comportement de référence est le parcours actuel qui fonctionne chez Galaxam ; toute modification de calcul doit avoir un cas métier validé et un test avant/après.

## Inventaire backend, fichier par fichier

| Fichier (sous `backend/src/main/java/mg/galaxam/sirh/pointage/`) | Rôle actuel | Décision provisoire |
|---|---|---|
| `api/PointageController.java` | API saisie, anomalies, synthèse, exports, paramètres, ajustements | Garder les routes ; clarifier la séparation des responsabilités après inventaire des consommateurs. |
| `api/PointagePeriodeController.java` | Consultation, création et commandes des périodes de paie | Garder ; le moteur d'état vit déjà dans `paie/application/workflow`. |
| `api/dto/SaisiePointageJourDTO.java` | Contrat de saisie et prévisualisation d'un jour | Garder, réduire seulement les champs démontrés inutilisés. |
| `api/dto/PointageJourDTO.java` | Ligne de fiche quotidienne | Garder. |
| `api/dto/PointageTronconSemaineDTO.java` | Calcul affichable par segment de semaine | Garder : important pour une paie du 26 au 25. |
| `api/dto/PointageSyntheseMensuelleDTO.java` | Totaux, solde, indemnités et état de période | Garder ; auditer les champs hérités et ceux du gel. |
| `api/dto/PointageAnomalieDTO.java` | Liste et détail des anomalies | Garder. |
| `api/dto/FichePointageEmployeDTO.java` | Fiche individuelle détaillée | Garder ; alléger après analyse des usages UI/export. |
| `api/dto/PeriodePaieDTO.java` | Option de sélection et état de campagne | Garder ; responsabilité partagée Pointage/Paie. |
| `application/PointageService.java` | Grille site/jour, prévisualisation, saisie batch, contrôle temporel, indemnités du jour | Garder la logique ; candidat principal à une extraction ciblée de la lecture de grille et des indemnités, sans créer de microservices artificiels. |
| `application/PointageAnomalieService.java` | Recherche et régularisation des anomalies, pont transactionnel Congés | Garder ; vérifier une seule voie d'écriture avec la saisie courante. |
| `application/PointageAjustementPeriodeService.java` | Ajustements horaires DAF motivés, annulation et provenance au gel | Garder : distinct d'une correction de badge. |
| `application/PointageSyntheseService.java` | Calcul jour/semaine/période, précontrôle, agrégations paginées, gel et snapshots | Garder les fonctions ; extraire éventuellement le gel/snapshot et les requêtes de lecture, seulement si cela réduit les duplications. |
| `application/PointageEmployeService.java` | Fiche individuelle et assemblage des détails historiques | Garder ; harmoniser progressivement ses lectures temporelles avec le contexte employé. |
| `application/PointageTemporalConfigurationGuard.java` | Détection de chevauchements affectations et indemnités sur une plage | Garder ; vérifier qu'il couvre tous les chemins qui choisissent aujourd'hui un `LIMIT 1`. |
| `application/PointageDocumentService.java` | Prépare le document à partir de la fiche et du snapshot applicable | Garder comme point commun aux exports. |
| `application/PointageDocumentData.java` | Vue commune et formatage des données PDF/Excel | Garder, éviter les calculs métier dans cette vue. |
| `application/PointageExcelExportService.java` | Rendu XLSX | Garder : format distinct. |
| `application/PointagePdfExportService.java` | Rendu PDF | Garder : format distinct. |
| `domain/calculation/AttendanceEngine.java` | Calcul pur du jour, compensation et segmentation hebdomadaire | Garder comme source du calcul. |
| `domain/calculation/TemporalAttendanceEngine.java` | Découpe les heures aux frontières de jour, nuit, dimanche et férié | Garder ; règle indispensable aux traversées minuit/année. |
| `domain/calculation/{DaySpecialType,NightWorkType,OvertimeTier,QualifiedTimeBucket,PeriodBalance}.java` | Types de qualification et résultat du calcul | Garder ; petits types cohérents avec le moteur. |
| `domain/engine/CalendrierOuvrableEngine.java` | Résolution du calendrier ouvrable/jours spéciaux | Garder ; comparer ses dates et sites aux contextes d'emploi réels. |
| `persistence/EmployeIndemniteDerogation.java` | Représentation de dérogation individuelle datée | Garder si les usages API/UI sont confirmés ; vérifier collisions temporelles. |
| `README.md` | Carte technique locale du domaine | Actualiser après refactoring. |

Dépendances directes hors dossier : `contrat/application/EmploymentContextResolver.java` fournit contrat, site et dispense datés ; `paie/application/workflow/PayrollPeriodWorkflowService.java` arbitre les commandes de période et appelle le gel ; la Paie lit les snapshots scellés. Ne pas dupliquer ces responsabilités dans Pointage.

## Inventaire frontend, fichier par fichier

| Fichier (sous `frontend/src/features/attendance/`) | Rôle actuel | Décision provisoire |
|---|---|---|
| `pages/GestionPointagesPage.jsx` | Cockpit à trois étapes, période et site partagés | Garder comme orchestrateur léger. |
| `pages/SaisieQuotidiennePage.jsx` | Saisie par site et jour | Garder. |
| `pages/SuiviAnomaliesPage.jsx` | Suivi, corrections et transmission | Garder ; hiérarchiser les blocages réels. |
| `pages/ArbitrageDafPage.jsx` | Contrôle DAF, synthèse, ajustements et validation | Garder ; réduire les panneaux secondaires après validation des besoins. |
| `components/views/PointageGridTable.jsx` | Grille de saisie | Garder. |
| `components/views/PointageGanttView.jsx` | Vue chronologique alternative | Vérifier l'usage réel avant décision ; aucune suppression sur la seule taille du fichier. |
| `components/WeeklyPointageCalculationTable.jsx` | Présente les chiffres hebdomadaires serveur | Garder ; les sommes affichées ne doivent pas redevenir une règle métier. |
| `components/controls/PointageToolbar.jsx` | Site/date/recherche et actions de saisie | Garder ; clarifier les actions automatiques. |
| `components/controls/StatusPickerPopover.jsx` | Choix de statut et prévisualisation | Garder si partagé par grille et Gantt. |
| `components/modals/AnomalieRegularisationModal.jsx` | Correction d'une anomalie et éventuel congé | Garder ; simplifier le guidage, préserver l'effet transactionnel. |
| `components/modals/DafAjustementPointageModal.jsx` | Ajustement de solde horaire motivé | Garder ; distinguer clairement d'une correction de badge. |
| `components/modals/PointageDetailHebdoModal.jsx` | Explication d'une semaine | Garder si utilisée pour justifier la compensation. |
| `components/modals/PointageRecapModal.jsx` | Récapitulatif de saisie | Vérifier si la vue principale le couvre déjà. |
| `components/modals/PointageSoldePeriodeModal.jsx` | Détail du solde de période | Garder si nécessaire à l'arbitrage ; éviter le doublon avec la fiche. |
| `hooks/usePointageSaisie.js` | État éditable, prévisualisation et enregistrement | Garder. |
| `hooks/usePointageAnomalies.js` | Chargement/filtrage des anomalies | Garder. |
| `hooks/usePeriodePaie.js` | Sélection de période et commandes | Garder si les consommateurs sont multiples. |
| `hooks/useDerogationIndemnites.js` | Gestion des dérogations d'indemnités | Garder tant que la fonctionnalité est utilisée. |
| `api/attendanceApi.js` | API groupée par intention + cache court de fiche | Garder ; retirer la reconstruction métier du précontrôle. |
| `utils/attendanceDisplayUtils.jsx` | Formatage et libellés | Garder si purement visuel. |
| `utils/pointageCalculations.js` | Détection d'une ligne modifiée | Garder, renommer éventuellement pour éviter l'impression d'un second moteur de calcul. |

Hors dossier : `features/employees/components/tabs/PointageTab.jsx` expose la fiche depuis l'employé ; `routes/Router.jsx` conserve des alias d'URL ; `context/MenuContext.jsx` et `utils/menu.js` affichent les menus issus de `ref_menu`. La configuration `features/administration/api/pointageParametreService.js` concerne les paramètres de pointage. Les tests frontend dans `attendance/__tests__/` couvrent saisie, chargement, document et accessibilité : les garder et les compléter avant simplification.

## Tables et vues : finalité à préserver

`periode_paie` et `periode_paie_transition` portent l'état et son audit ; `pointage_jour` porte la saisie source ; `pointage_indemnite_jour` porte l'indemnité attribuée au jour ; `pointage_ajustement_periode` porte la correction horaire DAF, distincte du jour. `pointage_cumul_periode` et les quatre tables `pointage_snapshot_{jour,bucket,indemnite,ajustement}` scellent les données consommées par la Paie et leur provenance. `ref_statut_{periode_paie,pointage_jour}`, `ref_indemnite`, `site_indemnite_config` et `employe_indemnite_derogation` sont des référentiels datés. Les vues `v_pointage_*` doivent être inventoriées par consommateurs avant toute suppression. Aucune nouvelle table n'est justifiée par le présent audit.

Le script de base est `backend/conception/bdd/05_schema_pointage.sql`. Les migrations actives Pointage ajoutent des colonnes/règles successives ; les scripts archivés sont historiques. Le menu courant est semé dans `backend/conception/bdd/01_schema_core_securite.sql` (trois entrées : saisie, suivi, validation), et `backend/conception/bdd/migrations/26-09-22-sp17-pointage-role-validation-menu.sql` donne la lecture de la troisième entrée à l'agent. Modifier menu et route dans la même livraison seulement si une étape change réellement.

## Workflow courant à préserver comme base

1. Période brouillon → ouverture (`EN_COURS`).
2. Agent saisit/rectifie les jours ; preview et sauvegarde utilisent le moteur backend ; le suivi révèle les anomalies et journées manquantes.
3. Transmission à la DAF (`TRANSMIS_DAF`) après prérequis ; rejet motivé revient à `EN_COURS`.
4. DAF contrôle, ajuste avec motif si nécessaire, approuve (`VALIDEE`) ; cette action crée des snapshots versionnés des journées, buckets, indemnités et ajustements.
5. La Paie consomme les snapshots ; la clôture est une commande distincte. Une réouverture motivée passe par le workflow Paie et préserve les versions historiques.

## Écarts observés et vérifications requises

| Priorité | Observation vérifiable | Risque / décision recommandée |
|---|---|---|
| P1 | Dans `PointageSyntheseService`, deux calculs d'indemnité utilisent une jointure `LATERAL` avec `ORDER BY ... LIMIT 1` pour affectation et tarif (`loadAllowanceAmounts`, `enregistrerProvenanceSnapshot`). | Cela sélectionne une version en cas de chevauchement au lieu de le rendre explicite. Le garde préalable vérifie certains chevauchements ; prouver par tests qu'il précède tous les chemins, puis enlever tout choix arbitraire ou lever `AMBIGUOUS_CONFIGURATION`. |
| P1 | `periode_paie` n'a dans le schéma canonique qu'un `code UNIQUE` et un contrôle `date_fin > date_debut`, sans exclusion des plages qui se chevauchent. | Deux campagnes peuvent couvrir un même jour ; l'affectation d'un pointage à la période devient ambiguë. Contrôler aussi l'état de la base test et les validations applicatives ; si confirmé, ajouter une contrainte d'exclusion après audit des données, uniquement sur `sirh_galaxam_test`. |
| P1 | `attendanceApi.js` fabrique `pret` si le champ n'est pas fourni par le précontrôle backend. | Deux sources de décision. Faire du précontrôle serveur la seule réponse, avec erreur de contrat si elle manque. |
| P2 | La grille de lecture `PointageService.trouverAffectationApplicable` conserve un choix historique de dernière affectation hors date, tandis que les écritures sont strictes. | Risque de montrer un salarié dans un site qui n'est plus le sien. Vérifier intention de lecture et rendre ce cas visuellement explicite plutôt que le laisser ressembler à une affectation active. |
| P2 | La fiche utilise encore ponctuellement `LocalDate.now()` et des requêtes historiques propres pour des métadonnées secondaires. | Tester l'affichage d'une période passée avant migration de ces lectures ; pas d'effet prouvé sur la validation d'une saisie. |
| P2 | `PointageSyntheseService` reçoit encore `estAutonome` dans des signatures alors que la dispense est désormais résolue par jour. | Compatibilité à préserver, mais paramètre candidat à suppression quand les appels externes sont recensés. |
| P2 | Le document `docs/boky/module-pointage-presence.md` se déclare canonique mais cite le Code du travail de 2003 et des chiffres/tests datés. | Réviser à partir du texte 2024 et des règles effectivement configurées ; ne pas changer le calcul sur seule lecture de ce document. |
| P3 | Plusieurs alias d'URL et vues détaillées coexistent dans le cockpit. | Garder les alias tant que des liens/menu/tests les utilisent ; supprimer seulement les panneaux réellement redondants après parcours utilisateur. |

La Loi malgache n° 2024-014, art. 109, pose une durée légale de 173,33 h/mois ou 40 h/semaine selon l'activité ; ses art. 113–114 encadrent les repos quotidien et hebdomadaire. Le choix Galaxam de compensation hebdomadaire, de plancher d'arrivée et de majorations doit être confronté aux textes d'application et à la convention/usage applicable avant toute modification de taux ou de mode de calcul. La documentation d'Odoo et SAP montre un schéma commun saisie → résolution des écarts → approbation → valorisation, mais ne détermine pas la règle juridique ou les usages de Galaxam.

## Plan de refactoring sans régression

1. **Caractérisation** : capturer des cas réels anonymisés et des sorties gelées ; tester 26→25, changement de site/contrat/salaire, férié, dimanche, nuit franchissant minuit, demi-journée, dispense, congé et indemnité. Les totaux et exports existants sont les références avant intervention.
2. **Sécurité temporelle** : rendre l'unicité des périodes explicite, supprimer les sélections arbitraires de versions et rendre le précontrôle serveur autoritaire. Tester les trous, chevauchements et bornes inclusives. Aucun taux inventé ni recalcul des snapshots historiques.
3. **Workflow** : afficher sur les trois étapes le responsable, les blocages et l'action suivante. Séparer visuellement « corriger un jour » et « ajuster le solde de période » ; garder rejet/réouverture motivés et historique des versions.
4. **Nettoyage ciblé** : mesurer les usages des champs DTO, endpoints, composants et vues SQL ; enlever seulement les doublons sans consommateur ; extraire les responsabilités des deux gros services là où le code commun ou la transaction le justifie. Ne pas multiplier les fichiers par principe.
5. **Validation** : tests backend/frontend ciblés puis tests d'intégration et parcours visuel agent/DAF ; données/migrations BDD exclusivement dans `sirh_galaxam_test`, après `SELECT current_database()`. Mise à jour du schéma, menus et documentation uniquement si le comportement retenu l'exige.

## Décisions métier confirmées le 04/10/2026

1. La compensation des heures supplémentaires reste strictement hebdomadaire selon l'objectif de la semaine. Les totaux finaux sont arrêtés en fin de période ; une régularisation motivée du solde peut être saisie si nécessaire.
2. Deux rôles sont utilisés : agent de pointage et admin, ce dernier représentant la DAF. La permission `POINTAGE_VALIDATE` reste la porte d'entrée pour permettre une évolution ultérieure sans modifier le calcul.
3. Les snapshots historiques restent figés ; toute correction ultérieure suit une décision explicite et historisée.

## Première tranche appliquée

- Contrainte d'exclusion des plages de `periode_paie` à bornes inclusives, ajoutée au schéma canonique et via migration `sp55` sur `sirh_galaxam_test` uniquement, après vérification de `current_database()` et de l'absence de chevauchements existants.
- `GET /periodes/current` refuse désormais deux campagnes actives à la même date ; la création signale une collision de plages et valide les dates avant l'INSERT.
- La création ne peut plus commencer directement à `EN_COURS` : l'ouverture passe par la commande `OPEN`, historisée dans `periode_paie_transition`.
- Le précontrôle des journées manquantes utilise le site et la dispense de pointage de chaque jour, y compris les affectations historiques, par résolution groupée salarié/période.
- Les lectures de forfaits mensuels ne choisissent plus arbitrairement une affectation ou un tarif par `LIMIT 1` ; le garde détecte les chevauchements des configurations actives et exclut les brouillons.
- Le navigateur exige `pret` dans la réponse du serveur ; un précontrôle indisponible est annoncé et bloque la validation. L'écran rappelle la différence entre correction d'une journée et ajustement motivé du solde final.
- Aucun taux, seuil ou formule de compensation hebdomadaire n'a été modifié. Les instantanés existants n'ont pas été recalculés.
- La grille de saisie n'affiche plus la dernière affectation connue comme si elle couvrait la date demandée ; les anciens pointages déjà enregistrés conservent leur lecture sur leur site enregistré.
- Après une correction DAF ou un ajustement de solde, l'écran relance le précontrôle serveur de la période ; il ne conserve plus un blocage périmé affiché avant l'écriture.
- L'objectif d'un tronçon hebdomadaire est maintenant calculé avec les jours réellement couverts par le contrat et l'affectation à chaque date, et avec les fériés chômés du site de ce jour. Le changement d'affectation du 01/01 dans une campagne 26/12→25/01 n'efface donc plus les jours travaillables de décembre. Un test transactionnel vérifie les cinq jours de la semaine 29/12→04/01.

## Limites encore identifiées

- Les métadonnées et certains totaux de la synthèse mensuelle utilisent encore un site représentatif de l'employé pour la campagne. Leur portée doit être clarifiée avant de modifier une valorisation mensuelle ou un snapshot déjà consommé par la paie.
- L'éligibilité aux heures supplémentaires reste un booléen hebdomadaire résolu au premier jour d'emploi du tronçon. Un changement de catégorie au milieu de la même semaine nécessite une règle métier explicite avant de répartir l'éligibilité par jour.

## Contre-audit du calcul et du découpage — 04/10/2026

Le rangement en fichiers n'est pas la difficulté principale : le calcul journalier (`AttendanceEngine`), la partition temporelle (`TemporalAttendanceEngine`) et la préparation du snapshot (`PointageSyntheseService`) exposent actuellement plusieurs représentations d'une même vacation. Le contrat cible est une chaîne procédurale et testable : résoudre le contexte daté → former les intervalles réellement travaillés → découper aux changements de date et de qualification → calculer l'objectif et la compensation du tronçon hebdomadaire → projeter les paliers HS sur les minutes → figer les buckets et totaux → valoriser en Paie avec les taux de leur date civile.

| Sujet | Comportement courant vérifié | Décision de refactoring |
|---|---|---|
| Nuit | `AttendanceEngine.nightMinutes` déduit la durée de la seule sortie PM ; `TemporalAttendanceEngine` découpe réellement les intervalles à 22 h/00 h/05 h. Une vacation 23 h→02 h représente trois heures réelles de nuit mais le calcul journalier peut annoncer quatre heures. | Une seule méthode de minutes de nuit, dérivée des intervalles effectivement travaillés ; écran, ajustement, synthèse et snapshot doivent utiliser cette valeur. Tests 21 h→02 h, 23 h→02 h, 04 h→06 h et pause traversant la plage nocturne. |
| Seuil nuit | Le jour lit `SEUIL_NUIT_DEBUT` et `SEUIL_NUIT_FIN` datés ; le partitionneur fige 22 h et 05 h. | Résoudre le seuil à la date du segment et l'injecter au moteur ; ne pas conserver simultanément un paramètre modifiable et une constante divergente. |
| Nuit occasionnelle | Les nouveaux buckets portent `OCCASIONNEL`, mais `HABITUEL` et `NIGHT_UNCLASSIFIED` sont encore lus dans les anciens snapshots. | Garder la compatibilité historique à la lecture. Aucun choix « habituel » dans la nouvelle saisie si Galaxam n'en a pas l'usage. |
| Vacation au-delà du 25 | Le partitionneur coupe à minuit, mais le snapshot lui donne toujours l'ID de la campagne d'origine (`date -> periodId`) et ajoute les buckets hors tronçon en `REGULAR`. | Définir l'imputation des minutes après minuit au 26 avant de modifier les données. Proposition : date civile du segment et période contenant cette date, avec rejet explicite si période suivante absente ; jamais attribution silencieuse à la campagne précédente. |
| Férié selon le site | L'objectif hebdomadaire utilise maintenant le site du jour. Le snapshot utilise un calendrier appelé avec `site=null`. | Utiliser la même résolution datée de site et de férié lors du gel ; caractériser d'abord les fériés locaux et leurs effets sur les anciennes données. |
| HS | Compensation hebdomadaire confirmée, tronquée à chaque frontière de campagne. Les quotas HS sont ensuite projetés chronologiquement sur les minutes pour permettre leur valorisation. | Conserver cette distinction et ses invariants de conservation ; expliquer en interface qu'une heure peut être à la fois HS et nuit, sans additionner les durées. |
| Catégorie modifiée en semaine | L'éligibilité HS est lue une fois au premier jour couvert du tronçon. | Clarifier la règle avant de répartir une semaine entre catégories ; tester changement au 01/01 et entrée/sortie en milieu de semaine. |
| Gel | Les snapshots versionnés sont la source de la Paie après validation. | Conserver inchangés les snapshots historiques ; correction seulement par réouverture/commande explicite et nouvelle version. |

### Découpage cible compatible avec la préférence de regroupement

Un fichier de moteur peut contenir plusieurs types internes et des méthodes unitaires orientées opérations métier. Les petits `enum` autonomes ne sont pas une frontière métier et peuvent être regroupés avec les types de résultat, à condition de conserver les noms sérialisés dans les snapshots et d'adapter explicitement les consommateurs Paie. Le calcul pur doit rester distinct de l'accès SQL/transaction, et les exports PDF/XLSX doivent garder leurs implémentations de format séparées d'un modèle documentaire commun. Un très grand fichier applicatif reste acceptable s'il présente des sections et une API publique courtes ; fusionner saisie, gel et export dans un seul fichier rendrait toutefois plus difficile l'audit des effets de bord.

La priorité est de caractériser les écarts de nuit et de vacation chevauchant le 25, puis de corriger la source de vérité des minutes. Le regroupement de fichiers vient après des tests de non-régression, car un déplacement de classes seul ne résout aucune divergence métier.
