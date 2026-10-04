# Audit de la Gestion Temporelle — GALAXAM SIRH
**Date d'analyse :** 2026-10-03  
**Version du codebase analysé :** migrations jusqu'à `sp51`, schémas `01`–`06`, Java `PayrollService`, `CongeService`, `CongeTauxApplicableService`, schedulers

---

## 1. Inventaire des Objets Temporels

| Table / Entité | Champs temporels clés | Rôle métier |
|---|---|---|
| `paie_config_legale` | `date_debut`, `date_fin` | Configuration légale fiscale et sociale (SME, IRSA, CNaPS) |
| `bareme_irsa` | `date_debut`, `date_fin` | Tranches IRSA progressives par version |
| `ref_taux_majoration` | `date_debut`, `date_fin` | Taux HS, nuit, dimanche, férié |
| `ref_type_cotisation` | `date_debut`, `date_fin` | Taux CNaPS/OSTIE/OMSI/FMFP |
| `paie_rubrique` | `date_debut_effet`, `date_fin_effet` | Versions des rubriques de paie |
| `periode_paie` | `date_debut`, `date_fin`, `date_validation`, `date_transmission` | Période de paie mensuelle (cycle 26→25) |
| `bulletin_paie` | `date_calcul`, `date_validation`, `date_cloture`, `date_reouverture` | Cycle de vie d'un bulletin |
| `conge_periode_reference` | `date_debut`, `date_fin`, `date_cutover` | Période TCONGE (12 mois glissants par employé) |
| `conge_taux_applicable` | `date_effet`, `date_fin` | Taux journalier congé applicable à une date |
| `conge_journal_droit` | `annee`, `mois` | Grand-livre mensuel d'acquisition congé |
| `mouvement_solde_conge` | `date_mouvement` | Journal de tous les mouvements (ACQUISITION, PRISE, BONIFICATION…) |
| `demande_conge` | `date_debut`, `date_fin`, `date_demande`, `date_validation` | Demande individuelle |
| `contrat_travail` | `date_debut`, `date_fin`, `date_signature`, `date_fin_essai` | Contrat (bornes de validité) |
| `contrat_periode_essai` | `date_debut`, `date_fin_prevue`, `date_fin_reelle` | Période d'essai |
| `employe_affectation_carriere` | `date_debut`, `date_fin` | Affectation (site, salaire, poste, fonction) |
| `workflow_rh` | `date_demande`, `date_effet` | Événement RH avec date d'effet |
| `pointage_jour` | `date_pointage` | Pointage journalier d'un employé |
| `pointage_cumul_periode` | — (lié à `periode_paie`) | Snapshot de clôture de pointage |
| `site_indemnite_config` | `date_debut`, `date_fin` | Montants des indemnités par site |
| `employe_indemnite_derogation` | `date_debut`, `date_fin` | Dérogation individuelle d'indemnité |
| `ref_periode_chomee` | `date_debut`, `date_fin` | Périodes chômées exceptionnelles |
| `ref_calendrier_ferie` | `date_ferie` | Jours fériés (calendrier) |
| `paie_avance_acompte` | `date_demande`, `date_accord`, `date_paiement` | Avances / acomptes |
| `paie_pret_echeance` | `date_echeance` | Échéances de prêt |
| `paie_stc` | `date_depart`, `date_validation` | Solde de Tout Compte |
| `conge_expiration_examen` | `date_prochaine_revue`, `created_at` | Décision DAF sur droits candidats prescription |
| `notification_rh` | `date_creation`, `date_expiration`, `date_lecture` | Alertes échéances |
| `scheduler_execution_journal` | `started_at` | Journal d'exécution des tâches planifiées |

---

## 2. Date Métier de Résolution par Domaine

