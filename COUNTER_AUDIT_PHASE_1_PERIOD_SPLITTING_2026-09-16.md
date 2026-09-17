# CONTRE-AUDIT CIBLÉ PHASE 1 : DÉCOUPAGE RÉEL DES PÉRIODES (26 M-1 → 25 M) & CONTINUITÉ HEBDOMADAIRE

**Date** : 16 septembre 2026  
**Auteur** : Agent Antigravity (Google DeepMind)  
**Périmètre** : Modèle de paie GALAXAM, algorithmes de découpage des semaines, gestion des frontières 26/25 et audit contradictoire de `WeeklyPriorState`.  
**Statut** : LIVRABLE D'ARBITRAGE — AUCUN CODE PRODUCTION NI MIGRATION MODIFIÉ.

---

## RÉSUMÉ EXÉCUTIF & VERDICT

| Critère | Constat du Contre-Audit |
| :--- | :--- |
| **Période de Paie Réelle** | Strictement **26 M-1 au 25 M** (ex: 26/12/2025 au 25/01/2026 dans les tests E2E canoniques `GrandWorkflowOhatraE2ETest`). |
| **Fonction Réelle de Découpage** | Deux implémentations actives dans `PointageSyntheseService` :<br>1. `getSemainesPeriode(idPeriode, idSite, c)` (lignes 1226-1253) pour l'IHM et `calculerToutesSemainesPeriode`.<br>2. Regroupement par lundi ISO (`previousOrSame(DayOfWeek.MONDAY)`) dans `calculerSyntheseEmploye` (lignes 326-329 & 472-477). |
| **Comportement Frontière Week-end** | Le code contient explicitement la règle métier : `if (isWeekend(cur) && finSemaine.plusDays(7).isBefore(dateFin.plusDays(7))) { cur = cur.plusDays(1); continue; }` (lignes 1235-1238) sautant les samedis/dimanches initiaux pour démarrer la semaine 1 au premier lundi ouvré. |
| **Nécessité de `WeeklyPriorState`** | **ABS-O-LU-MENT NÉCESSAIRE (Option A)**. Le découpage 26→25 fait en sorte que dans **4 mois sur 7 (~57% des cas)**, le 26 tombe un jour ouvré en milieu de semaine (mardi, mercredi, jeudi, vendredi). Le découpage coupe alors physiquement la semaine civile en deux tronçons. Sans `WeeklyPriorState`, l'employé subit une double distorsion financière (retenue indue en M-1 et HS indues en M). |
| **Risque de Régression / Conflit** | **NUL**. `WeeklyPriorState` est indexé sur le même matricule employé et le **même lundi civil ISO**. Il ne peut en aucun cas fusionner deux semaines civiles distinctes. |
| **Dette Technique Identifiée** | Divergence mineure entre `getSemainesPeriode` (qui fusionne/ignore le week-end du 26) et `calculerSyntheseEmploye` (qui génère un tronçon à 0h pour ce même week-end). |
| **VERDICT OFFICIEL** | **`PASS_WITH_DEBT`** (Phase 1 validée, avec dette technique de synchronisation de découpage à solder en Phase 2/consolidation). |

---

## 1. PÉRIODE RÉELLE DE PAIE : LE MODÈLE 26 M-1 → 25 M

Dans l'organisation GALAXAM, le cycle de paie n'est pas calqué sur le mois calendaire (1er au 30/31). La période de paie court du **26 du mois précédent (M-1) au 25 du mois courant (M)**.

### Preuve par le Code et les Données de Test Canoniques
Dans le test E2E de référence `GrandWorkflowOhatraE2ETest.java` (lignes 75-76 et 88) :
```java
private final LocalDate dateDebut = LocalDate.of(2025, 12, 26);
private final LocalDate dateFin = LocalDate.of(2026, 1, 25);
idPeriode = employeFixtures.createPeriodePaie("PER-OHATRA-2026", dateDebut, dateFin, "EN_COURS");
```

### Conséquence Fondamentale sur le Découpage
Dans une année civile :
* Le 26 ne tombe un **lundi** que dans ~14% des mois (1 à 2 fois par an).
* Dans ~57% des cas, le 26 tombe un **mardi, mercredi, jeudi ou vendredi**.
* Dans ~29% des cas, le 26 tombe un **samedi ou dimanche**.

