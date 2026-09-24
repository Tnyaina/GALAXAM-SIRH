# AUDIT WORKFLOW & UX — POINTAGE ET PRÉSENCE

**GALAXAM SIRH — 2026-09-23**  
**Statut : audit fonctionnel statique ; aucune modification de code ou de BDD**

Périmètre vérifié : routes React, 23 fichiers `features/attendance`, contrôleurs et
services Pointage, `PayrollPeriodWorkflowService`, `CongeService`, schémas Pointage,
Congés et Paie. « Visible » signifie rendu par le code courant ; aucune observation
terrain ni donnée de production n'a été fournie.

## 1. Executive summary

Le module présente un cockpit linéaire en trois étapes — **Saisie**, **Suivi &
Anomalies**, **Contrôle/Validation** — partagé par l'agent et la DAF. Le workflow
de période est mieux centralisé que le workflow de journée : l'API calcule déjà
les `availableActions` de période et impose les transitions. En revanche, React
recalcule encore les heures, retards, statuts et indemnités avant sauvegarde.

Le parcours nominal réel est : création `BROUILLON`, ouverture `EN_COURS`, saisie
batch, traitement des anomalies bloquantes, `SUBMIT` vers `TRANSMIS_DAF`, puis
`APPROVE` vers `VALIDEE` avec gel. `A_VALIDER` est accepté par le backend pour
compatibilité, mais `SUBMIT` cible réellement `TRANSMIS_DAF`.

Le principal défaut fonctionnel est la régularisation d'une absence en congé.
Depuis `AnomalieRegularisationModal`, tout utilisateur ayant `POINTAGE_WRITE`
peut demander `CONGE_PAYE`. `PointageAnomalieService` crée directement une
`demande_conge` déjà `VALIDE`, puis un mouvement `PRISE`, sans appeler
`CongeService` ni son moteur. Le solde n'est simulé que dans React : le serveur ne
bloque pas explicitement un solde insuffisant, ne valide pas le pool annuel, le
préavis, les chevauchements métier ou les règles du type de congé. Si les
référentiels ANNUEL/VALIDE manquent, la méthode peut ne créer aucun congé tout en
marquant le jour `CONGE`. C'est un contournement de domaine à corriger en priorité.

Conclusions principales :

1. Conserver le cockpit en trois étapes, mais faire piloter chaque bouton par les
   capacités serveur.
2. Faire de `CongeService` l'unique propriétaire de toute régularisation par congé.
3. La semaine est nécessaire au calcul des HS, mais sa « validation » persistée
   n'est pas une étape utilisateur obligatoire : **TECHNICAL_WORKFLOW_CANDIDATE_FOR_REMOVAL**.
4. Unifier les deux modales de correction autour d'un formulaire configurable par
   capacité et contexte.
5. Le frontend doit prévisualiser, pas décider : le backend reste la vérité pour
   statut, minutes, indemnités et anomalies.

## 2. Personas

| Persona | Écran | Action | Backend appelé | Effet métier |
| :--- | :--- | :--- | :--- | :--- |
| Agent (`POINTAGE_READ/WRITE`) | Saisie | choisir site/date, saisir horaires/statut/indemnités, sauvegarder | `GET /pointages/saisie`, `POST /pointages/saisie/batch` | crée/met à jour `pointage_jour` et `pointage_indemnite_jour` |
| Agent | Saisie | « Déclarer un congé » si `CONGE_READ` | navigation `/conges?tab=demandes` | quitte Pointage ; aucun write Pointage immédiat |
| Agent | Suivi | ouvrir « Régulariser » | `GET /pointages/anomalies` | lecture des anomalies et soldes |
| Agent | Modale régularisation | corriger horaires/absence/congé | `PUT /pointages/anomalies/{id}/regulariser` | modifie le jour, indemnités, semaine ; peut écrire directement Congés |
| Agent | Suivi ou synthèse | « Transmettre » | `POST /periodes/{id}/actions/SUBMIT` | `EN_COURS → TRANSMIS_DAF`, après contrôles |
| Agent | Synthèse | consulter semaine, fiche, Gantt | endpoints synthèse/détail/employé | lecture seulement |
| DAF/Admin (`POINTAGE_VALIDATE`) | Saisie | lecture ; édition seulement si capacité `EDIT_POINTAGE` reçue | mêmes endpoints | contrôle de période |
| DAF/Admin | Synthèse | « Recalculer tout » | `POST /pointages/synthese/calculer-tout` | rematérialise `pointage_semaine_calcule` |
| DAF/Admin | Arbitrage | ajuster une journée | même endpoint `regulariser` | correction DAF ; même backend que l'agent |
| DAF/Admin | Arbitrage | rejeter avec motif | `.../actions/REJECT` | `TRANSMIS_DAF/A_VALIDER → EN_COURS`, audit transition |
| DAF/Admin | Arbitrage | approuver/sceller | `.../actions/APPROVE` | gel Pointage puis état `VALIDEE` |
| Admin/Paie | Gestion périodes | créer/ouvrir/réouvrir/clôturer | `/periodes`, commandes workflow | cycle complet selon autorités Paie/Admin |