| Domaine | Date métier utilisée | Source |
|---|---|---|
| **Pointage journalier** | `date_pointage` (date civile du jour) | `pointage_jour.date_pointage` |
| **Période de paie** | `date_fin` de la période | `periode_paie.date_fin` — utilisé dans `mettreAJourJournalConge`, `IrsaBracketResolver`, `TaxConfigurationResolver` |
| **Config légale (IRSA, SME)** | `date_fin` de la période de paie | `PaieConfigLegale.findActiveAt(date_fin)` |
| **Barème IRSA** | `date_fin` de la période de paie | `IrsaBracketResolver.resolve(null, date_fin, c)` — conforme |
| **Taux de majoration HS** | Date civile unitaire du bucket | `TemporalRateResolver.resolveRates(date)` — **conforme, bucket par bucket** |
| **Taux ISI** | Date passée explicitement | `TaxConfigurationResolver.resolveIsiRate(date, c)` — conforme |
| **Acquisition congé** | `date_fin` de la période de paie → `annee`/`mois` extraits de `date_fin` | `PayrollService.mettreAJourJournalConge` — ⚠️ voir §6 |
| **Période de référence congé** | `date_fin` de la période de paie | `resoudrePeriodeReferenceStrict(idEmp, date_fin, c)` — conforme |
| **Taux journalier congé (TCONGE)** | Date d'acquisition (= `date_fin` paie) | `conge_taux_applicable.date_effet` |
| **STC** | `date_depart` | `paie_stc.date_depart` — conforme |
| **Indemnités de site** | Date du pointage journalier | `site_indemnite_config.date_debut ≤ date ≤ date_fin` — à vérifier en service |
| **Contrat actif** | Date concernée (paie ou pointage) | `employe_affectation_carriere` join `ref_statut_rh.code = 'ACTIF'` — ⚠️ voir §10 |
| **Bonification ancienneté** | `dateReference` passée au scheduler (= dernier jour du mois clos) | `AcquisitionCongeScheduler` → `executerBonificationMensuelle(moisClos)` — conforme |
| **Déclaration DNTS** | `date_fin` des périodes du trimestre | `DntsExportService.genererDonneesDntsTrimestre` — ⚠️ voir §8 |
| **Export ISI** | `date_fin` de la période | `EdiIsiExportService` → `exercice = dateFin.getYear()` — ⚠️ voir §8 |

---

## 3. Classification des Transitions

### Légende
- **A** = Renouvellement automatique
- **B** = Continuité automatique
- **C** = Nouvelle version obligatoire
- **D** = Action humaine obligatoire
- **E** = Erreur bloquante

| Objet | Transition | Classe | Commentaire |
|---|---|---|---|
| `conge_periode_reference` | Passage d'une année à l'autre (01/01) | **A** | Doit être créée automatiquement si absente — actuellement **manuelle** (risque §9) |
| `paie_config_legale` | Nouveau millésime légal (LF_2026) | **C** | Historique conservé, nouvelle entrée requise — ✅ déjà implémenté |
| `bareme_irsa` | Changement de tranches | **C** | Nouvelle version datée — ✅ implémenté avec `date_debut`/`date_fin` |
| `ref_taux_majoration` | Changement de taux HS | **C** | Idem — ✅ |
| `ref_type_cotisation` | Nouveau taux CNaPS | **C** | Nécessite nouvelle ligne — action humaine DAF |
| `paie_rubrique` | Nouvelle version d'une rubrique | **C** | Trigger `verifier_versions_rubrique` — ✅ chevauchement bloqué |
| `site_indemnite_config` | Changement de tarif indemnité | **C** | Nouvelle ligne avec `date_debut` — ✅ |
| `employe_affectation_carriere` | Changement de site, salaire, poste | **D** | Workflow RH — clôture ancienne affectation, création nouvelle — ✅ |
| `contrat_travail` | Nouveau contrat | **D** | Via workflow RH — ✅ |
| `periode_paie` | Ouverture nouveau mois | **D** | Création manuelle DAF — ✅ conforme |
| `mouvement_solde_conge ACQUISITION` | Fin de campagne paie | **A** | Déclenché par clôture — ✅ idempotent |
| `mouvement_solde_conge BONIFICATION` | Anniversaire | **A** | Scheduler cron 1er du mois — ✅ idempotent |
| `conge_journal_droit` | Mois de paie clôturé | **A** | UPSERT (idempotent) — ✅ |
| Clôture `conge_periode_reference` | 12e mois atteint | **A** | Auto-déclenchée en fin de mois 12 — ✅ |
| `paie_stc` | Départ employé | **D** | Saisie DAF, validation requise |
| Agrégats DNTS trimestriels | Fin T1/T2/T3/T4 | **A** | Calcul à la demande — ✅ pas de pré-agrégat persisté |
| Config légale expirée sans successeur | Toute date > `date_fin` | **E** | `IRSA_CONFIGURATION_MISSING` — ✅ bloquant |
| Période de référence manquante | Date paie sans période active | **E** | `TCONGE_REFERENCE_PERIOD_MISSING` — ✅ bloquant |

---

## 4. Actions Automatiques Définies