Par conséquent, **la majorité des périodes de paie GALAXAM démarrent au milieu d'une semaine civile ISO**.

---

## 2. FONCTIONS EXACTES RESPONSABLES DU DÉCOUPAGE

L'audit approfondi du repository révèle deux fonctions complémentaires dans [PointageSyntheseService.java](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/pointage/service/PointageSyntheseService.java) :

### 2.1. Fonction 1 : `getSemainesPeriode` (Lignes 1207-1253)
* **Emplacement** : `PointageSyntheseService.getSemainesPeriode(int idPeriode, Integer idSite, Connection c)`
* **Rôle** : Découpe la période en semaines/tronçons pour l'affichage IHM, le tableau de bord de validation des semaines, et l'orchestration du calcul global par lot via `calculerToutesSemainesPeriode`.
* **Particularité** : Contient l'optimisation métier évitant un tronçon vide de 0 jour ouvré si la période débute un samedi ou dimanche.

### 2.2. Fonction 2 : Regroupement Hebdomadaire dans `calculerSyntheseEmploye` (Lignes 326-329 & 468-478)
* **Emplacement** : `PointageSyntheseService.calculerSyntheseEmploye(...)`
* **Rôle** : Calcule les totaux mensuels pour un employé en partitionnant les jours de la période par leur lundi civil d'appartenance (`day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))`).

---

## 3. CALLERS & WORKFLOWS RÉELS

```
┌─────────────────────────────────────────────────────────────┐
│              Frontend : GestionPointagesPage.jsx            │
└──────────────────────────────┬──────────────────────────────┘
                               │ GET /pointages/synthese/semaines
                               ▼
┌─────────────────────────────────────────────────────────────┐
│     PointageSyntheseController.getSemainesPeriode           │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│        PointageSyntheseService.getSemainesPeriode           │
│       (Découpage en tronçons du 26 M-1 au 25 M)             │
└──────────────┬──────────────────────────────┬───────────────┘
               │                              │
               │ Appel boucle                 │ Utilisé par
               ▼                              ▼
┌──────────────────────────────┐ ┌────────────────────────────┐
│ calculerToutesSemainesPeriode│ │     validerSemaine         │
│ (POST /calculer-tout)        │ │  (POST /valider-semaine)   │
└──────────────┬───────────────┘ └────────────┬───────────────┘
               │                              │
               └──────────────┬───────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│          PointageSyntheseService.upsertSemaineCalculee      │
│        (Enregistre dans pointage_semaine_calcule)           │
└─────────────────────────────┬───────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│       PointageSyntheseService.getSyntheseSemaineDetail      │
└─────────────────────────────┬───────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│      PointageSyntheseService.calculerSyntheseEmploye        │
│    -> Appelle WeeklyAttendanceCalculator.calculateWeek      │
│    -> Injecte WeeklyPriorState si debutTroncon > Lundi      │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. PSEUDO-CODE RÉEL DU DÉCOUPAGE

### Pseudo-code de `getSemainesPeriode` (Lignes 1226-1253)
```python
def getSemainesPeriode(dateDebut, dateFin):
    result = []
    cur = dateDebut
    semNum = 1
    
    while cur <= dateFin:
        debutSemaine = cur.previousOrSame(MONDAY)
        finSemaine = cur.nextOrSame(SUNDAY)
        
        # RÈGLE MÉTIER FORMELLE : Weekend en début de période
        # Si cur est Samedi ou Dimanche et qu'il y a une semaine complète après dans la période :
        if isWeekend(cur) and (finSemaine + 7 days) < (dateFin + 7 days):
            cur = cur + 1 day
            continue  # Saute le samedi et dimanche, semaine 1 commencera lundi !
            
        dateDebutTroncon = max(cur, debutSemaine)
        if dateDebutTroncon < dateDebut:
            dateDebutTroncon = dateDebut
            
        dateFinTroncon = min(finSemaine, dateFin)
        
        # Décompte des jours ouvrables (hors samedi/dimanche)
        nbOuvrables = countWorkingDays(dateDebutTroncon, dateFinTroncon)
        seuilHeures = nbOuvrables * 8.0
        
        result.append({
            "semaineNumero": semNum,
            "dateDebut": dateDebutTroncon,
            "dateFin": dateFinTroncon,
            "nbJoursOuvrables": nbOuvrables,
            "seuilHeures": seuilHeures
        })
        
        semNum += 1
        cur = dateFinTroncon + 1 day
        
    return result