Actions interdites ou bloquées : l'agent ne crée pas une période, n'approuve pas,
ne rejette pas et ne dévalide pas une semaine. Une date future et une période sans
`EDIT_POINTAGE.allowed` rendent la grille read-only. Une période `CLOTUREE` bloque
toute régularisation ; `TRANSMIS_DAF`, `VALIDEE` et `PAIE_VALIDEE` bloquent l'agent,
mais pas nécessairement la DAF/Admin avant clôture.

## 3. Workflow global actuel

```text
Admin/DAF crée BROUILLON
        │ OPEN
        ▼
     EN_COURS
        │
        ├─ Agent saisit chaque jour (batch)
        ├─ Backend injecte congés validés et calendrier
        ├─ React prévisualise heures/statuts/indemnités
        └─ Backend recalcule et persiste
        │
        ▼
Suivi anomalies
  ├─ correction horaires
  ├─ absence justifiée/non justifiée
  ├─ congé payé/permission (écriture SQL Congés directe — défaut)
  └─ correction DAF
        │ SUBMIT + fin de période + pointages non vides
        │ + aucune anomalie bloquante
        ▼
  TRANSMIS_DAF
     ├─ REJECT + motif ───────────────► EN_COURS
     └─ APPROVE
           │ gelerPeriode (transaction workflow)
           ▼
        VALIDEE
           │ calcul/validation Paie
           ▼
      PAIE_VALIDEE → CLOTUREE
```

## 4. Workflow Agent Pointage

L'agent visite normalement deux écrans pour terminer une journée : **Saisie** puis,
si nécessaire, **Suivi & Anomalies**. La synthèse est utile en fin de période avant
transmission. Il sélectionne site/date, saisit ou auto-remplit les plages, choisit
les indemnités, ouvre le récapitulatif puis sauvegarde en batch.

Les anomalies ne sont pas présentées juste après la sauvegarde par un enchaînement
obligatoire : l'agent doit cliquer l'étape Suivi. Pour un congé normal il quitte le
module via `/conges`; pour une absence déjà constatée, la modale propose au contraire
une imputation directe de congé, sans passer par l'écran Congés.

La transmission apparaît dans Suivi et dans Arbitrage/Synthèse, donc deux chemins
UI déclenchent la même commande. Le bouton est masqué/désactivé selon permissions,
état et capacités, mais les pages conservent aussi leurs propres conditions.

## 5. Workflow DAF

La DAF consulte les mêmes trois étapes. Dans Arbitrage elle dispose de la synthèse
mensuelle, de filtres d'alertes, d'une vue Gantt, du détail hebdomadaire, d'une
fiche employé, du recalcul global, de l'ajustement d'un jour, puis de Rejeter ou
Valider. Rejeter exige un motif et renvoie la période en `EN_COURS`. Approuver
appelle le gel puis place la période en `VALIDEE`.

La DAF peut aussi corriger des pointages déjà transmis et, d'après le service,
validés/paie-validés (sauf clôture). Cette souplesse est dangereuse : un changement
après gel doit soit provoquer explicitement une nouvelle version de snapshot, soit
être interdit. Le code de régularisation ne montre pas ce contrat à l'utilisateur.

## 6. Saisie quotidienne

| Étape | Manuel / prérempli | Calcul | Persistance |
| :--- | :--- | :--- | :--- |
| Site, date | manuel, URL conservée | contexte période par backend | non |
| Employés | affectation/contrat backend | éligibilité backend | non |
| Congés | demandes `VALIDE` chargées backend | journée/full/matin/AM | `id_demande_conge` et statut du jour lors du write |
| Fériés/chômés | calendrier backend | `CalendrierOuvrableEngine` | résultat journalier |
| 4 horaires | manuel ou auto-remplissage site | preview React puis recalcul Java | `pointage_jour` |
| Pause | bouton « Déduire pauses » / inférence | React et backend | horaires + totaux |
| Statut | choix limité ou calcul automatique | React et backend | FK statut |
| Repas/déplacement | cases, valeurs préremplies | preview React, montant backend | `pointage_indemnite_jour` + total dénormalisé |
| Récapitulatif | affichage | totaux UI | non |
| Sauvegarde | batch | validation/recalcul serveur | transaction batch |

Le sélecteur manuel de statut n'offre que `PRESENT`, `ABSENCE_JUSTIFIEE` (motif
obligatoire UI) et `ABSENCE_NON_JUSTIFIEE`. Il n'autorise pas un faux `CONGE` : le
congé est censé venir de Congés, sauf le raccourci de régularisation analysé plus bas.

## 7. Statuts journaliers

