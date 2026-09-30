# Plan de refonte — gestion des droits à congé

Statut : **proposition à valider avant développement**. Périmètre : droits, reprise, temporalité, imputation, valorisation du stock, régularisation depuis le pointage et exports de contrôle. La paie générale, les rubriques et les calculs de bulletin restent des consommateurs du contrat Congés ; leurs interfaces seront adaptées seulement si nécessaire.

## 1. Constat dans le dépôt

Le calcul manuel fourni par la DAF (`OHATRA KAJY DROITS CONGES-given on sept26.xls`, Feuille1) distingue : assiette mensuelle `NET BASE + HS`, droits acquis `2,5 × formule / 30`, valeur acquise `assiette × droits / 30`, puis taux moyen de la période `Σ valeurs / Σ droits`. Le second fichier contient des prises de congés à côté des calculs. Ces colonnes ne forment pas à elles seules un registre complet des mouvements.

Aujourd'hui, les responsabilités sont dispersées : `CongeService` gère demandes, acquisition, régularisation, bonification, calcul de jours et lecture du solde ; `CongeTauxApplicableService` gère périodes, taux et cutover ; `CongeLotValorisationService` et `CongeLotValorisationEngine` rejouent les mouvements ; `PurgeFifoEngine` calcule aussi une imputation en jours ; `PayrollService.mettreAJourJournalConge` calcule et écrit le journal mensuel. Plusieurs endpoints permettent des reprises distinctes. Les commentaires de mouvements servent parfois à reconnaître leur nature. L'interface présente deux modes de reprise et propose `salaire actuel / 30` comme valeur journalière historique.

**Principe de refonte :** une donnée métier a un seul propriétaire, une seule règle de calcul et une date d'effet explicite. Une acquisition mensuelle, un mouvement du solde, un taux de référence et une valeur de stock sont quatre concepts distincts. Une table est conservée si elle porte une donnée, une décision ou une preuve qui ne peut pas être reconstruite de manière fiable ; une vue ou un cumul recalculable ne justifie pas une nouvelle table.

## 2. Règles cibles et points à décider au sprint 1

| Sujet | Règle de travail | Validation attendue |
| --- | --- | --- |
| Décompte | Congé validé = jours calendaires inclusifs jusqu'à la veille de reprise ; lundi–jeudi = 4 jours, même avec un férié mardi, selon la décision DAF communiquée. | DAF valide les cas vendredi–lundi, férié, demi-journée, congé à cheval sur deux mois et annulation. La divergence avec l'article 127 du Code 2024 est enregistrée comme décision de conformité à traiter séparément ; elle ne doit pas être dissimulée dans le code. |
| Acquisition | Le mois de congé payé ne réduit pas l'acquisition ; l'absence non régularisée peut la réduire selon la formule DAF. | DAF valide la colonne « formule », le premier/dernier mois sous contrat et les absences assimilées à du service effectif. |
| Valeur d'un droit | Le mois d'origine fixe les jours et la valeur acquis, fondés sur `NET BASE + HS` du mois ; hausse ultérieure du salaire sans effet rétroactif. | DAF valide que la prise consomme cette valeur historique, et si l'imputation des droits se fait toujours du plus ancien au plus récent. |
| TCONGE de période | `Σ valorisations / Σ droits` sur la période de référence ; distinct de la valeur du solde restant. | DAF valide début et fin de période pour salarié embauché en cours d'année et date d'application du taux clôturé. |
| Reprise | Un stock d'ouverture en jours et valeur, plus les cumuls de la période en cours ; les cumuls ne recréditent pas le stock. | DAF fournit un dossier exemple arrêté et confirme la convention de date de bascule. |
| Précision | Le modèle Excel conserve davantage de décimales que les colonnes actuelles en jours et en valeur. | DAF valide précision de calcul, arrondi d'écriture et tolérance de rapprochement. |

Le point juridique sur le férié est une décision de conformité identifiée, pas un prétexte pour changer silencieusement la pratique demandée dans ce chantier. Toute modification de cette règle requiert une décision explicite ultérieure.

