# PHASE 2A-3C — SÉCURISATION PURGE / EXPIRATION DES DROITS DE CONGÉ & FIFO

**Date :** 17 Septembre 2026  
**Auteur :** Antigravity Engine (Pair-Programming avec l'équipe GALAXAM)  
**Document Unique de Référence :** `PHASE_2A_3C_LEAVE_EXPIRATION_HARDENING_2026-09-17.md`  
**Statut :** VALIDÉ (Verdict : `PHASE_2A_3C_LEAVE_EXPIRATION_HARDENING_PASS`)

---

## 1. BASELINE

Cette phase fait suite immédiate au succès de la Phase 2A-3B :
- `PHASE_2A_3B_LEAVE_INITIALIZATION_AND_FRONTEND_2026-09-17.md` (`PHASE_2A_3B_LEAVE_INITIALIZATION_PASS`).
- Découplage complet de Pointage vis-à-vis de `NBCONGE` financier.
- Initialisation des soldes historiques sans passé inventé via `AJUSTEMENT` tagué `[SOLDE_INITIAL]`.
- Non-recalcul frontend de `NBCONGE` et `TCONGE`.

### Métriques d'entrée (Phase 2A-3B)
- Tests Backend : 447 tests PASS
- Tests Frontend : 22 tests PASS
- PostgreSQL Réel : PASS
- Vite Build : PASS
- `git diff --check` : PASS

### Métriques de sortie (Phase 2A-3C)
- **Tests Backend : 454 tests PASS (+7 tests critiques PostgreSQL)**
- **Tests Frontend : 26 tests PASS (+4 tests Vitest)**
- **PostgreSQL Réel : PASS (100% des cas A à G validés en conditions réelles)**
- **Vite Build : PASS (482 ms)**
- **`git diff --check` : PASS (0 warning, 0 espace résiduel)**

---

## 2. PROBLÈMES IDENTIFIÉS & PRINCIPES CANONIQUES

### 2.1 Distinction Fondamentale : Date de Constat ≠ Date d'Acquisition
En Phase 2A-3B, le système permet de reprendre un solde de départ certifié par Galaxam :
$$\text{SOLDE\_INITIAL} = 17.5\text{ jours}, \quad \text{dateArrete} = 31/08/2026$$
- **Risque critique :** Interpréter `dateArrete` comme une date d'acquisition des droits et en déduire une date de prescription triennale artificielle ($31/08/2026 + 3\text{ ans} = 31/08/2029$).
- **Réalité métier :** La date d'arrêté atteste seulement que : *"Au 31/08/2026, l'employé possède 17.5 jours."* L'âge individuel de ces jours, leur décomposition mensuelle ou annuelle sont **inconnus**.
- **Principe canonique :**
  $$\text{AGE\_DROIT} = \text{UNKNOWN} \implies \text{NON\_PURGEABLE\_AUTOMATICALLY}$$
  Tout droit dont l'ancienneté d'acquisition réelle est inconnue ne doit **jamais** être expiré automatiquement par le scheduler de purge.

### 2.2 Droits Datés vs Droits à Ancienneté Inconnue
| Type de Droit | Exemple | Provenance & Période | Comportement vis-à-vis de la Purge |
|---|---|---|---|
| **DROIT_DATE** | `ACQUISITION` (+2.5j), `BONIFICATION` (+1j) | Connues avec exactitude (`date_mouvement` d'acquisition) | Éligible à l'extinction par prescription si $\text{âge} > 36\text{ mois}$ |
| **DROIT_AGE_INCONNU** | `AJUSTEMENT` `[SOLDE_INITIAL]` (+17.5j) | Date de constat connue, antériorité d'acquisition inconnue | **Exclu de la purge automatique**. Ne peut être débité que par consommation réelle (`PRISE`) ou rectification manuelle RH. |
| **AJUSTEMENT_ORDINAIRE** | `AJUSTEMENT` régularisation (+2.0j) | Rectification opérationnelle RH | Exclu de la purge automatique des droits acquis. |

---

## 3. RUNTIME DE PURGE ACTUEL & CLASSIFICATION DU DÉLAI "3 ANS"

### 3.1 Audit du Call Graph Actuel
```text
PurgeCongeScheduler (@Scheduled 02:00 ou POST /api/conges/purge-expiration)
      │
      ▼
ParametreGlobal.loadActiveMap(c)
      │  DUREE_PRESCRIPTION_SOLDE_CONGE_MOIS (Défaut : 36)
      ▼
Calcul du seuil : seuilPrescription = dateReference - 36 mois
      │
      ▼
SELECT id_employe, type_mouvement, nb_jours, date_mouvement, commentaire
FROM mouvement_solde_conge
      │
      ▼
Agrégation mémoire (EmployeSoldeAccumulateur) :
- Droits datés anciens : (ACQUISITION | BONIFICATION) & dateMvt < seuil
- Consommations nettes : PRISE + (AJUSTEMENT si < 0) - ANNULATION
- Droits déjà expirés : EXPIRATION (< 0)
      │
      ▼
PurgeFifoEngine.calculerJoursAExpier(acquisAnciens, totalPris, totalDejaExpire)
      │
      ▼ Si joursAExpirer > 0 :
INSERT INTO mouvement_solde_conge (id_employe, 'EXPIRATION', -joursAExpirer, dateReference, ...)
      │
      ▼
NotificationRhService.creer (Alerte HAUTE "Congés expirés purgés (FIFO)")
```

### 3.2 Classification Juridique du Délai de 36 Mois (3 Ans)
Le délai de 36 mois est paramétré en base dans `parametre_global` (`DUREE_PRESCRIPTION_SOLDE_CONGE_MOIS`).
Bien que la documentation historique cite l'Article 85 du Code du Travail Malagasy, l'Article 85 traite principalement de l'ouverture du droit de jouissance (12 mois de service) et de la durée du congé (2.5 jours par mois). La prescription triennale de l'action en réclamation de salaire/congé relève du régime général de prescription des créances de travail (Art. 138).
- **Classification formelle :**
  - Durée 36 mois : `CURRENT_RUNTIME` / `CURRENT_GALAXAM_RULE`.
  - Application FIFO : `CURRENT_RUNTIME` / `CURRENT_GALAXAM_RULE`.
- En l'absence de texte législatif imposant l'effacement comptable automatique d'un droit acquis non pris sans mise en demeure ou accord d'entreprise, la qualification reste `CURRENT_GALAXAM_RULE` et n'est pas qualifiée d'obligation légale contraignante indiscutable.

---

## 4. CORRECTIONS TECHNIQUES APPLIQUÉES

### 4.1 Sécurisation de `PurgeCongeScheduler.java`
1. **Protection stricte de `[SOLDE_INITIAL]` :**
   Seuls les mouvements `ACQUISITION` et `BONIFICATION` sont candidats à l'accumulation dans `acc.acquisAnciens`. Tout `AJUSTEMENT` positif (dont `[SOLDE_INITIAL]`) est formellement exclu de l'expiration automatique :
   ```java
   // Invariant Canonique Phase 2A-3C :
   if (("ACQUISITION".equals(typeMouvement) || "BONIFICATION".equals(typeMouvement)) && dateMvt.isBefore(seuilPrescription)) {
       acc.acquisAnciens += nbJours;
   }
   ```
2. **Prise en compte des recrédits consécutifs aux `ANNULATION` :**
   Lorsqu'une demande de congé validée est annulée ultérieurement, le mouvement `ANNULATION` recrédite le compte. Le cumul des prises effectives est désormais corrigé pour éviter de sur-estimer la consommation :
   ```java
   } else if ("ANNULATION".equals(typeMouvement)) {
       acc.totalPris = Math.max(0.0, acc.totalPris - nbJours);
   }
   ```
3. **Surcharge temporelle pour tests et simulations :**
   Ajout de la méthode unitaire `public int purgerCongesExpires(LocalDate dateReference, Connection c) throws Exception`, permettant de tester des dates de cutover et des projections temporelles exactes sans altérer l'horloge système de la JVM.
4. **Ledger Append-Only Strict :**
   L'expiration génère un INSERT d'une écriture `EXPIRATION` négative horodatée à la date d'exécution/référence. Aucune ligne `ACQUISITION` historique n'est modifiée ou détruite.

### 4.2 Alignement de `NotificationRhScheduler.java`
La détection préventive des congés risquant d'expirer dans les 30 jours (`CONGE_EXPIRATION_PROCHE`) a été synchronisée avec `PurgeCongeScheduler` pour intégrer la gestion des mouvements `ANNULATION`.

### 4.3 Frontend : Distinction Sémantique & Filtrage Dédié
Dans `frontend/src/components/conges/MouvementsSoldeTable.jsx` :
- `getMouvementLabel(type, commentaire)` : Affiche le badge `"Solde initial (reprise historique)"` dès que le commentaire débute par `[SOLDE_INITIAL]`, et `"Ajustement manuel RH"` pour les ajustements ordinaires.
- Le sélecteur de filtre propose désormais :
  - `Tous les mouvements`
  - `Acquisition mensuelle (+2.5j)`
  - `Solde initial (reprise historique)` *(nouveau)*
  - `Ajustement manuel RH` *(nouveau, n'affiche que les ajustements hors reprise)*
  - `Bonification d'ancienneté`
  - `Prise de congé`
  - `Annulation de demande`
  - `Expiration triennale`
- **Garantie UX :** Aucune date d'expiration n'est inventée ni affichée pour les droits du solde initial.

---

## 5. ANALYSE FIFO & QUESTION MÉTIER DE PRIORITÉ D'APUREMENT

### 5.1 Question Soumise
Lorsqu'un salarié dispose d'un `SOLDE_INITIAL` à âge inconnu (ex: 10 jours) et d'acquisitions datées (ex: Septembre 2.5j), quelle génération de droits est apurée en premier lors d'une `PRISE` de 3 jours ?

### 5.2 Analyse des Deux Stratégies
- **Stratégie Recommandée (Priorité au stock historique) :**
  Les congés pris consomment en priorité le stock historique (`SOLDE_INITIAL`), puis les droits datés selon FIFO.
  *Avantage :* Évite que le stock non purgeable ne persiste indéfiniment pendant que les droits datés vieillissent et s'annulent.
- **Stratégie Alternative (FIFO strict sur droits datés seuls) :**
  Le stock historique reste isolé comme réserve d'âge indéterminé ; les prises consomment les droits du ledger chronologique standard.

### 5.3 Décision Phase 2A-3C
Conformément aux instructions des sections 10, 11 et 19 :
- En l'absence d'arbitrage officiel de la Direction Galaxam, **ce comportement n'est pas codé en dur unilatéralement**.
- Le statut est classifié : `BUSINESS_RULE_REQUIRED` / `PROPOSED_DESIGN`.
- Le runtime actuel maintient le stock `[SOLDE_INITIAL]` hors de tout calcul de purge automatique, garantissant qu'aucun droit n'est détruit par inadvertance.

---

## 6. TESTS POSTGRESQL RÉELS & RÉSULTATS D'EXÉCUTION

La classe de tests d'intégration réelle `Phase2a3cPurgeExpirationIntegrationTest` valide 100% des cas requis :

| Cas / Test | Scénario Testé | Résultat |
|---|---|---|
| **Test A** | Solde initial de 17.5j seul arrêté au 31/08/2026. Simulation de purge au 01/09/2029 (T + 37 mois) $\rightarrow$ 0 purge, solde reste 17.5j, 0 EXPIRATION. | **PASS** |
| **Test B** | Solde initial (10j) + Acquisition datée (2.5j du 01/08/2026). Purge au 01/09/2029 $\rightarrow$ Seuls les 2.5j datés sont purgés. Les 10j historiques restent intacts (solde final = 10.0j). | **PASS** |
| **Test C** | Solde initial arrêté au 31/08/2026 $\rightarrow$ `v_solde_conge_employe.derniere_acquisition` est NULL. Aucune date d'expiration artificielle 31/08/2029 n'est dérivée. | **PASS** |
| **Test D** | Acquisitions normales successives (2.5j en 01/2026, 2.5j en 02/2026) $\rightarrow$ Purge échelonnée exacte au passage des 36 mois respectifs (2.5j puis 2.5j). | **PASS** |
| **Test E** | Ledger Append-only $\rightarrow$ L'enregistrement initial d'acquisition reste intact en base. Une écriture négative `EXPIRATION` est insérée séparément. | **PASS** |
| **Test F** | Distinction nette entre `[SOLDE_INITIAL]` et `AJUSTEMENT` administratif ordinaire dans le ledger et les vues. | **PASS** |
| **Test G** | Prise de congé suivie d'une `ANNULATION` $\rightarrow$ Le recrédit de consommation est déduit de `totalPris`, permettant une purge exacte des droits non consommés. | **PASS** |

### Tests Unitaires Frontend (`purgeExpiration.test.js`)
4 tests Vitest validés :
- Distinction formelle des libellés de badges (`Solde initial (reprise historique)` vs `Ajustement manuel RH`).
- Filtrage dédié dans le tableau de bord des mouvements.
- Invariance de non-génération de fausse date d'expiration client.
- Respect strict de `totalExpire` calculé côté backend.

---

## 7. CARTOGRAPHIE DES OCCURRENCES & RECHERCHE DE DETTE

| Terme / Élément | Emplacement Principal | Classification | Traitement & Rôle |
|---|---|---|---|
| `[SOLDE_INITIAL]` | `CongeService.java`, `MouvementsSoldeTable.jsx` | `ACTIVE_CANONICAL` | Tag sémantique structurant distinguant la reprise historique |
| `AJUSTEMENT` | `MouvementSoldeConge.java`, `04_schema_conges.sql` | `ACTIVE_CANONICAL` | Type de grand-livre pour corrections RH manuelles |
| `ACQUISITION` | `AcquisitionCongeScheduler.java`, `CongeService.java` | `ACTIVE_CANONICAL` | Générateur mensuel de droits datés canoniques (+2.5j) |
| `EXPIRATION` | `PurgeCongeScheduler.java`, `mouvement_solde_conge` | `ACTIVE_CANONICAL` | Débit comptable d'extinction des droits acquis prescrits |
| `PurgeCongeScheduler` | `mg.galaxam.sirh.cron.PurgeCongeScheduler` | `ACTIVE_CANONICAL` | Batch nocturne d'extinction des droits datés prescrits |
| `PurgeFifoEngine` | `mg.galaxam.sirh.model.conge.engine.PurgeFifoEngine` | `ACTIVE_CANONICAL` | Moteur pur calculant l'imputation FIFO sur droits anciens |
| `DUREE_PRESCRIPTION_...` | `parametre_global` (36 mois) | `CURRENT_GALAXAM_RULE` | Paramètre de délai avant extinction des droits |
| `soldeInitial < 0` | `CongeService.initialiserSoldeHistorique` | `BUSINESS_RULE_REQUIRED` | Actuellement bloqué à $\ge 0$ ; besoin arbitrage pour soldes négatifs repris |

---

## 8. QUESTIONS MÉTIER RESTANTES (BUSINESS_RULE_REQUIRED)

1. **Priorité d'imputation des congés pris :**
   La Direction Galaxam doit-elle officialiser que les prises de congés apurent en premier lieu le solde initial de reprise historique avant les nouvelles acquisitions datées ?
2. **Reprise de soldes historiques négatifs :**
   Si un salarié migré depuis un fichier Excel présente un solde négatif (ex: -2.0 jours en raison de congés anticipés accordés avant la bascule SIRH), le système doit-il autoriser l'initialisation d'un solde négatif compensable sur les acquisitions futures ?

---

## 9. VERDICT GLOBAL

```text
================================================================================
PHASE 2A-3C — SÉCURISATION PURGE / EXPIRATION & DROITS HISTORIQUES
VERDICT : PHASE_2A_3C_LEAVE_EXPIRATION_HARDENING_PASS
================================================================================
[x] dateArrete != dateAcquisition (distinction garantie en base et en code)
[x] SOLDE_INITIAL sans historique n'expire pas automatiquement (NON_PURGEABLE)
[x] Aucune date d'acquisition fictive créée
[x] Aucune répartition historique fictive créée
[x] Droits datés (ACQUISITION) continuent de participer normalement à la purge FIFO
[x] Expiration strictement append-only (aucune mutation de l'acquisition d'origine)
[x] AJUSTEMENT ordinaire formellement distingué du SOLDE_INITIAL
[x] Aucune contamination TCONGE
[x] Aucune contamination NBCONGE
[x] Frontend n'invente aucune date d'expiration et propose un filtrage dédié
[x] PostgreSQL réel PASS (7/7 tests dans Phase2a3cPurgeExpirationIntegrationTest)
[x] Backend global PASS (454 tests, 0 échec, 0 régression)
[x] Frontend tests PASS (26 tests Vitest, 0 échec)
[x] Vite Build PASS (482 ms)
[x] git diff --check PASS
================================================================================
```