| Statut | Origine et modification | Heures / indemnités | Effet Paie |
| :--- | :--- | :--- | :--- |
| `PRESENT` | auto si plages complètes, ou choix utilisateur | minutes calculées ; indemnités selon cases/règles | heures normales/HS possibles |
| `ABSENCE_NON_JUSTIFIEE` | jour ouvré vide persisté ou choix/régularisation | 0 h, indemnités à 0 | absence retenue ; bloquante sans motif |
| `ANOMALIE_SAISIE` | paire horaire manquante/incohérente | résultat non fiable | bloque `SUBMIT` |
| `ABSENCE_JUSTIFIEE` | choix avec motif ou régularisation absence déduite | 0 h, indemnités à 0 | absence/retention selon synthèse ; aucun solde Congés |
| `CONGE` | congé validé chargé ou raccourci de régularisation | 0 h, indemnités à 0 | jours congé ; droits selon Congés |
| `FERIE_TRAVAILLE` | calendrier + présence | heures conservées | majoration férié |
| `FERIE_CHOME` | calendrier + aucune heure | 0 h, pas d'indemnités | objectif réduit, pas absence |
| `SAMEDI_TRAVAILLE` | samedi + heures | heures conservées | entre calcul hebdo |
| `DIMANCHE_TRAVAILLE` | dimanche + heures | heures conservées | majoration dimanche |
| `DISPENSE_POINTAGE` | employé autonome/congé non décompté selon mapping | UI affiche 8 h conceptuelles ; vérifier résultat serveur | pas de badgeage requis |
| `DEMI_JOURNEE` | une seule plage complète + motif/validation | minutes réelles ; indemnités par demi-part | travail partiel + éventuelle absence/congé 0,5 |

Le frontend connaît aussi `RETARD` et `NON_SAISI`, absents de la liste canonique
du schéma affichée dans l'audit structurel. Il faut vérifier qu'ils existent bien
dans les données de référence déployées : sinon React peut envoyer un code sans FK.

## 8. Anomalies

Le service ne possède pas un catalogue autonome d'anomalies. Sa requête sélectionne
les jours `ANOMALIE_SAISIE`, `DEMI_JOURNEE`, absences justifiées/non justifiées,
retards et toute ligne avec motif. Il les qualifie ensuite ainsi :

| Type | Détection réelle | Bloquante ? | Résolution |
| :--- | :--- | :---: | :--- |
| `INCOMPLET` | statut `ANOMALIE_SAISIE` (heure entrée/sortie manquante ou incohérente produite avant) | oui | compléter/corriger les plages |
| `ABSENCE` non justifiée | statut correspondant, jour hors week-end | oui seulement sans motif et sans demande EN_ATTENTE | congé, permission, absence déduite, motif |
| `ABSENCE` justifiée | statut + motif | non | déjà résolue mais reste listée |
| `DEMI_JOURNEE` | statut ou une seule demi-journée complète | non | motif, congé 0,5 ou absence déduite |
| `RETARD` | `minutes_retard > 0` | non | corriger heure ou ajouter motif |
| `AUTRE` | ligne avec motif ne correspondant pas aux cas précédents | non | information |

Il n'existe pas de détection dédiée, dans cet écran, des chevauchements entre
employés/jours, des jours totalement absents de `pointage_jour`, ni d'un intervalle
de nuit chevauchant le lendemain. « Jour non saisi » n'est donc pas une anomalie
SQL de cette liste tant qu'aucune ligne n'existe. C'est un trou de contrôle possible.

## 9. Régularisation

| Option UI | Endpoint/service | Tables modifiées | Statut final / audit |
| :--- | :--- | :--- | :--- |
| Corriger journée/matin/AM/continu | `PUT .../regulariser` → `regulariserPointage` | `pointage_jour`, `pointage_indemnite_jour`, éventuellement `pointage_semaine_calcule` | `PRESENT` ou `DEMI_JOURNEE`; motif dans la ligne |
| Autre demi-journée = congé payé | même endpoint | plus `demande_conge`, `mouvement_solde_conge` | jour reste `DEMI_JOURNEE`, congé 0,5 créé à côté |
| Autre demi-journée = absence déduite | même endpoint | Pointage | `DEMI_JOURNEE`; traitement de l'autre moitié peu explicite côté serveur |
| Congé payé | même endpoint | Congés direct + Pointage | `CONGE` |
| Permission exceptionnelle | même endpoint | `demande_conge` direct + Pointage | `CONGE` |
| Absence déduite | même endpoint | Pointage | `ABSENCE_JUSTIFIEE` ou non justifiée |
| Correction DAF | `DafAjustementPointageModal` → même endpoint | mêmes tables | `ajuste_par_daf` n'est pas clairement alimenté par ce chemin |

La transaction JDBC englobe congé, mouvement, pointage et recalcul semaine ; une
exception déclenche un rollback. Mais `syncIndemniteJour` absorbe toute exception,
ce qui permet une validation du pointage avec indemnités non synchronisées.

## 10. Régularisation par Congés

### Comportement exact actuel