## 3. Modèle fonctionnel cible

1. **Fait d'activité** : contrat actif, absence, congé validé, régularisation, HS et rémunération du mois. Le pointage en fournit les événements ; le domaine Congés décide de l'effet sur les droits.
2. **Droit mensuel acquis** : un enregistrement par salarié et mois de référence, avec jours, assiette, valeur, source et précision. Les cumuls et le TCONGE calculé sont des résultats de cette série.
3. **Mouvement de stock** : ouverture, acquisition, prise, restitution, correction ou expiration. Un identifiant de source et un type explicite remplacent la déduction par texte libre. Le solde à une date est la somme des mouvements admissibles jusqu'à cette date.
4. **Imputation valorisée** : une prise est affectée aux droits disponibles selon l'ordre approuvé ; elle garde les jours et valeurs des sources. L'annulation inverse les imputations d'origine.
5. **Taux applicable** : historique daté utilisé uniquement là où la règle le requiert ; ne remplace ni l'assiette mensuelle ni la valeur d'un lot connu.
6. **Bascule** : le dernier mois géré par la DAF et le premier mois géré par le SIRH sont sans recouvrement. L'import des lignes mensuelles antérieures alimente la période de référence ; le stock d'ouverture alimente le solde. Les lignes importées ne créent pas une deuxième fois les jours compris dans le stock.

Pour une embauche postérieure à la bascule, aucun cutover n'est créé : les dates du contrat bornent la première acquisition et les périodes suivantes. Pour une sortie, les calculs s'arrêtent à la date de départ et le solde est lu à cette date. Toute lecture historique utilise une date métier explicite, jamais `LocalDate.now()` à la place de cette date.

## 4. Inventaire des fichiers et décision prévue

Les suppressions ci-dessous sont **conditionnelles** : aucune suppression tant que les usages, données historiques et tests de non-régression n'ont pas été migrés.

| Élément actuel | Action cible | Sprint |
| --- | --- | --- |
| `backend/src/main/java/mg/galaxam/sirh/conge/application/CongeService.java` | Réduire aux workflows de demande et régularisation. Extraire acquisition, mouvements et calcul as-of vers des composants de domaine ciblés ; supprimer les méthodes doublonnées après migration des appels. | 1–3 |
| `.../CongeTauxApplicableService.java` | Garder une seule gestion datée des périodes et taux ; sortir le cutover vers un service de reprise. Supprimer les modes et fallbacks non utilisés après inventaire des données. | 2–4 |
| `.../CongeLotValorisationService.java` et `.../domain/CongeLotValorisationEngine.java` | Conserver le principe de lots historiques, simplifier le rejeu et le rattacher au registre canonique. Faire porter date de calcul et source de valeur explicitement. | 3 |
| `.../domain/PurgeFifoEngine.java` et `CongeService.getImputationSolde` | Fusionner avec l'unique moteur d'imputation valorisée ; supprimer ce deuxième calcul FIFO une fois les exports et expirations migrés. | 3–4 |
| `.../domain/CongeCalculateurEngine.java` | Garder les règles pures de jours et d'acquisition ; retirer les paramètres ou branches sans règle DAF approuvée. | 1–3 |
| `backend/src/main/java/mg/galaxam/sirh/paie/application/PayrollService.java` | Garder l'intégration paie ; déléguer le calcul et l'écriture des droits à l'API du domaine Congés. Aucune refonte du reste de la paie. | 3 |
| `.../cron/AcquisitionCongeScheduler.java` | Vérifier qu'il ne double pas l'acquisition liée à la paie ; conserver seulement une responsabilité distincte et idempotente (ex. bonification) si validée. | 3 |
| `.../api/CongeSoldeController.java` et `.../api/CongePeriodeReferenceController.java` | Unifier l'API de reprise et de consultation as-of. Déprécier puis retirer `/solde/initialisation` et `/taux-applicable/migration` si le nouveau parcours couvre leurs usages. | 2–4 |
| `frontend/src/features/leave/components/InitialisationSoldeModal.jsx` | Remplacer les modes « complet/simplifié » par un parcours guidé : stock arrêté, période courante, source DAF, aperçu, validation. Supprimer le préremplissage historique au salaire actuel. | 2 |
| `frontend/src/features/leave/components/MouvementsSoldeTable.jsx` | Afficher types et sources structurés ; retirer la classification par préfixe de commentaire. | 3 |
| `frontend/src/features/leave/components/ExportEtatCongesModal.jsx`, `.../api/congeService.js`, `frontend/src/features/employees/components/tabs/CongesTab.jsx` | Adapter aux endpoints unifiés et aux contrôles de reprise ; préserver les usages RH existants. | 2–4 |
| `backend/conception/bdd/04_schema_conges.sql`, `06_schema_paie.sql` | Définir le schéma cible. Réutiliser `mouvement_solde_conge`, `conge_journal_droit`, `conge_periode_reference` et l'historique des taux si possible. Ajouter des champs structurants seulement avec preuve de nécessité. | 1–4 |
| `conge_cutover_correction`, `conge_expiration_examen` | Examiner si chaque table conserve une décision/audit non reconstructible. Ne pas les supprimer par principe ; fusionner seulement si le nouveau registre préserve auteur, motif, date et ancien/nouveau état. | 1, 4 |
| `backend/docs/boky/module-conges-absences.md`, `backend/src/main/java/mg/galaxam/sirh/conge/README.md` | Réécrire après validation des règles ; enlever les descriptions devenues fausses et les anciennes références juridiques. | 4 |