### Clôture de paie (transition `PAIE_VALIDEE`)
```
→ mettreAJourJournalConge(idPeriode) — acquisition congé
→ executerBonificationMensuelle(moisClos) — [SCHEDULER cron, 1er/mois]
→ pointage_cumul_periode gelé avant validation
→ bulletin_paie.statut → CLOTURE (immuable)
```

### 01/01/N (passage d'année)
```
→ conge_periode_reference : aucune création automatique !
   → RISQUE : si DAF oublie de créer la période pour un employé,
              le premier calcul de paie échoue avec TCONGE_REFERENCE_PERIOD_MISSING
→ Action requise : créer manuellement via API CongePeriodeReferenceController
→ Recommandation : créer une action ensureLeaveReferencePeriod(idEmploye, annee)
                  appelée à l'ouverture de la première période de l'année
```

### Début nouveau trimestre
```
→ Aucun agrégat pré-calculé persisté (DNTS calculé à la demande) — ✅
→ Pas d'ouverture automatique de "déclaration trimestrielle" (pas de table dédiée)
```

### Changement de site
```
→ workflow_rh.date_effet détermine la bascule
→ employe_affectation_carriere ancienne : date_fin = date_effet - 1 (manuel)
→ Nouvelle affectation : date_debut = date_effet
→ site_indemnite_config : résolu par date du pointage journalier (conforme)
→ site_cotisation : résolu par le site de l'affectation active (conforme)
```

---

## 5. Règles de Continuité — État

| Règle | Statut |
|---|---|
| Ne jamais supprimer l'historique | ✅ — immutabilité post-clôture (triggers `fn_bulletin_paie_immutabilite`) |
| Ne jamais fusionner des lots historiques | ✅ — UPSERT `conge_journal_droit` sur clé (id_employe, annee, mois) |
| Ne pas recopier valeur ancienne sans règle | ✅ — aucun `COALESCE(new, old)` métier détecté |
| Ne pas recalculer bulletins validés | ✅ — trigger bloque UPDATE sur CLOTURE |
| Ne pas appliquer config future à période passée | ✅ — toutes les requêtes utilisent `date_debut ≤ date AND (date_fin IS NULL OR date_fin ≥ date)` |

---

## 6. Passage de Mois — Analyse 26/01 → 25/02

### Pointage
- Chaque pointage est lié à `id_periode_paie`. Les deux périodes sont distinctes. ✅
- La frontière 26/01–25/02 est gérée par les `date_debut`/`date_fin` de la période. ✅

### Acquisition congé
- `annee` et `mois` extraits de `EXTRACT(YEAR FROM date_fin)` / `EXTRACT(MONTH FROM date_fin)`.
- Pour la période 26/12/2025–25/01/2026 : `date_fin = 25/01/2026` → `annee=2026`, `mois=1`. ✅ Correct.
- Pour la période 26/01/2026–25/02/2026 : `date_fin = 25/02/2026` → `annee=2026`, `mois=2`. ✅ Correct.

### Salaire
- `employe_affectation_carriere` : la résolution utilise `statut = 'ACTIF'`. **Attention** : si un changement de salaire a `date_debut = 01/02`, l'affectation avec l'ancien salaire doit avoir `date_fin = 31/01`. La vue `v_employe_actif_courant` ne filtre pas par date — elle prend l'affectation ACTIF courante, pas celle valide à la date métier. ⚠️

### Indemnités
- `site_indemnite_config` : résolution par `date_debut ≤ date AND (date_fin IS NULL OR date_fin ≥ date)`.
- Aucun index partiel ne vérifie l'unicité temporelle — risque de chevauchement silencieux. ⚠️

### Cotisations sociales
- `paie_config_legale` résolu par `date_fin` de la période. ✅
- Pour la période 26/12/2025–25/01/2026 : `LF_2024_2025` s'arrête au 31/12/2025, `LF_2026_JAN_FEB` commence au 01/01/2026. La `date_fin` de la période est le 25/01/2026, donc config `LF_2026_JAN_FEB` est appliquée. ✅ Intentionnel.

---

## 7. Passage d'Année — 26/12/2025 → 25/01/2026

### Scénario de test : période `2025-12` : 26/12/2025–25/01/2026