```text
SuiviAnomaliesPage
  → bouton Régulariser
  → AnomalieRegularisationModal / onglet Absence
  → option « Congé payé » (1 ou 0,5 jour)
  → PUT /pointages/anomalies/{id}/regulariser
  → PointageAnomalieService.regulariserPointage
  → imputerCongeAnnuel (SQL direct)
      SELECT type ANNUEL + statut VALIDE
      SELECT toute demande couvrant la date
      sinon INSERT demande_conge déjà VALIDE
      si aucun PRISE : INSERT mouvement_solde_conge(-nbJours)
  → UPDATE pointage_jour statut CONGE, heures/indemnités à zéro
  → recalcul semaine
  → COMMIT
```

Réponses aux questions obligatoires :

1. Déclencheur : tout utilisateur passant l'endpoint avec `POINTAGE_WRITE` ou
   `ROLE_ADMIN`; le frontend l'offre depuis Suivi. `POINTAGE_VALIDATE` seul ne
   suffit pas à l'endpoint sauf si accompagné de WRITE/Admin.
2. Écran/bouton/modal : Suivi & Anomalies → bouton de régularisation →
   `AnomalieRegularisationModal`, onglet Absence, option « Congé payé ».
3. Statut : demande créée directement `VALIDE`; aucun `EN_ATTENTE`, aucune action
   de validation Congés.
4. Solde : un mouvement `PRISE` négatif est inséré. Le solde affiché est simulé
   dans React, mais aucune vérification serveur explicite `solde >= nbJours` n'est faite.
5. Pool annuel : non vérifié par ce chemin.
6. Préavis : non vérifié ; la date passée est implicitement acceptée.
7. Contrôles Congés : `CongeService`/`CongeCalculateurEngine` ne sont jamais appelés.
8. Période clôturée : bloquée. Transmise/validée bloque l'agent mais autorise DAF/Admin.
9. Mise à jour Pointage : statut `CONGE`, plages nulles, minutes et indemnités zéro,
   recalcul hebdomadaire. Le champ `pointage_jour.id_demande_conge` n'est pas mis
   à jour dans ce chemin : provenance incomplète.
10. Échec : toute exception SQL propagée provoque rollback. Mais type ANNUEL ou
    statut VALIDE absent entraîne silencieusement zéro création, puis le Pointage
    peut quand même devenir `CONGE`.
11. Idempotence : une demande couvrant la date et un mouvement PRISE existant sont
    réutilisés. Le contrôle n'est pas limité au type/statut/origine attendu et peut
    rattacher implicitement un congé différent. Le double clic est sérialisé par le
    verrou `FOR UPDATE` sur le pointage, mais la version fournie obsolète est remplacée
    par la version courante au lieu de rejeter strictement le conflit.

## 11. Frontière Congés / Pointage

Le comportement suspect est confirmé. Pointage connaît les tables, codes de types,
statuts et conventions de ledger de Congés. Il existe même un deuxième chemin SQL
direct pour `PERMISSION_EXCEPTIONNELLE` sans mouvement de solde.

Cible recommandée, sans choisir la règle métier :

```text
PointageRegularisationService
  demande « régulariser employé/date/partie/motif/origine »
        ↓
CongeService.regulariserDepuisPointage(command, actor)
  valide type, chevauchement, calendrier, pool, solde, préavis/dérogation
  décide EN_ATTENTE ou VALIDE
  écrit demande + ledger + audit
        ↓ résultat {demandeId, statut, couverture, décision}
Pointage
  met CONGE uniquement si couverture VALIDE
  stocke id_demande_conge et source
```

`BUSINESS_RULE_REQUIRED` : faut-il autoriser l'agent à créer une demande normale
`EN_ATTENTE`, ou réserver l'auto-validation rétroactive à la DAF/Admin ? Faut-il
une origine `REGULARISATION_POINTAGE` et une dérogation explicite au préavis ? Le
code actuel tranche implicitement « auto-valider pour tout POINTAGE_WRITE », mais
ce choix n'est pas justifié par une règle documentée.

## 12. Absences / Congés / Sans solde

| Concept | Pointage | Solde Congés | Indemnités | Paie / preuve |
| :--- | :--- | :--- | :--- | :--- |
| `ABSENCE_JUSTIFIEE` | 0 h, motif | aucun mouvement ici | zéro | peut rester retenue selon synthèse ; justification texte seulement |
| `CONGE` annuel | 0 h, devrait porter demande ID | PRISE | zéro | jour de congé, objectif réduit |
| Sans solde | pas de statut journalier explicite observé ; probablement type Congés non payé mappé en absence justifiée | dépend type | zéro | retenue ; contrat à clarifier |
| `DISPENSE_POINTAGE` | pas de saisie requise | aucun | dépend mapping | autonome/absence non décomptée ; sémantique mélangée |
| Permission exceptionnelle | codée `CONGE` | aucun mouvement dans ce chemin | zéro | indistinguable d'un congé payé via le seul statut Pointage |