Les anciennes migrations SQL restent archivées et **ne sont pas réécrites**. Toute modification de base passe par une nouvelle migration réversible/testée sur la base de test, avec inventaire des données avant suppression éventuelle de colonne ou table.

## 5. Sprints et portes de passage

**Règle commune : aucun sprint N+1 ne commence tant que tous les critères du sprint N ne sont pas satisfaits et que la validation DAF/RH du livrable métier est consignée. « 100 % » signifie 100 % des critères d'acceptation applicables, des scénarios obligatoires et des tests de non-régression verts ; ce n'est pas une promesse de 100 % de couverture de lignes. Un écart ouvert bloque le passage ou nécessite une décision explicite de changement de périmètre avant de reprendre le sprint.**

### Sprint 1 — Fondations et état initial

Livrables : carte des flux et dépendances, dictionnaire des quatre concepts (acquisition, mouvement, stock, taux), décisions DAF sur les lignes ambiguës des Excel, conventions temporelles et d'arrondi, contrat d'événement et modèle cible minimal. Capturer des exports et résultats de l'ancien code comme référence de comparaison, sans présenter les erreurs actuelles comme vérité métier. Classer chaque table, service et endpoint en « conserver / fusionner / retirer après migration » avec preuve d'usage.

Tests : cas chiffrés indépendants dérivés des deux Excel ; embauche en cours de mois ; congé avec férié selon la règle DAF ; absence régularisée ou non ; augmentation et HS ; consultation du solde à plusieurs dates. **Porte 1 :** aucune règle de calcul nécessaire au développement ne reste ambiguë, résultats attendus signés par la DAF, schéma cible et stratégie de migration validés, suite actuelle de référence exécutée et écarts inventoriés.

### Sprint 2 — Reprise et import de période

Livrables : service et écran uniques de reprise, validation de la date pivot, séparation stock/cumuls/taux, provenance DAF, prévisualisation avant écriture. Template d'import mensuel **facultatif** pour la période courante : matricule, mois, jours acquis, valeur acquise ; colonnes de contrôle facultatives (net base, HS, formule). Choix exclusif par période : cumuls saisis **ou** lignes importées ; le stock arrêté reste séparé. Import transactionnel, idempotent, erreurs par ligne, contrôle des doublons, mois manquants, bornes d'embauche et période fermée. Pas d'import détaillé des anciennes prises sans besoin DAF démontré.