| Vérification | Comportement | Conformité |
|---|---|---|
| `annee` d'acquisition congé | `EXTRACT(YEAR FROM '2026-01-25') = 2026` | ✅ Rattaché à 2026 |
| Config légale | `LF_2026_JAN_FEB` (01/01/2026–28/02/2026) | ✅ Couvre 25/01/2026 |
| Barème IRSA | Tranches `LF_2026_JAN_FEB` (6 tranches) | ✅ |
| Taux HS | `TemporalRateResolver` par bucket (chaque jour civil) | ✅ Bucket 26/12 → `LF_2024_2025`, bucket 01/01 → `LF_2026_JAN_FEB` |
| Période de référence congé | Recherchée au 25/01/2026 | ✅ si période active couvre cette date |
| **Période référence congé manquante** | `TCONGE_REFERENCE_PERIOD_MISSING` — bloquant | ⚠️ Pas de création auto |
| Clôture de l'ancienne période T4 2025 | Côté pointage : snapshot gelé ✅ ; côté congé : journal mis à jour ✅ | ✅ |
| DNTS T4 2025 | `EXTRACT(YEAR FROM date_fin) = 2025` | ⚠️ Pour la période 26/12–25/01 : `date_fin=25/01/2026` → pas inclus dans T4 2025 |

> **Point critique** : La période 26/12/2025–25/01/2026 a `date_fin = 25/01/2026`.  
> → `EXTRACT(YEAR FROM date_fin) = 2026` → rattachée à l'année 2026 côté DNTS/acquisition.  
> → Ce bulletin **ne figure pas** dans l'agrégat T4 2025.  
> → **C'est le comportement intentionnel** du modèle : la date fin de période est la date métier de référence.  
> → **Documenter explicitement** cette règle dans le README ou dans la doc DAF.

---

## 8. Passage de Trimestre — T4 2025 → T1 2026

### DNTS (`DntsExportService`)

```java
// Résolution des périodes du trimestre :
WHERE EXTRACT(YEAR FROM date_fin) = annee
AND EXTRACT(MONTH FROM date_fin) BETWEEN moisDebut AND moisFin
```

**Conformité T4 2025 :** mois 10, 11, 12 de `date_fin`.
- Période 26/09–25/10 : `date_fin = 25/10`, mois=10 ✅
- Période 26/10–25/11 : `date_fin = 25/11`, mois=11 ✅
- Période 26/11–25/12 : `date_fin = 25/12`, mois=12 ✅
- **La période 26/12–25/01/2026 n'est PAS dans T4 2025 mais dans T1 2026** ✅ Correct par convention.

**Validation de cohérence dans le trimestre :**
```java
// Bloque si le plafond ou les taux sociaux changent dans le trimestre :
if (!plafondMensuel.equals(monthConfig.getPlafondCotisationMensuel()) || !rates.equals(ratesAt(date, c)))
    throw new IllegalStateException("Plafond ou taux sociaux modifiés dans le trimestre...");
```
✅ **Protection active** : empêche le mélange de deux configs légales dans un même trimestre.

⚠️ **Cas limite T4 2025** : Si les trois périodes (oct, nov, déc) sont toutes sous `LF_2024_2025`, ce guard passe. Mais la période 26/12–25/01 (rattachée à T1 2026) sera sous `LF_2026_JAN_FEB`. La déclaration T1 2026 devra alors vérifier la cohérence des configs de janvier, février et mars 2026 — potentiellement trois configs différentes ! Le guard actuel lèverait une exception si le plafond change entre janvier (`LF_2026_JAN_FEB`) et mars 2026 (`LF_2026`). **Conforme : l'exception est correcte — le DAF doit alors exporter T1 2026 mois par mois si les taux changent en cours de trimestre**.

---

## 9. Période Congés — Règles et Conformité

### Règle générale : 01/01/N → 31/12/N

**Implémentation actuelle** : `conge_periode_reference` est créée manuellement ou par le service `creerPeriodeReference` (via API).

| Règle | Conformité |
|---|---|
| Création automatique à la première acquisition de l'année | ❌ **Non implémenté** — BLOQUANT |
| Rattachement acquisition à l'année de `date_fin` paie | ✅ `EXTRACT(YEAR FROM date_fin)` |
| Pas de recopie des anciens stocks | ✅ aucune copie automatique |
| Conservation des lots antérieurs | ✅ FIFO (`PurgeFifoEngine`) |
| FIFO et valorisation historique | ✅ `conge_taux_applicable` par lot daté |

### 🔴 Écart identifié : création auto de période de référence

**RG attendue** : à la première validation de paie de l'année N, si aucune période de référence n'existe pour l'employé à la date `date_fin`, créer automatiquement une période 01/01/N–31/12/N (ou depuis la date d'embauche si < 01/01/N).