Il manque un lien explicite entre nature de l'absence et effet Paie. Un statut
unique `CONGE` ne suffit pas ; Paie doit recevoir la provenance validée de Congés
et le caractère payé/non payé, sans réinterprétation locale.

## 13. Demi-journée

La modale permet matin seul ou après-midi seul, avec heures réelles, transport par
demi-part et repas décoché par défaut. L'autre moitié peut être `AUCUNE`,
`CONGE_PAYE` ou `ABSENCE_DEDUITE`. Pour un congé 0,5, le backend crée une demande
annuelle `est_demi_journee=true` et un mouvement `-0.5`, mais le moment MATIN/AM
envoyé par le frontend n'est pas stocké par `imputerCongeAnnuel`. Le jour reste
`DEMI_JOURNEE`, sans `id_demande_conge` mis à jour. Il est donc difficile de
reconstruire quelle moitié était couverte. C'est une perte de provenance.

## 14. Validation hebdomadaire

Les endpoints `validerSemaine` et `devaliderSemaine` existent, et la table stocke
`gele_par/gele_at`. Toutefois aucune page React n'appelle ces deux fonctions.
`PointageDetailHebdoModal` est une consultation. Le seul bouton actif est
« Recalculer tout », et `SUBMIT` appelle lui-même `calculerToutesSemainesPeriode`.

Une semaine calculée n'empêche pas les modifications : toute correction la recalcule
si elle existe. La transmission ne vérifie pas que chaque semaine a été validée ;
elle les calcule. La DAF voit les résultats, pas une décision hebdomadaire humaine.

**Verdict : `TECHNICAL_WORKFLOW_CANDIDATE_FOR_REMOVAL`.** Conserver le calcul
hebdomadaire requis pour les HS, pas une fausse étape de validation utilisateur.

## 15. Transmission DAF

`SUBMIT` est autorisé à `POINTAGE_WRITE` ou `POINTAGE_VALIDATE`, uniquement depuis
`EN_COURS`. Les blocages réels sont : date de fin encore future, aucun pointage,
un `ANOMALIE_SAISIE`, ou une `ABSENCE_NON_JUSTIFIEE` sans motif. Les semaines non
calculées ne bloquent pas : elles sont calculées dans la transaction de transmission.

Ne bloquent pas actuellement : retard sans motif, demi-journée sans motif, absence
avec simple texte, jours attendus sans ligne, demande de congé EN_ATTENTE. Ce sont
des choix métier à confirmer, pas des erreurs techniques automatiques.

## 16. Rejet

`REJECT` accepte `A_VALIDER` ou `TRANSMIS_DAF`, exige un motif non vide, puis revient
à `EN_COURS`. La transition est enregistrée avec état source/cible, versions,
utilisateur, motif et horodatage dans `periode_paie_transition`; `periode_paie`
conserve aussi `motif_rejet`. L'agent peut alors modifier et retransmettre. Le motif
est présent dans le DTO/période, mais sa visibilité exacte dans le cockpit Agent
mérite un test UX : aucune bannière dédiée évidente n'a été identifiée.

## 17. Validation & gel

`APPROVE` est réservé à `POINTAGE_VALIDATE`, depuis `A_VALIDER` ou `TRANSMIS_DAF`.
Dans la même transaction de workflow, `gelerPeriode` calcule la synthèse de chaque
employé, remplace l'ancien snapshot courant, puis écrit :

- `pointage_cumul_periode` versionné ;
- `pointage_cumul_indemnite` ;
- `pointage_snapshot_jour` ;
- `pointage_snapshot_semaine` ;
- `pointage_snapshot_bucket` ;
- enfin la transition et l'état `VALIDEE`.

Un échec propagé annule l'ensemble. Les utilités structurelles de ces tables sont
traitées dans l'audit précédent ; fonctionnellement, Paie exige au minimum le cumul
figé et les classifications temporelles historiques qu'elle relit effectivement.

## 18. Pointage → Paie

| Entrée Paie | Source figée | Origine avant gel | Recalcul |
| :--- | :--- | :--- | :--- |
| heures travaillées/normales | `pointage_cumul_periode.total_heures_travaillees` | jours + calcul jour/semaine | non après gel |
| absences | `heures_absence_retenues`, nombres de jours | statuts et objectifs | figé |
| retards | `total_minutes_retard` | calcul jour | figé |
| HS30/HS50 | colonnes cumul + buckets qualifiés | calcul hebdomadaire | figé |
| nuit/dimanche/férié | colonnes cumul + `pointage_snapshot_bucket` | intervalles/calendrier | figé |
| repas/déplacement | compteurs/montants cumul | indemnités jour/config | figé |
| congés | `nb_jours_conge` + contexte Congés utilisé ailleurs | jours liés aux demandes | provenance insuffisante si régularisation directe |

`PayrollService` sélectionne le snapshot `GELE/CONSOMME` et charge les buckets pour
`OvertimePayCalculator`. Toute cible doit préserver ce contrat historique ou le
remplacer par une preuve équivalente.