```

---

## 5. DÉFINITION RATIONNELLE DES CONCEPTS DE « SEMAINE »

Pour éviter toute confusion terminologique, le projet structure les notions suivantes :

1. **Semaine civile ISO** :  
   Bloc invariant du lundi 00:00 au dimanche 23:59. C'est l'**unité légale exclusive de compensation** et de calcul des heures supplémentaires (article 75 du Code du Travail malgache : 40h/semaine). Identifiée de manière unique par la date de son **lundi**.
2. **Tronçon de période (ou Semaine tronquée)** :  
   Portion d'une semaine civile ISO contenue à l'intérieur de la période de paie `[dateDebut, dateFin]`. Si la période démarre un mercredi 26, le premier tronçon est `[26/MM, 30/MM]`.
3. **Semaine calculée (`pointage_semaine_calcule`)** :  
   Ligne enregistrée en BDD représentant le résultat figé d'un tronçon validé pour un employé, clé composite `(id_employe, id_periode_paie, debut_semaine)`.
4. **Semaine nominale** :  
   Tronçon contenant l'intégralité des 5 jours ouvrables (lundi au vendredi). Objectif = 40 heures.
5. **Semaine partielle** :  
   Tronçon contenant strictement moins de 5 jours ouvrables (ex : mercredi à vendredi = 3 jours = 24 heures).
6. **Bloc ouvrable** :  
   Nombre de jours travaillés théoriques dans un tronçon après déduction des samedis, dimanches et jours fériés chômés.

---

## 6. ANALYSE DES 5 CAS FRONTIÈRES (TESTS RÉELS AVEC BORNES 26→25)

Les tests exécutés dans `PeriodSplittingAnalysisTest.java` (5 tests validés avec succès) démontrent les comportements suivants :

### Cas 1 : Le 26 tombe un Lundi (Période 2026-01-26 → 2026-02-25)
* **Début** : 2026-01-26 (Lundi) — **Fin** : 2026-02-25 (Mercredi).
* **Semaine 1** : `2026-01-26` au `2026-02-01` (Dimanche).
  * 5 jours ouvrables, objectif = **40.0h**.
  * C'est une semaine complète nominale. `WeeklyPriorState` est `NONE`.
* **Semaine Finale (Semaine 5)** : `2026-02-23` (Lundi) au `2026-02-25` (Mercredi).
  * 3 jours ouvrables, objectif = **24.0h**.

### Cas 2 : Le 26 tombe un Mercredi (Période 2025-11-26 → 2025-12-25)
* **Début** : 2025-11-26 (Mercredi) — **Fin** : 2025-12-25 (Jeudi).
* **Semaine 1** : `2025-11-26` (Mercredi) au `2025-11-30` (Dimanche).
  * 3 jours ouvrables (Mer, Jeu, Ven), objectif = **24.0h**.
* **Fait Majeur** :  
  Les jours **Lundi 24 et Mardi 25 novembre 2025** appartenaient à la période de paie précédente `PER-2025-11` (2025-10-26 → 2025-11-25) !
  La même semaine civile ISO (lundi 24/11 au dimanche 30/11) a été coupée au couteau entre les deux périodes de paie.

### Cas 3 : Le 26 tombe un Samedi (Période 2026-09-26 → 2026-10-25)
* **Début** : 2026-09-26 (Samedi) — **Fin** : 2026-10-25 (Dimanche).
* **Comportement `getSemainesPeriode`** :
  * Le 26 (Samedi) et le 27 (Dimanche) activent la condition `isWeekend(cur)`.
  * L'algorithme incrémente `cur` et saute au **Lundi 28/09/2026** !
  * **Semaine 1 démarre le Lundi 28/09/2026**. Le week-end 26-27 est combiné avec le cycle suivant ou traité comme hors-semaine ouvrée pour éviter une semaine vide à 0h.
* **Divergence observée dans `calculerSyntheseEmploye`** :
  * `calculerSyntheseEmploye` crée un groupe pour le lundi 21/09, avec un tronçon effectif du 26/09 au 27/09 ayant 0 jour ouvré.

### Cas 4 : Le 25 tombe un Mercredi (Période 2026-01-26 → 2026-02-25)
* **Dernière Semaine** : `2026-02-23` (Lundi) au `2026-02-25` (Mercredi).
  * 3 jours ouvrables, objectif = **24.0h**.
  * Les jours **Jeudi 26 et Vendredi 27 février 2026** appartiendront à la période suivante (2026-02-26 → 2026-03-25).

### Cas 5 : Le 25 tombe un Samedi (Période 2026-03-26 → 2026-04-25)
* **Début** : 2026-03-26 (Jeudi) — **Fin** : 2026-04-25 (Samedi).
* **Dernière Semaine** : `2026-04-20` (Lundi) au `2026-04-25` (Samedi).
  * Contient les 5 jours ouvrables complets (Lundi au Vendredi). Objectif = **40.0h**.
  * Le Dimanche 26 avril 2026 sera le premier jour de la période de paie suivante.

---

## 7. CONFRONTATION RÈGLE MÉTIER UTILISATEUR VS CODE

### Test Week-end à la Frontière
* **Règle formulée par l'utilisateur** :  
  *« Si la zone de découpage contient un week-end, celui-ci est combiné/rattaché avec la semaine suivante selon la logique existante. »*
* **Comportement réel du code** :  
  Confirmé à 100% dans `getSemainesPeriode` (lignes 1233-1238) :
  ```java
  if (CalendrierOuvrableEngine.isWeekend(cur) && finSemaine.plusDays(7).isBefore(dateFin.plusDays(7))) {
      cur = cur.plusDays(1);
      continue;
  }
  ```
  Le code saute délibérément le week-end du 26-27 pour rattacher le flux au lundi ouvré de la première vraie semaine de travail.
* **Verdict** : **CONFORME**.

### Test Jours Ouvrables à la Frontière
* **Règle formulée par l'utilisateur** :  
  *« Si la coupure concerne uniquement des jours ouvrables, ceux-ci peuvent constituer une semaine/tronçon distinct. »*
* **Comportement réel du code** :  
  Confirmé. Si le 26 est un mercredi, le code crée un tronçon `26/11 -> 30/11` (3 jours ouvrés, 24h). Si le 25 est un mercredi, il crée un tronçon `23/02 -> 25/02` (3 jours ouvrés, 24h).
* **Verdict** : **CONFORME**.

---

## 8. CONTRE-AUDIT DE `WeeklyPriorState` : ÉTAIT-IL NÉCESSAIRE ?

### VERDICT FORMEL : OPTION A — NÉCESSAIRE ET CORRECTE

Le contre-audit confirme de façon incontestable que :
1. **Le découpage existant N'ÉLIMINE PAS les semaines chevauchantes, il les GÉNÈRE SYSTÉMATIQUEMENT** :  
   Dans 4 mois sur 7, la coupure 26/25 coupe une semaine civile ISO en plein milieu de jours ouvrables (ex : lundi-mardi sur période M-1, mercredi-vendredi sur période M).
2. **L'unité légale du Code du Travail malgache est la semaine civile de 40 heures** :  
   Si un employé fait -2h (déficit) le lundi/mardi de la semaine sur la fin de période M-1, et +3h (surplus) le mercredi/jeudi sur le début de période M :
   * **Sans `WeeklyPriorState`** :  
     En M-1, la paie constate un déficit de 2h et prélève une retenue sur salaire.  
     En M, la paie constate un surplus de 3h et verse 3h d'heures supplémentaires !  
     **Conséquence** : L'employé et l'entreprise subissent une erreur financière grave (la compensation légale hebdomadaire de 40h est détruite par la frontière arbitraire du 25/26).
   * **Avec `WeeklyPriorState`** :  
     En M-1, la paie clôture avec son état local.  
     En M, le premier tronçon charge l'état antérieur (`WeeklyPriorState`), compense les 2h de déficit antérieures avec les heures faites en début de période M, et ne verse que **1h nette d'heures supplémentaires**.

### Audit des Clés et Sécurité de Raccordement
* **Clé d'identification du Prior State** :  
  `WeeklyPriorState` n'est appelé que si `debutTroncon.isAfter(lundi)`.  
  La requête SQL cible strictement :  
  `WHERE id_employe = ? AND debut_semaine = ? AND id_periode_paie != ?` où la date recherchée est le **lundi civil ISO**.
* **Risque de fusionner des semaines distinctes** : **ZÉRO**.  
  Deux tronçons ne peuvent partager le même lundi civil que s'ils appartiennent à la **même semaine civile calendaire**.
* **Impact d'un week-end à la frontière** :  
  Si la période démarre un samedi 26, `lundi` était le 21. Si des pointages ont eu lieu en M-1, ils sont protégés. Si aucun pointage n'a eu lieu ou si la semaine démarre le lundi 28, `debutTroncon == lundi` et `WeeklyPriorState.NONE` s'applique naturellement.

---

## 9. CONTRE-EXEMPLE RECHERCHÉ (TEST DU SCÉNARIO PIÈGE)

> *Question : Existe-t-il un cas où la fonction de découpage dit « ces jours forment volontairement un bloc indépendant » et où `WeeklyPriorState` les reconnecte à tort ?*

**Réponse : NON.**
Le découpage en tronçons n'a jamais eu pour but métier de décréter qu'un mercredi 26 novembre était une « semaine autonome » indépendante du lundi 24 novembre.  
Le découpage en tronçons est une **contrainte technique imposée par le calendrier de paie (26→25)**. Le législateur malgache et les règles RH GALAXAM imposent que la durée normale du travail soit appréciée à la **semaine de 40h**.  
`WeeklyPriorState` ne force aucune fusion abusive : il transmet le reliquat d'objectif et d'heures travaillées de la même semaine civile pour respecter l'équité de la compensation 40h.

---

## 10. SCÉNARIO FINANCIER COMPLET : POINTAGE → SNAPSHOT → PAIE

Vérification du cycle de vie du scénario :
* **Période M-1 (finissant le mardi 25)** :
  * Lundi 24 + Mardi 25 : Réalisé = 14h (au lieu de 16h) → Déficit = 2h (120 min).
  * Clôture / Validation DAF M-1 : La période M-1 est gelée dans `pointage_semaine_calcule` avec `heures_deficit = 2.0`. Le snapshot de paie M-1 applique une retenue de 2h.
* **Période M (débutant le mercredi 26)** :
  * Mercredi 26 au Vendredi 28 : Réalisé = 27h (au lieu de 24h) → Surplus brut = 3h (180 min).
  * `chargerEtatHebdomadaireAnterieur` charge : `priorDeficit = 120 min`, `priorWorked = 840 min`.
  * `WeeklyAttendanceCalculator` calcule la semaine unifiée :
    * Heures travaillées totales = 14h + 27h = 41h.
    * Seuil total = 16h + 24h = 40h.
    * Déficit global résiduel = 0 min.
    * Heures supplémentaires nettes de la semaine = 1h (60 min).
  * Enregistrement en M : Le tronçon 1 de la période M porte les 60 min de HS nettes.
  * Paie M : Paie les 60 min de HS. Le déficit antérieur ayant déjà fait l'objet d'une retenue en M-1, l'employé récupère son droit normal sans double peine.

---

## 11. CLÔTURE & IMMUABILITÉ DES PÉRIODES PASSÉES

Pourquoi GALAXAM utilise-t-il précisément cette architecture ?
1. **Immuabilité stricte** : Une période validée par la DAF ou clôturée (`periode_paie.id_statut_periode IN ('CLOTUREE', 'VALIDEE')`) ne doit **JAMAIS** être modifiée rétroactivement (interdit par le fisc, la CNAPS et l'audit comptable).
2. **Continuité sans rétroaction** : `WeeklyPriorState` garantit la continuité en **lecture seule**. Il ne met jamais à jour la période M-1. Il injecte l'antériorité dans le calcul de la période M en cours d'élaboration.

---

## 12. SALAIRE : CONFIRMATION DU NON-IMPACT

Conformément à la directive formelle :
* GALAXAM ne modifie pas les salaires de base au milieu d'un mois civil.
* Le découpage 26→25 du Pointage n'entraîne aucun découpage de taux horaire ni de double base salariale dans le moteur de Paie.

---

## 13. SOURCE EXACTE DE LA DURÉE JOURNALIÈRE (8H/JOUR)

L'audit des sources de données montre la hiérarchie suivante :
1. **Source Canonique BDD** :  
   Table `parametre_global`, clé **`SEUIL_HEURES_JOUR`** (`01_schema_core_securite.sql`, ligne 245) :
   ```sql
   (12, 'SEUIL_HEURES_JOUR', 'RH_GENERAL', '8', 'Durée normale d une journée de travail (utilisée par Pointage)', '2026-01-01', NULL, TRUE, FALSE)
   ```
2. **Lecture Service** :  
   Dans `PointageSyntheseService.java` (lignes 224-225 et 1412) :
   ```java
   BigDecimal seuilHeuresJour = ParametreGlobal.getBigDecimal(
       params, "SEUIL_HEURES_JOUR", dureeHebdoLegale.divide(BigDecimal.valueOf(5), 2, RoundingMode.HALF_UP));
   ```
3. **Hardcodage Legacy / IHM** :  
   Dans `getSemainesPeriode` (ligne 1253) : `double seuilHeures = nbOuvrables * 8.0;`.  
   *Recommandation technique* : Remplacer à terme ce `8.0` en dur par l'appel à `seuilHeuresJour` pour garantir une totale cohérence si une filiale venait à passer à 7h ou 7.7h/jour.

---

## 14. INVENTAIRE DES TESTS & PREUVES D'EXÉCUTION

| Suite de Tests | Nombre | Résultat | Description |
| :--- | :---: | :---: | :--- |
| `PeriodSplittingAnalysisTest` | 5 | **PASS** | Caractérisation des 5 cas réels 26→25 (Lundi, Mercredi, Samedi début/fin). |
| `Phase1PointageHebdomadaireTest` | 18 | **PASS** | Tests unitaires du moteur hebdomadaire, des tranches HS 30/50, compensations et de `WeeklyPriorState`. |
| `Phase0CharacterizationTest` | 12 | **PASS** | Baseline de caractérisation des règles métier et tolérances de pointage. |
| `GrandWorkflowOhatraE2ETest` | Intégral | **PASS** | Test E2E complet sur la période réelle 26/12/2025 au 25/01/2026. |

---

## 15. DETTE TECHNIQUE IDENTIFIÉE & PLAN DE RÉSOLUTION

### La Dette : Divergence sur le Week-end de Début (26 Samedi / Dimanche)
* `getSemainesPeriode` saute les samedis et dimanches du 26/27 pour faire commencer la Semaine 1 au lundi ouvré 28.
* `calculerSyntheseEmploye` crée un groupe pour le lundi 21 avec un tronçon du 26 au 27 (0 jour ouvré).
* **Impact opérationnel actuel** : Faible, car les heures de week-end travaillées sont capturées en majoration Dimanche/Férié, mais cela crée une divergence cosmétique entre l'IHM et la synthèse détaillée.
* **Action requise (en Phase 2 / refactorisation)** : Aligner `calculerSyntheseEmploye` sur l'algorithme de saut de week-end de `getSemainesPeriode`.

---

## 16. VERDICT FORMEL FINAL

```
================================================================================
                    VERDICT DU CONTRE-AUDIT : PASS_WITH_DEBT
================================================================================
- Phase 1 (Moteur Hebdomadaire & WeeklyPriorState) est CONFORME, NÉCESSAIRE
  et RESPECTE l'architecture canonique et le modèle 26 M-1 -> 25 M.
- Aucune régression conceptuelle ou financière n'est introduite par WeeklyPriorState.
- La dette technique de synchronisation sur le week-end du 26 est documentée
  et prête à être traitée.
================================================================================
```

**Arrêt strict : En attente de l'arbitrage de l'utilisateur.**