**Comportement actuel** : `resoudrePeriodeReferenceStrict` lève `TCONGE_REFERENCE_PERIOD_MISSING`. Bloquant sans création automatique préalable.

**Action nécessaire** : Implémenter `ensureLeaveReferencePeriod(idEmploye, dateMoisPaie, Connection)` appelé avant `resoudrePeriodeReferenceStrict` dans `mettreAJourJournalConge`.

---

## 10. Contrats / Salaires / Affectations

### Vue `v_employe_actif_courant`

```sql
JOIN employe_affectation_carriere eac ON e.id_employe = eac.id_employe
JOIN ref_statut_rh rsa ON eac.id_statut_rh = rsa.id_statut_rh AND rsa.code = 'ACTIF'
WHERE e.est_actif = TRUE;
```

⚠️ **Problème** : Cette vue ne filtre pas `eac.date_debut ≤ date_metier AND (eac.date_fin IS NULL OR eac.date_fin ≥ date_metier)`. Elle retourne l'affectation **actuellement active** (statut ACTIF), pas celle **valide à la date de paie**. Si un employé change de site ou de salaire au 01/01, la vue donne toujours le salaire et site actuels, non ceux de la période concernée.

**Impact** : Pour un recalcul ou une réouverture d'un bulletin d'une période passée, le `salaire_base` utilisé serait celui du contrat actuel, pas de l'époque.

**Conformité** : ❌ Non conforme pour les recalculs historiques.

**Action** : La vue est utilisée pour l'affichage courant — conforme pour cet usage. Pour le calcul de paie, le `PayrollService` doit résoudre l'affectation valide à la `date_fin` de la période.

> **Vérifier** : `PayrollService` utilise-t-il `v_employe_actif_courant` ou résout-il l'affectation par date ? Vérification à faire sur la construction du `PayrollCalculationInput`.

---

## 11. Référentiels Datés — Conformité des Requêtes

| Référentiel | Requête de résolution | Conforme |
|---|---|---|
| `paie_config_legale` | `date_debut ≤ date AND (date_fin IS NULL OR date_fin ≥ date)` | ✅ |
| `bareme_irsa` | Idem + vérification séquence tranches | ✅ |
| `ref_taux_majoration` | Idem (par bucket via `TemporalRateResolver`) | ✅ |
| `ref_type_cotisation` | `date_debut ≤ date AND (date_fin IS NULL OR date_fin ≥ date)` | ✅ (à vérifier en service) |
| `site_indemnite_config` | `date_debut ≤ date AND (date_fin IS NULL OR date_fin ≥ date)` | ✅ (index partiel sur actif, pas sur date) |
| `conge_periode_reference` | `date_debut ≤ date AND date_fin ≥ date AND statut = 'OUVERTE'` | ✅ |
| `conge_taux_applicable` | `date_effet ≤ date AND (date_fin IS NULL OR date_fin > date)` | ✅ |
| `employe_affectation_carriere` | Statut ACTIF seulement, **pas de filtre date** | ⚠️ (usage courant OK, historique non) |

---

## 12. Gestion des Chevauchements

| Objet | Mécanisme | Statut |
|---|---|---|
| `paie_rubrique` | Trigger `verifier_versions_rubrique` — daterange overlap | ✅ Bloquant |
| `bareme_irsa` | UK `(id_config_legale, numero_tranche, date_debut)` + vérification séquence | ✅ |
| `conge_periode_reference` | Check `AMBIGUOUS_CONFIGURATION` dans `resoudrePeriodeReferenceActive` | ✅ Bloquant |
| `paie_config_legale` | Pas de contrainte SQL d'unicité temporelle | ⚠️ Risque de deux configs actives simultanément |
| `site_indemnite_config` | Pas de contrainte temporelle | ⚠️ Risque chevauchement silencieux |
| `employe_affectation_carriere` | Pas de contrainte temporelle | ⚠️ Peut avoir deux affectations ACTIF sans date_fin |

**Action recommandée** pour `paie_config_legale` :
```sql
-- Ajouter un trigger d'exclusion temporelle (PostgreSQL extension btree_gist)
CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE paie_config_legale ADD CONSTRAINT uk_config_legale_periode
    EXCLUDE USING gist (daterange(date_debut, date_fin, '[)') WITH &&);
```

---

## 13. Gestion des Trous