## 19. Frontend actuel

| Écran/composant | But, données, API | Workflow / diagnostic |
| :--- | :--- | :--- |
| `GestionPointagesPage` | stepper, périodes, compteur anomalies | bon orchestrateur ; tous les steps cliquables |
| `SaisieQuotidiennePage` | site/date, grille, KPI, récap | étape EN_COURS ; mélange preview métier et orchestration |
| `PointageGridTable` | saisie massive, statuts, indemnités, actions groupées | cœur Agent ; utile mais très dense |
| `SuiviAnomaliesPage` | anomalies, filtres/semaine, régularisation, transmission | étape contrôle Agent ; contient aussi fonctions DAF |
| `ArbitrageDafPage` | synthèse, Gantt, filtres, recalcul, submit/reject/approve | trop de responsabilités ; utile DAF |
| `AnomalieRegularisationModal` | heures, absence, congé, permission, demi-journée | riche mais empiète sur Congés |
| `DafAjustementPointageModal` | sélectionner/corriger un jour d'un employé | même endpoint et grande duplication UI |
| `PointageDetailHebdoModal` | vue semaine/quotidien | consultation utile ; ne valide pas réellement |
| `PointageRecapModal` | confirmation batch | utile pour éviter sauvegarde accidentelle |
| `PointageFichePrintModal` | fiche imprimable | sortie métier autonome |
| `PointageGanttView` | investigation calendrier par semaine | utile DAF, dispensable au parcours Agent |

## 20. Duplications UI

`AnomalieRegularisationModal` et `DafAjustementPointageModal` saisissent les mêmes
quatre horaires, statut, motif et indemnités et appellent le même endpoint. La
différence est surtout le point d'entrée : une anomalie ciblée contre le choix d'un
jour dans la fiche DAF. Un seul `PointageCorrectionModal` pourrait recevoir
`mode=AGENT_REGULARISATION|DAF_ARBITRAGE` et des capacités serveur.

Autres duplications : `SUBMIT` sur deux pages ; calcul de KPI côté frontend et
backend ; libellés/couleurs de statuts dans plusieurs composants ; calculs horaires
dans `pointageCalculations.js` et dans la modale.

### Règles React à reclasser

| Fonction | Ce qu'elle fait réellement | Verdict |
| :--- | :--- | :--- |
| `parseMinutes` | parsing/validation légère de `HH:mm` pour interaction | `KEEP_UI_ONLY` |
| `isRowDataModified` | dirty-check des horaires, statut, motif et indemnités | `KEEP_UI_ONLY` |
| `calculerLignePointageTempsReel` | heures, pause implicite, chronologie, retard matin/AM, statut, week-end/férié/congé, badges d'anomalie | `MOVE_TO_BACKEND` comme décision ; garder seulement une preview non autoritative |
| `AnomalieRegularisationModal.calculHeures` | recalcule les plages et valide les paires | `DELETE_DUPLICATION` après endpoint de prévisualisation |
| `usePointageSaisie.updateRowIndemnite/updateRowDynamicIndemnite` | quantités 0,5/1 et montants repas/déplacement | `MOVE_TO_BACKEND`; React garde cases et estimation |
| KPI de `SaisieQuotidiennePage`/`ArbitrageDafPage` | total heures, retards, congés, indemnités | `KEEP_UI_ONLY` uniquement comme agrégation visuelle ; préférer les KPI serveur dès qu'il y a des données sauvegardées |

Les couleurs et libellés peuvent rester UI, mais doivent être indexés par un code
serveur stable. Les transitions et la signification paie ne doivent jamais dépendre
d'une couleur ou d'un libellé React.

## 21. availableActions

L'approche existe déjà : le contexte période expose une map d'`ActionCapability`
pour `OPEN`, `SUBMIT`, `APPROVE`, `REJECT`, `CLOSE`, `REOPEN`, actions Paie et
`EDIT_POINTAGE`. Il faut l'étendre, pas réinventer une simple liste.

Cible : capacités structurées par ressource, par exemple
`EDIT_DAY`, `REGULARIZE_HOURS`, `REGULARIZE_AS_LEAVE`, `SUBMIT`, `REJECT`,
`APPROVE`, avec `allowed`, `reasonCodes` et éventuellement paramètres autorisés.
Congés utilise déjà une approche backend-driven comparable. React doit afficher,
masquer ou expliquer une action depuis ces capacités, sans recalculer les transitions.

## 22. Transactions inter-domaines

Actuellement congé, mouvement, pointage et semaine partagent une connexion et un
commit : ils sont atomiques face aux exceptions propagées. Risques :

- absence de validation métier Congés ;
- succès silencieux sans demande si référentiel manquant ;
- demande existante arbitraire réutilisée ;
- pas de lien `id_demande_conge` sur le pointage ;
- exceptions d'indemnités avalées ;
- conflit de version assoupli au lieu d'être rejeté ;
- responsabilité inter-domaines impossible à tester indépendamment.