Tests : import janvier–août puis continuation en septembre ; même résultat avec cumuls équivalents ; solde d'ouverture non doublé ; bascule entre deux mois ; valeurs manquantes, employés inconnus, reprise répétée, conflit avec mois déjà calculé, rollback total. Générer un vrai `.xlsx` de template et un `.xlsx` résultat de simulation, puis les rouvrir et vérifier cellules et formules. **Porte 2 :** parcours DAF réalisé de bout en bout sur la base de test, toutes les écritures et erreurs expliquées, aucun double comptage, export Excel revu par la DAF et tests sprint 1 toujours verts.

### Sprint 3 — Moteur canonique de droits et de stock

Livrables : acquisition et valeur mensuelle comme calcul unique, règles as-of pour entrée/sortie et régularisations, registre de mouvements typés, imputation valorisée unique et annulation exacte. Migrer les consommateurs paie, pointage, STC et exports vers ce contrat sans modifier leurs règles générales. Supprimer les chemins FIFO parallèles uniquement après comparaison des sorties. Ne plus déduire une opération du texte d'un commentaire dans les nouveaux enregistrements.

Tests : matrice de simulation multi-salariés et multi-mois ; hausse de salaire ; HS ; absence puis régularisation ; prise traversant stock DAF et acquisitions ; annulation ; correction ; entrée/sortie ; période et année traversées. Invariants automatiques : `solde jours = ouverture + crédits − débits`, valeur restante = somme des lots restants, pas de prise imputée deux fois, historique passé stable après correction future. Chaque scénario principal génère un `.xlsx` réel de rapprochement avec source, mouvements, lots, résultat et écart DAF. **Porte 3 :** tous les scénarios et tests d'intégration verts, écarts Excel explicitement expliqués, DAF valide les sorties, aucun consommateur ne lit encore l'ancien calcul parallèle.

### Sprint 4 — Nettoyage, migration et mise en service

Livrables : retirer composants, modes, endpoints et colonnes devenus sans usage ; migrations de données contrôlées et réversibles ; écran et export final simplifiés ; documentation métier et technique réécrite. Vérifier les données existantes avant toute suppression : reprises déjà faites, lots sans valeur source, mouvements à commentaire hérité, périodes ambiguës, bulletins validés. Les données historiques et bulletins validés ne sont jamais réécrits silencieusement.

Tests : migration sur copie de la base de test, rapprochement salarié par salarié avant/après en jours et en valeur, suite backend/frontend complète, exports `.xlsx` ouverts et contrôlés, scénarios de retour arrière, revue d'interface DAF. **Porte 4 :** aucun écart non expliqué, aucune référence à une API supprimée, tous les critères précédents restent verts, guide de reprise et d'exploitation DAF validé. Déploiement seulement après cette porte et accord de mise en service.

## 6. Ce qu'on ne supprimera pas sans preuve

- Le journal mensuel, car il porte l'assiette et la valeur d'origine nécessaires au calcul futur.
- Le registre des mouvements, car il explique le solde et ses corrections.
- L'historique daté des taux, si des opérations doivent encore résoudre le taux applicable à une date.
- Une décision DAF d'expiration ou une correction auditée, sauf si le modèle fusionné conserve intégralement auteur, date, motif et effet.
- Les anciennes données ou migrations pour « simplifier » le code : la simplification concerne d'abord les chemins d'écriture et de calcul futurs.

## 7. Décisions de départ à recueillir

1. DAF : exemple réel anonymisé donnant à une date pivot le stock restant en **jours et valeur**, le taux déjà applicable et les cumuls de la période en cours ; préciser si la période est propre à chaque salarié.
2. DAF : confirmer si la valeur d'une prise vient des lots mensuels les plus anciens, et la règle d'arrondi. Le fichier Excel permet de calculer les droits, mais ne démontre pas à lui seul cette règle d'imputation.
3. RH/DAF : choisir la convention de bascule (dernier jour DAF / premier jour SIRH), le traitement d'un mois partiel et les cas d'embauche/sortie.
4. Juridique/DAF : consigner séparément la divergence entre le décompte calendaire confirmé avec férié et l'article 127 du Code du travail 2024.

Ces décisions sont des entrées du sprint 1. Aucun développement de tables ou de branches supplémentaires ne doit anticiper une réponse non obtenue.