| Objet | Comportement si trou | Conformité |
|---|---|---|
| `paie_config_legale` | `IRSA_CONFIGURATION_MISSING` — exception bloquante | ✅ |
| `bareme_irsa` | `IRSA_CONFIGURATION_MISSING` — exception bloquante | ✅ |
| `conge_periode_reference` | `TCONGE_REFERENCE_PERIOD_MISSING` — exception bloquante | ✅ (mais pas de création auto) |
| `site_indemnite_config` | À vérifier dans le service — probable `null` silencieux | ⚠️ |
| Taux ISI | `TAX_CONFIGURATION_MISSING` — exception bloquante | ✅ |

---

## 14. Actions Automatiques Idempotentes

| Action automatique | Mécanisme d'idempotence | Statut |
|---|---|---|
| Acquisition congé mensuelle | `UPSERT ON CONFLICT (id_employe, annee, mois)` | ✅ |
| Bonification ancienneté | `anyMatch(m -> "BONIFICATION" && m.date().getYear() == ref.getYear())` | ✅ |
| Clôture période de référence | `IF statut = 'CLOTUREE' RETURN` | ✅ |
| Gel snapshot pointage | `UNIQUE (id_employe, id_periode_paie)` sur statut GELE | ✅ |
| Création période référence congé | ❌ Pas de logique d'idempotence (pas de création auto) | ❌ |

---

## 15. Fallbacks Silencieux — Inventaire

| Code | Pattern détecté | Verdict |
|---|---|---|
| `CongeService.executerBonificationMensuelle` L382 | `dateReference != null ? dateReference : LocalDate.now()` | ⚠️ Fallback sur date courante acceptable pour le scheduler (contexte cron) |
| `CongeExpirationExamenService` L25 | `dateReference != null ? dateReference : LocalDate.now()` | ⚠️ Idem — API consultation |
| `RefPoolCongeAnnuel` L175 | `annee != null ? annee : Year.now().getValue()` | ⚠️ Fallback sur année courante — à documenter explicitement |
| `CongeCalculateurEngine` L230 | `dateDemande != null ? dateDemande : LocalDate.now()` | ⚠️ OK pour simulation temps réel |
| `PayrollExecutionEngine` | Aucun fallback silencieux détecté | ✅ |
| `IrsaBracketResolver` | Exception bloquante si vide | ✅ |
| `TaxConfigurationResolver` | Exception bloquante si absent ou ambigu | ✅ |

---

## 16. États et Transitions

### Bulletin de Paie
```
CALCULE → VALIDE → CLOTURE
CALCULE → ANNULE
CLOTURE → ROUVERT (avec motif ≥ 5 chars, trigger)
ROUVERT → CLOTURE (recalcul)
```
Actions déclenchées à `CLOTURE` : acquisition congé, gel snapshot, alimentation DNTS.

### Période de Paie
```
BROUILLON → EN_COURS → A_VALIDER → TRANSMIS_DAF → VALIDEE → PAIE_VALIDEE → CLOTUREE
```
Historique dans `periode_paie_transition`. ✅

### Période de Référence Congé
```
OUVERTE → CLOTUREE (auto au 12e mois) → ARCHIVEE (manuel)
```

### Workflow RH
```
BROUILLON → PLANIFIE → A_SIGNER → ACTIF → HISTORISE / CLOTURE / ROMPU / ANNULE
```

### Demande de Congé
```
BROUILLON → EN_ATTENTE → VALIDE → (ANNULE)
BROUILLON → EN_ATTENTE → REFUSE
```

---

## 17. Matrice Actions Auto vs Validation Humaine

| Action | Automatique ? | Validation humaine ? | Réversible ? | Historisée ? |
|---|---|---|---|---|
| Création période congé annuelle | ❌ Actuellement manuelle | Non (DAF) | Non | ✅ |
| Acquisition congé mensuelle | ✅ AUTO (clôture paie) | Non | Non (immuable) | ✅ |
| Bonification ancienneté | ✅ AUTO (cron 1er/mois) | Non | Non | ✅ |
| Clôture période de référence | ✅ AUTO (12e mois) | Non | Non | ✅ |
| Validation bulletin | ❌ MANUEL | ✅ DAF | ✅ (réouverture) | ✅ |
| Clôture bulletin | ❌ MANUEL | ✅ DAF | ✅ (motif) | ✅ |
| Correction cutover | ❌ MANUEL | ✅ DAF | Non | ✅ |
| Agrégation DNTS trimestrielle | ✅ AUTO (calcul à demande) | ✅ DAF (export officiel) | N/A | N/A |
| Déclaration officielle DNTS | ❌ MANUEL | ✅ DAF | Non | ✅ |
| STC | ❌ MANUEL | ✅ DAF | Non | ✅ |
| Gel snapshot pointage | ✅ AUTO (validation période) | Non | ✅ (nouvelle version) | ✅ |
| Ouverture période de paie | ❌ MANUEL | ✅ DAF | Non | ✅ |
| Changement salaire/site | ❌ MANUEL | ✅ RH | Non | ✅ |