La cible peut rester transactionnelle dans le même monolithe : un service
orchestrateur ouvre la transaction, appelle une méthode `CongeService` acceptant la
connexion/`TransactionRunner`, puis met à jour Pointage uniquement sur décision
validée. Si les domaines deviennent distribués, utiliser idempotency key + saga
compensable ; ce n'est pas nécessaire aujourd'hui.

## 23. Risques

| Cas | Comportement actuel / risque |
| :--- | :--- |
| Solde insuffisant | UI désactive/alerte, serveur direct ne prouve pas le blocage |
| Période clôturée | blocage serveur ferme |
| Type ANNUEL/statut VALIDE absent | congé non créé silencieusement, jour potentiellement `CONGE` |
| Jour déjà couvert | première demande quelconque réutilisée ; pas de contrôle de couverture partielle/type |
| Demande existante EN_ATTENTE/refusée | peut être réutilisée pour tester le mouvement sans la valider |
| Erreur SQL propagée | rollback global |
| Erreur indemnité | absorbée ; incohérence possible |
| Pointage concurrent | ligne verrouillée ; version client obsolète tolérée |
| Double clic | verrou + recherche limitent doublon, sans clé d'idempotence explicite |
| Validation DAF simultanée | version période + transaction et snapshot unique protègent partiellement |
| Correction après gel | DAF autorisée hors clôture ; contrat de re-gel/recalcul Paie insuffisamment explicite |
| Audit correction | motif/version/updated_at existent ; pas de journal complet ancienne/nouvelle valeur/auteur/source |

Pour chaque correction il manque un vrai journal append-only : avant/après, acteur,
motif, origine (`SAISIE`, `REGULARISATION`, `DAF`, `CONGE`), identifiant de demande,
horodatage et version. `AuditLog` n'est pas appelé par ce chemin.

## 24. Workflow cible

```text
AGENT
Saisie du jour
  ↓ sauvegarde et décision serveur
Anomalies du jour/période
  ├─ corriger horaires
  ├─ justifier absence
  └─ demander régularisation Congés
          ↓
       CongeService
       ├─ valide règles/solde/pool/préavis
       └─ retourne EN_ATTENTE ou VALIDE
          ↓ si VALIDE
       Pointage lié à demande_conge
  ↓ période sans blocage
SUBMIT

DAF
Vue période + anomalies restantes
  ├─ correction autorisée et auditée
  ├─ REJECT + motif → Agent
  └─ APPROVE
          ↓
Snapshot final versionné
          ↓
PAIE consomme uniquement le snapshot figé
```

La semaine reste une fonction pure de calcul. Le gel est la seule validation
persistante entre Pointage et Paie.

## 25. Frontend cible

```text
Pointage
├── Saisie                 (Agent ; grille + anomalies immédiates)
├── Anomalies              (Agent/DAF ; correction unique)
├── Validation période     (synthèse Agent, arbitrage DAF)
└── Fiche employé / export (lecture)
```

Conserver la grille, le récapitulatif, la fiche imprimable et le détail semaine.
Réserver le Gantt à la DAF comme vue secondaire. Fusionner les deux modales de
correction. Retirer toute notion « valider/dévalider semaine » si le métier confirme
qu'elle n'est pas une décision humaine. Afficher le motif de rejet en bannière.
Après sauvegarde, proposer directement « Voir les anomalies de ce jour ».

### Tableau obligatoire — chaque action utilisateur

| Écran | Persona | Action | Endpoint | Service | Tables | Avant | Après |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| Gestion périodes | Admin/DAF | créer | `POST /periodes` | contrôleur JDBC | `periode_paie` | — | `BROUILLON` ou `EN_COURS` |
| Gestion périodes | DAF | ouvrir | `.../actions/OPEN` | workflow | période, transition | `BROUILLON` | `EN_COURS` |
| Saisie | Agent | charger grille | `GET /pointages/saisie` | SaisieService | lecture multi-domaines | `EN_COURS` | idem |
| Saisie | Agent | sauvegarder batch | `POST /pointages/saisie/batch` | SaisieService | jours, indemnités, semaine éventuelle | `EN_COURS` | idem |
| Saisie | Agent | déclarer congé normal | navigation `/conges` | CongeService via écran Congés | Congés | divers | workflow Congés |
| Saisie | Agent | auto-remplir horaires / pauses | aucune avant Save | calcul React preview | aucune | `EN_COURS` | idem |
| Saisie | Agent | statut Présent/Absence | batch au Save | SaisieService | jours/indemnités | `EN_COURS` | idem |
| Suivi | Agent/DAF | lister anomalies | `GET /pointages/anomalies` | AnomalieService | lecture jours/Congés | tout | idem |
| Suivi | Agent | corriger heures | `PUT .../regulariser` | AnomalieService | jours, indemnités, semaine | `EN_COURS` | `EN_COURS` |
| Suivi | Agent | congé payé | même endpoint | AnomalieService direct | + demande, mouvement | `EN_COURS` | `EN_COURS` |
| Arbitrage | DAF | ajuster | même endpoint | AnomalieService | mêmes tables | transmis/validé hors clôture | même état |
| Arbitrage | Agent/DAF | recalculer tout | `POST .../calculer-tout` | SyntheseService | semaine calculée | `EN_COURS` | idem |
| Arbitrage | Agent/DAF | ouvrir détail semaine/Gantt | `GET .../semaines`, `.../semaine-detail` | SyntheseService | lecture jours/semaine | tout | idem |
| Fiche employé | lecture | consulter/exporter Excel/imprimer | `GET /pointages/employes/{id}` et `/export/excel` | Employe/ExcelExportService | lecture Pointage | tout | idem |
| Suivi/Arbitrage | Agent/DAF | transmettre | `.../actions/SUBMIT` | workflow + synthèse | semaine, période, transition | `EN_COURS` | `TRANSMIS_DAF` |
| Arbitrage | DAF | rejeter | `.../actions/REJECT` | workflow | période, transition | transmis | `EN_COURS` |
| Arbitrage | DAF | valider | `.../actions/APPROVE` | workflow + gel | cumuls/snapshots/période | transmis | `VALIDEE` |
| Paie | Paie/Admin | clôturer | `.../actions/CLOSE` | workflow/PayrollService | paie, période, transition | validée | `CLOTUREE` |

### Tableau obligatoire — résolution d'anomalie

| Anomalie | Résolution | Propriétaire cible | Effet Pointage | Effet Congés | Effet Paie |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Heure manquante/incohérente | corriger plages | Pointage | recalcul jour | aucun | nouvelles minutes au futur gel |
| Absence non justifiée | motif + statut | Pointage | absence non bloquante/justifiée | aucun | retenue selon nature |
| Absence = congé annuel | commande de régularisation | **Congés** | `CONGE` lié à demande validée | demande + PRISE contrôlées | objectif/jours congé |
| Permission exceptionnelle | commande dédiée | **Congés** | statut dérivé | demande validée selon règle | payé/non payé selon type |
| Demi-journée | horaires + couverture autre moitié | Pointage + Congés si congé | minutes partielles | éventuel 0,5 | travail + absence/congé |
| Retard | corriger heure ou motiver | Pointage | minutes/motif | aucun | retard figé |
| Jour non saisi | créer ligne ou attester non requis | Pointage | ligne explicite | possible lecture Congés | évite omission silencieuse |
| Ajustement DAF | correction auditée | Pointage | nouvelle version | seulement via CongeService | re-gel obligatoire si déjà figé |

## 26. Questions métier restantes

1. Qui peut auto-valider rétroactivement un congé : agent, DAF seulement, ou RH ?
2. Une régularisation doit-elle créer `EN_ATTENTE` ou `VALIDE` ?
3. Quelle dérogation de préavis est permise, par qui, et avec quel motif ?
4. Un solde négatif est-il interdit ou autorisé par dérogation explicite ?
5. `ABSENCE_JUSTIFIEE` est-elle payée, non payée, ou dépend-elle d'un motif/type ?
6. Comment distinguer sans-solde, permission payée et congé payé dans le snapshot ?
7. Une DAF peut-elle corriger après `VALIDEE`, et si oui quel re-gel invalide la Paie ?
8. Les jours sans ligne doivent-ils bloquer la transmission ?
9. Le retard non motivé doit-il devenir bloquant ?
10. La validation hebdomadaire a-t-elle une valeur juridique/opérationnelle réelle ?

## Si je reconstruisais aujourd'hui le workflow Pointage de GALAXAM, voici le parcours minimal que je conserverais

```text
AGENT                 BACKEND                 CONGÉS             ADMIN/DAF              PAIE
  │                      │                       │                    │                    │
  ├─ Saisir journée ────►│ calcul canonique      │                    │                    │
  ├─ Voir anomalies ◄────┤                       │                    │                    │
  ├─ Corriger heures ───►│                       │                    │                    │
  ├─ Régulariser congé ─►├─ commande validée ──►│ règles + ledger    │                    │
  │                      │◄─ décision + ID ──────┤                    │                    │
  │                      ├─ lier jour au congé   │                    │                    │
  └─ Transmettre ───────►│ contrôles + calcul semaine ──────────────►│                    │
                         │                       │                    ├─ Rejeter → Agent   │
                         │                       │                    └─ Valider            │
                         │◄──────── snapshot final versionné ─────────┤                    │
                         └───────────────────────────────────────────────────────────────►│
                                                                                         └─ bulletin
```

Le minimum métier est donc : une saisie quotidienne, une file d'anomalies, une
correction auditée, une délégation stricte à Congés, une transmission, un arbitrage
DAF et un snapshot final reproductible. Les statuts techniques de semaine, les
calculs React faisant autorité et les écritures SQL inter-domaines n'en font pas partie.