---

## 18. Tests BDD Obligatoires — Plan

> **Rappel** : Tous les tests doivent être exécutés sur `sirh_galaxam_test`.
> Vérifier `current_database()` avant toute migration.

### T1 — Passage de mois standard
```
Période : 26/11/2025 → 25/12/2025
Vérifier :
  - config_legale = LF_2024_2025 (date_fin = 25/12/2025) ✓
  - bareme_irsa : 5 tranches LF_2024_2025 ✓
  - annee=2025, mois=12 dans conge_journal_droit ✓
  - taux_majoration HS 30% LF_2024_2025 ✓
```

### T2 — Passage d'année (chevauchant deux millésimes)
```
Période : 26/12/2025 → 25/01/2026
Vérifier :
  - config_legale = LF_2026_JAN_FEB (date_fin = 25/01/2026) ✓
  - bareme_irsa : 6 tranches LF_2026_JAN_FEB ✓
  - annee=2026, mois=1 dans conge_journal_droit ✓
  - Taux HS bucket 26/12/2025 : LF_2024_2025 (30%) ✓
  - Taux HS bucket 01/01/2026 : LF_2026_JAN_FEB (30%) ✓
  - DNTS T4 2025 : cette période NOT IN (date_fin = 25/01/2026, YEAR=2026) ✓
  - DNTS T1 2026 : cette période IN (date_fin = 25/01/2026, YEAR=2026) ✓
  - conge_periode_reference pour employé : doit couvrir 25/01/2026 → sinon BLOQUANT
```

### T3 — Passage de trimestre T4→T1
```
Périodes T4 2025 : 26/09→25/10, 26/10→25/11, 26/11→25/12
Périodes T1 2026 : 26/12→25/01, 26/01→25/02, 26/02→25/03
Vérifier DNTS T4 2025 :
  - 3 périodes, toutes sous LF_2024_2025 ✓
  - Pas de mélange avec T1 2026 ✓
Vérifier DNTS T1 2026 :
  - 3 périodes : janv (LF_2026_JAN_FEB), fév (LF_2026_JAN_FEB), mars (LF_2026)
  - Guard "taux différents" → doit lever exception ✓
  - Comportement attendu : export T1 2026 impossible en un seul fichier si config change en mars
```

### T4 — Changement de salaire au 01/01
```
Affectation A : salaire=3 000 000, date_debut=01/01/2025, date_fin=31/12/2025
Affectation B : salaire=3 300 000, date_debut=01/01/2026, date_fin=NULL
Période 26/12→25/01 : salaire de référence = ??
→ Dépend de comment PayrollService résout l'affectation. À tester.
```

### T5 — Config légale expirée
```
date_fin = 31/12/2025 → date_metier = 01/01/2026
→ IRSA_CONFIGURATION_MISSING doit être levée ✓
```

### T6 — Config légale future
```
date_debut = 01/01/2026 → date_metier = 31/12/2025
→ Config non applicable ✓ (date_debut > date_metier)
```

### T7 — Config manquante (trou)
```
Supprimer LF_2026_JAN_FEB, calculer au 15/02/2026
→ IRSA_CONFIGURATION_MISSING ✓
```

### T8 — Config chevauchante
```
Insérer une 4e config_legale couvrant 01/01/2026–30/06/2026
→ AMBIGUOUS_CONFIGURATION (si guard activé) ✓
→ Actuellement pas de contrainte SQL → risque
```

### T9 — Nouvelle période congé
```
Employé sans conge_periode_reference en 2026
Calculer paie période 26/12→25/01/2026
→ TCONGE_REFERENCE_PERIOD_MISSING ✓ attendu (et bloquant)
→ Créer période 01/01/2026–31/12/2026
→ Recalculer → succès
```

---

## 19. Écarts Identifiés — Actions Requises

| # | RG attendue | Comportement actuel | Conforme | Action nécessaire |
|---|---|---|---|---|
| 1 | Création auto période référence congé N à la 1ère paie | Exception BLOQUANTE | ❌ | Implémenter `ensureLeaveReferencePeriod` idempotent |
| 2 | Guard chevauchement temporel `paie_config_legale` | Pas de contrainte SQL | ⚠️ | Ajouter trigger ou EXCLUDE USING gist |
| 3 | Guard chevauchement `site_indemnite_config` | Pas de contrainte temporelle | ⚠️ | Ajouter contrainte d'unicité temporelle |
| 4 | Résolution affectation par date métier (recalcul historique) | `v_employe_actif_courant` sans filtre date | ⚠️ | Clarifier usage (vue OK pour affichage courant seulement) |
| 5 | DNTS T1 2026 avec changement config en mars | Exception levée → export par mois | ✅ (conforme) | NE PAS MODIFIER — documenter |
| 6 | Documenter convention date_fin = date métier | Non documenté explicitement | ℹ️ | Ajouter dans README/doc DAF |
| 7 | Bucket HS par date civile (traversée 31/12→01/01) | `TemporalRateResolver` par bucket | ✅ | NE PAS MODIFIER |
| 8 | Blocage recalcul bulletin clôturé | Trigger `fn_bulletin_paie_immutabilite` | ✅ | NE PAS MODIFIER |
| 9 | Idempotence acquisition congé | UPSERT `(id_employe, annee, mois)` | ✅ | NE PAS MODIFIER |
| 10 | Idempotence bonification | Guard `getYear() == ref.getYear()` | ✅ | NE PAS MODIFIER |

---

## 20. Rapport Final — Matrice de Synthèse

| Transition | Date métier | Action auto | Action manuelle | Historisation | Statut |
|---|---|---|---|---|---|
| Passage de mois (paie) | `date_fin` période | Acquisition congé, gel snapshot | Validation DAF | ✅ | ✅ Conforme |
| Passage d'année 31/12→01/01 | `date_fin` période | Aucune (création période ref manquante) | Création période ref congé | ✅ | ❌ Écart |
| Passage trimestre T4→T1 | `date_fin` des périodes | Aucun (agrégat à la demande) | Export DNTS DAF | N/A | ✅ Conforme |
| Changement config légale (LF_2026) | `date_debut` de la nouvelle config | Résolution automatique par date | Saisie admin | ✅ | ✅ Conforme |
| Changement barème IRSA | Date bucket / date_fin période | Résolution auto | Saisie admin | ✅ | ✅ Conforme |
| Changement taux HS | Date civile bucket | `TemporalRateResolver` par jour | Saisie admin | ✅ | ✅ Conforme |
| Changement salaire/site | `date_effet` workflow RH | Aucune | Workflow RH → DAF | ✅ | ✅ Conforme (usage courant) |
| Clôture période référence congé (12 mois) | Mois 12 atteint | Clôture auto | Aucune | ✅ | ✅ Conforme |
| Config légale manquante | Date de calcul | Erreur `CONFIGURATION_MISSING` | Saisie admin | N/A | ✅ Conforme |
| Config légale chevauchante | Date de calcul | Erreur `AMBIGUOUS_CONFIGURATION` | Saisie admin | N/A | ⚠️ Partiel (pas de garde SQL) |
| STC | `date_depart` | Aucune | Saisie DAF | ✅ | ✅ Conforme |
| Bonification ancienneté | Anniversaire embauche | Scheduler cron idempotent | Aucune | ✅ | ✅ Conforme |

---

## Points Métier Encore Ouverts

1. **`ensureLeaveReferencePeriod`** : Définir les règles exactes de création auto : bornes 01/01/N–31/12/N ou depuis date d'embauche si embauchée en cours d'année. Mode de reprise (`NEW_HIRE` vs `FULL_HISTORY`).

2. **Résolution affectation historique** : Confirmer que `PayrollService` résout le salaire de base depuis `employe_affectation_carriere` par date métier (pas via la vue), et non depuis `v_employe_actif_courant`.

3. **Guard temporel `paie_config_legale`** : Arbitrage sur l'utilisation de `btree_gist` vs trigger applicatif.

4. **Période 26/12→25/01 dans DNTS** : Documenter formellement dans la doc DAF que cette période est rattachée à l'année 2026 (T1 2026), pas à l'année 2025.

5. **DNTS T1 2026 avec LF_2026 en mars** : Confirmer avec la DAF si l'export trimestriel doit être fractionné en deux fichiers (janv–fév vs mars) ou si une exception est acceptable avec export mois par mois.
